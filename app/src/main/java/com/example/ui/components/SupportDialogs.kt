package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
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
import com.example.util.SupportUtils

/**
 * Professional Contact Us Dialog.
 * Displays official email sortmsg@gmail.com, "Email Support" button, and "Copy Email Address" button.
 */
@Composable
fun ContactUsDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("contact_us_dialog"),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = null,
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Bar
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(listOf(RoyalIndigo, AccentPurple))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HeadsetMic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = AppStrings.get(language, "contact_us"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "MsgSort Official Customer Support",
                                    fontSize = 12.sp,
                                    color = SlateMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = SlateMuted
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Official Support Email Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ContainerIndigo.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.2.dp, RoyalIndigo.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "OFFICIAL SUPPORT EMAIL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RoyalIndigo,
                                    letterSpacing = 0.8.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SuccessEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "VERIFIED",
                                        color = SuccessEmerald,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Email,
                                        contentDescription = null,
                                        tint = RoyalIndigo,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = SupportUtils.OFFICIAL_SUPPORT_EMAIL,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { SupportUtils.copySupportEmail(context) },
                                    modifier = Modifier.size(32.dp).testTag("copy_email_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy Email",
                                        tint = RoyalIndigo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Have questions, technical issues, or feedback? Reach out directly to our dedicated support team.",
                                fontSize = 12.sp,
                                color = SlateMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Action Buttons: "Email Support" and "Copy Email Address"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                SupportUtils.openSupportEmail(
                                    context = context,
                                    subject = SupportUtils.DEFAULT_SUPPORT_SUBJECT
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("email_support_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get(language, "email_support"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                SupportUtils.copySupportEmail(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("copy_email_address_btn"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, RoyalIndigo)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = RoyalIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get(language, "copy_email"),
                                color = RoyalIndigo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Support note
                    Text(
                        text = "Response time: Typically within 24-48 business hours.\nFor payment inquiries, please include your Google Play Order ID.",
                        fontSize = 11.sp,
                        color = SlateMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

/**
 * Comprehensive Help & Support Dialog with:
 * - Official email sortmsg@gmail.com
 * - Working Contact Support button
 * - 6 Support categories: Login Issues, File Sharing, SortCode Problems, Payment Issues, Privacy, General Questions
 * - Complete FAQ section including official support contact question & answer
 */
@Composable
fun HelpAndSupportDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Categories, 1: FAQ

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
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
                                .background(Brush.linearGradient(listOf(RoyalIndigo, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpCenter,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = AppStrings.get(language, "help_support"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = SupportUtils.OFFICIAL_SUPPORT_EMAIL,
                                fontSize = 12.sp,
                                color = RoyalIndigo,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = SlateMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Prominent Contact Support Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ContainerIndigo),
                    border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Need Direct Assistance?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reach us at ${SupportUtils.OFFICIAL_SUPPORT_EMAIL}",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }

                        Button(
                            onClick = {
                                SupportUtils.openSupportEmail(
                                    context = context,
                                    subject = "MsgSort Help & Support"
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("help_contact_support_btn")
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Contact Support", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sub-tabs: Support Categories & FAQ
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = RoyalIndigo
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Support Topics", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("FAQ", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    if (selectedTab == 0) {
                        // 6 Support Categories
                        item {
                            Text(
                                text = "Select a topic for troubleshooting or to email support:",
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                        }

                        items(SupportUtils.supportCategories, key = { it.id }) { cat ->
                            val isExpanded = expandedCategoryId == cat.id

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        expandedCategoryId = if (isExpanded) null else cat.id
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isExpanded) 1.5.dp else 1.dp,
                                    color = if (isExpanded) RoyalIndigo else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(ContainerIndigo),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = cat.icon,
                                                contentDescription = null,
                                                tint = RoyalIndigo,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cat.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = cat.description,
                                                fontSize = 11.sp,
                                                color = SlateMuted
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = SlateMuted
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(color = BorderLight)
                                            Text(
                                                text = "Troubleshooting & Guidelines:",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = RoyalIndigo
                                            )

                                            cat.commonTips.forEach { tip ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("•", color = RoyalIndigo, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = tip,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        lineHeight = 16.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Button(
                                                onClick = {
                                                    SupportUtils.openSupportEmail(
                                                        context = context,
                                                        subject = cat.emailSubject
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Email,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Email Support about ${cat.title}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // FAQ SECTION
                        item {
                            FaqItem(
                                question = "How can I contact MsgSort support?",
                                answer = "You can contact the MsgSort support team at sortmsg@gmail.com for questions, technical issues, payment-related queries, or general assistance.",
                                isHighlight = true
                            )
                        }

                        item {
                            FaqItem(
                                question = "What is a SortCode?",
                                answer = "A SortCode is a short, unique 6 to 8-character alphanumeric code (e.g., MS-7X9K2) that links directly to your encrypted message, document, or media file."
                            )
                        }

                        item {
                            FaqItem(
                                question = "Does a recipient need an account?",
                                answer = "No! Recipients do not need an account. They can simply open the 'Retrieve' tab or 'Open with SortCode' card on the Home screen to view and download the shared content."
                            )
                        }

                        item {
                            FaqItem(
                                question = "Can I revoke access early?",
                                answer = "Yes. As the creator, you can revoke any active SortCode at any time from the 'My SortCodes' tab. Revocation takes effect immediately on our secure servers."
                            )
                        }

                        item {
                            FaqItem(
                                question = "What happens when a SortCode expires?",
                                answer = "Once a SortCode passes its expiration duration, our server-side zero-retention policy permanently purges the stored payload. Expired codes cannot be restored or accessed by anyone."
                            )
                        }

                        item {
                            FaqItem(
                                question = "How do I restore my Premium purchase?",
                                answer = "Open the Premium screen and tap 'Restore Purchases'. If you still encounter billing discrepancies, email us at sortmsg@gmail.com with your Google Play order number."
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqItem(
    question: String,
    answer: String,
    isHighlight: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlight) ContainerIndigo.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isHighlight) 1.4.dp else 1.dp,
            color = if (isHighlight) RoyalIndigo else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isHighlight) Icons.Filled.Stars else Icons.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = if (isHighlight) RoyalIndigo else SlateMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = question,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isHighlight) RoyalIndigo else MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = answer,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Updated Privacy Policy Dialog displaying sortmsg@gmail.com as the official privacy contact email.
 */
@Composable
fun PrivacyPolicyDialog(
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Policy, contentDescription = null, tint = RoyalIndigo)
                Text(AppStrings.get(language, "privacy_policy"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "MsgSort Privacy & Data Handling",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Content Lifetime: Every SortCode has an immutable server-side expiration timestamp. Once expired, the backend permanently rejects retrieval.\n\n" +
                                "2. Recipient Access: Anyone in possession of a valid SortCode can retrieve the message or document until it is expired or revoked.\n\n" +
                                "3. Early Revocation: As creator, you can revoke access anytime from 'My SortCodes'. Revocation takes effect immediately on the backend.\n\n" +
                                "4. Zero-Retention: Expired and revoked content is permanently purged from our servers.",
                        fontSize = 13.sp,
                        color = SlateGray,
                        lineHeight = 18.sp
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ContainerIndigo),
                        border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "PRIVACY & PERSONAL DATA CONTACT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalIndigo
                            )
                            Text(
                                text = "To exercise your privacy rights, request data deletion, or submit privacy queries, contact our dedicated team at:",
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = SupportUtils.OFFICIAL_SUPPORT_EMAIL,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = {
                                    SupportUtils.openSupportEmail(
                                        context = context,
                                        subject = "MsgSort Privacy & Personal Data Request"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Email Privacy Team", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Updated Terms of Service Dialog displaying sortmsg@gmail.com as the official support contact email.
 */
@Composable
fun TermsOfServiceDialog(
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = RoyalIndigo)
                Text(AppStrings.get(language, "terms_of_service"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "MsgSort Terms of Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "By using MsgSort, you agree not to upload harmful, infringing, or illegal materials. Content is subject to file size limits and a maximum 30-day retention. You are solely responsible for managing and sharing your SortCodes. Content shared through MsgSort is subject to lawful usage terms.",
                        fontSize = 13.sp,
                        color = SlateGray,
                        lineHeight = 18.sp
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ContainerIndigo),
                        border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TERMS & LEGAL CONTACT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalIndigo
                            )
                            Text(
                                text = "For questions regarding these terms, acceptable usage, or licensing, reach our support team at:",
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = SupportUtils.OFFICIAL_SUPPORT_EMAIL,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = {
                                    SupportUtils.openSupportEmail(
                                        context = context,
                                        subject = "MsgSort Terms & Legal Inquiry"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Contact Support", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
