package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SortCodeRepository
import com.example.model.PremiumPlan
import com.example.model.SortCodeItem
import com.example.model.UserSubscription
import com.example.ui.components.ScreenTab
import com.example.ui.theme.AppThemeMode
import com.example.util.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val repository: SortCodeRepository
) : ViewModel() {

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _currentTab = MutableStateFlow(ScreenTab.HOME)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _userCodes = MutableStateFlow<List<SortCodeItem>>(emptyList())
    val userCodes: StateFlow<List<SortCodeItem>> = _userCodes.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _createdItem = MutableStateFlow<SortCodeItem?>(null)
    val createdItem: StateFlow<SortCodeItem?> = _createdItem.asStateFlow()

    private val _creationError = MutableStateFlow<String?>(null)
    val creationError: StateFlow<String?> = _creationError.asStateFlow()

    private val _isRetrieving = MutableStateFlow(false)
    val isRetrieving: StateFlow<Boolean> = _isRetrieving.asStateFlow()

    private val _retrievedItem = MutableStateFlow<SortCodeItem?>(null)
    val retrievedItem: StateFlow<SortCodeItem?> = _retrievedItem.asStateFlow()

    private val _retrievalError = MutableStateFlow<String?>(null)
    val retrievalError: StateFlow<String?> = _retrievalError.asStateFlow()

    private val _initialRetrievalCode = MutableStateFlow("")
    val initialRetrievalCode: StateFlow<String> = _initialRetrievalCode.asStateFlow()

    private val _userSubscription = MutableStateFlow<UserSubscription?>(null)
    val userSubscription: StateFlow<UserSubscription?> = _userSubscription.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _purchaseMessage = MutableStateFlow<String?>(null)
    val purchaseMessage: StateFlow<String?> = _purchaseMessage.asStateFlow()

    private val _initialCreateContentType = MutableStateFlow("text")
    val initialCreateContentType: StateFlow<String> = _initialCreateContentType.asStateFlow()

    private var userCodesJob: Job? = null
    private var subscriptionJob: Job? = null

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun selectLanguage(lang: AppLanguage) {
        _currentLanguage.value = lang
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun toggleTheme() {
        _themeMode.value = if (_themeMode.value == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
    }

    fun observeUserCodes(userId: String) {
        userCodesJob?.cancel()
        userCodesJob = viewModelScope.launch {
            repository.observeUserSortCodes(userId)
                .catch { _userCodes.value = emptyList() }
                .collect { codes ->
                    _userCodes.value = codes
                }
        }
    }

    fun observeUserSubscription(userId: String) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            repository.observeUserSubscription(userId)
                .catch { 
                    _userSubscription.value = null
                    _isPremium.value = false
                }
                .collect { sub ->
                    _userSubscription.value = sub
                    _isPremium.value = sub?.isPremiumActive() == true
                }
        }
    }

    fun onUserSignedOut() {
        userCodesJob?.cancel()
        userCodesJob = null
        subscriptionJob?.cancel()
        subscriptionJob = null
        _userCodes.value = emptyList()
        _userSubscription.value = null
        _isPremium.value = false
        _createdItem.value = null
        _creationError.value = null
    }

    fun registerGooglePlayPurchase(
        userId: String,
        purchase: com.android.billingclient.api.Purchase,
        planOrBasePlanId: String
    ) {
        viewModelScope.launch {
            // Strict verification: purchase must be in PURCHASED state and possess valid identifiers
            if (purchase.purchaseState != com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) {
                android.util.Log.w("MainViewModel", "[MsgSortBilling] Purchase rejected: purchaseState is not PURCHASED (${purchase.purchaseState})")
                return@launch
            }
            if (purchase.purchaseToken.isBlank()) {
                android.util.Log.w("MainViewModel", "[MsgSortBilling] Purchase rejected: purchaseToken is blank")
                return@launch
            }

            val isAnnual = planOrBasePlanId == "yearly-799" ||
                    planOrBasePlanId.contains("year", ignoreCase = true) ||
                    planOrBasePlanId.contains("annual", ignoreCase = true)
            val isWeekly = planOrBasePlanId == "weekly-99-new" ||
                    planOrBasePlanId.contains("week", ignoreCase = true)

            val durationDays = when {
                isAnnual -> 365L
                isWeekly -> 7L
                else -> 30L
            }
            val planName = when {
                isAnnual -> "Annual Premium"
                isWeekly -> "7-Day Premium Pass"
                else -> "Monthly Premium"
            }
            val priceInr = when {
                isAnnual -> 799
                isWeekly -> 99
                else -> 199
            }
            val billingPeriod = when {
                isAnnual -> "annual"
                isWeekly -> "weekly"
                else -> "monthly"
            }
            val canonicalPlanId = when {
                isAnnual -> com.example.model.PremiumPlans.ANNUAL.id
                isWeekly -> com.example.model.PremiumPlans.WEEKLY_7D.id
                else -> com.example.model.PremiumPlans.MONTHLY.id
            }

            val now = java.util.Date()
            val expiry = java.util.Date(now.time + durationDays * 24L * 3600L * 1000L)

            val verifiedSub = UserSubscription(
                userId = userId,
                status = "active",
                planId = canonicalPlanId,
                planName = planName,
                priceInr = priceInr,
                billingPeriod = billingPeriod,
                startDate = com.google.firebase.Timestamp(now),
                expiryDate = com.google.firebase.Timestamp(expiry),
                provider = "google_play",
                transactionId = purchase.orderId?.ifBlank { null } ?: purchase.purchaseToken.take(20),
                autoRenewing = purchase.isAutoRenewing,
                isTest = false
            )

            // Persist verified subscription to Firestore
            repository.saveGooglePlaySubscription(userId, verifiedSub)

            _userSubscription.value = verifiedSub
            _isPremium.value = verifiedSub.isPremiumActive()
            _purchaseMessage.value = "Google Play Purchase verified! $planName active."
            android.util.Log.i("MainViewModel", "[MsgSortBilling] Activated $planName for user $userId (expires: ${verifiedSub.formattedExpiryDate()})")
        }
    }

    fun restorePurchases(userId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isPurchasing.value = true
            val result = repository.getUserSubscription(userId)
            _isPurchasing.value = false
            result.onSuccess { sub ->
                if (sub != null && sub.isPremiumActive()) {
                    _userSubscription.value = sub
                    _isPremium.value = true
                    onResult(true, "Restored active ${sub.planName} subscription (valid until ${sub.formattedExpiryDate()}).")
                } else {
                    _userSubscription.value = null
                    _isPremium.value = false
                    onResult(false, "No active subscription found for this Google account.")
                }
            }.onFailure {
                _userSubscription.value = null
                _isPremium.value = false
                onResult(false, "Failed to restore purchases: ${it.localizedMessage}")
            }
        }
    }

    fun createSortCode(item: SortCodeItem, mediaBytes: ByteArray? = null) {
        viewModelScope.launch {
            _isCreating.value = true
            _creationError.value = null
            var finalItem = item

            val isPdf = item.fileMimeType == "application/pdf" ||
                    item.contentType == "application/pdf" ||
                    item.fileName.endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                // Strict PDF requirements: upload actual binary to Firebase Storage before generating SortCode
                if (mediaBytes == null || mediaBytes.isEmpty()) {
                    _isCreating.value = false
                    _creationError.value = "Please select a valid PDF file to upload."
                    return@launch
                }

                android.util.Log.i("MainViewModel", "Uploading PDF (${mediaBytes.size} bytes) to Firebase Storage for code ${item.code}...")
                val uploadRes = repository.uploadFileToStorage(
                    code = item.code,
                    fileName = item.fileName.ifBlank { "document_${System.currentTimeMillis()}.pdf" },
                    fileBytes = mediaBytes,
                    mimeType = "application/pdf",
                    ownerUid = item.ownerUid,
                    folder = "files"
                )

                if (uploadRes.isSuccess) {
                    val (path, url) = uploadRes.getOrThrow()
                    android.util.Log.i("MainViewModel", "PDF uploaded successfully to storagePath: $path, url: $url")
                    finalItem = item.copy(
                        contentType = "application/pdf",
                        fileMimeType = "application/pdf",
                        storagePath = path,
                        fileUrl = url,
                        fileName = item.fileName.ifBlank { "document.pdf" },
                        fileSize = mediaBytes.size.toLong(),
                        fileBase64 = "",
                        totalChunks = 0
                    )
                } else {
                    val ex = uploadRes.exceptionOrNull()
                    android.util.Log.w("MainViewModel", "PDF Storage upload failed (${ex?.message}). Activating secure Firestore storage fallback...")

                    if (mediaBytes.size <= 720 * 1024) {
                        val base64Str = android.util.Base64.encodeToString(mediaBytes, android.util.Base64.NO_WRAP)
                        finalItem = item.copy(
                            contentType = "application/pdf",
                            fileMimeType = "application/pdf",
                            fileBase64 = base64Str,
                            fileUrl = "",
                            storagePath = "",
                            fileName = item.fileName.ifBlank { "document.pdf" },
                            fileSize = mediaBytes.size.toLong(),
                            totalChunks = 0
                        )
                    } else {
                        android.util.Log.i("MainViewModel", "Uploading ${mediaBytes.size} bytes PDF via Firestore Chunks subcollection...")
                        val chunkRes = repository.uploadChunksToFirestore(item.code, mediaBytes)
                        if (chunkRes.isSuccess) {
                            val count = chunkRes.getOrThrow()
                            finalItem = item.copy(
                                contentType = "application/pdf",
                                fileMimeType = "application/pdf",
                                totalChunks = count,
                                fileBase64 = "",
                                fileUrl = "",
                                storagePath = "",
                                fileName = item.fileName.ifBlank { "document.pdf" },
                                fileSize = mediaBytes.size.toLong()
                            )
                        } else {
                            val chunkEx = chunkRes.exceptionOrNull()
                            android.util.Log.e("MainViewModel", "Failed to store PDF chunks in Firestore: ${chunkEx?.message}", chunkEx)
                            _isCreating.value = false
                            _creationError.value = "Failed to secure PDF payload: ${chunkEx?.localizedMessage ?: "Upload error"}. Please retry."
                            return@launch
                        }
                    }
                }
            } else if (mediaBytes != null && item.contentType in listOf("image", "file", "document", "video")) {
                val uploadRes = repository.uploadFileToStorage(
                    code = item.code,
                    fileName = item.fileName.ifBlank { "upload_${System.currentTimeMillis()}" },
                    fileBytes = mediaBytes,
                    mimeType = item.fileMimeType.ifBlank { "application/octet-stream" },
                    ownerUid = item.ownerUid
                )
                if (uploadRes.isSuccess) {
                    val (path, url) = uploadRes.getOrThrow()
                    finalItem = if (item.contentType == "image") {
                        finalItem.copy(storagePath = path, imageUrl = url, fileBase64 = "")
                    } else {
                        finalItem.copy(storagePath = path, fileUrl = url, fileBase64 = "")
                    }
                } else {
                    val ex = uploadRes.exceptionOrNull()
                    android.util.Log.w("MainViewModel", "Storage upload failed: ${ex?.message}. Checking fallback.")
                    val fallbackBytes = if (item.contentType == "image") {
                        if (mediaBytes.size > 600 * 1024) compressImageForFallback(mediaBytes) else mediaBytes
                    } else {
                        mediaBytes
                    }

                    // Ensure payload fits safely within Firestore document (1,000,000 char base64 limit)
                    if (fallbackBytes.size <= 720 * 1024) {
                        val base64Str = android.util.Base64.encodeToString(fallbackBytes, android.util.Base64.NO_WRAP)
                        finalItem = if (item.contentType == "image") {
                            finalItem.copy(
                                fileBase64 = base64Str,
                                imageUrl = "", // MUST remain empty for base64 inline images to satisfy URL length rules
                                fileUrl = "",
                                storagePath = "",
                                fileSize = fallbackBytes.size.toLong()
                            )
                        } else {
                            finalItem.copy(
                                fileBase64 = base64Str,
                                fileUrl = "",
                                imageUrl = "",
                                storagePath = "",
                                fileSize = fallbackBytes.size.toLong()
                            )
                        }
                    } else {
                        // Chunked upload to Firestore subcollection (supports videos & large files without external bucket)
                        android.util.Log.i("MainViewModel", "Uploading ${fallbackBytes.size} bytes via Firestore Chunks fallback...")
                        val chunkRes = repository.uploadChunksToFirestore(item.code, fallbackBytes)
                        if (chunkRes.isSuccess) {
                            val count = chunkRes.getOrThrow()
                            finalItem = if (item.contentType == "image") {
                                finalItem.copy(
                                    totalChunks = count,
                                    fileBase64 = "",
                                    imageUrl = "",
                                    fileUrl = "",
                                    storagePath = "",
                                    fileSize = fallbackBytes.size.toLong()
                                )
                            } else {
                                finalItem.copy(
                                    totalChunks = count,
                                    fileBase64 = "",
                                    fileUrl = "",
                                    imageUrl = "",
                                    storagePath = "",
                                    fileSize = fallbackBytes.size.toLong()
                                )
                            }
                        } else {
                            _isCreating.value = false
                            _creationError.value = "Failed to upload ${item.contentType}: ${chunkRes.exceptionOrNull()?.localizedMessage ?: "File chunking failed"}. Please ensure file is within tier limits (5 MB Free / 25 MB Pro)."
                            return@launch
                        }
                    }
                }
            }
            val result = repository.createSortCode(finalItem)
            _isCreating.value = false
            result.onSuccess {
                _createdItem.value = it
            }.onFailure { error ->
                _creationError.value = "Failed to create SortCode: ${error.localizedMessage ?: "Network or permission error"}"
            }
        }
    }

    fun resetCreatedItem() {
        _createdItem.value = null
    }

    fun resetCreationError() {
        _creationError.value = null
    }

    fun retrieveSortCode(code: String) {
        viewModelScope.launch {
            _isRetrieving.value = true
            _retrievalError.value = null
            _retrievedItem.value = null

            val result = repository.getSortCode(code)
            _isRetrieving.value = false

            result.onSuccess { item ->
                if (item == null) {
                    _retrievalError.value = "Invalid SortCode. Please check the code and try again."
                } else if (item.isRevoked()) {
                    _retrievalError.value = "This SortCode is no longer available."
                } else if (item.isExpired()) {
                    _retrievalError.value = "This SortCode has expired. The content is no longer available."
                } else {
                    _retrievedItem.value = item
                    // Increment view count
                    repository.incrementViewCount(code)
                }
            }.onFailure { error ->
                _retrievalError.value = "Invalid SortCode or service temporarily unavailable. Please retry."
            }
        }
    }

    fun resetRetrieval() {
        _retrievedItem.value = null
        _retrievalError.value = null
    }

    suspend fun getMediaBytesForSortCode(item: SortCodeItem): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Primary: Firebase Storage download using storagePath
                if (item.storagePath.isNotBlank()) {
                    val res = repository.downloadFileBytes(item.storagePath)
                    if (res.isSuccess) {
                        val bytes = res.getOrThrow()
                        if (bytes.isNotEmpty()) {
                            android.util.Log.i("MainViewModel", "Retrieved ${bytes.size} bytes from storagePath for ${item.code}")
                            return@withContext bytes
                        }
                    }
                    android.util.Log.w("MainViewModel", "storagePath download failed for ${item.code}: ${res.exceptionOrNull()?.message}")
                }

                // 2. Secondary: Secure tokenized download URL via HTTPS
                val sourceUrl = item.fileUrl.ifBlank { item.imageUrl }
                if (sourceUrl.isNotBlank() && !sourceUrl.startsWith("data:")) {
                    try {
                        val url = java.net.URL(sourceUrl)
                        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                        }
                        try {
                            conn.connect()
                            if (conn.responseCode in 200..299) {
                                val bytes = conn.inputStream.use { it.readBytes() }
                                if (bytes.isNotEmpty()) {
                                    android.util.Log.i("MainViewModel", "Retrieved ${bytes.size} bytes via fileUrl for ${item.code}")
                                    return@withContext bytes
                                }
                            }
                        } finally {
                            conn.disconnect()
                        }
                    } catch (httpEx: Exception) {
                        android.util.Log.w("MainViewModel", "HTTP download failed for $sourceUrl: ${httpEx.message}")
                    }
                }

                // 3. Fallback: Firestore chunked subcollection
                if (item.totalChunks > 0) {
                    val res = repository.downloadChunksFromFirestore(item.code, item.totalChunks)
                    if (res.isSuccess) {
                        val bytes = res.getOrThrow()
                        if (bytes.isNotEmpty()) {
                            return@withContext bytes
                        }
                    }
                }

                // 4. Fallback: Inline Base64 payload
                if (item.fileBase64.isNotBlank()) {
                    val cleanBase64 = if (item.fileBase64.contains(",")) item.fileBase64.substringAfter(",") else item.fileBase64
                    val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                    if (bytes.isNotEmpty()) {
                        return@withContext bytes
                    }
                }

                null
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed getting media bytes for ${item.code}: ${e.message}", e)
                null
            }
        }
    }

    fun navigateToRetrieveWithCode(code: String) {
        _initialRetrievalCode.value = code
        _currentTab.value = ScreenTab.RETRIEVE
        retrieveSortCode(code)
    }

    fun navigateToCreateWithType(type: String) {
        _initialCreateContentType.value = type
        _currentTab.value = ScreenTab.CREATE
    }

    fun revokeSortCode(code: String) {
        viewModelScope.launch {
            repository.revokeSortCode(code)
        }
    }

    fun deleteSortCode(code: String) {
        viewModelScope.launch {
            repository.deleteSortCode(code)
        }
    }

    fun cleanupExpiredCodes(userId: String, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.cleanupExpiredCodes(userId)
            result.onSuccess { count ->
                onComplete(count)
            }.onFailure {
                onComplete(0)
            }
        }
    }

    fun verifyAndActivateRazorpayPayment(
        userId: String,
        paymentId: String,
        orderId: String = "",
        signature: String = "",
        planId: String = "premium_weekly_7d",
        onSuccess: (UserSubscription) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isPurchasing.value = true
            val res = repository.verifyRazorpayPayment(
                userId = userId,
                paymentId = paymentId,
                orderId = orderId,
                signature = signature,
                planId = planId
            )
            _isPurchasing.value = false
            res.onSuccess { sub ->
                _userSubscription.value = sub
                onSuccess(sub)
            }.onFailure { err ->
                onError(err.localizedMessage ?: "Payment verification failed. Premium was NOT activated.")
            }
        }
    }

    fun verifyAndActivateBilldeskPayment(
        userId: String,
        paymentId: String,
        orderId: String = "",
        signature: String = "",
        planId: String = "premium_weekly_7d",
        onSuccess: (UserSubscription) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isPurchasing.value = true
            val res = repository.verifyBilldeskPayment(
                userId = userId,
                paymentId = paymentId,
                orderId = orderId,
                signature = signature,
                planId = planId
            )
            _isPurchasing.value = false
            res.onSuccess { sub ->
                _userSubscription.value = sub
                onSuccess(sub)
            }.onFailure { err ->
                onError(err.localizedMessage ?: "BillDesk verification failed. Premium was NOT activated.")
            }
        }
    }

    private fun compressImageForFallback(bytes: ByteArray): ByteArray {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 1200) {
                sampleSize *= 2
            }
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
            var result = baos.toByteArray()
            if (result.size > 650 * 1024) {
                val baos2 = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 45, baos2)
                result = baos2.toByteArray()
            }
            if (result.size > 680 * 1024) {
                // Scale down further if high density
                val scaled = android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.7f).toInt().coerceAtLeast(1),
                    (bitmap.height * 0.7f).toInt().coerceAtLeast(1),
                    true
                )
                val baos3 = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 45, baos3)
                result = baos3.toByteArray()
            }
            if (result.size < bytes.size) result else bytes
        } catch (e: Exception) {
            bytes
        }
    }
}
