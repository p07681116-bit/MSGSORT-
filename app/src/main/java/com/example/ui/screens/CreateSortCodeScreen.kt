package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.SortCodeItem
import com.example.ui.components.SortCodeBadge
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.SecurityUtils
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class ExpiryOption(val minutes: Long, val labelKey: String) {
    ONE_MINUTE(1L, "duration_1m"),
    FIVE_MINUTES(5L, "duration_5m"),
    TEN_MINUTES(10L, "duration_10m"),
    FIFTEEN_MINUTES(15L, "duration_15m"),
    THIRTY_MINUTES(30L, "duration_30m"),
    SIXTY_MINUTES(60L, "duration_60m"),
    CUSTOM(-1L, "duration_custom");

    val hours: Long get() = (minutes / 60L).coerceAtLeast(1L)
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun CreateSortCodeScreen(
    language: AppLanguage,
    currentUserId: String?,
    isCreating: Boolean,
    createdItem: SortCodeItem?,
    onCreateSortCode: (SortCodeItem, ByteArray?) -> Unit,
    onResetCreatedItem: () -> Unit,
    onSignInClicked: () -> Unit = {},
    initialContentType: String = "text",
    creationError: String? = null,
    onResetCreationError: () -> Unit = {},
    isPremium: Boolean = false,
    onUpgradeToPremiumClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessingFile by remember { mutableStateOf(false) }
    var selectedContentType by remember(initialContentType) { mutableStateOf(initialContentType) } // "text", "image", "video", "file"

    var title by remember { mutableStateOf("") }
    var textMessage by remember { mutableStateOf("") }

    // File state
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf(0L) }
    var selectedFileMime by remember { mutableStateOf<String?>(null) }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Video state
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedVideoName by remember { mutableStateOf<String?>(null) }
    var selectedVideoSize by remember { mutableStateOf(0L) }
    var selectedVideoMime by remember { mutableStateOf<String?>(null) }

    // Image state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var selectedImageSize by remember { mutableStateOf(0L) }
    var selectedImageMime by remember { mutableStateOf<String?>(null) }
    var selectedImageWidth by remember { mutableStateOf(0) }
    var selectedImageHeight by remember { mutableStateOf(0) }
    var selectedImageThumbnailBase64 by remember { mutableStateOf<String?>(null) }

    // Expiry in Minutes
    var selectedExpiryOption by remember { mutableStateOf(ExpiryOption.TEN_MINUTES) }
    var customMinutes by remember { mutableStateOf(10f) }

    // Optional settings
    var isPasswordProtected by remember { mutableStateOf(false) }
    var passwordText by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var allowDownload by remember { mutableStateOf(true) }
    var description by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Process picked image
    fun processImageUri(uri: Uri) {
        isProcessingFile = true
        errorMessage = null
        onResetCreationError()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var name = "photo_${System.currentTimeMillis()}"
                var size = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }

                var mime = contentResolver.getType(uri) ?: ""
                val ext = name.substringAfterLast('.', "").lowercase()
                if (mime.isBlank()) {
                    mime = when (ext) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                }

                val validMimes = listOf("image/jpeg", "image/jpg", "image/png", "image/webp")
                val validExts = listOf("jpg", "jpeg", "png", "webp")
                if (!validMimes.any { mime.contains(it, ignoreCase = true) } && !validExts.contains(ext)) {
                    withContext(Dispatchers.Main) {
                        errorMessage = AppStrings.get(language, "unsupported_format")
                        isProcessingFile = false
                    }
                    return@launch
                }

                // Check tier-based file size limit (5MB Free vs 25MB Pro)
                val maxLimit = if (isPremium) 25L * 1024L * 1024L else 5L * 1024L * 1024L
                if (size > maxLimit) {
                    withContext(Dispatchers.Main) {
                        errorMessage = if (!isPremium && size > 5L * 1024L * 1024L) {
                            "Image exceeds the 5 MB free tier limit. Upgrade to MsgSort Pro for up to 25 MB uploads."
                        } else {
                            AppStrings.get(language, "file_too_large")
                        }
                        isProcessingFile = false
                    }
                    return@launch
                }

                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()

                if (bytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Selected image file was empty."
                        isProcessingFile = false
                    }
                    return@launch
                }

                // Decode dimensions
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                val width = boundsOptions.outWidth.coerceAtLeast(0)
                val height = boundsOptions.outHeight.coerceAtLeast(0)

                // Generate compact thumbnail for instant card rendering
                val thumbOptions = BitmapFactory.Options().apply {
                    val maxDim = maxOf(width, height)
                    inSampleSize = if (maxDim > 400) (maxDim / 200).coerceAtLeast(1) else 1
                }
                val thumbBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, thumbOptions)
                val thumbBase64 = if (thumbBitmap != null) {
                    val baos = ByteArrayOutputStream()
                    thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    Base64.encode(baos.toByteArray())
                } else ""

                withContext(Dispatchers.Main) {
                    selectedImageUri = uri
                    selectedImageBytes = bytes
                    selectedImageName = name
                    selectedImageSize = if (size > 0) size else bytes.size.toLong()
                    selectedImageMime = mime
                    selectedImageWidth = width
                    selectedImageHeight = height
                    selectedImageThumbnailBase64 = thumbBase64
                    errorMessage = null

                    if (title.isBlank()) {
                        title = name.substringBeforeLast('.')
                    }
                    isProcessingFile = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load image: ${e.localizedMessage}"
                    isProcessingFile = false
                }
            }
        }
    }

    // Process picked video
    fun processVideoUri(uri: Uri) {
        isProcessingFile = true
        errorMessage = null
        onResetCreationError()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var name = "video_${System.currentTimeMillis()}.mp4"
                var size = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }

                val mime = contentResolver.getType(uri) ?: "video/mp4"
                // Check tier-based video size limit (5MB Free vs 25MB Pro)
                val maxLimit = if (isPremium) 25L * 1024L * 1024L else 5L * 1024L * 1024L
                if (size > maxLimit) {
                    withContext(Dispatchers.Main) {
                        errorMessage = if (!isPremium && size > 5L * 1024L * 1024L) {
                            "Video exceeds the 5 MB free tier limit. Upgrade to MsgSort Pro for up to 25 MB uploads."
                        } else {
                            AppStrings.get(language, "file_too_large")
                        }
                        isProcessingFile = false
                    }
                    return@launch
                }

                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()

                if (bytes.size.toLong() > maxLimit) {
                    withContext(Dispatchers.Main) {
                        errorMessage = if (!isPremium && bytes.size.toLong() > 5L * 1024L * 1024L) {
                            "Video exceeds the 5 MB free tier limit. Upgrade to MsgSort Pro for up to 25 MB uploads."
                        } else {
                            AppStrings.get(language, "file_too_large")
                        }
                        isProcessingFile = false
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    selectedVideoUri = uri
                    selectedVideoBytes = bytes
                    selectedVideoName = name
                    selectedVideoSize = if (size > 0) size else bytes.size.toLong()
                    selectedVideoMime = mime
                    errorMessage = null

                    if (title.isBlank()) {
                        title = name.substringBeforeLast('.')
                    }
                    isProcessingFile = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load video: ${e.localizedMessage}"
                    isProcessingFile = false
                }
            }
        }
    }

    // Android Video Picker Launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processVideoUri(uri)
        }
    }

    // Android Photo Picker Launcher (Modern, zero-permission)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processImageUri(uri)
        }
    }

    // Fallback Image Document Launcher
    val imageDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            processImageUri(uri)
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingFile = true
            errorMessage = null
            onResetCreationError()
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    var name = "uploaded_file"
                    var size = 0L

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }

                    // Check tier-based file size limit (5MB Free vs 25MB Pro)
                    val maxLimit = if (isPremium) 25L * 1024L * 1024L else 5L * 1024L * 1024L
                    if (size > maxLimit) {
                        withContext(Dispatchers.Main) {
                            errorMessage = if (!isPremium && size > 5L * 1024L * 1024L) {
                                "File exceeds the 5 MB free tier limit. Upgrade to MsgSort Pro for up to 25 MB uploads."
                            } else {
                                AppStrings.get(language, "file_too_large")
                            }
                            isProcessingFile = false
                        }
                        return@launch
                    }

                    var mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    if (name.endsWith(".pdf", ignoreCase = true) || mime.contains("pdf", ignoreCase = true)) {
                        mime = "application/pdf"
                    }
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    inputStream?.close()

                    if (bytes.size.toLong() > maxLimit) {
                        withContext(Dispatchers.Main) {
                            errorMessage = if (!isPremium && bytes.size.toLong() > 5L * 1024L * 1024L) {
                                "File exceeds the 5 MB free tier limit. Upgrade to MsgSort Pro for up to 25 MB uploads."
                            } else {
                                AppStrings.get(language, "file_too_large")
                            }
                            isProcessingFile = false
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        selectedFileName = name
                        selectedFileSize = if (size > 0) size else bytes.size.toLong()
                        selectedFileMime = mime
                        selectedFileBytes = bytes
                        errorMessage = null

                        if (title.isBlank()) {
                            title = name
                        }
                        isProcessingFile = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to load file: ${e.localizedMessage}"
                        isProcessingFile = false
                    }
                }
            }
        }
    }

    val effectiveMinutes = if (selectedExpiryOption == ExpiryOption.CUSTOM) {
        customMinutes.toLong().coerceIn(1L, if (isPremium) 43200L else 60L)
    } else {
        selectedExpiryOption.minutes
    }

    val calculatedExpiryDate = remember(effectiveMinutes) {
        Date(System.currentTimeMillis() + effectiveMinutes * 60L * 1000L)
    }

    val dateFormat = remember {
        SimpleDateFormat("EEE, MMM d, yyyy • h:mm:ss a", Locale.getDefault())
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = AppStrings.get(language, "create_sortcode"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pack message, photo, video, or file into a short, secure code with expiry controls.",
                    fontSize = 13.sp,
                    color = SlateMuted
                )
            }
        }

        // Authentication Gate Card (if not signed in)
        if (currentUserId == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_gate_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ContainerIndigo)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = RoyalIndigo,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sign-in required to create",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DeepIndigo
                            )
                            Text(
                                text = "Google Sign-In is required to verify ownership and protect your shared data.",
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                        }
                        Button(
                            onClick = onSignInClicked,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                        ) {
                            Text("Sign In", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4-way Content Type Segmented Tabs: Text, Image, Video, File
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Text Tab
                ContentTypeTabItem(
                    title = AppStrings.get(language, "text_content"),
                    icon = Icons.Filled.Notes,
                    selected = selectedContentType == "text",
                    modifier = Modifier.weight(1f),
                    onClick = { selectedContentType = "text" }
                )

                // Image Tab
                ContentTypeTabItem(
                    title = AppStrings.get(language, "image_content"),
                    icon = Icons.Filled.Image,
                    selected = selectedContentType == "image",
                    modifier = Modifier.weight(1f),
                    onClick = { selectedContentType = "image" }
                )

                // Video Tab
                ContentTypeTabItem(
                    title = "Video",
                    icon = Icons.Filled.Videocam,
                    selected = selectedContentType == "video",
                    modifier = Modifier.weight(1f),
                    onClick = { selectedContentType = "video" }
                )

                // File Tab
                ContentTypeTabItem(
                    title = AppStrings.get(language, "file_content"),
                    icon = Icons.Filled.AttachFile,
                    selected = selectedContentType == "file",
                    modifier = Modifier.weight(1f),
                    onClick = { selectedContentType = "file" }
                )
            }
        }

        // Title Field
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(AppStrings.get(language, "title_optional")) },
                placeholder = {
                    Text(
                        when (selectedContentType) {
                            "image" -> "e.g. Receipt Photo, Whiteboard, ID Scan"
                            "video" -> "e.g. Tutorial Clip, Video Note"
                            "file" -> "e.g. Project Brief, Contract PDF"
                            else -> "e.g. WiFi Passwords, Note"
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_title_input"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalIndigo,
                    unfocusedBorderColor = BorderLight
                )
            )
        }

        // Content Section Based on Selected Type
        when (selectedContentType) {
            "text" -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = textMessage,
                            onValueChange = { textMessage = it },
                            label = { Text("Message Body") },
                            placeholder = { Text(AppStrings.get(language, "message_hint")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp)
                                .testTag("create_message_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoyalIndigo,
                                unfocusedBorderColor = BorderLight
                            )
                        )

                        val maxChars = if (isPremium) 100000 else 20000
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isPremium) "Preserves original linebreaks (Pro 100k limit)" else "Free limit: 20k chars • Pro: 100k chars",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = "${textMessage.length} / ${if (isPremium) "100,000" else "20,000"} ${AppStrings.get(language, "char_count")}",
                                fontSize = 11.sp,
                                color = if (textMessage.length > maxChars) ErrorRed else SlateMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            "image" -> {
                item {
                    if (selectedImageUri != null) {
                        // Image Preview Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Image Box Preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(selectedImageUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Selected Photo Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                // Metadata badges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedImageName ?: "photo.jpg",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${selectedImageSize / 1024} KB • ${selectedImageWidth}x${selectedImageHeight} px",
                                            fontSize = 12.sp,
                                            color = SlateMuted
                                        )
                                    }

                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = (selectedImageMime?.substringAfter('/') ?: "IMAGE").uppercase(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = ContainerIndigo, labelColor = RoyalIndigo)
                                    )
                                }

                                HorizontalDivider(color = BorderLight)

                                // Replace & Remove Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(AppStrings.get(language, "replace_image"), fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            selectedImageUri = null
                                            selectedImageBytes = null
                                            selectedImageName = null
                                            selectedImageSize = 0L
                                            selectedImageThumbnailBase64 = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer, contentColor = ErrorRed)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(AppStrings.get(language, "remove_image"), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty Image Picker Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("choose_image_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(ContainerIndigo),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = RoyalIndigo,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Text(
                                    text = AppStrings.get(language, "choose_image"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = AppStrings.get(language, "image_hint"),
                                    fontSize = 12.sp,
                                    color = SlateMuted,
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Gallery Photo Picker")
                                    }

                                    OutlinedIconButton(
                                        onClick = {
                                            imageDocumentLauncher.launch(
                                                arrayOf("image/jpeg", "image/png", "image/webp")
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = "Files Fallback")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "video" -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                            .testTag("choose_video_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(ContainerIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedVideoName != null) Icons.Filled.CheckCircle else Icons.Filled.Videocam,
                                    contentDescription = null,
                                    tint = if (selectedVideoName != null) SuccessEmerald else RoyalIndigo,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            if (selectedVideoName != null) {
                                Text(
                                    text = selectedVideoName ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Size: ${selectedVideoSize / (1024 * 1024)} MB (${selectedVideoSize / 1024} KB) • ${selectedVideoMime ?: "video/mp4"}",
                                    fontSize = 12.sp,
                                    color = SlateMuted
                                )
                                FilledTonalButton(
                                    onClick = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Choose Different Video")
                                }
                            } else {
                                Text(
                                    text = "Tap to choose a video clip",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPremium) "Supports MP4, WebM, QuickTime up to 25 MB max limit." else "Supports MP4, WebM up to 5 MB free (Up to 25 MB with Pro).",
                                    fontSize = 12.sp,
                                    color = SlateMuted,
                                    textAlign = TextAlign.Center
                                )
                                AssistChip(
                                    onClick = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    label = { Text(if (isPremium) "Browse Videos (Max 25 MB)" else "Browse Videos (Max 5 MB • 25 MB Pro)") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            "file" -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/plain",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "application/json",
                                        "*/*"
                                    )
                                )
                            }
                            .testTag("choose_file_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(ContainerIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedFileName != null) Icons.Filled.CheckCircle else Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    tint = if (selectedFileName != null) SuccessEmerald else RoyalIndigo,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            if (selectedFileName != null) {
                                Text(
                                    text = selectedFileName ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Size: ${selectedFileSize / 1024} KB • ${selectedFileMime ?: ""}",
                                    fontSize = 12.sp,
                                    color = SlateMuted
                                )
                                FilledTonalButton(
                                    onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf("application/pdf", "text/plain", "application/json", "*/*")
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Choose Different File")
                                }
                            } else {
                                Text(
                                    text = "Tap to choose a document",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPremium) AppStrings.get(language, "file_hint") else "Max 5 MB for free accounts. Upgrade to Pro for up to 25 MB documents.",
                                    fontSize = 12.sp,
                                    color = SlateMuted,
                                    textAlign = TextAlign.Center
                                )
                                AssistChip(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    label = { Text(if (isPremium) "PDF, TXT, DOCX, Code, Files (Max 25 MB)" else "PDF, TXT, DOCX, Code (Max 5 MB • 25 MB Pro)") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expiry Duration Selector Section
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = RoyalIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = AppStrings.get(language, "expiry_duration"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Grid of presets: 1m, 5m, 10m, 15m, 30m, 60m, Custom
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpiryChip(
                                option = ExpiryOption.ONE_MINUTE,
                                selected = selectedExpiryOption == ExpiryOption.ONE_MINUTE,
                                language = language,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedExpiryOption = ExpiryOption.ONE_MINUTE }
                            )
                            ExpiryChip(
                                option = ExpiryOption.FIVE_MINUTES,
                                selected = selectedExpiryOption == ExpiryOption.FIVE_MINUTES,
                                language = language,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedExpiryOption = ExpiryOption.FIVE_MINUTES }
                            )
                            ExpiryChip(
                                option = ExpiryOption.TEN_MINUTES,
                                selected = selectedExpiryOption == ExpiryOption.TEN_MINUTES,
                                language = language,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedExpiryOption = ExpiryOption.TEN_MINUTES }
                            )
                            ExpiryChip(
                                option = ExpiryOption.FIFTEEN_MINUTES,
                                selected = selectedExpiryOption == ExpiryOption.FIFTEEN_MINUTES,
                                language = language,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedExpiryOption = ExpiryOption.FIFTEEN_MINUTES }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpiryChip(
                                option = ExpiryOption.THIRTY_MINUTES,
                                selected = selectedExpiryOption == ExpiryOption.THIRTY_MINUTES,
                                language = language,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedExpiryOption = ExpiryOption.THIRTY_MINUTES }
                            )
                            ExpiryChip(
                                option = ExpiryOption.SIXTY_MINUTES,
                                selected = selectedExpiryOption == ExpiryOption.SIXTY_MINUTES,
                                language = language,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedExpiryOption = ExpiryOption.SIXTY_MINUTES }
                            )
                            ExpiryChip(
                                option = ExpiryOption.CUSTOM,
                                selected = selectedExpiryOption == ExpiryOption.CUSTOM,
                                language = language,
                                isPro = !isPremium,
                                modifier = Modifier.weight(1f),
                                onSelect = {
                                    selectedExpiryOption = ExpiryOption.CUSTOM
                                }
                            )
                        }
                    }

                    // Custom slider if Custom selected
                    AnimatedVisibility(visible = selectedExpiryOption == ExpiryOption.CUSTOM) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Custom: ${customMinutes.toInt()} minute${if (customMinutes.toInt() > 1) "s" else ""}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = RoyalIndigo
                                )
                                Text(
                                    text = if (customMinutes >= 60f) "${(customMinutes / 60f).toInt()} hr ${(customMinutes % 60f).toInt()}m" else "< 1 hour",
                                    fontSize = 13.sp,
                                    color = SlateMuted
                                )
                            }
                            val maxMinutesRange = if (isPremium) 1440f else 60f
                            Slider(
                                value = customMinutes.coerceIn(1f, maxMinutesRange),
                                onValueChange = { customMinutes = it },
                                valueRange = 1f..maxMinutesRange,
                                steps = (maxMinutesRange.toInt() - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = RoyalIndigo,
                                    activeTrackColor = RoyalIndigo
                                )
                            )
                            if (!isPremium) {
                                Text(
                                    text = "Free accounts can choose up to 60 minutes. Upgrade to Pro for extended limits (up to 24+ hours).",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                        }
                    }

                    // Exact Expiration Time Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ContainerIndigo)
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = null,
                                tint = DeepIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Visibility countdown ($effectiveMinutes minutes duration):",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                                Text(
                                    text = "Expires in $effectiveMinutes minutes • ${dateFormat.format(calculatedExpiryDate)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepIndigo
                                )
                            }
                        }
                    }
                }
            }
        }

        // Optional Security Settings
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Security & Access Controls",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Password protection toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = AppStrings.get(language, "password_protect"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Requires password before content is revealed",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                        }
                        Switch(
                            checked = isPasswordProtected,
                            onCheckedChange = { isPasswordProtected = it },
                            modifier = Modifier.testTag("password_switch")
                        )
                    }

                    AnimatedVisibility(visible = isPasswordProtected) {
                        OutlinedTextField(
                            value = passwordText,
                            onValueChange = { passwordText = it },
                            label = { Text("SortCode Password") },
                            placeholder = { Text("Enter a secure password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = BorderLight)

                    // Allow Download toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = AppStrings.get(language, "allow_download"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Allow recipients to download or copy content",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                            }
                        }
                        Switch(
                            checked = allowDownload,
                            onCheckedChange = { allowDownload = it },
                            modifier = Modifier.testTag("download_switch")
                        )
                    }

                    HorizontalDivider(color = BorderLight)

                    // Description / notes
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(AppStrings.get(language, "description_optional")) },
                        placeholder = { Text("Internal note or hint for recipient...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        }

        // Processing file indicator
        if (isProcessingFile) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ContainerIndigo)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = RoyalIndigo,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Loading and securing selected media...",
                            color = DeepIndigo,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Error message if any (validation or server creation failure)
        val displayedError = errorMessage ?: creationError
        if (displayedError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = ErrorRed)
                        Text(
                            text = displayedError,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (creationError != null) {
                            TextButton(onClick = onResetCreationError) {
                                Text("Dismiss", color = ErrorRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Create Button
        item {
            Button(
                onClick = {
                    if (currentUserId.isNullOrBlank()) {
                        errorMessage = "Google Sign-In is required to create a SortCode. Please sign in above."
                        return@Button
                    }

                    when (selectedContentType) {
                        "text" -> {
                            if (textMessage.isBlank()) {
                                errorMessage = "Please enter a message."
                                return@Button
                            }
                        }
                        "image" -> {
                            if (selectedImageBytes == null) {
                                errorMessage = "Please choose a photo to upload."
                                return@Button
                            }
                        }
                        "video" -> {
                            if (selectedVideoBytes == null) {
                                errorMessage = "Please choose a video to upload."
                                return@Button
                            }
                        }
                        "file" -> {
                            if (selectedFileBytes == null) {
                                errorMessage = "Please choose a file to upload."
                                return@Button
                            }
                        }
                    }

                    // Enforce Pro Expiry Authorization
                    val effectiveMins = if (selectedExpiryOption == ExpiryOption.CUSTOM) {
                        customMinutes.toLong()
                    } else {
                        selectedExpiryOption.minutes
                    }
                    if (selectedExpiryOption == ExpiryOption.CUSTOM && customMinutes > 60f && !isPremium) {
                        errorMessage = "Durations exceeding 60 minutes require a MsgSort Pro subscription. Upgrade to Pro for extended limits."
                        onUpgradeToPremiumClicked()
                        return@Button
                    }

                    // Enforce Text Length Limits
                    val maxTextLen = if (isPremium) 100000 else 20000
                    if (selectedContentType == "text" && textMessage.length > maxTextLen) {
                        errorMessage = if (!isPremium) {
                            "Message length (${textMessage.length} chars) exceeds the 20,000 character free limit. Upgrade to Pro for up to 100,000 characters."
                        } else {
                            "Message length exceeds 100,000 character maximum limit."
                        }
                        return@Button
                    }

                    // Enforce Media Size Limits
                    val maxAllowedSize = if (isPremium) 26214400L else 5242880L
                    val mediaSize = when (selectedContentType) {
                        "image" -> selectedImageSize
                        "video" -> selectedVideoSize
                        "file" -> selectedFileSize
                        else -> 0L
                    }
                    if (mediaSize > maxAllowedSize) {
                        errorMessage = if (!isPremium) {
                            "Media size exceeds the 5 MB free tier limit. Upgrade to MsgSort Pro for up to 25 MB uploads."
                        } else {
                            "Media size exceeds 25 MB maximum limit."
                        }
                        return@Button
                    }

                    if (isPasswordProtected && passwordText.isBlank()) {
                        errorMessage = "Please enter a password for protection."
                        return@Button
                    }

                    errorMessage = null
                    val code = SecurityUtils.generateSortCode()
                    val passwordHash = if (isPasswordProtected) SecurityUtils.sha256(passwordText) else ""

                    val mediaBytes = when (selectedContentType) {
                        "image" -> selectedImageBytes
                        "video" -> selectedVideoBytes
                        "file" -> selectedFileBytes
                        else -> null
                    }

                    val mediaName = when (selectedContentType) {
                        "image" -> (selectedImageName ?: "photo.jpg")
                        "video" -> (selectedVideoName ?: "video.mp4")
                        "file" -> (selectedFileName ?: "document")
                        else -> ""
                    }


                    val isPdf = selectedContentType == "file" && (
                        (selectedFileMime?.contains("pdf", ignoreCase = true) == true) ||
                        (selectedFileName?.endsWith(".pdf", ignoreCase = true) == true)
                    )

                    val mediaMime = when {
                        isPdf -> "application/pdf"
                        selectedContentType == "image" -> (selectedImageMime ?: "image/jpeg")
                        selectedContentType == "video" -> (selectedVideoMime ?: "video/mp4")
                        selectedContentType == "file" -> (selectedFileMime ?: "application/octet-stream")
                        else -> ""
                    }

                    val effectiveContentType = if (isPdf) "application/pdf" else selectedContentType

                    val activeUid = (currentUserId ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid) ?: ""
                    if (activeUid.isBlank()) {
                        errorMessage = "Google Sign-In is required to create a SortCode. Please sign in above."
                        return@Button
                    }

                    val item = SortCodeItem(
                        code = code,
                        ownerUid = activeUid,
                        title = title.trim(),
                        contentType = effectiveContentType,
                        textContent = if (selectedContentType == "text") textMessage else "",
                        fileName = mediaName,
                        fileSize = mediaSize,
                        fileMimeType = mediaMime,
                        fileBase64 = "",
                        thumbnailBase64 = if (selectedContentType == "image") (selectedImageThumbnailBase64 ?: "") else "",
                        imageWidth = selectedImageWidth,
                        imageHeight = selectedImageHeight,
                        expiresAt = Timestamp(Date(System.currentTimeMillis() + effectiveMins * 60L * 1000L)),
                        status = "active",
                        passwordProtected = isPasswordProtected,
                        passwordHash = passwordHash,
                        downloadAllowed = allowDownload,
                        description = description.trim(),
                        viewsCount = 0L
                    )

                    onCreateSortCode(item, mediaBytes)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_create_button"),
                shape = RoundedCornerShape(16.dp),
                enabled = !isCreating && !isProcessingFile,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (selectedContentType == "image") "Uploading Photo & Securing..." else "Securing & Creating...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get(language, "create_sortcode"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Success Dialog / Sheet when created
    if (createdItem != null) {
        val shareLink = "https://msgsort.app/c/${createdItem.code}"

        AlertDialog(
            onDismissRequest = { onResetCreatedItem() },
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessEmerald,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = AppStrings.get(language, "code_created_success"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Share this code with your recipient. It will remain accessible until it expires.",
                        fontSize = 13.sp,
                        color = SlateGray,
                        textAlign = TextAlign.Center
                    )

                    // Large SortCode Badge
                    SortCodeBadge(
                        code = createdItem.code,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Image preview in dialog if image
                    if (createdItem.contentType == "image" && selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(selectedImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Created Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Expiry info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ContainerIndigo)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Expires in: ${createdItem.formattedRemainingTime()}",
                                fontWeight = FontWeight.Bold,
                                color = DeepIndigo,
                                fontSize = 13.sp
                            )
                            Text(
                                text = createdItem.expiresAt?.toDate()?.let { dateFormat.format(it) } ?: "",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    // Shareable Link display
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = shareLink,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("MsgSort Link", shareLink))
                                    Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Link", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SortCode", createdItem.code))
                            Toast.makeText(context, "Code ${createdItem.code} copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.get(language, "copy_code"), fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "MsgSort Code")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Retrieve this content on MsgSort using code: ${createdItem.code}\nOr open: $shareLink"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share SortCode"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.get(language, "share"), fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onResetCreatedItem()
                        title = ""
                        textMessage = ""
                        selectedFileName = null
                        selectedFileBytes = null
                        selectedFileMime = null
                        selectedImageUri = null
                        selectedImageBytes = null
                        selectedImageName = null
                        selectedImageThumbnailBase64 = null
                        selectedVideoUri = null
                        selectedVideoBytes = null
                        selectedVideoName = null
                        passwordText = ""
                        isPasswordProtected = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun ContentTypeTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) RoyalIndigo else SlateMuted,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (selected) RoyalIndigo else SlateMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ExpiryChip(
    option: ExpiryOption,
    selected: Boolean,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    isPro: Boolean = false,
    onSelect: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.get(language, option.labelKey),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                if (isPro) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "★",
                        color = AmberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ContainerIndigo,
            selectedLabelColor = RoyalIndigo,
            containerColor = Color.Transparent,
            labelColor = SlateGray
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) RoyalIndigo else BorderLight,
            selectedBorderColor = RoyalIndigo
        )
    )
}

