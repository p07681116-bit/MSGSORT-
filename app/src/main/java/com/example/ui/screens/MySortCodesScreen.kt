package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.SortCodeItem
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.SortCodeBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MySortCodesScreen(
    language: AppLanguage,
    userCodes: List<SortCodeItem>,
    onSelectCode: (String) -> Unit,
    onRevokeCode: (String) -> Unit,
    onDeleteCode: (String) -> Unit,
    onNavigateCreate: () -> Unit,
    onCleanupExpired: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("all") } // "all", "active", "expired", "revoked"

    var codeToRevoke by remember { mutableStateOf<String?>(null) }
    var codeToDelete by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    }

    val filteredList = remember(userCodes, selectedFilter) {
        when (selectedFilter) {
            "active" -> userCodes.filter { it.getComputedStatus() == "active" }
            "expired" -> userCodes.filter { it.getComputedStatus() == "expired" }
            "revoked" -> userCodes.filter { it.getComputedStatus() == "revoked" }
            else -> userCodes
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = AppStrings.get(language, "my_codes"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Manage, monitor, revoke, or delete your active and past SortCodes.",
                    fontSize = 13.sp,
                    color = SlateMuted
                )
            }
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTabChip(
                    label = AppStrings.get(language, "all"),
                    count = userCodes.size,
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" }
                )
                FilterTabChip(
                    label = AppStrings.get(language, "active"),
                    count = userCodes.count { it.getComputedStatus() == "active" },
                    selected = selectedFilter == "active",
                    onClick = { selectedFilter = "active" }
                )
                FilterTabChip(
                    label = AppStrings.get(language, "expired"),
                    count = userCodes.count { it.getComputedStatus() == "expired" },
                    selected = selectedFilter == "expired",
                    onClick = { selectedFilter = "expired" }
                )
                FilterTabChip(
                    label = AppStrings.get(language, "revoked"),
                    count = userCodes.count { it.getComputedStatus() == "revoked" },
                    selected = selectedFilter == "revoked",
                    onClick = { selectedFilter = "revoked" }
                )
            }
        }

        // Cleanup Expired/Revoked Banner
        val expiredCount = userCodes.count { it.getComputedStatus() == "expired" || it.getComputedStatus() == "revoked" }
        if (expiredCount > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(18.dp))
                            Text(
                                text = "$expiredCount expired or revoked item(s)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = onCleanupExpired,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clean Up Storage", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoyalIndigo)
                        }
                    }
                }
            }
        }

        // Empty State
        if (filteredList.isEmpty()) {
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
                            .padding(32.dp),
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
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = "No SortCodes in this category",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Create a new SortCode to share protected text or documents.",
                            fontSize = 12.sp,
                            color = SlateMuted,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateCreate,
                            modifier = Modifier.padding(top = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.get(language, "create_sortcode"))
                        }
                    }
                }
            }
        } else {
            items(filteredList, key = { it.code }) { item ->
                MySortCodeCard(
                    item = item,
                    language = language,
                    dateFormat = dateFormat,
                    onOpenDetails = { onSelectCode(item.code) },
                    onCopyLink = {
                        val link = "https://msgsort.app/c/${item.code}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MsgSort Link", link))
                        Toast.makeText(context, "Link copied: $link", Toast.LENGTH_SHORT).show()
                    },
                    onRequestRevoke = { codeToRevoke = item.code },
                    onRequestDelete = { codeToDelete = item.code }
                )
            }
        }
    }

    // Revocation Confirmation Dialog
    ConfirmationDialog(
        show = codeToRevoke != null,
        title = AppStrings.get(language, "revoke"),
        message = AppStrings.get(language, "confirm_revoke"),
        confirmText = AppStrings.get(language, "confirm"),
        cancelText = AppStrings.get(language, "cancel"),
        isDestructive = true,
        onConfirm = {
            codeToRevoke?.let { onRevokeCode(it) }
            codeToRevoke = null
        },
        onDismiss = { codeToRevoke = null }
    )

    // Deletion Confirmation Dialog
    ConfirmationDialog(
        show = codeToDelete != null,
        title = AppStrings.get(language, "delete"),
        message = AppStrings.get(language, "confirm_delete"),
        confirmText = AppStrings.get(language, "confirm"),
        cancelText = AppStrings.get(language, "cancel"),
        isDestructive = true,
        onConfirm = {
            codeToDelete?.let { onDeleteCode(it) }
            codeToDelete = null
        },
        onDismiss = { codeToDelete = null }
    )
}

