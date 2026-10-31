package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.billing.BillingConnectionState
import com.example.billing.ProductCatalogState
import com.example.billing.PlayBillingManager
import com.example.model.PremiumPlan
import com.example.model.PremiumPlans
import com.example.model.UserSubscription
import com.example.ui.components.BilldeskCheckoutDialog
import com.example.ui.components.ContactUsDialog
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.RazorpayCheckoutDialog
import com.example.ui.components.TermsOfServiceDialog
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.SupportUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    language: AppLanguage,
    currentUserId: String?,
    subscription: UserSubscription?,
    isPurchasing: Boolean,
    onSubscribePlan: (PremiumPlan) -> Unit,
    onRestorePurchases: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    billingConnectionState: BillingConnectionState = BillingConnectionState.Connected,
    productCatalogState: ProductCatalogState = ProductCatalogState.Idle,
    formattedWeeklyPrice: String? = null,
    formattedMonthlyPrice: String? = null,
    formattedAnnualPrice: String? = null,
    onRefreshBilling: () -> Unit = {},
    onRazorpayPayment: (paymentId: String, orderId: String, signature: String, plan: PremiumPlan) -> Unit = { _, _, _, _ -> },
    onBilldeskPayment: (paymentId: String, orderId: String, signature: String, plan: PremiumPlan) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    var selectedPlan by remember { mutableStateOf(PremiumPlans.WEEKLY_7D) }
    var showBilldeskDialog by remember { mutableStateOf(false) }
    var showRazorpayDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showBillingDiagnosticsDialog by remember { mutableStateOf(false) }

    val isSubscribed = subscription?.isPremiumActive() == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "MsgSort Premium",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("premium_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            // Hero Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, AmberGold.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        AmberGold.copy(alpha = 0.15f),
                                        RoyalIndigo.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
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
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Elevate Your Privacy with Premium",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Enjoy higher 25 MB file sharing limits, extended 90-day expiry options, an ad-free interface, and verified priority status.",
                                fontSize = 13.sp,
                                color = SlateGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Current Subscription Status Card (if subscribed)
            if (isSubscribed && subscription != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessContainer),
                        border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessEmerald, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Active Subscription",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SuccessEmerald
                                )
                            }

                            Text(
                                text = "Plan: ${subscription.planName} (₹${subscription.priceInr}/${subscription.billingPeriod})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "Valid until: ${subscription.formattedExpiryDate()}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )

                            // Remaining Time Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SuccessEmerald.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null, tint = SuccessEmerald, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Remaining Time: ${subscription.formattedRemainingTime()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessEmerald
                                )
                            }

                            if (subscription.provider == "billdesk" || subscription.provider == "razorpay") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Payment verified via ${if (subscription.provider == "billdesk") "BillDesk" else "Razorpay"} • ID: ${subscription.transactionId.take(18)}",
                                        fontSize = 11.sp,
                                        color = SlateGray
                                    )
                                }
                            } else if (subscription.autoRenewing) {
                                Text(
                                    text = "Auto-renewal is active. You can manage or cancel your subscription at any time in the Google Play Store under Subscriptions.",
                                    fontSize = 12.sp,
                                    color = SlateMuted
                                )
                            } else {
                                Text(
                                    text = "Subscription is active for the current period and will automatically expire on the date above.",
                                    fontSize = 12.sp,
                                    color = SlateGray
                                )
                            }
                        }
                    }
                }
            }

            // Feature Comparison List
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "What's Included in Premium",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        FeatureComparisonRow(
                            icon = Icons.Outlined.Block,
                            title = "100% Ad-Free Experience",
                            description = "No sponsored content or third-party banners across any screens",
                            freeLimit = "Ads Shown",
                            premiumLimit = "Zero Ads"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        FeatureComparisonRow(
                            icon = Icons.Outlined.CloudUpload,
                            title = "File & Media Uploads",
                            description = "Share full-resolution images, documents, and video clips",
                            freeLimit = "Max 5 MB",
                            premiumLimit = "Max 25 MB"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        FeatureComparisonRow(
                            icon = Icons.Outlined.TextFields,
                            title = "Text Message Length",
                            description = "Store extensive code snippets, reports, or legal drafts",
                            freeLimit = "20,000 chars",
                            premiumLimit = "100,000 chars"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        FeatureComparisonRow(
                            icon = Icons.Outlined.HourglassTop,
                            title = "Extended Expiry Options",
                            description = "Keep SortCodes active for long-term project handoffs",
                            freeLimit = "Up to 3 Days",
                            premiumLimit = "Up to 90 Days"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        FeatureComparisonRow(
                            icon = Icons.Outlined.Verified,
                            title = "Gold Verified Badge",
                            description = "Exclusive badge shown on your account & shared SortCodes",
                            freeLimit = "Standard",
                            premiumLimit = "Verified Pro"
                        )
                    }
                }
            }

            // Plan Selection Cards
            item {
                Text(
                    text = "Select a Plan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 7-Day Plan Card (₹99 for 7 days - Google Play / BillDesk)
            item {
                PlanCard(
                    plan = PremiumPlans.WEEKLY_7D,
                    isSelected = selectedPlan.id == PremiumPlans.WEEKLY_7D.id,
                    onSelect = { selectedPlan = PremiumPlans.WEEKLY_7D },
                    displayPrice = formattedWeeklyPrice ?: "₹99",
                    badgeText = "⚡ ₹99 FOR 7 DAYS • POPULAR"
                )
            }

            // Monthly Plan Card (₹199/month)
            item {
                PlanCard(
                    plan = PremiumPlans.MONTHLY,
                    isSelected = selectedPlan.id == PremiumPlans.MONTHLY.id,
                    onSelect = { selectedPlan = PremiumPlans.MONTHLY },
                    displayPrice = formattedMonthlyPrice,
                    badgeText = "MOST POPULAR"
                )
            }

            // Annual Plan Card (₹799/year - Save 66%)
            item {
                PlanCard(
                    plan = PremiumPlans.ANNUAL,
                    isSelected = selectedPlan.id == PremiumPlans.ANNUAL.id,
                    onSelect = { selectedPlan = PremiumPlans.ANNUAL },
                    displayPrice = formattedAnnualPrice,
                    badgeText = "BEST VALUE • SAVE 66%"
                )
            }

            // Google Play Billing Status & Verification Notice
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_play_billing_status_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (productCatalogState) {
                            is ProductCatalogState.Ready -> ContainerIndigo
                            is ProductCatalogState.Loading -> ContainerIndigo.copy(alpha = 0.6f)
                            is ProductCatalogState.Empty -> WarningContainer
                            is ProductCatalogState.Error -> ErrorContainer
                            else -> ContainerIndigo
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when (productCatalogState) {
                            is ProductCatalogState.Ready -> RoyalIndigo.copy(alpha = 0.4f)
                            is ProductCatalogState.Empty -> WarningAmber.copy(alpha = 0.6f)
                            is ProductCatalogState.Error -> ErrorRed.copy(alpha = 0.5f)
                            else -> RoyalIndigo.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (productCatalogState) {
                                        is ProductCatalogState.Ready -> Icons.Filled.Verified
                                        is ProductCatalogState.Empty -> Icons.Filled.Info
                                        is ProductCatalogState.Error -> Icons.Filled.Warning
                                        else -> Icons.Filled.ShoppingBag
                                    },
                                    contentDescription = null,
                                    tint = when (productCatalogState) {
                                        is ProductCatalogState.Ready -> RoyalIndigo
                                        is ProductCatalogState.Empty -> WarningAmber
                                        is ProductCatalogState.Error -> ErrorRed
                                        else -> RoyalIndigo
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Google Play Billing & Server Verification",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = onRefreshBilling,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("refresh_billing_button")
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh Google Play connection",
                                    modifier = Modifier.size(16.dp),
                                    tint = RoyalIndigo
                                )
                            }
                        }

                        when (productCatalogState) {
                            is ProductCatalogState.Ready -> {
                                Text(
                                    text = "• Google Play Store: Connected\n• Subscription Product: 'premium_monthly' (Active)\n• Base Plans: monthly-199 (${formattedMonthlyPrice ?: "₹199"}), weekly-99-new (${formattedWeeklyPrice ?: "₹99"}), yearly-799 (${formattedAnnualPrice ?: "₹799"})",
                                    fontSize = 11.sp,
                                    color = SlateGray,
                                    lineHeight = 16.sp
                                )
                            }
                            is ProductCatalogState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = RoyalIndigo
                                    )
                                    Text(
                                        text = "Connecting to Google Play Store and querying subscription product details...",
                                        fontSize = 11.sp,
                                        color = SlateGray
                                    )
                                }
                            }
                            is ProductCatalogState.Empty -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Play Store catalog returned 0 subscription products for 'premium_monthly'.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = "Ensure subscription 'premium_monthly' with an Active Base Plan (₹199) is configured in Play Console and this tester account is authorized.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF92400E),
                                        lineHeight = 15.sp
                                    )
                                    TextButton(
                                        onClick = { showBillingDiagnosticsDialog = true },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Tap to view Google Play Console setup checklist →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepIndigo)
                                    }
                                }
                            }
                            is ProductCatalogState.Error -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Google Play Billing Error: ${productCatalogState.message}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ErrorRed
                                    )
                                    TextButton(
                                        onClick = { showBillingDiagnosticsDialog = true },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("View troubleshooting guide →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepIndigo)
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    text = "All in-app purchases are securely processed through Google Play. Auto-renewing subscriptions can be cancelled anytime with a single tap in Google Play.",
                                    fontSize = 11.sp,
                                    color = SlateGray,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isLoadingCatalog = productCatalogState is ProductCatalogState.Loading || billingConnectionState is BillingConnectionState.Connecting
                    val effectivePrice = when {
                        selectedPlan.basePlanId == PremiumPlans.BASE_PLAN_WEEKLY || selectedPlan.id == PremiumPlans.WEEKLY_7D.id -> formattedWeeklyPrice ?: "₹99"
                        selectedPlan.basePlanId == PremiumPlans.BASE_PLAN_MONTHLY || selectedPlan.id == PremiumPlans.MONTHLY.id -> formattedMonthlyPrice ?: "₹199"
                        else -> formattedAnnualPrice ?: "₹799"
                    }

                    Button(
                        onClick = {
                            if (currentUserId == null) {
                                Toast.makeText(context, "Please sign in with Google first", Toast.LENGTH_SHORT).show()
                            } else {
                                onSubscribePlan(selectedPlan)
                            }
                        },
                        enabled = !isPurchasing && !isLoadingCatalog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("subscribe_plan_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalIndigo
                        )
                    ) {
                        if (isPurchasing || isLoadingCatalog) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isLoadingCatalog) "Checking Google Play Store..." else "Processing payment...")
                        } else {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSubscribed) "Switch to ${selectedPlan.name} ($effectivePrice)" else "Subscribe with Google Play ($effectivePrice)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // For the 7-day plan, also offer instant web/UPI payment via BillDesk as an alternative
                    if (selectedPlan.id == PremiumPlans.WEEKLY_7D.id) {
                        OutlinedButton(
                            onClick = {
                                if (currentUserId == null) {
                                    Toast.makeText(context, "Please sign in with Google first", Toast.LENGTH_SHORT).show()
                                } else {
                                    showBilldeskDialog = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("billdesk_alternative_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoyalIndigo)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Or Pay ₹99 via BillDesk (Instant)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RoyalIndigo)
                        }
                    }

                    OutlinedButton(
                        onClick = onRestorePurchases,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("restore_purchases_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Restore Purchases", fontSize = 13.sp)
                    }
                }
            }

            // Billing & Subscription Support Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("premium_support_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ContainerIndigo.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SupportAgent,
                                contentDescription = null,
                                tint = RoyalIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Premium Billing & Subscription Support",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Have questions regarding Premium billing, Google Play receipts, renewal status, or plan upgrades? Our official team is ready to assist at ${SupportUtils.OFFICIAL_SUPPORT_EMAIL}.",
                            fontSize = 12.sp,
                            color = SlateMuted,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    SupportUtils.openSupportEmail(
                                        context = context,
                                        subject = "MsgSort Premium Billing Question",
                                        body = "Hello MsgSort Support Team,\n\nI have a question regarding my Premium subscription:\n\nUser ID: ${currentUserId ?: "Not signed in"}\nActive Plan: ${subscription?.planId ?: "None"}\n\nDetails:\n"
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("contact_billing_support_btn"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Contact Support", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { SupportUtils.copySupportEmail(context) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, RoyalIndigo),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("premium_copy_support_email_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Email",
                                    tint = RoyalIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Terms, Privacy, and Support links
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showTermsDialog = true }) {
                        Text(text = "Terms of Service", fontSize = 12.sp, color = RoyalIndigo)
                    }
                    Text("•", color = SlateMuted, fontSize = 12.sp)
                    TextButton(onClick = { showPrivacyDialog = true }) {
                        Text(text = "Privacy Policy", fontSize = 12.sp, color = RoyalIndigo)
                    }
                    Text("•", color = SlateMuted, fontSize = 12.sp)
                    TextButton(onClick = { showContactDialog = true }) {
                        Text(text = "Support", fontSize = 12.sp, color = RoyalIndigo)
                    }
                }
            }
        }
    }

    // Terms Dialog
    if (showTermsDialog) {
        TermsOfServiceDialog(
            language = language,
            onDismiss = { showTermsDialog = false }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            language = language,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    // Contact Us Dialog
    if (showContactDialog) {
        ContactUsDialog(
            language = language,
            onDismiss = { showContactDialog = false }
        )
    }
    // Billing Diagnostics & Play Console Setup Dialog
    // Billing Diagnostics & Play Console Setup Dialog
    if (showBillingDiagnosticsDialog) {
        GooglePlayBillingGuideDialog(
            connectionState = billingConnectionState,
            catalogState = productCatalogState,
            onDismiss = { showBillingDiagnosticsDialog = false },
            onRetry = {
                showBillingDiagnosticsDialog = false
                onRefreshBilling()
            }
        )
    }

    // BillDesk Checkout Dialog for ₹99 7-Day Plan
    if (showBilldeskDialog) {
        BilldeskCheckoutDialog(
            plan = selectedPlan,
            userEmail = "",
            isProcessing = isPurchasing,
            onDismiss = { showBilldeskDialog = false },
            onConfirmPayment = { paymentId, orderId, signature ->
                showBilldeskDialog = false
                onBilldeskPayment(paymentId, orderId, signature, selectedPlan)
            }
        )
    }

    // Razorpay Checkout Dialog (legacy/fallback)
    if (showRazorpayDialog) {
        RazorpayCheckoutDialog(
            plan = selectedPlan,
            userEmail = "",
            isProcessing = isPurchasing,
            onDismiss = { showRazorpayDialog = false },
            onConfirmPayment = { paymentId, orderId, signature ->
                showRazorpayDialog = false
                onRazorpayPayment(paymentId, orderId, signature, selectedPlan)
            }
        )
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    onSelect: () -> Unit,
    displayPrice: String? = null,
    badgeText: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("plan_card_${plan.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ContainerIndigo else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) RoyalIndigo else MaterialTheme.colorScheme.outlineVariant
        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        colors = RadioButtonDefaults.colors(selectedColor = RoyalIndigo)
                    )
                    Column {
                        Text(
                            text = plan.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = plan.description,
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }
                }

                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) RoyalIndigo else AmberGold)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayPrice ?: "₹${plan.priceInr}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) RoyalIndigo else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "/ ${plan.period}",
                    fontSize = 13.sp,
                    color = SlateMuted,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun GooglePlayBillingGuideDialog(
    connectionState: BillingConnectionState,
    catalogState: ProductCatalogState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = RoyalIndigo
                )
                Text("Google Play Console Setup Guide", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ContainerIndigo)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Live Status Diagnostics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = DeepIndigo
                        )
                        Text(
                            text = "• Connection: ${connectionState::class.simpleName}\n• Product Catalog: ${
                                when (catalogState) {
                                    is ProductCatalogState.Ready -> "Ready (${catalogState.productCount} product)"
                                    is ProductCatalogState.Empty -> "Empty (0 items returned)"
                                    is ProductCatalogState.Error -> "Error: ${catalogState.message}"
                                    is ProductCatalogState.Loading -> "Loading..."
                                    else -> "Idle"
                                }
                            }\n• Expected Product ID: premium_monthly\n• Base Plans:\n   - monthly-199 (₹199 / 1 month)\n   - weekly-99-new (₹99 / 1 week)\n   - yearly-799 (₹799 / 1 year)",
                            fontSize = 11.sp,
                            color = SlateGray,
                            lineHeight = 15.sp
                        )
                    }
                }

                Text(
                    text = "If checkout fails with 'Google Play Store checkout could not be initiated' or 'Item Unavailable', ensure these settings in Google Play Console:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )

                Text(
                    text = "1. Subscription Product ID:\n   Must be exactly 'premium_monthly' under Monetize > Subscriptions.\n\n" +
                            "2. Active Base Plans inside 'premium_monthly':\n" +
                            "   • monthly-199 = ₹199 / 1 month (Active)\n" +
                            "   • weekly-99-new = ₹99 / 1 week (Active)\n" +
                            "   • yearly-799 = ₹799 / 1 year (Active)\n\n" +
                            "3. Authorized License Tester:\n   In Play Console > Setup > License Testing, add your Google account email with License response 'RESPOND_NORMALLY'.\n\n" +
                            "4. Internal Testing Release:\n   The app must be uploaded to an Internal or Closed testing track, and the tester must have accepted the invite via the testing web link.",
                    fontSize = 11.sp,
                    color = SlateGray,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
            ) {
                Text("Retry Connection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SlateGray)
            }
        }
    )
}

@Composable
private fun FeatureComparisonRow(
    icon: ImageVector,
    title: String,
    description: String,
    freeLimit: String,
    premiumLimit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ContainerIndigo),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text = description, fontSize = 11.sp, color = SlateMuted, lineHeight = 14.sp)

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Free: $freeLimit", fontSize = 11.sp, color = SlateGray)
                Text(text = "Pro: $premiumLimit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoyalIndigo)
            }
        }
    }
}
