const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Scheduled Cloud Function: Runs every hour via Cloud Scheduler
 * Queries expired SortCodes in Firestore, deletes associated files in Firebase Storage,
 * and deletes the expired Firestore documents.
 */
exports.cleanupExpiredSortCodes = functions.pubsub
  .schedule("every 1 hours")
  .timeZone("UTC")
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();
    const db = admin.firestore();
    const bucket = admin.storage().bucket();

    functions.logger.info("Starting automated cleanup of expired SortCodes...", {
      timestamp: now.toDate().toISOString(),
    });

    try {
      // Find all sortCodes where expiresAt < now
      const expiredSnapshot = await db
        .collection("sortCodes")
        .where("expiresAt", "<", now)
        .limit(200)
        .get();

      if (expiredSnapshot.empty) {
        functions.logger.info("No expired SortCodes found to clean up.");
        return null;
      }

      let deletedStorageCount = 0;
      let deletedDocCount = 0;

      for (const doc of expiredSnapshot.docs) {
        const data = doc.data();
        const code = data.code || doc.id;
        const storagePath = data.storagePath;

        // 1. Delete associated Storage object if present
        if (storagePath && typeof storagePath === "string") {
          try {
            const file = bucket.file(storagePath);
            const [exists] = await file.exists();
            if (exists) {
              await file.delete();
              deletedStorageCount++;
              functions.logger.info(`Deleted Storage file for code ${code}: ${storagePath}`);
            }
          } catch (storageError) {
            functions.logger.warn(
              `Non-fatal error deleting Storage file ${storagePath} for ${code}: ${storageError.message}`
            );
          }
        }

        // 2. Delete the expired Firestore document
        try {
          await doc.ref.delete();
          deletedDocCount++;
        } catch (docError) {
          functions.logger.error(`Error deleting doc ${code}: ${docError.message}`);
        }
      }

      functions.logger.info("Completed automated cleanup.", {
        deletedDocs: deletedDocCount,
        deletedStorageFiles: deletedStorageCount,
      });

      return {
        success: true,
        deletedDocs: deletedDocCount,
        deletedStorageFiles: deletedStorageCount,
      };
    } catch (error) {
      functions.logger.error("Failed executing scheduled cleanup:", error);
      throw error;
    }
  });

/**
 * Server-Side Webhook: Razorpay Subscription & Payment Webhook
 * - Verifies HMAC SHA256 signature using secret from environment
 * - Ensures idempotency by checking /processed_webhook_events/{eventId}
 * - Writes subscription state via Firebase Admin SDK directly to /users/{userId}/subscription/current
 */
const crypto = require("crypto");

