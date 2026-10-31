package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.google.firebase.auth.FirebaseUser

sealed class DeletionUiState {
    object Idle : DeletionUiState()
    data class InProgress(val currentStep: String) : DeletionUiState()
    data class ReauthRequired(val message: String) : DeletionUiState()
    data class Error(val message: String) : DeletionUiState()
}

/**
 * Comprehensive, secure Account Deletion Confirmation Dialog.
 *
 * Requirements satisfied:
 * 1. Explains permanent removal of personal profile and data.
 * 2. Explains purge of shared messages, files, SortCodes, and storage payloads.
 * 3. Clearly explains that Google Play subscriptions must be cancelled manually via Play Store.
 * 4. Provides two clear action buttons: "Cancel" and "Continue to Delete".
 * 5. Requires explicit checkbox confirmation before destructive action.
 * 6. Displays real-time loading indicator and step progression during deletion.
 * 7. Supports graceful re-authentication prompt if session is stale.
 */
@Composable
fun DeleteAccountDialog(
    language: AppLanguage,
    currentUser: FirebaseUser?,
    deletionState: DeletionUiState,
    onConfirmDelete: () -> Unit,
    onRequestReauth: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isConfirmedCheckbox by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (deletionState !is DeletionUiState.InProgress) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = deletionState !is DeletionUiState.InProgress,
            dismissOnClickOutside = deletionState !is DeletionUiState.InProgress,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("delete_account_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ErrorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Delete Account",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = ErrorRed
                            )
                            Text(
                                text = "Irreversible Action",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    if (deletionState !is DeletionUiState.InProgress) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = SlateMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Warning Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Account deletion permanently removes your profile and all created SortCodes from MsgSort. This cannot be undone.",
                            color = Color(0xFF7F1D1D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Detailed Consequences
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    item {
                        DeletionConsequenceCard(
                            icon = Icons.Outlined.PersonOff,
                            title = "Account & Personal Data",
                            description = "Your user profile, email (${currentUser?.email ?: "connected account"}), and authentication credentials will be permanently erased."
                        )
                    }

                    item {
                        DeletionConsequenceCard(
                            icon = Icons.Outlined.FolderDelete,
                            title = "Shared Messages, Files & SortCodes",
                            description = "All SortCodes created by your account will be immediately deleted from Firestore. All uploaded photos, videos, PDFs, and documents will be purged from Firebase Storage. Recipients will immediately lose access to your content."
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = WarningContainer),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Payment,
                                        contentDescription = null,
                                        tint = Color(0xFF92400E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Subscriptions & Billing Notice",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }

                                Text(
                                    text = "Deleting your MsgSort account does NOT automatically cancel an active Google Play subscription. Google Play manages renewals separately. Please cancel active subscriptions in the Google Play Store to prevent future charges. Financial records required by law are retained.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF78350F),
                                    lineHeight = 16.sp
                                )

                                OutlinedButton(
                                    onClick = {
                                        openGooglePlaySubscriptions(context)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFB45309)),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInNew,
                                        contentDescription = null,
                                        tint = Color(0xFFB45309),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Manage Google Play Subscriptions",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }

                    // Re-Authentication or Error Alerts
                    if (deletionState is DeletionUiState.ReauthRequired) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ContainerIndigo),
                                border = BorderStroke(1.dp, RoyalIndigo)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Recent Authentication Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = RoyalIndigo
                                    )
                                    Text(
                                        text = deletionState.message,
                                        fontSize = 12.sp,
                                        color = SlateMuted
                                    )
                                    Button(
                                        onClick = onRequestReauth,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                                    ) {
                                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Re-Authenticate with Google", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (deletionState is DeletionUiState.Error) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ErrorContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorRed)
                                    Text(
                                        text = deletionState.message,
                                        fontSize = 12.sp,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Safety Checkbox (only when not in progress)
                if (deletionState !is DeletionUiState.InProgress) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isConfirmedCheckbox = !isConfirmedCheckbox }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isConfirmedCheckbox,
                            onCheckedChange = { isConfirmedCheckbox = it },
                            colors = CheckboxDefaults.colors(checkedColor = ErrorRed),
                            modifier = Modifier.testTag("delete_account_checkbox")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "I understand that my SortCodes, files, and account will be permanently deleted and cannot be recovered.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons: "Cancel" and "Continue to Delete"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("cancel_delete_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = AppStrings.get(language, "cancel"),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onConfirmDelete,
                            enabled = isConfirmedCheckbox,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("continue_to_delete_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorRed,
                                disabledContainerColor = ErrorRed.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Continue to Delete",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Loading overlay in progress
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ErrorRed,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = deletionState.currentStep,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Please keep the app open while we securely clean up your records...",
                                fontSize = 11.sp,
                                color = SlateMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletionConsequenceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = SlateMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private fun openGooglePlaySubscriptions(context: Context) {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions")
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open Google Play Store", Toast.LENGTH_SHORT).show()
    }
}