@Composable
fun FilterTabChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "$label ($count)",
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(20.dp),
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

@Composable
fun MySortCodeCard(
    item: SortCodeItem,
    language: AppLanguage,
    dateFormat: SimpleDateFormat,
    onOpenDetails: () -> Unit,
    onCopyLink: () -> Unit,
    onRequestRevoke: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val computedStatus = item.getComputedStatus()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenDetails() },
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
            // Top Row: Code & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortCodeBadge(code = item.code)
                    if (item.passwordProtected) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Password Protected",
                            tint = AccentPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                StatusBadge(status = computedStatus, language = language)
            }

            // Content Details Row (with Image Thumbnail if image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.contentType == "image") {
                    val thumbBitmap = remember(item.code, item.thumbnailBase64, item.fileBase64) {
                        val base64 = if (item.thumbnailBase64.isNotBlank()) item.thumbnailBase64 else item.fileBase64
                        if (base64.isNotBlank()) {
                            try {
                                val clean = if (base64.contains(",")) base64.substringAfter(",") else base64
                                val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }
                    val thumbModel: Any? = when {
                        thumbBitmap != null -> thumbBitmap
                        item.imageUrl.isNotBlank() && !item.imageUrl.startsWith("data:") -> item.imageUrl
                        else -> null
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(thumbModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Image Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Filled.Image, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (item.contentType == "file" || item.contentType == "document") {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ContainerIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(22.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (item.title.isNotBlank()) item.title else if (item.fileName.isNotBlank()) item.fileName else "Message content",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.contentType == "image") {
                        val dimensionStr = if (item.imageWidth > 0 && item.imageHeight > 0) " • ${item.imageWidth}x${item.imageHeight}" else ""
                        Text(
                            text = "Photo • ${item.fileSize / 1024} KB$dimensionStr",
                            fontSize = 12.sp,
                            color = RoyalIndigo,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (item.fileName.isNotBlank()) {
                        Text(
                            text = "${item.fileName} • ${item.fileSize / 1024} KB",
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }
                }
            }

            // Timestamps
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item.createdAt?.toDate()?.let {
                    Text(
                        text = "${AppStrings.get(language, "created_at")}: ${dateFormat.format(it)}",
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                }
                Text(
                    text = "${AppStrings.get(language, "expires_in")}: ${item.formattedRemainingTime()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (computedStatus == "active") DeepIndigo else SlateMuted
                )
            }

            HorizontalDivider(color = BorderLight)

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Copy Code, Copy Link, Share & View Details
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val context = LocalContext.current
                    FilledTonalIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SortCode", item.code))
                            Toast.makeText(context, "Code ${item.code} copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Code", modifier = Modifier.size(17.dp), tint = RoyalIndigo)
                    }

                    FilledTonalIconButton(
                        onClick = onCopyLink,
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = "Copy Link", modifier = Modifier.size(18.dp), tint = RoyalIndigo)
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "MsgSort Code")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Retrieve content on MsgSort with SortCode: ${item.code}\nLink: https://msgsort.app/c/${item.code}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share SortCode"))
                        },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(17.dp), tint = RoyalIndigo)
                    }

                    FilledTonalIconButton(
                        onClick = onOpenDetails,
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = "View", modifier = Modifier.size(18.dp), tint = RoyalIndigo)
                    }
                }

                // Right: Revoke & Delete
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (computedStatus == "active") {
                        OutlinedButton(
                            onClick = onRequestRevoke,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(14.dp), tint = WarningAmber)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.get(language, "revoke"), fontSize = 11.sp, color = WarningAmber)
                        }
                    }

                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