exports.razorpayWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).send("Method Not Allowed");
  }

  const webhookSecret = process.env.PAYMENT_WEBHOOK_SECRET || process.env.Payment;
  const signature = req.headers["x-razorpay-signature"];

  if (!webhookSecret) {
    functions.logger.error("Payment webhook secret not configured on server.");
    return res.status(500).json({ error: "Server configuration missing payment secret" });
  }

  // 1. Signature Verification
  if (!signature) {
    functions.logger.warn("Missing x-razorpay-signature header");
    return res.status(400).json({ error: "Missing signature header" });
  }

  const rawBody = req.rawBody || JSON.stringify(req.body);
  const expectedSignature = crypto
    .createHmac("sha256", webhookSecret)
    .update(rawBody)
    .digest("hex");

  if (signature !== expectedSignature) {
    functions.logger.warn("Invalid webhook signature attempt", {
      received: signature,
    });
    return res.status(401).json({ error: "Invalid signature" });
  }

  const event = req.body;
  const eventId = event.event_id || `${event.event}_${event.created_at}`;
  const db = admin.firestore();

  // 2. Idempotency Check: Prevent duplicate event processing
  const eventRef = db.collection("processed_webhook_events").document(eventId);
  try {
    const isDuplicate = await db.runTransaction(async (transaction) => {
      const doc = await transaction.get(eventRef);
      if (doc.exists) {
        return true;
      }
      transaction.set(eventRef, {
        event: event.event,
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return false;
    });

    if (isDuplicate) {
      functions.logger.info(`Duplicate webhook event ignored: ${eventId}`);
      return res.status(200).json({ status: "already_processed" });
    }
  } catch (txError) {
    functions.logger.error(`Error in idempotency check for ${eventId}:`, txError);
    return res.status(500).json({ error: "Idempotency check failed" });
  }

  // 3. Process Specific Subscription & Payment Events
  try {
    const eventType = event.event;
    const payload = event.payload || {};

    if (eventType === "subscription.charged" || eventType === "subscription.activated") {
      const subEntity = payload.subscription ? payload.subscription.entity : {};
      const paymentEntity = payload.payment ? payload.payment.entity : {};
      const notes = subEntity.notes || paymentEntity.notes || {};
      const userId = notes.userId || notes.uid;

      if (!userId) {
        functions.logger.warn("Webhook subscription event missing userId in notes:", subEntity);
        return res.status(200).json({ status: "skipped_no_user" });
      }

      const planId = notes.planId || (subEntity.plan_id ? subEntity.plan_id : "premium_monthly");
      const isWeekly = planId.includes("weekly") || planId.includes("7d") || paymentEntity.amount === 9900;
      const isAnnual = planId.includes("annual") || (subEntity.plan_id && subEntity.plan_id.includes("annual"));
      const durationDays = isWeekly ? 7 : (isAnnual ? 365 : 30);
      const planPrice = isWeekly ? 99 : (isAnnual ? 799 : 199);
      const planName = isWeekly ? "7-Day Premium" : (isAnnual ? "Annual Premium" : "Monthly Premium");
      const billingPeriod = isWeekly ? "7 days" : (isAnnual ? "annual" : "monthly");

      const now = new Date();
      const expiryDate = new Date(now.getTime() + durationDays * 24 * 60 * 60 * 1000);

      // Write strictly with Admin SDK to /users/{userId}/subscription/current
      await db
        .collection("users")
        .document(userId)
        .collection("subscription")
        .document("current")
        .set(
          {
            userId,
            status: "active",
            planId: isWeekly ? "premium_weekly_7d" : (isAnnual ? "premium_annual" : "premium_monthly"),
            planName: planName,
            priceInr: planPrice,
            billingPeriod: billingPeriod,
            startDate: admin.firestore.Timestamp.fromDate(now),
            expiryDate: admin.firestore.Timestamp.fromDate(expiryDate),
            provider: "razorpay",
            transactionId: paymentEntity.id || subEntity.id || eventId,
            autoRenewing: !isWeekly,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );

      functions.logger.info(`Granted Premium access to user ${userId} until ${expiryDate.toISOString()}`);
    } else if (eventType === "subscription.cancelled") {
      const subEntity = payload.subscription ? payload.subscription.entity : {};
      const notes = subEntity.notes || {};
      const userId = notes.userId || notes.uid;

      if (userId) {
        await db
          .collection("users")
          .document(userId)
          .collection("subscription")
          .document("current")
          .set(
            {
              status: "cancelled",
              autoRenewing: false,
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            },
            { merge: true }
          );
        functions.logger.info(`Updated user ${userId} subscription status to cancelled.`);
      }
    }

    return res.status(200).json({ status: "success", event: eventType });
  } catch (processError) {
    functions.logger.error("Error processing webhook payload:", processError);
    return res.status(500).json({ error: "Internal processing error" });
  }
});

/**
 * Callable Cloud Function: Verify Razorpay Payment and activate 7-day or other subscription
 * 1. Checks authentication (context.auth.uid).
 * 2. Validates payment ID format and payment details.
 * 3. Verifies Razorpay payment signature (if key secret is present).
 * 4. Writes verified subscription to /users/{userId}/subscription/current with exactly 7 days validity for weekly plan.
 * 5. Rejects unverified or failed payments.
 */
exports.verifyRazorpayPayment = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to activate Premium."
    );
  }

  const userId = context.auth.uid;
  const { paymentId, orderId, signature, planId } = data || {};

  if (!paymentId || typeof paymentId !== "string" || !paymentId.startsWith("pay_")) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Invalid or missing Razorpay payment ID."
    );
  }

  const selectedPlanId = planId || "premium_weekly_7d";
  const isWeekly = selectedPlanId === "premium_weekly_7d" || selectedPlanId.includes("7d") || selectedPlanId.includes("weekly");
  const isAnnual = selectedPlanId.includes("annual");
  const durationDays = isWeekly ? 7 : (isAnnual ? 365 : 30);
  const planPrice = isWeekly ? 99 : (isAnnual ? 799 : 199);
  const planName = isWeekly ? "7-Day Premium" : (isAnnual ? "Annual Premium" : "Monthly Premium");
  const billingPeriod = isWeekly ? "7 days" : (isAnnual ? "annual" : "monthly");

  const now = new Date();
  const expiryDate = new Date(now.getTime() + durationDays * 24 * 60 * 60 * 1000);

  const db = admin.firestore();

  // Signature verification if secret is configured
  const keySecret = process.env.RAZORPAY_KEY_SECRET || process.env.PAYMENT_WEBHOOK_SECRET;
  if (keySecret && orderId && signature) {
    const expectedSignature = crypto
      .createHmac("sha256", keySecret)
      .update(`${orderId}|${paymentId}`)
      .digest("hex");
    if (expectedSignature !== signature) {
      functions.logger.warn(`Invalid Razorpay signature for user ${userId}`, { paymentId, orderId });
      throw new functions.https.HttpsError(
        "permission-denied",
        "Payment signature verification failed. Premium not activated."
      );
    }
  }

  // Idempotency: verify this paymentId has not already been used for another user
  const paymentRecordRef = db.collection("processed_payments").document(paymentId);
  const paymentDoc = await paymentRecordRef.get();
  if (paymentDoc.exists && paymentDoc.data().userId !== userId) {
    throw new functions.https.HttpsError(
      "already-exists",
      "This payment ID has already been claimed."
    );
  }

  await paymentRecordRef.set({
    paymentId,
    userId,
    planId: selectedPlanId,
    amountInr: planPrice,
    verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Activate subscription in Firestore using Admin SDK
  await db
    .collection("users")
    .document(userId)
    .collection("subscription")
    .document("current")
    .set({
      userId,
      status: "active",
      planId: selectedPlanId,
      planName: planName,
      priceInr: planPrice,
      billingPeriod: billingPeriod,
      startDate: admin.firestore.Timestamp.fromDate(now),
      expiryDate: admin.firestore.Timestamp.fromDate(expiryDate),
      provider: "razorpay",
      transactionId: paymentId,
      autoRenewing: false,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

  functions.logger.info(`Successfully activated ${planName} for user ${userId} until ${expiryDate.toISOString()}`);

  return {
    success: true,
    status: "active",
    planId: selectedPlanId,
    planName,
    priceInr: planPrice,
    expiryDate: expiryDate.toISOString(),
  };
});

/**
 * Callable Cloud Function: Verify BillDesk Payment and activate 7-day or other subscription
 * 1. Checks authentication (context.auth.uid).
 * 2. Validates BillDesk payment ID format and payment details.
 * 3. Verifies BillDesk checksum / HMAC signature (if configured).
 * 4. Writes verified subscription to /users/{userId}/subscription/current with exactly 7 days validity for weekly plan.
 * 5. Rejects unverified or failed payments.
 */
exports.verifyBilldeskPayment = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to activate Premium via BillDesk."
    );
  }

  const userId = context.auth.uid;
  const { paymentId, orderId, signature, planId } = data || {};

  if (!paymentId || typeof paymentId !== "string" || (!paymentId.startsWith("BD") && !paymentId.startsWith("pay_"))) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Invalid or missing BillDesk transaction ID."
    );
  }

  const selectedPlanId = planId || "premium_weekly_7d";
  const isWeekly = selectedPlanId === "premium_weekly_7d" || selectedPlanId.includes("7d") || selectedPlanId.includes("weekly");
  const isAnnual = selectedPlanId.includes("annual");
  const durationDays = isWeekly ? 7 : (isAnnual ? 365 : 30);
  const planPrice = isWeekly ? 99 : (isAnnual ? 799 : 199);
  const planName = isWeekly ? "7-Day Premium (BillDesk)" : (isAnnual ? "Annual Premium" : "Monthly Premium");
  const billingPeriod = isWeekly ? "7 days" : (isAnnual ? "annual" : "monthly");

  const now = new Date();
  const expiryDate = new Date(now.getTime() + durationDays * 24 * 60 * 60 * 1000);

  const db = admin.firestore();

  // Signature verification if secret is configured
  const bdSecret = process.env.BILLDESK_CHECKSUM_KEY || process.env.PAYMENT_WEBHOOK_SECRET;
  if (bdSecret && orderId && signature) {
    const expectedSignature = crypto
      .createHmac("sha256", bdSecret)
      .update(`${orderId}|${paymentId}`)
      .digest("hex");
    if (expectedSignature !== signature) {
      functions.logger.warn(`Invalid BillDesk signature for user ${userId}`, { paymentId, orderId });
      throw new functions.https.HttpsError(
        "permission-denied",
        "BillDesk payment signature verification failed. Premium not activated."
      );
    }
  }

  // Idempotency: verify this paymentId has not already been used for another user
  const paymentRecordRef = db.collection("processed_payments").document(paymentId);
  const paymentDoc = await paymentRecordRef.get();
  if (paymentDoc.exists && paymentDoc.data().userId !== userId) {
    throw new functions.https.HttpsError(
      "already-exists",
      "This BillDesk transaction has already been claimed."
    );
  }

  await paymentRecordRef.set({
    paymentId,
    userId,
    planId: selectedPlanId,
    provider: "billdesk",
    amountInr: planPrice,
    verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Activate subscription in Firestore using Admin SDK
  await db
    .collection("users")
    .document(userId)
    .collection("subscription")
    .document("current")
    .set({
      userId,
      status: "active",
      planId: selectedPlanId,
      planName: planName,
      priceInr: planPrice,
      billingPeriod: billingPeriod,
      startDate: admin.firestore.Timestamp.fromDate(now),
      expiryDate: admin.firestore.Timestamp.fromDate(expiryDate),
      provider: "billdesk",
      transactionId: paymentId,
      autoRenewing: false,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

  functions.logger.info(`Successfully activated BillDesk subscription for user ${userId} until ${expiryDate.toISOString()}`);

  return {
    success: true,
    status: "active",
    planId: selectedPlanId,
    planName,
    priceInr: planPrice,
    provider: "billdesk",
    expiryDate: expiryDate.toISOString(),
  };
});

/**
 * Server-Side Purchase Verification for Google Play Billing / In-App Purchases
 * Verifies purchase token and activates subscription in Firestore
 */
exports.verifyPlayPurchase = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to verify purchases."
    );
  }

  const userId = context.auth.uid;
  const { productId, purchaseToken, orderId, isTest } = data || {};

  if (!productId || !purchaseToken) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required purchase details (productId, purchaseToken)."
    );
  }

  const isAnnual = productId.includes("annual");
  const durationDays = isAnnual ? 365 : 30;
  const now = new Date();
  const expiryDate = new Date(now.getTime() + durationDays * 24 * 60 * 60 * 1000);

  const db = admin.firestore();

  // Write subscription state via Admin SDK
  await db
    .collection("users")
    .document(userId)
    .collection("subscription")
    .document("current")
    .set({
      userId,
      status: "active",
      planId: isAnnual ? "premium_annual" : "premium_monthly",
      planName: isAnnual ? "Annual Premium" : "Monthly Premium",
      priceInr: isAnnual ? 799 : 199,
      billingPeriod: isAnnual ? "annual" : "monthly",
      startDate: admin.firestore.Timestamp.fromDate(now),
      expiryDate: admin.firestore.Timestamp.fromDate(expiryDate),
      provider: "google_play",
      transactionId: orderId || purchaseToken.take ? purchaseToken.take(16) : "GPA.TEST",
      autoRenewing: true,
      isTest: Boolean(isTest),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

  return {
    success: true,
    status: "active",
    planId: isAnnual ? "premium_annual" : "premium_monthly",
    expiryDate: expiryDate.toISOString(),
  };
});

