package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.SortCodeItem
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.UUID

class SortCodeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createSortCode_authenticatedUser_succeeds(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SortCodeRepository(firestore)

        val uniqueCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val item = SortCodeItem(
            code = uniqueCode,
            ownerUid = aliceUid,
            title = "Top Secret Meeting",
            contentType = "text",
            textContent = "Meet at room 402 at noon",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSortCode(item)
        }
        assertTrue(createResult.isSuccess)

        // Verify retrieval
        val retrieveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getSortCode(uniqueCode)
        }
        assertTrue(retrieveResult.isSuccess)
        val retrieved = retrieveResult.getOrThrow()
        assertNotNull(retrieved)
        assertEquals("Top Secret Meeting", retrieved?.title)
    }

    @Test
    fun observeUserSortCodes_authenticatedOwner_returnsMatchingCodes(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SortCodeRepository(firestore)

        val uniqueCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val item = SortCodeItem(
            code = uniqueCode,
            ownerUid = aliceUid,
            title = "Alice Note",
            contentType = "text",
            textContent = "Hello Alice",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSortCode(item).getOrThrow()
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserSortCodes(aliceUid).first { list ->
                list.any { it.code == uniqueCode }
            }
        }
        assertTrue(observed.any { it.code == uniqueCode })
    }

    @Test
    fun revokeSortCode_authenticatedOwner_updatesStatusToRevoked(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SortCodeRepository(firestore)

        val uniqueCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val item = SortCodeItem(
            code = uniqueCode,
            ownerUid = aliceUid,
            title = "Revoke Note",
            contentType = "text",
            textContent = "Will be revoked",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSortCode(item).getOrThrow()
        }

        val revokeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.revokeSortCode(uniqueCode)
        }
        assertTrue(revokeResult.isSuccess)

        val getResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getSortCode(uniqueCode).getOrThrow()
        }
        assertEquals("revoked", getResult?.status)
    }

    @Test
    fun deleteSortCode_crossUser_failsWithPermissionDenied(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SortCodeRepository(firestore)

        val uniqueCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val item = SortCodeItem(
            code = uniqueCode,
            ownerUid = aliceUid,
            title = "Alice Confidential",
            contentType = "text",
            textContent = "Alice data",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSortCode(item).getOrThrow()
        }

        // Bob signs in and tries to delete Alice's code
        signInTestUser(BOB_EMAIL)
        val bobRepo = SortCodeRepository(firestore)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.deleteSortCode(uniqueCode)
        }
        assertTrue(deleteResult.isFailure)
    }

    @Test
    fun createSortCode_videoAndFileMetadata_succeeds(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SortCodeRepository(firestore)

        val videoCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val videoItem = SortCodeItem(
            code = videoCode,
            ownerUid = aliceUid,
            title = "Demo Video",
            contentType = "video",
            fileName = "demo.mp4",
            fileSize = 4096000L,
            fileMimeType = "video/mp4",
            storagePath = "videos/${videoCode}_demo.mp4",
            fileUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/videos%2Fdemo.mp4",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSortCode(videoItem)
        }
        assertTrue(result.isSuccess)

        val retrieved = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getSortCode(videoCode)
        }.getOrThrow()
        assertNotNull(retrieved)
        assertEquals("video", retrieved?.contentType)
        assertEquals("videos/${videoCode}_demo.mp4", retrieved?.storagePath)
        assertEquals("https://firebasestorage.googleapis.com/v0/b/bucket/o/videos%2Fdemo.mp4", retrieved?.fileUrl)
    }

    @Test
    fun retrieveSortCode_fromAnotherAuthenticatedAccount_succeeds(): Unit = runBlocking {
        // Alice creates a message SortCode
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SortCodeRepository(firestore)

        val uniqueCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val item = SortCodeItem(
            code = uniqueCode,
            ownerUid = aliceUid,
            title = "Shared Secret Message",
            contentType = "text",
            textContent = "Confidential payload for Bob",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSortCode(item).getOrThrow()
        }

        // Bob signs in and retrieves the message using the SortCode
        signInTestUser(BOB_EMAIL)
        val bobRepo = SortCodeRepository(firestore)

        val bobRetrieveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSortCode(uniqueCode)
        }
        assertTrue(bobRetrieveResult.isSuccess)
        val retrieved = bobRetrieveResult.getOrThrow()
        assertNotNull(retrieved)
        assertEquals("Shared Secret Message", retrieved?.title)
        assertEquals("Confidential payload for Bob", retrieved?.textContent)

        // Bob can also increment the view count
        val viewCountResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.incrementViewCount(uniqueCode)
        }
        assertTrue(viewCountResult.isSuccess)
    }

    @Test
    fun retrieveRevokedSortCode_byRecipient_failsWithPermissionDenied(): Unit = runBlocking {
        // Alice creates and then revokes a SortCode
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SortCodeRepository(firestore)

        val uniqueCode = "MS-" + UUID.randomUUID().toString().take(6).uppercase()
        val future = Timestamp(Date(System.currentTimeMillis() + 3600 * 1000))
        val item = SortCodeItem(
            code = uniqueCode,
            ownerUid = aliceUid,
            title = "Ephemeral Note",
            contentType = "text",
            textContent = "Will be cancelled shortly",
            expiresAt = future,
            status = "active",
            passwordProtected = false,
            downloadAllowed = true
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSortCode(item).getOrThrow()
            aliceRepo.revokeSortCode(uniqueCode).getOrThrow()
        }

        // Bob tries to retrieve the revoked SortCode
        signInTestUser(BOB_EMAIL)
        val bobRepo = SortCodeRepository(firestore)

        val bobRetrieveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSortCode(uniqueCode)
        }
        // Should fail due to security rules (only owner can read revoked codes)
        assertTrue(bobRetrieveResult.isFailure)

        // Alice (the owner) CAN still retrieve her own revoked code
        signInTestUser(ALICE_EMAIL)
        val aliceRetrieveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getSortCode(uniqueCode)
        }
        assertTrue(aliceRetrieveResult.isSuccess)
        assertEquals("revoked", aliceRetrieveResult.getOrThrow()?.status)
    }

    @Test
    fun userProfile_saveAndRetrieve_succeedsForSelf_failsForOther(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SortCodeRepository(firestore)

        val aliceProfile = com.example.model.UserProfile(
            userId = aliceUid,
            displayName = "Alice Wonderland",
            email = ALICE_EMAIL
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveUserProfile(aliceProfile)
        }
        assertTrue(saveResult.isSuccess)

        val getProfileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getUserProfile(aliceUid)
        }
        assertTrue(getProfileResult.isSuccess)
        val fetchedProfile = getProfileResult.getOrThrow()
        assertNotNull(fetchedProfile)
        assertEquals("Alice Wonderland", fetchedProfile?.displayName)

        // Bob signs in and tries to read Alice's profile -> blocked by security rules
        signInTestUser(BOB_EMAIL)
        val bobRepo = SortCodeRepository(firestore)
        val bobReadAliceResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getUserProfile(aliceUid)
        }
        assertTrue(bobReadAliceResult.isFailure)
    }

    @Test
    fun userSubscription_clientWriteAttempt_blockedBySecurityRules(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)

        // Alice attempts to forge an 'active' premium subscription directly from client SDK
        val subDocRef = firestore.collection("users").document(aliceUid).collection("subscription").document("current")
        val writeSuccess = try {
            subDocRef.set(
                mapOf(
                    "userId" to aliceUid,
                    "status" to "active",
                    "planId" to "premium_annual"
                )
            ).await()
            true
        } catch (_: Exception) {
            false
        }
        // Client write MUST fail because security rules strictly enforce 'allow write: if false;'
        org.junit.Assert.assertFalse("Client should not be able to write subscription status", writeSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
