package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ContactUsDialog
import com.example.ui.components.DeleteAccountDialog
import com.example.ui.components.DeletionUiState
import com.example.ui.components.HelpAndSupportDialog
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.TermsOfServiceDialog
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.SupportUtils
import com.google.firebase.auth.FirebaseUser

@Composable
fun ProfileSettingsScreen(
    language: AppLanguage,
    currentUser: FirebaseUser?,
    isPremium: Boolean = false,
    themeMode: AppThemeMode = AppThemeMode.DARK,
    deletionState: DeletionUiState = DeletionUiState.Idle,
    onThemeModeSelected: (AppThemeMode) -> Unit = {},
    onLanguageSelected: (AppLanguage) -> Unit,
    onSignInClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    onDeleteAccount: () -> Unit,
    onConfirmDeleteAccount: () -> Unit = onDeleteAccount,
    onRequestReauth: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    var showContactUsDialog by remember { mutableStateOf(false) }
    var showHelpSupportDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

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
                    text = AppStrings.get(language, "profile"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Manage your account, preferences, and privacy settings.",
                    fontSize = 13.sp,
                    color = SlateMuted
                )
            }
        }

        // Account Profile Card
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
                    if (currentUser != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(RoyalIndigo, AccentPurple)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (currentUser.displayName ?: currentUser.email ?: "U").take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser.displayName ?: "MsgSort User",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentUser.email ?: "",
                                    fontSize = 13.sp,
                                    color = SlateMuted
                                )
                                Text(
                                    text = "UID: ${currentUser.uid.take(12)}...",
                                    fontSize = 11.sp,
                                    color = SlateMuted
                                )
                                if (isPremium) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    com.example.ui.components.PremiumBadge()
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = AppStrings.get(language, "sign_out"))
                        }
                    } else {
                        // Unauthenticated prompt
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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
                                Icon(Icons.Filled.Person, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(28.dp))
                            }

                            Text(
                                text = "Sign in to MsgSort",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Text(
                                text = "Sign in with Google to synchronize your SortCodes, access dashboard history, and revoke codes anytime.",
                                fontSize = 12.sp,
                                color = SlateMuted,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = onSignInClicked,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("google_sign_in_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.get(language, "sign_in_google"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Premium Subscription Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToPremium)
                    .testTag("profile_premium_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, AmberGold.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(AmberGold.copy(alpha = 0.12f), RoyalIndigo.copy(alpha = 0.05f))
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AmberGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MsgSort Premium",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (isPremium) {
                                com.example.ui.components.PremiumBadge(text = "ACTIVE")
                            }
                        }
                        Text(
                            text = if (isPremium) "Manage auto-renewal, limits, and plan details" else "₹199/month or ₹799/year • 25MB sharing & zero ads",
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Details",
                        tint = AmberGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Language Selection Section
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
                        Icon(Icons.Filled.Language, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(20.dp))
                        Text(
                            text = AppStrings.get(language, "language"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.values().forEach { lang ->
                            val isSelected = lang == language
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onLanguageSelected(lang) }
                                    .border(
                                        1.dp,
                                        if (isSelected) RoyalIndigo else BorderLight,
                                        RoundedCornerShape(10.dp)
                                    ),
                                color = if (isSelected) ContainerIndigo else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = lang.nativeName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) DeepIndigo else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = lang.displayName,
                                        fontSize = 10.sp,
                                        color = SlateMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Appearance & Theme Section (Dark and White Option)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_theme_card"),
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
                            imageVector = if (themeMode == AppThemeMode.DARK) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = if (themeMode == AppThemeMode.DARK) AccentPurple else AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Appearance & Theme",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Toggle between Dark (Navy) and White (Light) mode",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Light / White Option
                        ThemeOptionItem(
                            label = "White / Light",
                            icon = Icons.Filled.LightMode,
                            isSelected = themeMode == AppThemeMode.LIGHT,
                            accentColor = AmberGold,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeSelected(AppThemeMode.LIGHT) }
                        )

                        // Dark / Navy Option
                        ThemeOptionItem(
                            label = "Dark / Navy",
                            icon = Icons.Filled.DarkMode,
                            isSelected = themeMode == AppThemeMode.DARK,
                            accentColor = RoyalIndigo,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeSelected(AppThemeMode.DARK) }
                        )

                        // System Auto Option
                        ThemeOptionItem(
                            label = "System Auto",
                            icon = Icons.Filled.BrightnessAuto,
                            isSelected = themeMode == AppThemeMode.SYSTEM,
                            accentColor = AccentSky,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeSelected(AppThemeMode.SYSTEM) }
                        )
                    }
                }
            }
        }

        // App Tier & Storage
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Plan Tier",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        AssistChip(
                            onClick = {},
                            label = { Text("Core Free Plan") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = SuccessContainer, labelColor = SuccessEmerald)
                        )
                    }

                    Text(
                        text = "Max upload: 25 MB per SortCode • Max duration: 30 days (720 hrs) • Zero tracking",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )
                }
            }
        }

        // Account & Privacy Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Account & Privacy",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("account_privacy_settings_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsRowItem(
                            icon = Icons.Outlined.Security,
                            title = "Privacy & Data Preferences",
                            subtitle = "Zero-retention rules, encryption & data policies",
                            onClick = { showPrivacyDialog = true }
                        )

                        if (currentUser != null) {
                            HorizontalDivider(color = BorderLight)
                            SettingsRowItem(
                                icon = Icons.Outlined.DeleteForever,
                                title = "Delete Account",
                                subtitle = "Permanently purge your account, SortCodes, and personal data",
                                textColor = ErrorRed,
                                onClick = { showDeleteAccountDialog = true }
                            )
                        }
                    }
                }
            }
        }

        // Legal & Support Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Legal & Support",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsRowItem(
                            icon = Icons.Outlined.HeadsetMic,
                            title = AppStrings.get(language, "contact_us"),
                            subtitle = "Official support at ${SupportUtils.OFFICIAL_SUPPORT_EMAIL}",
                            onClick = { showContactUsDialog = true }
                        )
                        HorizontalDivider(color = BorderLight)
                        SettingsRowItem(
                            icon = Icons.Outlined.HelpCenter,
                            title = AppStrings.get(language, "help_support"),
                            subtitle = "Troubleshooting, 6 support topics & FAQs",
                            onClick = { showHelpSupportDialog = true }
                        )
                        HorizontalDivider(color = BorderLight)
                        SettingsRowItem(
                            icon = Icons.Outlined.Policy,
                            title = AppStrings.get(language, "privacy_policy"),
                            subtitle = "Data protection & privacy contact at ${SupportUtils.OFFICIAL_SUPPORT_EMAIL}",
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider(color = BorderLight)
                        SettingsRowItem(
                            icon = Icons.Outlined.Gavel,
                            title = AppStrings.get(language, "terms_of_service"),
                            subtitle = "Usage terms and legal support at ${SupportUtils.OFFICIAL_SUPPORT_EMAIL}",
                            onClick = { showTermsDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Sign Out Confirmation Dialog
    ConfirmationDialog(
        show = showSignOutDialog,
        title = AppStrings.get(language, "sign_out"),
        message = "Are you sure you want to sign out from MsgSort?",
        confirmText = AppStrings.get(language, "sign_out"),
        cancelText = AppStrings.get(language, "cancel"),
        onConfirm = {
            onSignOutClicked()
            showSignOutDialog = false
        },
        onDismiss = { showSignOutDialog = false }
    )

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            language = language,
            currentUser = currentUser,
            deletionState = deletionState,
            onConfirmDelete = {
                onConfirmDeleteAccount()
            },
            onRequestReauth = onRequestReauth,
            onDismiss = {
                showDeleteAccountDialog = false
            }
        )
    }

    // Contact Us Dialog
    if (showContactUsDialog) {
        ContactUsDialog(
            language = language,
            onDismiss = { showContactUsDialog = false }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            language = language,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        TermsOfServiceDialog(
            language = language,
            onDismiss = { showTermsDialog = false }
        )
    }

    // Help & Support Dialog (handles both showHelpSupportDialog and showHelpDialog)
    if (showHelpSupportDialog || showHelpDialog) {
        HelpAndSupportDialog(
            language = language,
            onDismiss = {
                showHelpSupportDialog = false
                showHelpDialog = false
            }
        )
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = textColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SlateMuted
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ThemeOptionItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 1.8.dp else 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        color = if (isSelected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
