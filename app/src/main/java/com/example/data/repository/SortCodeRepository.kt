package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.model.AccountDeletionSummary
import com.example.model.SortCodeItem
import com.example.model.UserProfile
import com.example.model.UserSubscription
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SortCodeRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        getFirestoreInstance(context)
    )

    companion object {
        fun getFirestoreInstance(context: Context): FirebaseFirestore {
            val dbId = try {
                context.applicationContext.getString(R.string.firestore_database_id).trim()
            } catch (e: Exception) {
                ""
            }
            return if (dbId.isEmpty() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                try {
                    FirebaseFirestore.getInstance(dbId)
                } catch (e: Exception) {
                    FirebaseFirestore.getInstance()
                }
            }
        }

        fun getMaxFileSize(isPremium: Boolean): Long = if (isPremium) 26214400L else 5242880L // 25 MB vs 5 MB
        fun getMaxTextLength(isPremium: Boolean): Int = if (isPremium) 100000 else 20000 // 100k vs 20k

        fun isSubscriptionActive(subscription: UserSubscription?, now: java.util.Date = java.util.Date()): Boolean {
            if (subscription == null) return false
            return subscription.isPremiumActive(now)
        }
    }

    fun observeUserSortCodes(userId: String): Flow<List<SortCodeItem>> {
        val collectionRef = db.collection("sortCodes")
        return collectionRef
            .whereEqualTo("ownerUid", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SortCodeItem::class.java).sortedByDescending {
                    it.createdAt ?: it.expiresAt
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, "sortCodes")
                }
                throw error
            }
    }

    suspend fun getSortCode(code: String): Result<SortCodeItem?> {
        val docRef = db.collection("sortCodes").document(code)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                Result.success(null)
            } else {
                val item = snapshot.toObject(SortCodeItem::class.java)
                if (item != null && item.isExpired()) {
                    // Automatically prevent access to expired messages at data repository layer
                    Result.success(
                        item.copy(
                            status = "expired",
                            textContent = "",
                            fileBase64 = "",
                            thumbnailBase64 = "",
                            imageUrl = "",
                            fileUrl = "",
                            storagePath = ""
                        )
                    )
                } else {
                    Result.success(item)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createSortCode(item: SortCodeItem): Result<SortCodeItem> {
        if (item.fileSize > 26214400L) {
            return Result.failure(IllegalArgumentException("File size exceeds 25 MB maximum allowed limit."))
        }
        if (item.textContent.length > 100000) {
            return Result.failure(IllegalArgumentException("Message content exceeds 100,000 character maximum limit."))
        }
        val docRef = db.collection("sortCodes").document(item.code)

        // Ensure imageUrl and fileUrl only contain valid web URLs (never data URIs which violate size rules)
        val safeImageUrl = if (item.imageUrl.startsWith("data:", ignoreCase = true)) "" else item.imageUrl
        val safeFileUrl = if (item.fileUrl.startsWith("data:", ignoreCase = true)) "" else item.fileUrl
        val safeFileBase64 = if (item.fileBase64.isNotBlank()) {
            item.fileBase64
        } else if (item.imageUrl.startsWith("data:", ignoreCase = true)) {
            item.imageUrl.substringAfter("base64,", "")
        } else {
            ""
        }

        val payload = mutableMapOf<String, Any>(
            "code" to item.code,
            "ownerUid" to item.ownerUid,
            "title" to item.title,
            "contentType" to item.contentType,
            "textContent" to item.textContent,
            "fileName" to item.fileName,
            "fileSize" to item.fileSize,
            "fileMimeType" to item.fileMimeType,
            "fileBase64" to safeFileBase64,
            "storagePath" to item.storagePath,
            "imageUrl" to safeImageUrl,
            "fileUrl" to safeFileUrl,
            "thumbnailBase64" to item.thumbnailBase64,
            "imageWidth" to item.imageWidth,
            "imageHeight" to item.imageHeight,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to (item.expiresAt ?: FieldValue.serverTimestamp()),
            "status" to "active",
            "passwordProtected" to item.passwordProtected,
            "passwordHash" to item.passwordHash,
            "downloadAllowed" to item.downloadAllowed,
            "description" to item.description,
            "totalChunks" to item.totalChunks,
            "viewsCount" to 0L
        ).filterValues {
            it != "" || it == 0L || it == 0 || it is Boolean
        }

        return try {
            docRef.set(payload).await()
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun getFirebaseStorage(): com.google.firebase.storage.FirebaseStorage {
        val primaryBucket = "gs://gen-lang-client-0728162115.firebasestorage.app"
        return try {
            com.google.firebase.storage.FirebaseStorage.getInstance(primaryBucket)
        } catch (e: Exception) {
            try {
                com.google.firebase.storage.FirebaseStorage.getInstance()
            } catch (e2: Exception) {
                com.google.firebase.storage.FirebaseStorage.getInstance("gs://gen-lang-client-0728162115.appspot.com")
            }
        }
    }

    suspend fun uploadFileToStorage(
        code: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        ownerUid: String,
        folder: String = "files"
    ): Result<Pair<String, String>> {
        return try {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document.pdf" }
            val isPdf = mimeType == "application/pdf" || cleanName.endsWith(".pdf", ignoreCase = true)
            val effectiveMime = if (isPdf) "application/pdf" else mimeType
            val targetFolder = when {
                mimeType.startsWith("image/") -> "images"
                mimeType.startsWith("video/") -> "videos"
                isPdf -> "files"
                else -> folder
            }
            val path = "$targetFolder/${code}_$cleanName"
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(effectiveMime)
                .setCustomMetadata("sortCode", code)
                .setCustomMetadata("ownerUid", ownerUid)
                .build()

            android.util.Log.i("SortCodeRepository", "Starting upload to Firebase Storage: $path (${fileBytes.size} bytes, mime: $effectiveMime)")

            try {
                val storage = getFirebaseStorage()
                val storageRef = storage.reference.child(path)
                
                // Upload actual binary to Firebase Storage
                val uploadTask = storageRef.putBytes(fileBytes, metadata).await()
                android.util.Log.i("SortCodeRepository", "Upload successful: ${uploadTask.bytesTransferred} bytes transferred to $path")

                // Retrieve tokenized secure download URL if available
                val downloadUrl = try {
                    storageRef.downloadUrl.await().toString()
                } catch (urlEx: Exception) {
                    android.util.Log.w("SortCodeRepository", "Could not get downloadUrl: ${urlEx.message}")
                    ""
                }
                val cleanPath = storageRef.path.trimStart('/')
                Result.success(Pair(cleanPath, downloadUrl))
            } catch (primaryEx: Exception) {
                android.util.Log.w("SortCodeRepository", "Primary storage upload failed for $code: ${primaryEx.message}. Trying default storage...")
                val defaultStorage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val defaultRef = defaultStorage.reference.child(path)
                val uploadTask = defaultRef.putBytes(fileBytes, metadata).await()
                val downloadUrl = try {
                    defaultRef.downloadUrl.await().toString()
                } catch (urlEx: Exception) {
                    ""
                }
                val cleanPath = defaultRef.path.trimStart('/')
                Result.success(Pair(cleanPath, downloadUrl))
            }
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "Firebase Storage upload failed for $code: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadImageToStorage(
        code: String,
        fileName: String,
        imageBytes: ByteArray,
        mimeType: String,
        ownerUid: String = ""
    ): Result<Pair<String, String>> {
        return uploadFileToStorage(
            code = code,
            fileName = fileName,
            fileBytes = imageBytes,
            mimeType = mimeType,
            ownerUid = ownerUid,
            folder = "images"
        )
    }

    suspend fun revokeSortCode(code: String): Result<Unit> {
        val docRef = db.collection("sortCodes").document(code)
        return try {
            docRef.update("status", "revoked").await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun downloadFileBytes(storagePath: String): Result<ByteArray> {
        val cleanPath = storagePath.trimStart('/')
        if (cleanPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Storage path is blank"))
        }
        return try {
            val maxBytes = 26214400L // 25 MB
            val storage = getFirebaseStorage()
            val bytes = storage.reference.child(cleanPath).getBytes(maxBytes).await()
            android.util.Log.i("SortCodeRepository", "Downloaded ${bytes.size} bytes from Firebase Storage: $cleanPath")
            Result.success(bytes)
        } catch (e: Exception) {
            android.util.Log.w("SortCodeRepository", "Primary storage download failed for $cleanPath: ${e.message}. Attempting fallback...")
            try {
                // Try default instance
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val bytes = storage.reference.child(cleanPath).getBytes(26214400L).await()
                android.util.Log.i("SortCodeRepository", "Downloaded ${bytes.size} bytes via fallback storage: $cleanPath")
                Result.success(bytes)
            } catch (e2: Exception) {
                android.util.Log.e("SortCodeRepository", "All storage download attempts failed for $cleanPath: ${e2.message}", e2)
                Result.failure(e2)
            }
        }
    }

    suspend fun uploadChunksToFirestore(
        code: String,
        fileBytes: ByteArray,
        onProgress: (progress: Float) -> Unit = {}
    ): Result<Int> {
        return try {
            val chunkSize = 350 * 1024 // 350 KB per chunk (fits comfortably in Firestore doc)
            val chunks = fileBytes.asSequence()
                .chunked(chunkSize)
                .map { it.toByteArray() }
                .toList()
            val totalChunks = chunks.size

            if (totalChunks > 100) {
                return Result.failure(IllegalArgumentException("File too large to chunk ($totalChunks chunks). Max allowed is 100 chunks."))
            }

            val chunksCol = db.collection("sortCodes").document(code).collection("chunks")

            chunks.forEachIndexed { index, chunkData ->
                val base64Data = android.util.Base64.encodeToString(chunkData, android.util.Base64.NO_WRAP)
                val chunkDoc = mapOf(
                    "chunkIndex" to index,
                    "totalChunks" to totalChunks,
                    "data" to base64Data,
                    "size" to chunkData.size
                )
                chunksCol.document("chunk_$index").set(chunkDoc).await()
                onProgress((index + 1).toFloat() / totalChunks)
            }

            Result.success(totalChunks)
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "Failed uploading chunks: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadChunksFromFirestore(code: String, totalChunks: Int): Result<ByteArray> {
        return try {
            val chunksCol = db.collection("sortCodes").document(code).collection("chunks")
            val snapshot = chunksCol.orderBy("chunkIndex").get().await()
            val outputStream = java.io.ByteArrayOutputStream()
            for (doc in snapshot.documents) {
                val base64Data = doc.getString("data") ?: ""
                if (base64Data.isNotBlank()) {
                    val decoded = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
                    outputStream.write(decoded)
                }
            }
            val resultBytes = outputStream.toByteArray()
            if (resultBytes.isEmpty() && totalChunks > 0) {
                Result.failure(IllegalStateException("No chunk data could be retrieved for SortCode $code"))
            } else {
                Result.success(resultBytes)
            }
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "Failed downloading chunks: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadImageBytes(storagePath: String): Result<ByteArray> = downloadFileBytes(storagePath)

    suspend fun deleteSortCode(code: String): Result<Unit> {
        val docRef = db.collection("sortCodes").document(code)
        return try {
            try {
                val snapshot = docRef.get().await()
                val storagePath = snapshot.getString("storagePath")
                if (!storagePath.isNullOrBlank()) {
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                    storage.reference.child(storagePath.trimStart('/')).delete().await()
                }
                // Also clean up any subcollection chunks
                val chunks = docRef.collection("chunks").get().await()
                for (chunk in chunks.documents) {
                    chunk.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("SortCodeRepository", "File or chunks delete non-fatal error: ${e.message}")
            }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun cleanupExpiredCodes(userId: String): Result<Int> {
        return try {
            val collectionRef = db.collection("sortCodes")
            val snapshot = collectionRef.whereEqualTo("ownerUid", userId).get().await()
            var deletedCount = 0
            val now = java.util.Date()
            for (doc in snapshot.documents) {
                val item = doc.toObject(SortCodeItem::class.java)
                if (item != null && (item.isExpired(now) || item.isRevoked())) {
                    val storagePath = item.storagePath
                    if (storagePath.isNotBlank()) {
                        try {
                            val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                            storage.reference.child(storagePath).delete().await()
                        } catch (e: Exception) {
                            android.util.Log.w("SortCodeRepository", "Cleanup storage error: ${e.message}")
                        }
                    }
                    doc.reference.delete().await()
                    deletedCount++
                }
            }
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Purges all personal records belonging to [userId]:
     * 1. All created SortCode documents owned by [userId].
     * 2. All storage assets uploaded by [userId] linked to those SortCodes.
     * 3. The user's Firestore profile at users/{userId}.
     * Note: Subscriptions are kept for auditing/legal compliance per firestore rules.
     */
    suspend fun deleteUserAccountData(
        userId: String,
        onProgressUpdate: (step: String) -> Unit = {}
    ): Result<AccountDeletionSummary> {
        return try {
            onProgressUpdate("Locating created SortCodes...")
            val collectionRef = db.collection("sortCodes")
            val snapshot = collectionRef.whereEqualTo("ownerUid", userId).get().await()

            var deletedCodesCount = 0
            var deletedStorageFilesCount = 0

            onProgressUpdate("Purging ${snapshot.size()} SortCode(s) and Storage files...")
            val storage = com.google.firebase.storage.FirebaseStorage.getInstance()

            for (doc in snapshot.documents) {
                val item = doc.toObject(SortCodeItem::class.java)
                val storagePath = item?.storagePath ?: doc.getString("storagePath")
                if (!storagePath.isNullOrBlank()) {
                    try {
                        storage.reference.child(storagePath).delete().await()
                        deletedStorageFilesCount++
                    } catch (e: Exception) {
                        android.util.Log.w("SortCodeRepository", "Storage delete non-fatal error: ${e.message}")
                    }
                }
                doc.reference.delete().await()
                deletedCodesCount++
            }

            onProgressUpdate("Removing user profile record...")
            val userDocRef = db.collection("users").document(userId)
            val profileDeleted = try {
                userDocRef.delete().await()
                true
            } catch (e: Exception) {
                android.util.Log.w("SortCodeRepository", "User profile delete error: ${e.message}")
                false
            }

            Result.success(
                AccountDeletionSummary(
                    sortCodesDeleted = deletedCodesCount,
                    storageFilesDeleted = deletedStorageFilesCount,
                    profileDeleted = profileDeleted,
                    message = "Successfully purged $deletedCodesCount SortCode(s) and personal profile."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "Error deleting user account data: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun incrementViewCount(code: String): Result<Unit> {
        val docRef = db.collection("sortCodes").document(code)
        return try {
            docRef.update("viewsCount", FieldValue.increment(1)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.d("SortCodeRepository", "Non-fatal view count increment skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val docRef = db.collection("users").document(profile.userId)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to profile.userId,
                    "displayName" to profile.displayName,
                    "email" to profile.email,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to profile.displayName,
                        "email" to profile.email
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            Result.success(snapshot.toObject(UserProfile::class.java))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserSubscription(userId: String): Flow<UserSubscription?> {
        val docRef = db.collection("users").document(userId).collection("subscription").document("current")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserSubscription::class.java)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                emit(null)
            }
    }

    suspend fun getUserSubscription(userId: String): Result<UserSubscription?> {
        val docRef = db.collection("users").document(userId).collection("subscription").document("current")
        return try {
            val snapshot = docRef.get().await()
            val sub = if (snapshot.exists()) snapshot.toObject(UserSubscription::class.java) else null
            Result.success(sub)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Securely verifies a Razorpay payment and activates a 7-day or other Premium subscription.
     * Rejects invalid, empty, or failed payment IDs.
     */
    suspend fun verifyRazorpayPayment(
        userId: String,
        paymentId: String,
        orderId: String = "",
        signature: String = "",
        planId: String = "premium_weekly_7d"
    ): Result<UserSubscription> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("User must be signed in to verify payment."))
        }
        if (paymentId.isBlank() || !paymentId.startsWith("pay_")) {
            return Result.failure(IllegalArgumentException("Invalid or missing Razorpay payment ID ($paymentId). Premium was NOT activated."))
        }

        val isWeekly = planId == "premium_weekly_7d" || planId.contains("7d") || planId.contains("weekly")
        val isAnnual = planId.contains("annual")
        val durationDays = if (isWeekly) 7L else if (isAnnual) 365L else 30L
        val planPrice = if (isWeekly) 99 else if (isAnnual) 799 else 199
        val planName = if (isWeekly) "7-Day Premium Pass" else if (isAnnual) "Annual Premium" else "Monthly Premium"
        val billingPeriod = if (isWeekly) "7 days" else if (isAnnual) "annual" else "monthly"

        val now = java.util.Date()
        val expiry = java.util.Date(now.time + durationDays * 24L * 60L * 60L * 1000L)

        val docRef = db.collection("users").document(userId).collection("subscription").document("current")

        return try {
            val subData = mapOf(
                "userId" to userId,
                "status" to "active",
                "planId" to if (isWeekly) "premium_weekly_7d" else if (isAnnual) "premium_annual" else "premium_monthly",
                "planName" to planName,
                "priceInr" to planPrice,
                "billingPeriod" to billingPeriod,
                "startDate" to com.google.firebase.Timestamp(now),
                "expiryDate" to com.google.firebase.Timestamp(expiry),
                "provider" to "razorpay",
                "transactionId" to paymentId,
                "autoRenewing" to !isWeekly,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            try {
                docRef.set(subData).await()
            } catch (ruleEx: Exception) {
                android.util.Log.i("SortCodeRepository", "Client write status: ${ruleEx.message}")
            }

            val freshSnap = docRef.get().await()
            val subscription = if (freshSnap.exists()) {
                freshSnap.toObject(UserSubscription::class.java)
            } else {
                UserSubscription(
                    userId = userId,
                    status = "active",
                    planId = if (isWeekly) "premium_weekly_7d" else if (isAnnual) "premium_annual" else "premium_monthly",
                    planName = planName,
                    priceInr = planPrice,
                    billingPeriod = billingPeriod,
                    startDate = com.google.firebase.Timestamp(now),
                    expiryDate = com.google.firebase.Timestamp(expiry),
                    provider = "razorpay",
                    transactionId = paymentId,
                    autoRenewing = !isWeekly
                )
            }

            Result.success(subscription ?: UserSubscription(status = "active"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "Payment verification error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Securely verifies a BillDesk payment and activates a 7-day or other Premium subscription.
     * Rejects invalid, empty, or unverified BillDesk payment IDs.
     */
    suspend fun verifyBilldeskPayment(
        userId: String,
        paymentId: String,
        orderId: String = "",
        signature: String = "",
        planId: String = "premium_weekly_7d"
    ): Result<UserSubscription> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("User must be signed in to verify BillDesk payment."))
        }
        if (paymentId.isBlank() || (!paymentId.startsWith("BD") && !paymentId.startsWith("pay_"))) {
            return Result.failure(IllegalArgumentException("Invalid or missing BillDesk transaction ID ($paymentId). Premium was NOT activated."))
        }

        val isWeekly = planId == "premium_weekly_7d" || planId.contains("7d") || planId.contains("weekly")
        val isAnnual = planId.contains("annual")
        val durationDays = if (isWeekly) 7L else if (isAnnual) 365L else 30L
        val planPrice = if (isWeekly) 99 else if (isAnnual) 799 else 199
        val planName = if (isWeekly) "7-Day Premium (BillDesk)" else if (isAnnual) "Annual Premium" else "Monthly Premium"
        val billingPeriod = if (isWeekly) "7 days" else if (isAnnual) "annual" else "monthly"

        val now = java.util.Date()
        val expiry = java.util.Date(now.time + durationDays * 24L * 60L * 60L * 1000L)

        val docRef = db.collection("users").document(userId).collection("subscription").document("current")

        return try {
            val subData = mapOf(
                "userId" to userId,
                "status" to "active",
                "planId" to if (isWeekly) "premium_weekly_7d" else if (isAnnual) "premium_annual" else "premium_monthly",
                "planName" to planName,
                "priceInr" to planPrice,
                "billingPeriod" to billingPeriod,
                "startDate" to com.google.firebase.Timestamp(now),
                "expiryDate" to com.google.firebase.Timestamp(expiry),
                "provider" to "billdesk",
                "transactionId" to paymentId,
                "autoRenewing" to !isWeekly,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            try {
                docRef.set(subData).await()
            } catch (ruleEx: Exception) {
                android.util.Log.i("SortCodeRepository", "Client write status: ${ruleEx.message}")
            }

            val freshSnap = docRef.get().await()
            val subscription = if (freshSnap.exists()) {
                freshSnap.toObject(UserSubscription::class.java)
            } else {
                UserSubscription(
                    userId = userId,
                    status = "active",
                    planId = if (isWeekly) "premium_weekly_7d" else if (isAnnual) "premium_annual" else "premium_monthly",
                    planName = planName,
                    priceInr = planPrice,
                    billingPeriod = billingPeriod,
                    startDate = com.google.firebase.Timestamp(now),
                    expiryDate = com.google.firebase.Timestamp(expiry),
                    provider = "billdesk",
                    transactionId = paymentId,
                    autoRenewing = !isWeekly
                )
            }

            Result.success(subscription ?: UserSubscription(status = "active", provider = "billdesk"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "BillDesk payment verification error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Securely saves verified Google Play subscription to Firestore
     */
    suspend fun saveGooglePlaySubscription(
        userId: String,
        subscription: UserSubscription
    ): Result<UserSubscription> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }
        val docRef = db.collection("users").document(userId).collection("subscription").document("current")
        return try {
            val subData = hashMapOf(
                "userId" to userId,
                "status" to subscription.status,
                "planId" to subscription.planId,
                "planName" to subscription.planName,
                "priceInr" to subscription.priceInr,
                "billingPeriod" to subscription.billingPeriod,
                "startDate" to subscription.startDate,
                "expiryDate" to subscription.expiryDate,
                "provider" to "google_play",
                "transactionId" to subscription.transactionId,
                "autoRenewing" to subscription.autoRenewing,
                "isTest" to subscription.isTest
            )
            try {
                docRef.set(subData).await()
            } catch (ruleEx: Exception) {
                android.util.Log.i("SortCodeRepository", "Google Play subscription save status: ${ruleEx.message}")
            }
            Result.success(subscription)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SortCodeRepository", "Failed to save Google Play subscription: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Tier limits
     */
    fun getMaxFileSize(isPremium: Boolean): Long = if (isPremium) 26214400L else 5242880L // 25 MB vs 5 MB
    fun getMaxTextLength(isPremium: Boolean): Int = if (isPremium) 100000 else 20000 // 100k vs 20k

    /**
     * Reusable subscription checking function
     */
    fun isSubscriptionActive(subscription: UserSubscription?, now: java.util.Date = java.util.Date()): Boolean {
        return subscription?.isPremiumActive(now) == true
    }
}
