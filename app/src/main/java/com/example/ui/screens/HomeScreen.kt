package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SortCodeItem
import com.example.ui.components.ScreenTab
import com.example.ui.components.StatusBadge
import com.example.util.SupportUtils
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.google.firebase.auth.FirebaseUser
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    language: AppLanguage,
    recentCodes: List<SortCodeItem>,
    isPremium: Boolean = false,
    currentUser: FirebaseUser? = null,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onNavigateTab: (ScreenTab) -> Unit,
    onCodeSelected: (String) -> Unit,
    onQuickAction: (String) -> Unit = {},
    onLanguageSelected: (AppLanguage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputSortCode by remember { mutableStateOf("") }
    var codeInputError by remember { mutableStateOf<String?>(null) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    // Adaptive Theme Palette: Dark (Navy) vs White (Light)
    val screenBg = if (isDarkTheme) Color(0xFF0B0F19) else Color(0xFFF8FAFC)
    val cardBg = if (isDarkTheme) Color(0xFF131B2E) else Color.White
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color.White else DarkNavy
    val textSecondary = if (isDarkTheme) LightSlate else SlateGray
    val inputBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val headerActionBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val headerActionTint = if (isDarkTheme) Color.White else DarkNavy

    // Dynamic greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Welcome back"
        }
    }

    val userDisplayName = remember(currentUser) {
        currentUser?.displayName?.split(" ")?.firstOrNull()?.ifBlank { null }
            ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Explorer"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp)
    ) {
        // 1. TOP HEADER SECTION
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
                elevation = if (!isDarkTheme) CardDefaults.cardElevation(defaultElevation = 2.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Logo & Greeting
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(RoyalIndigo, AccentPurple, AccentCyan)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "MsgSort Logo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "MsgSort",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = textPrimary,
                                    letterSpacing = 0.5.sp
                                )

                                // Real Membership Badge
                                if (isPremium) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.horizontalGradient(listOf(AmberGold, Color(0xFFFFA000)))
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.WorkspacePremium,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "PRO",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF1E293B) else ContainerIndigo)
                                            .clickable { onNavigateTab(ScreenTab.PREMIUM) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "FREE",
                                            color = if (isDarkTheme) LightSlate else RoyalIndigo,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "$greeting, $userDisplayName",
                                fontSize = 12.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right: Theme Toggle (Dark/White), Language, Notifications & Profile Avatar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Theme Switcher Button (Sun / Moon)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(headerActionBg)
                                .clickable {
                                    onToggleTheme()
                                    Toast.makeText(
                                        context,
                                        if (isDarkTheme) "Switched to White / Light Mode" else "Switched to Dark Mode",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .testTag("home_theme_toggle_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = if (isDarkTheme) AmberGold else RoyalIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Language Selector
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(headerActionBg)
                                    .clickable { languageMenuExpanded = true }
                                    .testTag("home_language_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = "Language",
                                    tint = headerActionTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false }
                            ) {
                                AppLanguage.values().forEach { lang ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${lang.nativeName} (${lang.displayName})",
                                                fontWeight = if (lang == language) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onLanguageSelected(lang)
                                            languageMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (lang == language) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = RoyalIndigo)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Notification Bell
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(headerActionBg)
                                .clickable { showNotificationSheet = true }
                                .testTag("home_notification_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = headerActionTint,
                                modifier = Modifier.size(19.dp)
                            )
                            // Subtle notification indicator
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan)
                            )
                        }

                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(RoyalIndigo, AccentPurple))
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { onNavigateTab(ScreenTab.PROFILE) }
                                .testTag("home_avatar_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userDisplayName.take(1).uppercase(),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. HERO SECTION
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(26.dp))
                    .testTag("home_hero_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF1E1B4B),
                                        Color(0xFF2E1065)
                                    )
                                } else {
                                    listOf(
                                        RoyalIndigo,
                                        DeepIndigo,
                                        AccentPurple
                                    )
                                }
                            )
                        )
                        .border(1.2.dp, if (isDarkTheme) RoyalIndigo.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(26.dp))
                        .padding(22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tagline Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(30.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(30.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⚡ Share Anything. Instantly. Securely.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkTheme) AccentCyan else Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Heading & Subtitle
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Share Smarter with MsgSort",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.5).sp
                            )

                            Text(
                                text = "Your messages and files. One code. Secure sharing.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        // Connected Share Illustration (Doc, Image, Video connected to SortCode)
                        ConnectedShareIllustration(isDarkTheme = isDarkTheme)

                        // Two Primary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Button 1: Create New Share
                            Button(
                                onClick = { onNavigateTab(ScreenTab.CREATE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("hero_create_share_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) RoyalIndigo else Color.White,
                                    contentColor = if (isDarkTheme) Color.White else DeepIndigo
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Create Share",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // Button 2: Enter SortCode
                            OutlinedButton(
                                onClick = { onNavigateTab(ScreenTab.RETRIEVE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("hero_enter_code_btn"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isDarkTheme) AccentCyan else Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Enter SortCode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. QUICK ACTIONS SECTION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Quick Actions",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Tap to share by format",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDarkTheme) Color(0xFF1E293B) else ContainerIndigo)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "6 FORMATS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) AccentCyan else RoyalIndigo
                        )
                    }
                }

                // 2x3 Grid of Quick Actions
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Text, PDF, Image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            title = "Share Text",
                            subtitle = "Messages",
                            icon = Icons.Filled.Notes,
                            accentColor = RoyalIndigo,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("text") }
                        )

                        QuickActionCard(
                            title = "Upload PDF",
                            subtitle = "Documents",
                            icon = Icons.Filled.PictureAsPdf,
                            accentColor = Color(0xFFF43F5E),
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("file") }
                        )

                        QuickActionCard(
                            title = "Share Image",
                            subtitle = "Photos",
                            icon = Icons.Filled.Image,
                            accentColor = SuccessEmerald,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("image") }
                        )
                    }

                    // Row 2: Video, Document, Scan QR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            title = "Share Video",
                            subtitle = "Clips",
                            icon = Icons.Filled.Videocam,
                            accentColor = AccentPurple,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("video") }
                        )

                        QuickActionCard(
                            title = "Share Doc",
                            subtitle = "Files",
                            icon = Icons.Filled.Description,
                            accentColor = AccentSky,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f),
                            onClick = { onQuickAction("file") }
                        )

                        QuickActionCard(
                            title = "Scan QR",
                            subtitle = "Instant",
                            icon = Icons.Filled.QrCodeScanner,
                            accentColor = AccentCyan,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateTab(ScreenTab.SCAN) }
                        )
                    }
                }
            }
        }

        // 4. SORTCODE SECTION ("Open with SortCode")
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_sortcode_input_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.2.dp, if (isDarkTheme) RoyalIndigo.copy(alpha = 0.5f) else RoyalIndigo.copy(alpha = 0.35f)),
                elevation = if (!isDarkTheme) CardDefaults.cardElevation(defaultElevation = 2.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ContainerIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = RoyalIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Open with SortCode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "Enter 6-character code to view shared content",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // Input Field with Monospace & Action Buttons
                    OutlinedTextField(
                        value = inputSortCode,
                        onValueChange = {
                            inputSortCode = it.uppercase()
                            codeInputError = null
                        },
                        placeholder = {
                            Text("e.g. MS-7X9K2", color = SlateMuted)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_sortcode_textfield"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = RoyalIndigo)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputSortCode.isNotBlank()) {
                                    IconButton(onClick = { inputSortCode = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = textSecondary)
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrBlank()) {
                                                inputSortCode = clip.trim().uppercase()
                                                codeInputError = null
                                            } else {
                                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = RoyalIndigo)
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = RoyalIndigo,
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF334155) else BorderLight,
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val trimmed = inputSortCode.trim()
                                if (trimmed.length in 4..20) {
                                    onCodeSelected(trimmed)
                                } else {
                                    codeInputError = "Please enter a valid SortCode (min 4 characters)"
                                }
                            }
                        )
                    )

                    if (codeInputError != null) {
                        Text(
                            text = codeInputError ?: "",
                            color = ErrorRed,
                            fontSize = 11.sp
                        )
                    }

                    // Open Share Button
                    Button(
                        onClick = {
                            val trimmed = inputSortCode.trim()
                            if (trimmed.length in 4..20) {
                                onCodeSelected(trimmed)
                            } else {
                                codeInputError = "Please enter a valid SortCode (min 4 characters)"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("home_open_share_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) {
                        Text(
                            text = "Open Share",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 5. RECENT SHARES SECTION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recent Shares",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    if (recentCodes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ContainerIndigo)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${recentCodes.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoyalIndigo
                            )
                        }
                    }
                }

                if (recentCodes.isNotEmpty()) {
                    TextButton(onClick = { onNavigateTab(ScreenTab.MY_CODES) }) {
                        Text(
                            text = "View All",
                            fontSize = 13.sp,
                            color = if (isDarkTheme) AccentCyan else RoyalIndigo,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (recentCodes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder),
                    elevation = if (!isDarkTheme) CardDefaults.cardElevation(defaultElevation = 1.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0xFF1E293B) else ContainerIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = null,
                                tint = if (isDarkTheme) AccentCyan else RoyalIndigo,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "No Recent Shares",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textPrimary
                        )

                        Text(
                            text = "Your generated SortCodes will appear here with real-time expiry tracking.",
                            fontSize = 12.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        OutlinedButton(
                            onClick = { onNavigateTab(ScreenTab.CREATE) },
                            modifier = Modifier.padding(top = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RoyalIndigo)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Create First Share", color = if (isDarkTheme) Color.White else RoyalIndigo, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(recentCodes.take(4), key = { it.code }) { item ->
                RecentShareCard(
                    item = item,
                    language = language,
                    isDarkTheme = isDarkTheme,
                    onClick = { onCodeSelected(item.code) }
                )
            }
        }

        // 6. PREMIUM BANNER
        item {
            if (!isPremium) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateTab(ScreenTab.PREMIUM) }
                        .testTag("home_premium_banner_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1A1728) else Color.White),
                    border = BorderStroke(1.2.dp, AmberGold.copy(alpha = 0.6f)),
                    elevation = if (!isDarkTheme) CardDefaults.cardElevation(defaultElevation = 2.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        AmberGold.copy(alpha = 0.12f),
                                        RoyalIndigo.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(AmberGold, Color(0xFFFFA000)))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WorkspacePremium,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Unlock More with MsgSort Premium",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "Higher Limits • Extended Expiry • Ad-Free",
                                            fontSize = 11.sp,
                                            color = AmberGold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AmberGold)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text("₹199/mo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BenefitPill("25 MB Uploads", isDarkTheme)
                                BenefitPill("90-Day Expiry", isDarkTheme)
                                BenefitPill("Gold Pro Badge", isDarkTheme)
                            }

                            Button(
                                onClick = { onNavigateTab(ScreenTab.PREMIUM) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                            ) {
                                Text("Explore Premium Plans", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF0F291E) else SuccessContainer),
                    border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SuccessEmerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MsgSort Premium Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "You have 25 MB file sharing, 90-day expiry, and ad-free priority access.",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = { onNavigateTab(ScreenTab.PREMIUM) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Manage", fontSize = 11.sp, color = SuccessEmerald)
                        }
                    }
                }
            }
        }
    }

    // Notifications Bottom Sheet
    if (showNotificationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationSheet = false },
            containerColor = cardBg,
            dragHandle = { BottomSheetDefaults.DragHandle(color = if (isDarkTheme) Color(0xFF334155) else BorderLight) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                    Text(
                        text = "System Alerts & Security Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = textPrimary
                    )
                }

                StatusNotificationItem(
                    title = "End-to-End Encryption Active",
                    subtitle = "All SortCode payloads and files are encrypted in transit.",
                    icon = Icons.Filled.Lock,
                    tint = SuccessEmerald,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                StatusNotificationItem(
                    title = "Cloud Storage Watchdog Online",
                    subtitle = "Expired codes are automatically purged per zero-retention policy.",
                    icon = Icons.Filled.CheckCircle,
                    tint = RoyalIndigo,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                StatusNotificationItem(
                    title = "Google Sign-In Connected",
                    subtitle = if (currentUser != null) "Logged in as ${currentUser.email}" else "Guest mode active (sign in to save codes permanently).",
                    icon = Icons.Filled.AccountCircle,
                    tint = AccentCyan,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                StatusNotificationItem(
                    title = "Official Customer Support",
                    subtitle = "Questions or feedback? Reach out to ${SupportUtils.OFFICIAL_SUPPORT_EMAIL}.",
                    icon = Icons.Filled.HeadsetMic,
                    tint = RoyalIndigo,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SupportUtils.openSupportEmail(
                                context = context,
                                subject = SupportUtils.DEFAULT_SUPPORT_SUBJECT
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RoyalIndigo)
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Email Support", color = RoyalIndigo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showNotificationSheet = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Connected Illustration:
 * Shows floating Document, Image, Video cards linked to a central radiant SortCode
 */
@Composable
private fun ConnectedShareIllustration(isDarkTheme: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDarkTheme) Color(0xFF0B1020) else Color(0x33FFFFFF))
            .border(1.dp, if (isDarkTheme) Color(0xFF1E293B) else Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Connecting laser lines and nodes in the background
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFF43F5E),
                            RoyalIndigo,
                            AccentCyan,
                            SuccessEmerald
                        )
                    )
                )
        )

        // 4 Format Cards, Central SortCode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Card 1: Document (PDF)
            FormatPillCard(
                icon = Icons.Filled.PictureAsPdf,
                label = "PDF",
                color = Color(0xFFF43F5E),
                isDarkTheme = isDarkTheme,
                modifier = Modifier.graphicsLayer { translationY = floatOffset * density }
            )

            // Card 2: Image
            FormatPillCard(
                icon = Icons.Filled.Image,
                label = "JPG",
                color = SuccessEmerald,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.graphicsLayer { translationY = -floatOffset * density }
            )

            // Central Luminous SortCode Node
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            if (isDarkTheme) {
                                listOf(Color(0xFF1E1B4B), Color(0xFF311042))
                            } else {
                                listOf(Color(0xFF312E81), Color(0xFF4C1D95))
                            }
                        )
                    )
                    .border(1.5.dp, AmberGold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "MS-7X9K2",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Card 3: Video
            FormatPillCard(
                icon = Icons.Filled.Videocam,
                label = "MP4",
                color = AccentPurple,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.graphicsLayer { translationY = floatOffset * density }
            )

            // Card 4: Text
            FormatPillCard(
                icon = Icons.Filled.Notes,
                label = "TXT",
                color = AccentCyan,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.graphicsLayer { translationY = -floatOffset * density }
            )
        }
    }
}

