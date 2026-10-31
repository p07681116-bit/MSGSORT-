package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings

enum class ScreenTab(val icon: ImageVector, val selectedIcon: ImageVector, val stringKey: String) {
    HOME(Icons.Outlined.Home, Icons.Filled.Home, "home"),
    MY_CODES(Icons.Outlined.Folder, Icons.Filled.Folder, "my_codes"),
    SCAN(Icons.Outlined.QrCodeScanner, Icons.Filled.QrCodeScanner, "scan"),
    PREMIUM(Icons.Outlined.WorkspacePremium, Icons.Filled.WorkspacePremium, "premium"),
    PROFILE(Icons.Outlined.Person, Icons.Filled.Person, "profile"),
    CREATE(Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircle, "create"),
    RETRIEVE(Icons.Outlined.Search, Icons.Filled.Search, "retrieve")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MsgSortTopBar(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    userEmail: String?,
    modifier: Modifier = Modifier
) {
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Logo & Brand Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(RoyalIndigo, AccentPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "MsgSort Logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "MsgSort",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "SortCode Vault",
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                }
            }

            // Language Selector & User Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Language Dropdown
                Box {
                    AssistChip(
                        onClick = { languageMenuExpanded = true },
                        label = {
                            Text(
                                text = currentLanguage.nativeName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Language",
                                modifier = Modifier.size(16.dp),
                                tint = RoyalIndigo
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = ContainerIndigo,
                            labelColor = DeepIndigo
                        ),
                        border = null
                    )

                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        AppLanguage.values().forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${lang.nativeName} (${lang.displayName})",
                                        fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onLanguageSelected(lang)
                                    languageMenuExpanded = false
                                },
                                leadingIcon = {
                                    if (lang == currentLanguage) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = RoyalIndigo)
                                    }
                                }
                            )
                        }
                    }
                }

                // Auth indicator badge
                if (userEmail != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ContainerIndigo)
                            .border(1.dp, RoyalIndigo.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userEmail.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RoyalIndigo
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MsgSortBottomNav(
    selectedTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        val primaryTabs = remember {
            listOf(ScreenTab.HOME, ScreenTab.MY_CODES, ScreenTab.SCAN, ScreenTab.PREMIUM, ScreenTab.PROFILE)
        }
        primaryTabs.forEach { tab ->
            val isSelected = selectedTab == tab || (tab == ScreenTab.SCAN && selectedTab == ScreenTab.RETRIEVE)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                        contentDescription = AppStrings.get(language, tab.stringKey)
                    )
                },
                label = {
                    Text(
                        text = AppStrings.get(language, tab.stringKey),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoyalIndigo,
                    selectedTextColor = RoyalIndigo,
                    indicatorColor = ContainerIndigo,
                    unselectedIconColor = SlateMuted,
                    unselectedTextColor = SlateMuted
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
fun PremiumBadge(
    modifier: Modifier = Modifier,
    text: String = "PREMIUM"
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AmberGold, Color(0xFFFFA000))
                )
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, labelKey) = when (status.lowercase()) {
        "active" -> Triple(SuccessContainer, SuccessEmerald, "active")
        "expired" -> Triple(WarningContainer, WarningAmber, "expired")
        "revoked" -> Triple(ErrorContainer, ErrorRed, "revoked")
        else -> Triple(ContainerIndigo, SlateMuted, status)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = AppStrings.get(language, labelKey).uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SortCodeBadge(
    code: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ContainerIndigo, ContainerPurple)
                )
            )
            .border(1.dp, RoyalIndigo.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = RoyalIndigo,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ConfirmationDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = message, color = SlateGray, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDestructive) ErrorRed else RoyalIndigo
                    )
                ) {
                    Text(text = confirmText)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text(text = cancelText)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