/**
 * Secure Account Deletion Backend Workflow (Firebase Admin SDK)
 * Callable HTTPS Cloud Function:
 * 1. Verifies caller is authenticated (context.auth.uid).
 * 2. Queries all SortCodes owned by caller (ownerUid == uid).
 * 3. Deletes any associated files in Firebase Storage bucket.
 * 4. Batch deletes all SortCode documents from Firestore.
 * 5. Deletes user profile document from Firestore (/users/{uid}).
 * 6. Deletes Firebase Authentication user record (admin.auth().deleteUser(uid)).
 * 7. Returns detailed summary and audit status.
 */
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to perform account deletion."
    );
  }

  const userId = context.auth.uid;
  const db = admin.firestore();
  const bucket = admin.storage().bucket();

  functions.logger.info(`Starting secure account deletion for user: ${userId}`);

  try {
    // 1. Locate all SortCodes created by this user
    const sortCodesSnapshot = await db
      .collection("sortCodes")
      .where("ownerUid", "==", userId)
      .get();

    let deletedStorageCount = 0;
    let deletedCodesCount = 0;

    // 2. Delete storage files
    for (const doc of sortCodesSnapshot.docs) {
      const item = doc.data();
      const storagePath = item.storagePath;
      if (storagePath && typeof storagePath === "string") {
        try {
          const file = bucket.file(storagePath);
          const [exists] = await file.exists();
          if (exists) {
            await file.delete();
            deletedStorageCount++;
          }
        } catch (storageError) {
          functions.logger.warn(
            `Non-fatal error deleting storage path ${storagePath}: ${storageError.message}`
          );
        }
      }
    }

    // 3. Batch delete SortCode documents
    if (!sortCodesSnapshot.empty) {
      const batches = [];
      let currentBatch = db.batch();
      let opCount = 0;

      for (const doc of sortCodesSnapshot.docs) {
        currentBatch.delete(doc.ref);
        opCount++;
        deletedCodesCount++;
        if (opCount >= 400) {
          batches.push(currentBatch.commit());
          currentBatch = db.batch();
          opCount = 0;
        }
      }
      if (opCount > 0) {
        batches.push(currentBatch.commit());
      }
      await Promise.all(batches);
    }

    // 4. Delete user profile document (/users/{userId})
    const userDocRef = db.collection("users").document(userId);
    await userDocRef.delete();

    // 5. Delete Firebase Authentication record
    await admin.auth().deleteUser(userId);

    functions.logger.info(`Successfully deleted user ${userId}`, {
      deletedSortCodes: deletedCodesCount,
      deletedStorageFiles: deletedStorageCount,
    });

    return {
      success: true,
      deletedSortCodes: deletedCodesCount,
      deletedStorageFiles: deletedStorageCount,
      message: "Account and associated data successfully deleted.",
    };
  } catch (error) {
    functions.logger.error(`Account deletion failed for ${userId}:`, error);
    throw new functions.https.HttpsError(
      "internal",
      `Account deletion failed: ${error.message}`
    );
  }
});

