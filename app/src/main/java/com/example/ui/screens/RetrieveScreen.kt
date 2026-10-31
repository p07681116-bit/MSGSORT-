package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SortCodeItem
import com.example.ui.components.SortCodeBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.SecurityUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.util.ScreenSecurityProtection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun RetrieveScreen(
    language: AppLanguage,
    initialCode: String = "",
    isRetrieving: Boolean,
    retrievedItem: SortCodeItem?,
    retrievalError: String?,
    onRetrieveCode: (String) -> Unit,
    onResetRetrieval: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadMediaBytes: suspend (SortCodeItem) -> ByteArray? = { null }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputCode by remember(initialCode) { mutableStateOf(initialCode) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordUnlocked by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showImageZoomModal by remember { mutableStateOf(false) }
    var isDownloadingImage by remember { mutableStateOf(false) }
    var isDownloadingVideo by remember { mutableStateOf(false) }
    var isPreparingVideo by remember { mutableStateOf(false) }
    var isDownloadingFile by remember { mutableStateOf(false) }
    var isOpeningPdf by remember { mutableStateOf(false) }

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(retrievedItem?.code, retrievedItem?.expiresAt) {
        val exp = retrievedItem?.expiresAt?.toDate()?.time
        if (exp != null) {
            while (true) {
                currentTimeMillis = System.currentTimeMillis()
                if (currentTimeMillis >= exp) {
                    break
                }
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    }

    // Determine if sensitive SortCode content (messages, photos, videos, PDFs, files) is currently decrypted and visible
    val isContentUnlocked = retrievedItem != null && 
        retrievedItem.isAccessible(Date(currentTimeMillis)) && 
        (!retrievedItem.passwordProtected || passwordUnlocked)

    // Android native FLAG_SECURE protection: Blocks screenshots, hides content from Recent Apps switcher, and blocks screen recording
    ScreenSecurityProtection(isProtected = isContentUnlocked)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = AppStrings.get(language, "retrieve_content"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Enter any active SortCode to decrypt and view the original message or document.",
                    fontSize = 13.sp,
                    color = SlateMuted
                )
            }
        }

        // Code Input Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = {
                            inputCode = it.uppercase()
                            onResetRetrieval()
                            passwordUnlocked = false
                            passwordError = null
                        },
                        label = { Text(AppStrings.get(language, "enter_sortcode")) },
                        placeholder = { Text(AppStrings.get(language, "sortcode_hint")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("retrieve_code_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.VpnKey, contentDescription = null, tint = RoyalIndigo)
                        },
                        trailingIcon = {
                            if (inputCode.isNotBlank()) {
                                IconButton(onClick = {
                                    inputCode = ""
                                    onResetRetrieval()
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (inputCode.isNotBlank()) {
                                    onRetrieveCode(SecurityUtils.normalizeCode(inputCode))
                                }
                            }
                        )
                    )

                    Button(
                        onClick = {
                            if (inputCode.isNotBlank()) {
                                passwordUnlocked = false
                                passwordError = null
                                onRetrieveCode(SecurityUtils.normalizeCode(inputCode))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("retrieve_submit_button"),
                        shape = RoundedCornerShape(14.dp),
                        enabled = inputCode.isNotBlank() && !isRetrieving,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) {
                        if (isRetrieving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking code...")
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get(language, "retrieve_button"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Error Feedback Display
        if (retrievalError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                        Text(
                            text = retrievalError,
                            color = ErrorRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Retrieved Item Details
        if (retrievedItem != null) {
            val item = retrievedItem
            val currentDate = Date(currentTimeMillis)
            val isExpired = item.isExpired(currentDate)
            val isRevoked = item.isRevoked()

            if (isExpired) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningContainer),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(WarningAmber, WarningAmber)))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.TimerOff, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(36.dp))
                            Text(
                                text = AppStrings.get(language, "expired_code"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF78350F),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (isRevoked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ErrorRed, ErrorRed)))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                            Text(
                                text = AppStrings.get(language, "revoked_code"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ErrorRed,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (item.passwordProtected && !passwordUnlocked) {
                // Password Challenge UI
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ContainerPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(24.dp))
                            }

                            Text(
                                text = "Password Protected Content",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "The creator of this SortCode set a password requirement.",
                                fontSize = 12.sp,
                                color = SlateMuted,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text(AppStrings.get(language, "enter_password")) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("retrieve_password_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (passwordError != null) {
                                Text(
                                    text = passwordError ?: "",
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = {
                                    val hash = SecurityUtils.sha256(passwordInput)
                                    if (hash == item.passwordHash) {
                                        passwordUnlocked = true
                                        passwordError = null
                                    } else {
                                        passwordError = "Incorrect password. Please try again."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                            ) {
                                Text("Unlock Content")
                            }
                        }
                    }
                }
            } else {
                // Valid Unlocked Content Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Top Meta Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SortCodeBadge(code = item.code)
                                StatusBadge(status = "active", language = language)
                            }

                            // Native OS Security Badge (FLAG_SECURE Active)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ContainerIndigo)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "Security Shield",
                                    tint = RoyalIndigo,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Protected View • Screenshots & Screen Recording Blocked",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RoyalIndigo
                                )
                            }

                            // Title if present
                            if (item.title.isNotBlank()) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Expiry & Meta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ContainerIndigo)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = item.remainingTimeDescription(Date(currentTimeMillis)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepIndigo
                                    )
                                }

                                if (item.viewsCount > 0) {
                                    Text(
                                        text = "${item.viewsCount} ${AppStrings.get(language, "views")}",
                                        fontSize = 12.sp,
                                        color = SlateMuted
                                    )
                                }
                            }

                            HorizontalDivider(color = BorderLight)

                            // Content Rendering
                            if (item.contentType == "text") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = item.textContent,
                                        modifier = Modifier.padding(16.dp),
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (item.downloadAllowed) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("MsgSort Content", item.textContent))
                                            Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(AppStrings.get(language, "copy_text"))
                                    }
                                } else {
                                    Text(
                                        text = AppStrings.get(language, "downloads_disabled"),
                                        fontSize = 12.sp,
                                        color = SlateMuted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else if (item.contentType == "image") {
                                val decodedImageBitmap: Bitmap? = remember(item.code, item.fileBase64) {
                                    if (item.fileBase64.isNotBlank()) {
                                        try {
                                            val clean = if (item.fileBase64.contains(",")) item.fileBase64.substringAfter(",") else item.fileBase64
                                            val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null
                                }

                                val decodedThumbnailBitmap: Bitmap? = remember(item.code, item.thumbnailBase64) {
                                    if (item.thumbnailBase64.isNotBlank()) {
                                        try {
                                            val clean = if (item.thumbnailBase64.contains(",")) item.thumbnailBase64.substringAfter(",") else item.thumbnailBase64
                                            val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null
                                }

                                var resolvedStorageUrl by remember(item.code) { mutableStateOf<String?>(null) }
                                var isResolvingStorage by remember(item.code) { mutableStateOf(false) }
                                var storageResolutionError by remember(item.code) { mutableStateOf<String?>(null) }
                                var imageRetryTrigger by remember(item.code) { mutableStateOf(0) }

                                LaunchedEffect(item.code, item.storagePath, item.imageUrl, item.fileBase64, imageRetryTrigger) {
                                    if (decodedImageBitmap == null && item.imageUrl.isBlank() && item.storagePath.isNotBlank()) {
                                        isResolvingStorage = true
                                        storageResolutionError = null
                                        try {
                                            val cleanPath = item.storagePath.trimStart('/')
                                            val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                                            try {
                                                val uri = storage.reference.child(cleanPath).downloadUrl.await()
                                                resolvedStorageUrl = uri.toString()
                                            } catch (e: Exception) {
                                                val appspotBucket = "gs://gen-lang-client-0728162115.appspot.com"
                                                val fallbackStorage = com.google.firebase.storage.FirebaseStorage.getInstance(appspotBucket)
                                                val uri = fallbackStorage.reference.child(cleanPath).downloadUrl.await()
                                                resolvedStorageUrl = uri.toString()
                                            }
                                        } catch (e: Exception) {
                                            storageResolutionError = e.localizedMessage ?: "Could not resolve photo URL from cloud storage"
                                        } finally {
                                            isResolvingStorage = false
                                        }
                                    }
                                }

                                val activeImageModel: Any? = when {
                                    decodedImageBitmap != null -> decodedImageBitmap
                                    item.imageUrl.isNotBlank() && !item.imageUrl.startsWith("data:") -> item.imageUrl
                                    resolvedStorageUrl != null -> resolvedStorageUrl
                                    decodedThumbnailBitmap != null -> decodedThumbnailBitmap
                                    else -> null
                                }

                                // Image Card & Actions
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = activeImageModel != null) { showImageZoomModal = true }
                                            .testTag("retrieve_image_preview"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 200.dp, max = 340.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isResolvingStorage) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(240.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(36.dp),
                                                        color = RoyalIndigo,
                                                        strokeWidth = 3.dp
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(
                                                        text = "Retrieving photo from cloud...",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            } else if (activeImageModel != null) {
                                                SubcomposeAsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(activeImageModel)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Uploaded Photo",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 200.dp, max = 340.dp),
                                                    contentScale = ContentScale.Fit,
                                                    loading = {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(240.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (decodedThumbnailBitmap != null) {
                                                                Image(
                                                                    bitmap = decodedThumbnailBitmap.asImageBitmap(),
                                                                    contentDescription = null,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = ContentScale.Fit,
                                                                    alpha = 0.4f
                                                                )
                                                            }
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(32.dp),
                                                                    color = RoyalIndigo,
                                                                    strokeWidth = 3.dp
                                                                )
                                                                Text(
                                                                    text = "Loading photo...",
                                                                    fontSize = 12.sp,
                                                                    color = SlateMuted
                                                                )
                                                            }
                                                        }
                                                    },
                                                    error = {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(220.dp)
                                                                .background(ErrorContainer),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                                modifier = Modifier.padding(16.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.BrokenImage,
                                                                    contentDescription = null,
                                                                    tint = ErrorRed,
                                                                    modifier = Modifier.size(40.dp)
                                                                )
                                                                Text(
                                                                    text = "Photo could not be rendered",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 14.sp,
                                                                    color = ErrorRed
                                                                )
                                                                Text(
                                                                    text = "The photo file could not be displayed. Tap to retry or download below.",
                                                                    fontSize = 12.sp,
                                                                    color = SlateMuted,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                                OutlinedButton(
                                                                    onClick = { imageRetryTrigger++ },
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Retry", fontSize = 12.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                )

                                                // Tap to zoom overlay badge
                                                Surface(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(10.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.Black.copy(alpha = 0.65f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                        Text("Tap to zoom", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                }
                                            } else {
                                                // Error Card when no photo data is resolvable
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(220.dp)
                                                        .background(ErrorContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.padding(16.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.ImageNotSupported,
                                                            contentDescription = null,
                                                            tint = ErrorRed,
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                        Text(
                                                            text = "Photo Unavailable",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = ErrorRed
                                                        )
                                                        Text(
                                                            text = storageResolutionError ?: "Photo reference or file data is missing.",
                                                            fontSize = 12.sp,
                                                            color = SlateMuted,
                                                            textAlign = TextAlign.Center
                                                        )
                                                        OutlinedButton(
                                                            onClick = { imageRetryTrigger++ },
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Retry Loading", fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Image details chip row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.fileName.ifBlank { "MsgSort_Image_${item.code}.jpg" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            val dimensionStr = if (item.imageWidth > 0 && item.imageHeight > 0) " • ${item.imageWidth}x${item.imageHeight} px" else ""
                                            Text(
                                                text = "${item.fileSize / 1024} KB$dimensionStr • ${item.fileMimeType}",
                                                fontSize = 12.sp,
                                                color = SlateMuted
                                            )
                                        }

                                        AssistChip(
                                            onClick = { showImageZoomModal = true },
                                            enabled = activeImageModel != null,
                                            label = { Text("FULL VIEW") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        )
                                    }

                                    // Download Button
                                    if (item.downloadAllowed) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isDownloadingImage = true
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                                                            val ext = when {
                                                                item.fileMimeType.contains("png", true) -> ".png"
                                                                item.fileMimeType.contains("webp", true) -> ".webp"
                                                                else -> ".jpg"
                                                            }
                                                            val safeName = item.fileName.ifBlank { "MsgSort_${item.code}$ext" }
                                                            val outFile = File(downloadsDir, safeName)

                                                            val sourceUrl = item.imageUrl.ifBlank { resolvedStorageUrl ?: "" }
                                                            if (sourceUrl.isNotBlank() && !sourceUrl.startsWith("data:")) {
                                                                val url = URL(sourceUrl)
                                                                val conn = (url.openConnection() as HttpURLConnection).apply {
                                                                    connectTimeout = 15000
                                                                    readTimeout = 30000
                                                                }
                                                                try {
                                                                    conn.connect()
                                                                    conn.inputStream.use { input ->
                                                                        FileOutputStream(outFile).use { output ->
                                                                            input.copyTo(output)
                                                                        }
                                                                    }
                                                                } finally {
                                                                    conn.disconnect()
                                                                }
                                                            } else if (item.fileBase64.isNotBlank()) {
                                                                val clean = if (item.fileBase64.contains(",")) item.fileBase64.substringAfter(",") else item.fileBase64
                                                                val decodedBytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                                                                FileOutputStream(outFile).use { it.write(decodedBytes) }
                                                            } else if (item.storagePath.isNotBlank()) {
                                                                val cleanPath = item.storagePath.trimStart('/')
                                                                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                                                                val bytes = storage.reference.child(cleanPath).getBytes(25L * 1024 * 1024).await()
                                                                FileOutputStream(outFile).use { it.write(bytes) }
                                                            } else if (decodedThumbnailBitmap != null) {
                                                                FileOutputStream(outFile).use { out ->
                                                                    decodedThumbnailBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                                                }
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Image saved: ${outFile.name}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } finally {
                                                        isDownloadingImage = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("download_image_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                            enabled = !isDownloadingImage
                                        ) {
                                            if (isDownloadingImage) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Downloading Image...")
                                            } else {
                                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(AppStrings.get(language, "download_image"))
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = AppStrings.get(language, "downloads_disabled"),
                                            fontSize = 12.sp,
                                            color = SlateMuted,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Secondary actions: Copy link & share
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val link = "https://msgsort.app/c/${item.code}"
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("MsgSort Link", link))
                                                Toast.makeText(context, "Link copied: $link", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(AppStrings.get(language, "copy_link"), fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "MsgSort Photo")
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "View protected photo on MsgSort with SortCode: ${item.code}\nLink: https://msgsort.app/c/${item.code}"
                                                    )
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share SortCode"))
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("share_image_button"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(AppStrings.get(language, "share"), fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else if (item.contentType == "video") {
                                // Video Card & Actions
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .testTag("retrieve_video_card"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(ContainerIndigo),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayCircle,
                                                    contentDescription = "Video Play",
                                                    tint = RoyalIndigo,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }

                                            Text(
                                                text = item.fileName.ifBlank { "MsgSort_Video_${item.code}.mp4" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )

                                            val sizeLabel = if (item.fileSize > 0) {
                                                if (item.fileSize >= 1024 * 1024) {
                                                    String.format(Locale.US, "%.1f MB", item.fileSize.toDouble() / (1024 * 1024))
                                                } else {
                                                    "${item.fileSize / 1024} KB"
                                                }
                                            } else {
                                                "Video Clip"
                                            }

                                            Text(
                                                text = "Video Clip • $sizeLabel • ${item.fileMimeType.ifBlank { "video/mp4" }}",
                                                fontSize = 12.sp,
                                                color = SlateMuted
                                            )

                                            // Play Video Button (handles local chunks, base64, cached file, or streaming URL)
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isPreparingVideo = true
                                                        try {
                                                            val videosDir = File(context.cacheDir, "videos").apply { mkdirs() }
                                                            val safeName = item.fileName.ifBlank { "MsgSort_${item.code}.mp4" }
                                                            val cachedFile = File(videosDir, safeName)

                                                            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                                                                val bytes = onDownloadMediaBytes(item)
                                                                if (bytes != null && bytes.isNotEmpty()) {
                                                                    withContext(Dispatchers.IO) {
                                                                        FileOutputStream(cachedFile).use { it.write(bytes) }
                                                                    }
                                                                } else if (item.fileUrl.isNotBlank()) {
                                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(android.net.Uri.parse(item.fileUrl), item.fileMimeType.ifBlank { "video/*" })
                                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    }
                                                                    context.startActivity(intent)
                                                                    isPreparingVideo = false
                                                                    return@launch
                                                                }
                                                            }

                                                            if (cachedFile.exists() && cachedFile.length() > 0L) {
                                                                val uri = FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.fileprovider",
                                                                    cachedFile
                                                                )
                                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                    setDataAndType(uri, item.fileMimeType.ifBlank { "video/*" })
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                }
                                                                context.startActivity(intent)
                                                            } else {
                                                                Toast.makeText(context, "Could not load video data. Please retry.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Error opening video: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        } finally {
                                                            isPreparingVideo = false
                                                        }
                                                    }
                                                },
                                                enabled = !isPreparingVideo,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                                modifier = Modifier.fillMaxWidth().testTag("play_video_button")
                                            ) {
                                                if (isPreparingVideo) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Loading Video...")
                                                } else {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Play Video")
                                                }
                                            }
                                        }
                                    }

                                    if (item.downloadAllowed) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isDownloadingVideo = true
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                                                            val safeName = item.fileName.ifBlank { "MsgSort_${item.code}.mp4" }
                                                            val outFile = File(moviesDir, safeName)

                                                            val cachedFile = File(File(context.cacheDir, "videos"), safeName)
                                                            if (cachedFile.exists() && cachedFile.length() > 0L) {
                                                                cachedFile.copyTo(outFile, overwrite = true)
                                                            } else {
                                                                val bytes = onDownloadMediaBytes(item)
                                                                if (bytes != null && bytes.isNotEmpty()) {
                                                                    FileOutputStream(outFile).use { it.write(bytes) }
                                                                } else if (item.fileUrl.isNotBlank()) {
                                                                    val url = URL(item.fileUrl)
                                                                    val conn = (url.openConnection() as HttpURLConnection).apply {
                                                                        connectTimeout = 15000
                                                                        readTimeout = 30000
                                                                    }
                                                                    try {
                                                                        conn.connect()
                                                                        conn.inputStream.use { input ->
                                                                            FileOutputStream(outFile).use { output ->
                                                                                input.copyTo(output)
                                                                            }
                                                                        }
                                                                    } finally {
                                                                        conn.disconnect()
                                                                    }
                                                                }
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Video saved to gallery: ${outFile.name}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } finally {
                                                        isDownloadingVideo = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("download_video_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !isDownloadingVideo
                                        ) {
                                            if (isDownloadingVideo) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RoyalIndigo, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Saving Video...")
                                            } else {
                                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = RoyalIndigo)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Save Video to Device", color = RoyalIndigo)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = AppStrings.get(language, "downloads_disabled"),
                                            fontSize = 12.sp,
                                            color = SlateMuted,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Secondary actions: Copy link & share
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val link = "https://msgsort.app/c/${item.code}"
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("MsgSort Link", link))
                                                Toast.makeText(context, "Link copied: $link", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(AppStrings.get(language, "copy_link"), fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "MsgSort Video")
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "View protected video on MsgSort with SortCode: ${item.code}\nLink: https://msgsort.app/c/${item.code}"
                                                    )
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share SortCode"))
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(AppStrings.get(language, "share"), fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                val isPdf = item.contentType == "application/pdf" ||
                                        item.fileMimeType.equals("application/pdf", ignoreCase = true) ||
                                        item.fileName.endsWith(".pdf", ignoreCase = true)

                                if (isPdf) {
                                    // Dedicated PDF Document Card
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFFEE2E2)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PictureAsPdf,
                                                        contentDescription = "PDF Document",
                                                        tint = Color(0xFFDC2626),
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.fileName.ifBlank { "MsgSort_${item.code}.pdf" },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${if (item.fileSize > 1024 * 1024) String.format(Locale.US, "%.1f MB", item.fileSize / (1024.0 * 1024.0)) else "${(item.fileSize / 1024).coerceAtLeast(1)} KB"} • PDF Document",
                                                        fontSize = 12.sp,
                                                        color = SlateMuted
                                                    )
                                                }
                                            }

                                            // Status Banner
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFFEF2F2)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                                                    Text(
                                                        text = "Protected with Android FLAG_SECURE",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF991B1B)
                                                    )
                                                }
                                            }

                                            // Open & View PDF Button
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isOpeningPdf = true
                                                        try {
                                                            val bytes = withContext(Dispatchers.IO) {
                                                                var data = onDownloadMediaBytes(item)
                                                                if (data == null || data.isEmpty()) {
                                                                    val sourceUrl = item.fileUrl.ifBlank { item.imageUrl }
                                                                    if (sourceUrl.isNotBlank() && !sourceUrl.startsWith("data:")) {
                                                                        try {
                                                                            val conn = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                                                                                connectTimeout = 15000
                                                                                readTimeout = 30000
                                                                            }
                                                                            conn.connect()
                                                                            if (conn.responseCode in 200..299) {
                                                                                data = conn.inputStream.use { it.readBytes() }
                                                                            }
                                                                            conn.disconnect()
                                                                        } catch (e: Exception) {
                                                                            android.util.Log.w("RetrieveScreen", "Fallback URL fetch failed: ${e.message}")
                                                                        }
                                                                    }
                                                                }
                                                                data
                                                            }

                                                            if (bytes == null || bytes.isEmpty()) {
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, "Could not retrieve PDF: file payload is unavailable or expired.", Toast.LENGTH_LONG).show()
                                                                }
                                                            } else {
                                                                withContext(Dispatchers.IO) {
                                                                    val docsDir = File(context.cacheDir, "documents").apply { mkdirs() }
                                                                    val safeName = item.fileName.ifBlank { "MsgSort_${item.code}.pdf" }.let {
                                                                        if (!it.endsWith(".pdf", ignoreCase = true)) "$it.pdf" else it
                                                                    }
                                                                    val pdfFile = File(docsDir, safeName)
                                                                    FileOutputStream(pdfFile).use { it.write(bytes) }

                                                                    val contentUri = FileProvider.getUriForFile(
                                                                        context,
                                                                        "${context.packageName}.fileprovider",
                                                                        pdfFile
                                                                    )

                                                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(contentUri, "application/pdf")
                                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }

                                                                    withContext(Dispatchers.Main) {
                                                                        try {
                                                                            context.startActivity(Intent.createChooser(viewIntent, "Open PDF"))
                                                                        } catch (e: Exception) {
                                                                            Toast.makeText(context, "No PDF viewer app found on device.", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Failed to open PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } finally {
                                                            isOpeningPdf = false
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("open_pdf_button"),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                enabled = !isOpeningPdf && !isDownloadingFile
                                            ) {
                                                if (isOpeningPdf) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Loading PDF...")
                                                } else {
                                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Open & View PDF")
                                                }
                                            }

                                            // Save to Downloads Button
                                            if (item.downloadAllowed) {
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            isDownloadingFile = true
                                                            try {
                                                                val bytes = withContext(Dispatchers.IO) {
                                                                    var data = onDownloadMediaBytes(item)
                                                                    if (data == null || data.isEmpty()) {
                                                                        val sourceUrl = item.fileUrl.ifBlank { item.imageUrl }
                                                                        if (sourceUrl.isNotBlank() && !sourceUrl.startsWith("data:")) {
                                                                            try {
                                                                                val conn = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                                                                                    connectTimeout = 15000
                                                                                    readTimeout = 30000
                                                                                }
                                                                                conn.connect()
                                                                                if (conn.responseCode in 200..299) {
                                                                                    data = conn.inputStream.use { it.readBytes() }
                                                                                }
                                                                                conn.disconnect()
                                                                            } catch (e: Exception) {
                                                                                android.util.Log.w("RetrieveScreen", "Fallback URL fetch failed: ${e.message}")
                                                                            }
                                                                        }
                                                                    }
                                                                    data
                                                                }

                                                                if (bytes == null || bytes.isEmpty()) {
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "Download failed: file payload unavailable.", Toast.LENGTH_LONG).show()
                                                                    }
                                                                } else {
                                                                    withContext(Dispatchers.IO) {
                                                                        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                                                                        val safeName = item.fileName.ifBlank { "MsgSort_${item.code}.pdf" }.let {
                                                                            if (!it.endsWith(".pdf", ignoreCase = true)) "$it.pdf" else it
                                                                        }
                                                                        val outFile = File(downloadsDir, safeName)
                                                                        FileOutputStream(outFile).use { it.write(bytes) }
                                                                        withContext(Dispatchers.Main) {
                                                                            Toast.makeText(context, "PDF saved to downloads: ${outFile.name}", Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } finally {
                                                                isDownloadingFile = false
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("download_file_button"),
                                                    shape = RoundedCornerShape(12.dp),
                                                    enabled = !isDownloadingFile && !isOpeningPdf
                                                ) {
                                                    if (isDownloadingFile) {
                                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RoyalIndigo, strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Saving PDF...")
                                                    } else {
                                                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = RoyalIndigo)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Save to Downloads", color = RoyalIndigo)
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = AppStrings.get(language, "downloads_disabled"),
                                                    fontSize = 12.sp,
                                                    color = SlateMuted,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            // Copy link & Share
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val link = "https://msgsort.app/c/${item.code}"
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("MsgSort Link", link))
                                                        Toast.makeText(context, "Link copied: $link", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(AppStrings.get(language, "copy_link"), fontSize = 12.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_SUBJECT, "MsgSort PDF Document")
                                                            putExtra(
                                                                Intent.EXTRA_TEXT,
                                                                "Retrieve protected PDF on MsgSort with SortCode: ${item.code}\nLink: https://msgsort.app/c/${item.code}"
                                                            )
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Share SortCode"))
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(AppStrings.get(language, "share"), fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Generic File Download Card
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ContainerIndigo
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Description, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(32.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.fileName.ifBlank { "attached_file" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = DeepIndigo
                                                )
                                                Text(
                                                    text = "${(item.fileSize / 1024).coerceAtLeast(1)} KB • ${item.fileMimeType}",
                                                    fontSize = 12.sp,
                                                    color = SlateMuted
                                                )
                                            }
                                        }
                                    }

                                    if (item.downloadAllowed) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isDownloadingFile = true
                                                    try {
                                                        val bytes = withContext(Dispatchers.IO) {
                                                            var data = onDownloadMediaBytes(item)
                                                            if (data == null || data.isEmpty()) {
                                                                val sourceUrl = item.fileUrl.ifBlank { item.imageUrl }
                                                                if (sourceUrl.isNotBlank() && !sourceUrl.startsWith("data:")) {
                                                                    try {
                                                                        val conn = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                                                                            connectTimeout = 15000
                                                                            readTimeout = 30000
                                                                        }
                                                                        conn.connect()
                                                                        if (conn.responseCode in 200..299) {
                                                                            data = conn.inputStream.use { it.readBytes() }
                                                                        }
                                                                        conn.disconnect()
                                                                    } catch (e: Exception) {
                                                                        android.util.Log.w("RetrieveScreen", "Fallback URL fetch failed: ${e.message}")
                                                                    }
                                                                }
                                                            }
                                                            data
                                                        }

                                                        if (bytes == null || bytes.isEmpty()) {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Download failed: file payload unavailable.", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.IO) {
                                                                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                                                                val file = File(downloadsDir, item.fileName.ifBlank { "MsgSort_File_${item.code}" })
                                                                FileOutputStream(file).use { it.write(bytes) }
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, "File saved: ${file.name}", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } finally {
                                                        isDownloadingFile = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("download_file_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                            enabled = !isDownloadingFile
                                        ) {
                                            if (isDownloadingFile) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Downloading File...")
                                            } else {
                                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(AppStrings.get(language, "download_file"))
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = AppStrings.get(language, "downloads_disabled"),
                                            fontSize = 12.sp,
                                            color = SlateMuted,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            if (item.description.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Info, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = item.description,
                                            fontSize = 12.sp,
                                            color = SlateGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Zoomable Image Modal Dialog
    if (showImageZoomModal && retrievedItem != null) {
        val decodedZoomBitmap: Bitmap? = if (retrievedItem.fileBase64.isNotBlank()) {
            try {
                val clean = if (retrievedItem.fileBase64.contains(",")) retrievedItem.fileBase64.substringAfter(",") else retrievedItem.fileBase64
                val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        } else if (retrievedItem.thumbnailBase64.isNotBlank()) {
            try {
                val clean = if (retrievedItem.thumbnailBase64.contains(",")) retrievedItem.thumbnailBase64.substringAfter(",") else retrievedItem.thumbnailBase64
                val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        } else null

        val activeZoomModel: Any? = when {
            decodedZoomBitmap != null -> decodedZoomBitmap
            retrievedItem.imageUrl.isNotBlank() && !retrievedItem.imageUrl.startsWith("data:") -> retrievedItem.imageUrl
            else -> null
        }

        ZoomableImageDialog(
            imageModel = activeZoomModel,
            onDismiss = { showImageZoomModal = false }
        )
    }
}

@Composable
fun ZoomableImageDialog(
    imageModel: Any?,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            // Close & Reset Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                FilledTonalButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Zoom", fontSize = 12.sp)
                }
            }

            // Pinch and Pan Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 5.0f)
                            offsetX += pan.x * scale
                            offsetY += pan.y * scale
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Zoomed Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            }
                        },
                        error = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Unable to render full photo", color = Color.White)
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No photo content available", color = Color.White)
                    }
                }
            }

            Text(
                text = "Pinch with two fingers to zoom • Drag to pan",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            )
        }
    }
}
