const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot list sortCodes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("sortCodes").get());
});

test("Authenticated user: can create a valid sortCode", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await assertSucceeds(
    aliceDb.collection("sortCodes").doc("SORT123").set({
      code: "SORT123",
      ownerUid: ALICE_UID,
      title: "Secret Document",
      contentType: "text",
      textContent: "Hello world this is secret text",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    })
  );
});

test("Recipient: can retrieve active unexpired sortCode by code without authentication", async () => {
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  // Seed active sortCode
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("sortCodes").doc("CODE123").set({
      code: "CODE123",
      ownerUid: ALICE_UID,
      title: "Active note",
      contentType: "text",
      textContent: "Confidential message",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("sortCodes").doc("CODE123").get());
});

test("Recipient: cannot retrieve expired sortCode", async () => {
  const past = new Date(Date.now() - 3600 * 1000);

  // Seed expired sortCode
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("sortCodes").doc("EXPIRED1").set({
      code: "EXPIRED1",
      ownerUid: ALICE_UID,
      title: "Old note",
      contentType: "text",
      textContent: "Expired text",
      createdAt: new Date(Date.now() - 7200 * 1000),
      expiresAt: past,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("sortCodes").doc("EXPIRED1").get());
});

test("Recipient: cannot retrieve revoked sortCode", async () => {
  const future = new Date(Date.now() + 3600 * 1000);

  // Seed revoked sortCode
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("sortCodes").doc("REVOKED1").set({
      code: "REVOKED1",
      ownerUid: ALICE_UID,
      title: "Cancelled note",
      contentType: "text",
      textContent: "Revoked text",
      createdAt: new Date(),
      expiresAt: future,
      status: "revoked",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("sortCodes").doc("REVOKED1").get());
});

test("Owner: can list their own sortCodes", async () => {
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("sortCodes").doc("ALICE1").set({
      code: "ALICE1",
      ownerUid: ALICE_UID,
      contentType: "text",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("sortCodes").where("ownerUid", "==", ALICE_UID).get()
  );
});

test("Non-owner: cannot list other user's sortCodes", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("sortCodes").where("ownerUid", "==", ALICE_UID).get()
  );
});

test("Owner: can revoke their own sortCode", async () => {
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("sortCodes").doc("MYCODE").set({
      code: "MYCODE",
      ownerUid: ALICE_UID,
      contentType: "text",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("sortCodes").doc("MYCODE").update({
      status: "revoked",
    })
  );
});

test("Non-owner: cannot revoke or delete another user's sortCode", async () => {
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("sortCodes").doc("ALICE_CODE").set({
      code: "ALICE_CODE",
      ownerUid: ALICE_UID,
      contentType: "text",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("sortCodes").doc("ALICE_CODE").update({
      status: "revoked",
    })
  );
  await assertFails(
    bobDb.collection("sortCodes").doc("ALICE_CODE").delete()
  );
});

test("Image SortCode: Authenticated user can create image SortCode and retrieve without binary in DB", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await assertSucceeds(
    aliceDb.collection("sortCodes").doc("IMG789").set({
      code: "IMG789",
      ownerUid: ALICE_UID,
      title: "Contract Scan",
      contentType: "image",
      fileName: "contract_page1.jpg",
      fileSize: 1048576,
      fileMimeType: "image/jpeg",
      storagePath: "images/IMG789_contract_page1.jpg",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/bucket/o/images%2FIMG789.jpg",
      imageWidth: 1920,
      imageHeight: 1080,
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    })
  );

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const doc = await assertSucceeds(unauthDb.collection("sortCodes").doc("IMG789").get());
  if (doc.data().fileBase64) {
    throw new Error("fileBase64 should not be present in image doc!");
  }
});

test("Video SortCode: Authenticated user can create video metadata SortCode", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await assertSucceeds(
    aliceDb.collection("sortCodes").doc("VID123").set({
      code: "VID123",
      ownerUid: ALICE_UID,
      title: "Product Demo Video",
      contentType: "video",
      fileName: "demo.mp4",
      fileSize: 5242880,
      fileMimeType: "video/mp4",
      storagePath: "videos/VID123_demo.mp4",
      fileUrl: "https://firebasestorage.googleapis.com/v0/b/bucket/o/videos%2FVID123_demo.mp4",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
      viewsCount: 0,
    })
  );
});

test("File SortCode: Authenticated user can create document metadata with fileUrl", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await assertSucceeds(
    aliceDb.collection("sortCodes").doc("DOC456").set({
      code: "DOC456",
      ownerUid: ALICE_UID,
      title: "Confidential Report",
      contentType: "file",
      fileName: "report.pdf",
      fileSize: 2097152,
      fileMimeType: "application/pdf",
      storagePath: "files/DOC456_report.pdf",
      fileUrl: "https://firebasestorage.googleapis.com/v0/b/bucket/o/files%2FDOC456_report.pdf",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: true,
      passwordHash: "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
      downloadAllowed: false,
      viewsCount: 0,
    })
  );
});

test("Security: Unauthenticated user cannot create sortCodes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date();
  const future = new Date(Date.now() + 3600 * 1000);

  await assertFails(
    unauthDb.collection("sortCodes").doc("UNAUTH1").set({
      code: "UNAUTH1",
      ownerUid: "unauth_user",
      title: "Illegal Entry",
      contentType: "text",
      textContent: "Should fail",
      createdAt: now,
      expiresAt: future,
      status: "active",
      passwordProtected: false,
      downloadAllowed: true,
    })
  );
});

test("Subscription security: Client cannot write to subscription subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("subscription").doc("current").set({
      userId: ALICE_UID,
      status: "active",
      planId: "premium_annual",
      priceInr: 799,
    })
  );
});

test("Subscription security: Authenticated user can read own subscription seeded by admin", async () => {
  // Seed subscription with security rules disabled (simulating Admin SDK)
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("subscription").doc("current").set({
      userId: ALICE_UID,
      status: "active",
      planId: "premium_annual",
      priceInr: 799,
      billingPeriod: "annual",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subscription").doc("current").get()
  );

  // Bob cannot read Alice's subscription
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("subscription").doc("current").get()
  );
});