@Composable
private fun FormatPillCard(
    icon: ImageVector,
    label: String,
    color: Color,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDarkTheme) Color(0xFF131B2E) else Color.White)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else DarkNavy
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("quick_action_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF131B2E) else Color.White),
        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        elevation = if (!isDarkTheme) CardDefaults.cardElevation(defaultElevation = 1.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isDarkTheme) Color.White else DarkNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = if (isDarkTheme) LightSlate else SlateMuted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentShareCard(
    item: SortCodeItem,
    language: AppLanguage,
    isDarkTheme: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("recent_share_${item.code}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF131B2E) else Color.White),
        border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        elevation = if (!isDarkTheme) CardDefaults.cardElevation(defaultElevation = 1.dp) else CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val iconColor = when (item.contentType) {
                    "image" -> SuccessEmerald
                    "video" -> AccentPurple
                    "file", "document" -> Color(0xFFF43F5E)
                    else -> RoyalIndigo
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconColor.copy(alpha = 0.15f))
                        .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.contentType) {
                            "image" -> Icons.Filled.Image
                            "video" -> Icons.Filled.Videocam
                            "file", "document" -> Icons.Filled.Description
                            else -> Icons.Filled.Notes
                        },
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (item.title.isNotBlank()) item.title else item.code,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDarkTheme) Color.White else DarkNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.code} • ${item.formattedRemainingTime()}",
                        fontSize = 12.sp,
                        color = if (isDarkTheme) LightSlate else SlateMuted
                    )
                }
            }

            StatusBadge(status = item.getComputedStatus(), language = language)
        }
    }
}

@Composable
private fun BenefitPill(text: String, isDarkTheme: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F5F9))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "✓ $text",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else DarkNavy
        )
    }
}

@Composable
private fun StatusNotificationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = textSecondary)
        }
    }
}
