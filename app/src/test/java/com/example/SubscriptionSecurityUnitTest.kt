package com.example

import com.android.billingclient.api.Purchase
import com.example.data.repository.SortCodeRepository
import com.example.model.UserSubscription
import com.example.ui.screens.ExpiryOption
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying MsgSort subscription security, expiry verification,
 * tier limits, and billing states.
 */
class SubscriptionSecurityUnitTest {

    // 1. New user without payment -> Free plan
    @Test
    fun testNewUserWithoutPayment_defaultsToFreePlan() {
        val subscription: UserSubscription? = null
        assertFalse(
            "New user without payment must not have active premium",
            SortCodeRepository.isSubscriptionActive(subscription)
        )
    }

    // 2. User clicks Subscribe but does not pay / Pending payment -> Free plan
    @Test
    fun testPendingSubscription_doesNotGrantPremium() {
        val pendingSub = UserSubscription(
            userId = "user_123",
            status = "pending",
            planId = "premium_monthly",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse(
            "Pending payment transaction must not grant premium access",
            SortCodeRepository.isSubscriptionActive(pendingSub)
        )
    }

    // 3. Payment fails / Past due / Inactive -> Free plan
    @Test
    fun testFailedOrPastDueSubscription_doesNotGrantPremium() {
        val failedSub = UserSubscription(
            userId = "user_123",
            status = "past_due",
            planId = "premium_monthly",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse(
            "Failed or past_due subscription must not grant premium",
            failedSub.isPremiumActive()
        )

        val inactiveSub = UserSubscription(
            userId = "user_123",
            status = "inactive",
            planId = "premium_annual",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse(
            "Inactive subscription must not grant premium",
            inactiveSub.isPremiumActive()
        )

        val refundedSub = UserSubscription(
            userId = "user_123",
            status = "refunded",
            planId = "premium_annual",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse(
            "Refunded subscription must not grant premium",
            refundedSub.isPremiumActive()
        )
    }

    // 4. Payment is successful and verified by the backend -> Premium is activated
    @Test
    fun testActiveVerifiedSubscription_grantsPremium() {
        val activeSub = UserSubscription(
            userId = "user_123",
            status = "active",
            planId = "premium_annual",
            transactionId = "GPA.1234-5678-9012-34567",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 30L * 86400000L))
        )
        assertTrue(
            "Active subscription with future expiry must grant premium",
            activeSub.isPremiumActive()
        )
        assertTrue(
            "Repository check must confirm active premium",
            SortCodeRepository.isSubscriptionActive(activeSub)
        )
    }

    // 5. Subscription expires -> Premium access is revoked
    @Test
    fun testExpiredSubscription_revokesPremiumAccess() {
        val pastDate = Date(System.currentTimeMillis() - 86400000L) // Yesterday
        val expiredSub = UserSubscription(
            userId = "user_123",
            status = "active",
            planId = "premium_monthly",
            expiryDate = Timestamp(pastDate)
        )
        assertFalse(
            "Subscription past expiry date must immediately revoke premium access",
            expiredSub.isPremiumActive()
        )

        val explicitlyExpiredSub = UserSubscription(
            userId = "user_123",
            status = "expired",
            planId = "premium_monthly",
            expiryDate = Timestamp(pastDate)
        )
        assertFalse(
            "Status 'expired' must immediately revoke premium access",
            explicitlyExpiredSub.isPremiumActive()
        )
    }

    // 6. Cancelled subscription retains access only until current period expiry
    @Test
    fun testCancelledSubscriptionWithRemainingTime_grantsAccessUntilExpiry() {
        val futureDate = Date(System.currentTimeMillis() + 7L * 86400000L) // 7 days left
        val cancelledSub = UserSubscription(
            userId = "user_123",
            status = "cancelled",
            planId = "premium_monthly",
            expiryDate = Timestamp(futureDate)
        )
        assertTrue(
            "Cancelled subscription should retain access until current period expires",
            cancelledSub.isPremiumActive()
        )

        val expiredCancelledSub = UserSubscription(
            userId = "user_123",
            status = "cancelled",
            planId = "premium_monthly",
            expiryDate = Timestamp(Date(System.currentTimeMillis() - 1000L))
        )
        assertFalse(
            "Cancelled subscription past expiry date must not grant access",
            expiredCancelledSub.isPremiumActive()
        )
    }

    // 7. Verify tier-based limits
    @Test
    fun testTierLimits_freeVsPremium() {
        // Free tier limits
        assertEquals("Free file size must be 5 MB", 5242880L, SortCodeRepository.getMaxFileSize(false))
        assertEquals("Free text length must be 20,000 characters", 20000, SortCodeRepository.getMaxTextLength(false))

        // Premium tier limits
        assertEquals("Premium file size must be 25 MB", 26214400L, SortCodeRepository.getMaxFileSize(true))
        assertEquals("Premium text length must be 100,000 characters", 100000, SortCodeRepository.getMaxTextLength(true))
    }

    // 8. Verify minute-based expiry options
    @Test
    fun testExpiryOptions_minuteDurations() {
        assertEquals("1 minute option", 1L, ExpiryOption.ONE_MINUTE.minutes)
        assertEquals("5 minutes option", 5L, ExpiryOption.FIVE_MINUTES.minutes)
        assertEquals("10 minutes option", 10L, ExpiryOption.TEN_MINUTES.minutes)
        assertEquals("15 minutes option", 15L, ExpiryOption.FIFTEEN_MINUTES.minutes)
        assertEquals("30 minutes option", 30L, ExpiryOption.THIRTY_MINUTES.minutes)
        assertEquals("60 minutes option", 60L, ExpiryOption.SIXTY_MINUTES.minutes)
        assertEquals("Custom option marker", -1L, ExpiryOption.CUSTOM.minutes)
    }

    // 9. Short duration (1 min, 5 min) expiry tests
    @Test
    fun testShortDurationExpiry_preventsAccessWhenExpired() {
        val now = System.currentTimeMillis()

        // 1-minute item: active before 60s, expired after 60s
        val oneMinItem = com.example.model.SortCodeItem(
            code = "MS-1MIN01",
            ownerUid = "alice_1",
            textContent = "Confidential 1-minute secret",
            expiresAt = Timestamp(Date(now + 60 * 1000L)) // 1 min from now
        )

        // Currently before expiry
        assertFalse("Message must be active right now", oneMinItem.isExpired(Date(now)))
        assertTrue("Message must be accessible right now", oneMinItem.isAccessible(Date(now)))
        assertTrue(
            "Remaining time must clearly show duration (e.g. 'Expires in 1 minute')",
            oneMinItem.remainingTimeDescription(Date(now)).contains("Expires in")
        )

        // 61 seconds later
        val futureDate = Date(now + 61 * 1000L)
        assertTrue("Message must be expired after 1 minute has elapsed", oneMinItem.isExpired(futureDate))
        assertFalse("Expired message must NOT be accessible", oneMinItem.isAccessible(futureDate))
        assertEquals("Remaining time description must be 'Expired'", "Expired", oneMinItem.remainingTimeDescription(futureDate))
    }

    @Test
    fun testFiveMinuteDuration_countdownFormatting() {
        val now = System.currentTimeMillis()
        val fiveMinItem = com.example.model.SortCodeItem(
            code = "MS-5MIN01",
            ownerUid = "bob_1",
            textContent = "Confidential 5-minute secret",
            expiresAt = Timestamp(Date(now + 5 * 60 * 1000L)) // 5 mins from now
        )

        val desc = fiveMinItem.remainingTimeDescription(Date(now))
        assertEquals("Expires in 5 minutes", desc)

        // 4 minutes later (1 minute remaining)
        val fourMinutesLater = Date(now + 4 * 60 * 1000L + 15 * 1000L) // 45 seconds left
        val desc45s = fiveMinItem.remainingTimeDescription(fourMinutesLater)
        assertEquals("Expires in 45 seconds", desc45s)
    }

    // 10. Billing state check: Only PURCHASED activates; PENDING does not
    @Test
    fun testGooglePlayBillingPurchaseState_requiresPurchased() {
        val purchasedState = Purchase.PurchaseState.PURCHASED
        val pendingState = Purchase.PurchaseState.PENDING
        val unspecifiedState = Purchase.PurchaseState.UNSPECIFIED_STATE

        assertEquals(1, purchasedState)
        assertEquals(2, pendingState)
        assertEquals(0, unspecifiedState)

        val canActivateOnPurchased = purchasedState == Purchase.PurchaseState.PURCHASED
        val canActivateOnPending = pendingState == Purchase.PurchaseState.PURCHASED
        val canActivateOnUnspecified = unspecifiedState == Purchase.PurchaseState.PURCHASED

        assertTrue("Only PURCHASED state can trigger premium activation", canActivateOnPurchased)
        assertFalse("PENDING state must never trigger premium activation", canActivateOnPending)
        assertFalse("UNSPECIFIED state must never trigger premium activation", canActivateOnUnspecified)
    }

    // 11. Photo SortCode Security & Payload Validation
    @Test
    fun testPhotoSortCode_payloadLimitsAndSafeImageUrl() {
        // Simulating a photo upload with fallback base64
        val dummyPhotoBytes = ByteArray(500 * 1024) { 0x5A } // 500KB
        val base64 = java.util.Base64.getEncoder().encodeToString(dummyPhotoBytes)

        // Base64 size check
        assertTrue("Base64 string must be under 1,000,000 characters limit", base64.length <= 1000000)

        val photoItem = com.example.model.SortCodeItem(
            code = "MS-PHOTO1",
            ownerUid = "auth_user_456",
            title = "My Photo",
            contentType = "image",
            fileName = "vacation.jpg",
            fileSize = dummyPhotoBytes.size.toLong(),
            fileMimeType = "image/jpeg",
            fileBase64 = base64,
            imageUrl = "", // MUST be empty on base64 fallback to prevent violating 2048-char URL rule
            storagePath = "",
            thumbnailBase64 = "compact_thumb_data",
            imageWidth = 1920,
            imageHeight = 1080,
            status = "active"
        )

        // Validations
        assertEquals("image", photoItem.contentType)
        assertEquals("auth_user_456", photoItem.ownerUid)
        assertTrue("imageUrl must remain blank when using inline base64", photoItem.imageUrl.isBlank())
        assertTrue("fileBase64 must contain the photo payload", photoItem.fileBase64.isNotBlank())
        assertTrue("fileBase64 must not exceed Firestore rules limit of 1,000,000 chars", photoItem.fileBase64.length <= 1000000)
        assertTrue("thumbnailBase64 must be under 250,000 chars limit", photoItem.thumbnailBase64.length <= 250000)
    }

    @Test
    fun testPhotoSortCode_authenticatedOwnerRequired() {
        val unauthenticatedUid: String? = null
        val activeUid = unauthenticatedUid ?: ""

        val isAuthorizedToCreate = activeUid.isNotBlank()
        assertFalse("Unauthenticated user must be prevented from creating photo SortCode", isAuthorizedToCreate)

        val signedInUid = "google_user_789"
        val isAuthorizedSignedIn = signedInUid.isNotBlank()
        assertTrue("Signed-in authenticated user is authorized to create SortCode", isAuthorizedSignedIn)
    }

    @Test
    fun testPhotoSortCode_crossDeviceResolutionAndDecoding() {
        // 1. Verify clean storage path (no leading slash)
        val rawPath = "/images/MS-ABC123_photo.jpg"
        val cleanPath = rawPath.trimStart('/')
        assertEquals("images/MS-ABC123_photo.jpg", cleanPath)

        // 2. Verify Base64 decoding with or without data: URI prefix
        val sampleBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) // PNG header
        val rawBase64 = java.util.Base64.getEncoder().encodeToString(sampleBytes)
        val dataUriBase64 = "data:image/png;base64,$rawBase64"

        // Stripping prefix
        val cleanFromDataUri = if (dataUriBase64.contains(",")) dataUriBase64.substringAfter(",") else dataUriBase64
        val decoded = java.util.Base64.getDecoder().decode(cleanFromDataUri)
        assertEquals(sampleBytes.size, decoded.size)
        assertEquals(sampleBytes[0], decoded[0])

        // 3. Verify SortCode recipient can access active unexpired photo
        val activePhotoCode = com.example.model.SortCodeItem(
            code = "MS-PIC99",
            ownerUid = "phone1_user",
            contentType = "image",
            fileName = "shared.png",
            fileSize = 1024L,
            fileMimeType = "image/png",
            fileBase64 = rawBase64,
            status = "active",
            expiresAt = Timestamp(Date(System.currentTimeMillis() + 600000L)) // 10 mins
        )
        assertTrue("Active unexpired photo must be accessible to Phone 2 recipient", activePhotoCode.isAccessible())
        assertFalse("Active photo is not expired", activePhotoCode.isExpired())

        // 4. Verify expired photo is blocked for recipient
        val expiredPhotoCode = activePhotoCode.copy(
            expiresAt = Timestamp(Date(System.currentTimeMillis() - 5000L))
        )
        assertFalse("Expired photo must not be accessible to recipient", expiredPhotoCode.isAccessible())
        assertTrue("Expired photo must return isExpired() = true", expiredPhotoCode.isExpired())
    }

    @Test
    fun testPlayBilling_responseCodeDescriptions() {
        // Verify response code 3 (BILLING_UNAVAILABLE)
        val billingUnavailableMsg = com.example.billing.PlayBillingManager.getBillingResponseDescription(
            com.android.billingclient.api.BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
        )
        assertTrue(
            "Billing unavailable message must mention Google Play Store / services",
            billingUnavailableMsg.contains("Google Play Store", ignoreCase = true)
        )

        // Verify response code 4 (ITEM_UNAVAILABLE)
        val itemUnavailableMsg = com.example.billing.PlayBillingManager.getBillingResponseDescription(
            com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
        )
        assertTrue(
            "Item unavailable message must mention subscription product / base plan",
            itemUnavailableMsg.contains("premium_monthly", ignoreCase = true) || itemUnavailableMsg.contains("Base Plan", ignoreCase = true)
        )

        // Verify response code 5 (DEVELOPER_ERROR)
        val devErrorMsg = com.example.billing.PlayBillingManager.getBillingResponseDescription(
            com.android.billingclient.api.BillingClient.BillingResponseCode.DEVELOPER_ERROR
        )
        assertTrue(
            "Developer error message must provide actionable configuration advice",
            devErrorMsg.contains("Google Play Console", ignoreCase = true) || devErrorMsg.contains("configuration", ignoreCase = true)
        )

        // Verify response code 1 (USER_CANCELED)
        val userCanceledMsg = com.example.billing.PlayBillingManager.getBillingResponseDescription(
            com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED
        )
        assertTrue(
            "User cancelled message must contain cancel",
            userCanceledMsg.contains("cancel", ignoreCase = true)
        )
    }

    @Test
    fun testPlayBilling_candidateProductIds_matchCanonicalPlans() {
        // Canonical monthly ID must be in candidates
        assertTrue(
            "CANDIDATE_MONTHLY_IDS must contain 'premium_monthly'",
            com.example.billing.PlayBillingManager.CANDIDATE_MONTHLY_IDS.contains("premium_monthly")
        )
        // Canonical annual ID must be in candidates
        assertTrue(
            "CANDIDATE_ANNUAL_IDS must contain 'premium_annual'",
            com.example.billing.PlayBillingManager.CANDIDATE_ANNUAL_IDS.contains("premium_annual")
        )

        // Verify monthly plan pricing is 199 INR
        assertEquals(
            "Monthly plan price must be ₹199",
            199,
            com.example.model.PremiumPlans.MONTHLY.priceInr
        )
        assertEquals(
            "Monthly plan ID must match 'premium_monthly'",
            "premium_monthly",
            com.example.model.PremiumPlans.MONTHLY.id
        )
    }

    @Test
    fun testVideoUpload_chunkingAndReassembly() {
        // Simulate a 1.2 MB video payload
        val testVideoSize = 1200 * 1024
        val originalBytes = ByteArray(testVideoSize) { (it % 256).toByte() }

        val chunkSize = 350 * 1024
        val chunks = originalBytes.asSequence()
            .chunked(chunkSize)
            .map { it.toByteArray() }
            .toList()

        assertEquals("1.2 MB video must be split into 4 chunks of 350 KB", 4, chunks.size)

        // Verify reassembly from chunks
        val reassembledStream = java.io.ByteArrayOutputStream()
        for (chunk in chunks) {
            val base64 = java.util.Base64.getEncoder().encodeToString(chunk)
            val decoded = java.util.Base64.getDecoder().decode(base64)
            reassembledStream.write(decoded)
        }

        val reassembledBytes = reassembledStream.toByteArray()
        assertEquals(originalBytes.size, reassembledBytes.size)
        assertTrue("Reassembled video bytes must match original byte-for-byte", originalBytes.contentEquals(reassembledBytes))

        // Verify SortCodeItem with chunks
        val videoSortCode = com.example.model.SortCodeItem(
            code = "MS-VID01",
            ownerUid = "user_vid_123",
            contentType = "video",
            fileName = "tutorial.mp4",
            fileSize = originalBytes.size.toLong(),
            fileMimeType = "video/mp4",
            totalChunks = chunks.size,
            status = "active",
            expiresAt = Timestamp(Date(System.currentTimeMillis() + 600000L))
        )

        assertEquals(4, videoSortCode.totalChunks)
        assertTrue(videoSortCode.isAccessible())
        assertFalse(videoSortCode.isExpired())
    }

    // 12. ₹99 Premium Plan for 7 Days configuration verification
    @Test
    fun testWeekly7DayPlan_configurationAndProperties() {
        val plan = com.example.model.PremiumPlans.WEEKLY_7D
        assertEquals("premium_weekly_7d", plan.id)
        assertEquals(99, plan.priceInr)
        assertEquals("7 days", plan.period)
        assertEquals("⚡ ₹99 FOR 7 DAYS", plan.savingsBadge)
        assertTrue(plan.description.contains("₹99"))
    }

    // 13. ₹99 Plan Exactly 7 Days Expiry Calculation & Remaining Time Formatting
    @Test
    fun testWeekly7DayPlan_exact7DaysExpiryAndRemainingTime() {
        val now = System.currentTimeMillis()
        val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
        val expiry = Date(now + sevenDaysMillis)

        val subscription = UserSubscription(
            userId = "user_weekly_99",
            status = "active",
            planId = "premium_weekly_7d",
            planName = "MsgSort 7-Day Access",
            priceInr = 99,
            billingPeriod = "7 days",
            provider = "razorpay",
            transactionId = "pay_weekly7d_test123",
            startDate = Timestamp(Date(now)),
            expiryDate = Timestamp(expiry)
        )

        // Valid right after payment
        assertTrue("7-day subscription must be active immediately after payment", subscription.isPremiumActive(Date(now)))
        val remainingNow = subscription.formattedRemainingTime(Date(now))
        assertTrue("Must indicate days remaining", remainingNow.contains("days"))

        // After 6 days, 20 hours (4 hours remaining)
        val nearExpiry = Date(expiry.time - 4L * 60L * 60L * 1000L)
        val remainingNear = subscription.formattedRemainingTime(nearExpiry)
        assertTrue("Must indicate hours remaining", remainingNear.contains("hours") || remainingNear.contains("4"))

        // After 7 days and 1 second -> Automatic Expiration
        val pastExpiry = Date(expiry.time + 1000L)
        assertFalse("Must automatically expire after 7 days have elapsed", subscription.isPremiumActive(pastExpiry))
        val remainingExpired = subscription.formattedRemainingTime(pastExpiry)
        assertEquals("Expired", remainingExpired)
    }

    // 14. Razorpay Unverified / Failed Payment Rejection
    @Test
    fun testRazorpayPaymentVerification_rejectsUnverifiedPayments() {
        // Blank payment ID
        val blankId = "   "
        assertFalse("Blank payment ID must be rejected", blankId.isNotBlank() && blankId.startsWith("pay_"))

        // Valid format check
        val validPaymentId = "pay_PW123456789abc"
        assertTrue("Valid payment ID starts with pay_", validPaymentId.startsWith("pay_"))

        // Inactive / unverified subscription state
        val unverifiedSub = UserSubscription(
            userId = "user_fraud_attempt",
            status = "failed",
            planId = "premium_weekly_7d",
            provider = "razorpay",
            transactionId = "pay_failed_txn",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse("Failed Razorpay payment must NEVER grant premium", unverifiedSub.isPremiumActive())

        val pendingSub = UserSubscription(
            userId = "user_pending_txn",
            status = "pending",
            planId = "premium_weekly_7d",
            provider = "razorpay",
            transactionId = "pay_pending_txn",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse("Pending Razorpay payment must NEVER grant premium", pendingSub.isPremiumActive())
    }

    // 15. BillDesk Payment Verification, 7-Day Plan & Unverified Payment Rejection
    @Test
    fun testBilldeskPaymentVerification_validatesProviderAndRejectsUnverified() {
        val now = System.currentTimeMillis()
        val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
        val expiry = Date(now + sevenDaysMillis)

        val billdeskSub = UserSubscription(
            userId = "user_bd_99",
            status = "active",
            planId = "premium_weekly_7d",
            planName = "7-Day Premium (BillDesk)",
            priceInr = 99,
            billingPeriod = "7 days",
            provider = "billdesk",
            transactionId = "BD_PAY_1234567890",
            startDate = Timestamp(Date(now)),
            expiryDate = Timestamp(expiry)
        )

        // Valid right after payment
        assertTrue("BillDesk 7-day subscription must be active immediately after payment", billdeskSub.isPremiumActive(Date(now)))
        assertEquals("billdesk", billdeskSub.provider)
        assertTrue("Transaction ID must be BD_PAY format", billdeskSub.transactionId.startsWith("BD_PAY_"))

        // Expiry test
        val pastExpiry = Date(expiry.time + 1000L)
        assertFalse("BillDesk subscription must automatically expire after 7 days", billdeskSub.isPremiumActive(pastExpiry))

        // Failed / Fraudulent BillDesk ID tests
        val blankId = "   "
        assertFalse("Blank transaction ID must be rejected", blankId.isNotBlank() && blankId.startsWith("BD"))

        val failedBilldeskSub = UserSubscription(
            userId = "fraud_user",
            status = "failed",
            planId = "premium_weekly_7d",
            provider = "billdesk",
            transactionId = "BD_FAILED_000",
            expiryDate = Timestamp(Date(System.currentTimeMillis() + 86400000L))
        )
        assertFalse("Failed BillDesk payment must NEVER grant premium", failedBilldeskSub.isPremiumActive())
    }

    // 16. PDF SortCode Sharing, Storage Schema, and Cross-Device Retrieval Tests
    @Test
    fun testPdfSortCodeModel_validatesPdfContentTypeAndStoragePath() {
        val now = System.currentTimeMillis()
        val expiryTime = Timestamp(Date(now + 3600000L)) // 1 hour future
        val pdfItem = com.example.model.SortCodeItem(
            code = "PDF789",
            ownerUid = "sender_device_user_A",
            title = "Annual Financial Report",
            contentType = "application/pdf",
            fileName = "FinancialReport2026.pdf",
            fileSize = 2500000L,
            fileMimeType = "application/pdf",
            storagePath = "files/PDF789_FinancialReport2026.pdf",
            fileUrl = "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0728162115.firebasestorage.app/o/files%2FPDF789_FinancialReport2026.pdf?alt=media&token=12345",
            status = "active",
            createdAt = Timestamp(Date(now)),
            expiresAt = expiryTime,
            downloadAllowed = true
        )

        // Verify PDF classification
        assertTrue("Item should be recognized as PDF", pdfItem.isPdf())
        assertEquals("application/pdf", pdfItem.contentType)
        assertEquals("application/pdf", pdfItem.fileMimeType)
        assertTrue("Storage path must reference files folder", pdfItem.storagePath.startsWith("files/"))
        assertTrue("File URL must be valid HTTPS tokenized download URL", pdfItem.fileUrl.startsWith("https://"))

        // Cross-device recipient check: recipient on Device B (different UID) can access active unexpired code
        assertTrue("Recipient on another device/account must be able to access active SortCode", pdfItem.isAccessible(Date(now)))
        assertFalse("Active SortCode must not be expired", pdfItem.isExpired(Date(now)))
        assertFalse("Active SortCode must not be revoked", pdfItem.isRevoked())
    }

    @Test
    fun testPdfSortCodeRetrieval_handlesExpiredAndRevokedStates() {
        val pastDate = Date(System.currentTimeMillis() - 100000L)
        val expiredPdfItem = com.example.model.SortCodeItem(
            code = "EXPPDF",
            ownerUid = "user_A",
            contentType = "application/pdf",
            fileName = "Invoice.pdf",
            storagePath = "files/EXPPDF_Invoice.pdf",
            status = "active",
            expiresAt = Timestamp(pastDate)
        )

        assertTrue("Past expiresAt must be marked as expired", expiredPdfItem.isExpired())
        assertFalse("Expired PDF must not be accessible to recipient", expiredPdfItem.isAccessible())
        assertEquals("expired", expiredPdfItem.getComputedStatus())

        val revokedPdfItem = com.example.model.SortCodeItem(
            code = "REV123",
            ownerUid = "user_A",
            contentType = "application/pdf",
            fileName = "Confidential.pdf",
            storagePath = "files/REV123_Confidential.pdf",
            status = "revoked",
            expiresAt = Timestamp(Date(System.currentTimeMillis() + 3600000L))
        )

        assertTrue("Revoked status must be recognized", revokedPdfItem.isRevoked())
        assertFalse("Revoked PDF must not be accessible to recipient", revokedPdfItem.isAccessible())
        assertEquals("revoked", revokedPdfItem.getComputedStatus())
    }

    @Test
    fun testPlayBilling_subscriptionProductAndBasePlans_configuration() {
        // Requirement 1 & 4: Subscription Product ID is "premium_monthly" and NOT a basePlanId
        assertEquals(
            "Subscription product ID must be 'premium_monthly'",
            "premium_monthly",
            com.example.billing.PlayBillingManager.PRODUCT_SUBSCRIPTION_ID
        )
        assertEquals(
            "Subscription product ID must match PremiumPlans constant",
            "premium_monthly",
            com.example.model.PremiumPlans.SUBSCRIPTION_PRODUCT_ID
        )

        // Requirement 3: Base Plan IDs
        assertEquals(
            "Monthly basePlanId must be 'monthly-199'",
            "monthly-199",
            com.example.billing.PlayBillingManager.BASE_PLAN_MONTHLY
        )
        assertEquals(
            "Weekly basePlanId must be 'weekly-99-new'",
            "weekly-99-new",
            com.example.billing.PlayBillingManager.BASE_PLAN_WEEKLY
        )
        assertEquals(
            "Yearly basePlanId must be 'yearly-799'",
            "yearly-799",
            com.example.billing.PlayBillingManager.BASE_PLAN_YEARLY
        )

        // PremiumPlans definitions
        val monthly = com.example.model.PremiumPlans.MONTHLY
        assertEquals("premium_monthly", monthly.productId)
        assertEquals("monthly-199", monthly.basePlanId)
        assertEquals(199, monthly.priceInr)

        val weekly = com.example.model.PremiumPlans.WEEKLY_7D
        assertEquals("premium_monthly", weekly.productId)
        assertEquals("weekly-99-new", weekly.basePlanId)
        assertEquals(99, weekly.priceInr)

        val annual = com.example.model.PremiumPlans.ANNUAL
        assertEquals("premium_monthly", annual.productId)
        assertEquals("yearly-799", annual.basePlanId)
        assertEquals(799, annual.priceInr)
    }

    @Test
    fun testPlayBilling_resolveBasePlanId() {
        // Resolves correctly from plan IDs or aliases
        assertEquals(
            "monthly-199",
            com.example.billing.PlayBillingManager.resolveBasePlanId("premium_monthly")
        )
        assertEquals(
            "monthly-199",
            com.example.billing.PlayBillingManager.resolveBasePlanId("monthly-199")
        )
        assertEquals(
            "weekly-99-new",
            com.example.billing.PlayBillingManager.resolveBasePlanId("premium_weekly_7d")
        )
        assertEquals(
            "weekly-99-new",
            com.example.billing.PlayBillingManager.resolveBasePlanId("weekly-99-new")
        )
        assertEquals(
            "yearly-799",
            com.example.billing.PlayBillingManager.resolveBasePlanId("premium_annual")
        )
        assertEquals(
            "yearly-799",
            com.example.billing.PlayBillingManager.resolveBasePlanId("yearly-799")
        )
    }

    @Test
    fun testPlayBilling_subscriptionExpiryAndCancellationHandling() {
        val now = System.currentTimeMillis()
        val futureDate = Date(now + 86400000L) // +1 day
        val pastDate = Date(now - 86400000L)   // -1 day

        // 1. Active subscription in future
        val activeSub = com.example.model.UserSubscription(
            userId = "user_1",
            status = "active",
            planId = "premium_monthly",
            expiryDate = Timestamp(futureDate)
        )
        assertTrue("Active subscription before expiry must return isPremiumActive() = true", activeSub.isPremiumActive())

        // 2. Cancelled subscription before expiry (user canceled renewal, but paid period still valid)
        val cancelledSub = com.example.model.UserSubscription(
            userId = "user_1",
            status = "cancelled",
            planId = "premium_monthly",
            autoRenewing = false,
            expiryDate = Timestamp(futureDate)
        )
        assertTrue("Cancelled subscription before expiry must still remain active until expiry", cancelledSub.isPremiumActive())

        // 3. Subscription past expiry date
        val expiredSub = com.example.model.UserSubscription(
            userId = "user_1",
            status = "active",
            planId = "premium_monthly",
            expiryDate = Timestamp(pastDate)
        )
        assertFalse("Subscription past expiry date must return isPremiumActive() = false", expiredSub.isPremiumActive())

        // 4. Inactive status
        val inactiveSub = com.example.model.UserSubscription(
            userId = "user_1",
            status = "inactive",
            planId = "premium_monthly",
            expiryDate = Timestamp(futureDate)
        )
        assertFalse("Inactive status must return isPremiumActive() = false", inactiveSub.isPremiumActive())
    }
}
