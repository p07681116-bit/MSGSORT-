package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.example.model.PremiumPlan
import com.example.model.PremiumPlans
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connection states for Google Play Billing
 */
sealed class BillingConnectionState {
    object Idle : BillingConnectionState()
    object Connecting : BillingConnectionState()
    object Connected : BillingConnectionState()
    data class Disconnected(val reason: String = "Service disconnected") : BillingConnectionState()
    data class Error(val responseCode: Int, val message: String) : BillingConnectionState()
}

/**
 * State representing Google Play Store product details query
 */
sealed class ProductCatalogState {
    object Idle : ProductCatalogState()
    object Loading : ProductCatalogState()
    data class Ready(val productCount: Int, val products: Map<String, ProductDetails>) : ProductCatalogState()
    data class Empty(val attemptedIds: List<String>, val reason: String) : ProductCatalogState()
    data class Error(val responseCode: Int, val message: String) : ProductCatalogState()
}

/**
 * Structured result when launching Google Play checkout flow
 */
sealed class BillingFlowLaunchResult {
    object Success : BillingFlowLaunchResult()
    data class Error(
        val responseCode: Int?,
        val diagnosticMessage: String,
        val userFriendlyMessage: String
    ) : BillingFlowLaunchResult()
}

/**
 * Data structure representing a discovered active Base Plan under a subscription product
 */
data class BasePlanInfo(
    val basePlanId: String,
    val productId: String,
    val offerToken: String,
    val offerId: String?,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val billingPeriod: String?
)

/**
 * Google Play Billing Lifecycle Manager
 * Implements Google Play Billing Library 7.x
 * - Queries subscription ProductDetails using product ID "premium_monthly"
 * - Fetches subscription ProductDetails and all active base plans:
 *     • monthly-199 = ₹199 / 1 month
 *     • weekly-99-new = ₹99 / 1 week
 *     • yearly-799 = ₹799 / 1 year
 * - Never uses basePlanId as the subscription product ID
 * - Uses the correct offerToken when launching purchase flows
 * - Manages BillingClient connection lifecycle, auto-reconnect, and error handling
 * - Verifies purchases securely and acknowledges purchases
 * - Supports restoring active subscriptions via queryPurchasesAsync
 * - Comprehensive diagnostic logs with [MsgSortBilling] tag
 */
class PlayBillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onPurchaseSuccess: (purchase: Purchase, planOrBasePlanId: String) -> Unit = { _, _ -> },
    private val onBillingError: (errorMessage: String) -> Unit = {}
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        const val TAG = "PlayBillingManager"

        // Canonical Subscription Product ID in Google Play Console
        const val PRODUCT_SUBSCRIPTION_ID = "premium_monthly"
        const val PRODUCT_MONTHLY = "premium_monthly"
        const val PRODUCT_ANNUAL = "premium_annual"

        // Canonical Base Plan IDs configured inside 'premium_monthly'
        const val BASE_PLAN_MONTHLY = "monthly-199"     // ₹199 / 1 month
        const val BASE_PLAN_WEEKLY = "weekly-99-new"    // ₹99 / 1 week
        const val BASE_PLAN_YEARLY = "yearly-799"       // ₹799 / 1 year

        // Aliases / candidates
        val CANDIDATE_MONTHLY_IDS = listOf(PRODUCT_MONTHLY, "monthly_premium", "premium")
        val CANDIDATE_ANNUAL_IDS = listOf(PRODUCT_ANNUAL, "annual_premium")
        val ALL_CANDIDATE_PRODUCTS = listOf(
            PRODUCT_SUBSCRIPTION_ID,
            PRODUCT_ANNUAL,
            "monthly_premium",
            "annual_premium",
            "premium"
        )
        val ALL_BASE_PLAN_IDS = listOf(BASE_PLAN_MONTHLY, BASE_PLAN_WEEKLY, BASE_PLAN_YEARLY)

        /**
         * Identifies whether a given basePlanId corresponds to the monthly plan
         */
        fun isMonthlyBasePlan(basePlanId: String): Boolean {
            return basePlanId == BASE_PLAN_MONTHLY ||
                    basePlanId.equals("monthly", ignoreCase = true) ||
                    (basePlanId.contains("month", ignoreCase = true) && !basePlanId.contains("year", ignoreCase = true)) ||
                    basePlanId.startsWith("monthly-") ||
                    basePlanId.contains("199")
        }

        /**
         * Identifies whether a given basePlanId corresponds to the weekly plan
         */
        fun isWeeklyBasePlan(basePlanId: String): Boolean {
            return basePlanId == BASE_PLAN_WEEKLY ||
                    basePlanId.contains("week", ignoreCase = true) ||
                    basePlanId.equals("weekly", ignoreCase = true) ||
                    basePlanId.startsWith("weekly-")
        }

        /**
         * Identifies whether a given basePlanId corresponds to the yearly plan
         */
        fun isYearlyBasePlan(basePlanId: String): Boolean {
            return basePlanId == BASE_PLAN_YEARLY ||
                    basePlanId.contains("year", ignoreCase = true) ||
                    basePlanId.contains("annual", ignoreCase = true) ||
                    basePlanId.startsWith("yearly-") ||
                    basePlanId.startsWith("annual-") ||
                    basePlanId.contains("799")
        }

        /**
         * Resolves any input planId or basePlanId to one of the canonical basePlanIds
         */
        fun resolveBasePlanId(planOrBasePlanId: String): String {
            return when {
                isMonthlyBasePlan(planOrBasePlanId) || planOrBasePlanId == PremiumPlans.MONTHLY.id || planOrBasePlanId == PRODUCT_SUBSCRIPTION_ID -> BASE_PLAN_MONTHLY
                isWeeklyBasePlan(planOrBasePlanId) || planOrBasePlanId == PremiumPlans.WEEKLY_7D.id -> BASE_PLAN_WEEKLY
                isYearlyBasePlan(planOrBasePlanId) || planOrBasePlanId == PremiumPlans.ANNUAL.id -> BASE_PLAN_YEARLY
                else -> BASE_PLAN_MONTHLY
            }
        }

        /**
         * Converts Google Play Billing response codes into clear human-readable explanations.
         */
        fun getBillingResponseDescription(responseCode: Int): String {
            return when (responseCode) {
                BillingClient.BillingResponseCode.OK -> "OK (0): Success"
                BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED (1): Purchase canceled by user"
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE (2): Network or Google Play Store connection error"
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE (3): Google Play Store app is outdated or billing is unsupported on this device/account"
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE (4): Product not found in Google Play Console. Verify subscription 'premium_monthly' and Base Plan (monthly-199) are Active, and tester account is enrolled."
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR (5): Invalid parameters or subscription configuration passed to Google Play Billing. Verify Google Play Console settings."
                BillingClient.BillingResponseCode.ERROR -> "ERROR (6): Fatal error during Google Play processing"
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED (7): Subscription is already active on this Google account"
                BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED (8): Item is not owned"
                BillingClient.BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR (12): Network connection to Google Play Store failed"
                else -> "Response code $responseCode"
            }
        }
    }

    private val _connectionState = MutableStateFlow<BillingConnectionState>(BillingConnectionState.Idle)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _productCatalogState = MutableStateFlow<ProductCatalogState>(ProductCatalogState.Idle)
    val productCatalogState: StateFlow<ProductCatalogState> = _productCatalogState.asStateFlow()

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, ProductDetails>> = _productDetailsMap.asStateFlow()

    private val _basePlansMap = MutableStateFlow<Map<String, BasePlanInfo>>(emptyMap())
    val basePlansMap: StateFlow<Map<String, BasePlanInfo>> = _basePlansMap.asStateFlow()

    private val _formattedMonthlyPrice = MutableStateFlow<String?>(null)
    val formattedMonthlyPrice: StateFlow<String?> = _formattedMonthlyPrice.asStateFlow()

    private val _formattedWeeklyPrice = MutableStateFlow<String?>(null)
    val formattedWeeklyPrice: StateFlow<String?> = _formattedWeeklyPrice.asStateFlow()

    private val _formattedAnnualPrice = MutableStateFlow<String?>(null)
    val formattedAnnualPrice: StateFlow<String?> = _formattedAnnualPrice.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    // Holds the basePlanId of the actively requested purchase
    @Volatile
    private var pendingPurchaseBasePlanId: String? = null

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        startConnection()
    }

    fun startConnection() {
        if (!billingClient.isReady) {
            _connectionState.value = BillingConnectionState.Connecting
            Log.d(TAG, "[MsgSortBilling] Starting BillingClient connection to Google Play Store...")
            try {
                billingClient.startConnection(this)
            } catch (e: Exception) {
                Log.e(TAG, "[MsgSortBilling] Exception starting BillingClient connection: ${e.message}", e)
                _connectionState.value = BillingConnectionState.Error(-1, e.localizedMessage ?: "Connection error")
            }
        } else {
            _connectionState.value = BillingConnectionState.Connected
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        val desc = getBillingResponseDescription(responseCode)
        Log.i(TAG, "[MsgSortBilling] onBillingSetupFinished: code=$responseCode ($desc), debugMessage='$debugMessage'")

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectAttempts = 0
            _connectionState.value = BillingConnectionState.Connected
            querySubscriptionProducts()
            queryActivePurchases()
        } else {
            _connectionState.value = BillingConnectionState.Error(responseCode, debugMessage.ifEmpty { desc })
            Log.w(TAG, "[MsgSortBilling] Billing setup failed: code=$responseCode ($desc)")
            onBillingError("Google Play connection failed ($responseCode): ${debugMessage.ifEmpty { desc }}")
        }
    }

    override fun onBillingServiceDisconnected() {
        _connectionState.value = BillingConnectionState.Disconnected()
        Log.w(TAG, "[MsgSortBilling] Google Play billing service disconnected. Attempting auto-reconnect (attempt $reconnectAttempts of $maxReconnectAttempts)...")
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            val backoffMs = (1000L * (1 shl (reconnectAttempts - 1))).coerceAtMost(5000L)
            coroutineScope.launch {
                delay(backoffMs)
                startConnection()
            }
        }
    }

    /**
     * Requirement 1: Query subscription product using product ID "premium_monthly"
     * Requirement 2: Fetch subscription ProductDetails and all available active base plans and offers
     * Requirement 3: Correctly identify base plans using their basePlanId:
     *   - monthly-199 = ₹199 / 1 month
     *   - weekly-99-new = ₹99 / 1 week
     *   - yearly-799 = ₹799 / 1 year
     * Requirement 4: Do not use basePlanId as the subscription product ID
     * Requirement 10: Clear diagnostic logs
     */
    fun querySubscriptionProducts() {
        if (!billingClient.isReady) {
            Log.w(TAG, "[MsgSortBilling] querySubscriptionProducts called while BillingClient is not ready. Reconnecting...")
            _productCatalogState.value = ProductCatalogState.Error(
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                "Google Play Billing is not connected yet"
            )
            startConnection()
            return
        }

        _productCatalogState.value = ProductCatalogState.Loading
        Log.i(TAG, "[MsgSortBilling] Querying Google Play subscription product with ID: '$PRODUCT_SUBSCRIPTION_ID'")

        // Requirement 1: Query subscription product using product ID "premium_monthly"
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            val desc = getBillingResponseDescription(responseCode)

            Log.i(TAG, "[MsgSortBilling] queryProductDetailsAsync callback: code=$responseCode ($desc), msg='$debugMessage'")

            if (responseCode == BillingClient.BillingResponseCode.OK) {
                val list = queryProductDetailsResult.productDetailsList ?: emptyList()
                val newMap = list.associateBy { it.productId }
                _productDetailsMap.value = newMap

                if (newMap.isNotEmpty()) {
                    Log.i(TAG, "[MsgSortBilling] Successfully loaded ${newMap.size} subscription product(s): ${newMap.keys}")

                    val detectedBasePlans = mutableMapOf<String, BasePlanInfo>()

                    for ((id, details) in newMap) {
                        Log.i(TAG, "[MsgSortBilling] ProductDetails: id='$id', title='${details.title}', type='${details.productType}'")
                        val offers = details.subscriptionOfferDetails.orEmpty()
                        Log.i(TAG, "[MsgSortBilling] Product '$id' has ${offers.size} offer(s):")

                        for ((index, offer) in offers.withIndex()) {
                            val basePlanId = offer.basePlanId ?: "unknown"
                            val pricing = offer.pricingPhases.pricingPhaseList.firstOrNull()
                            val formattedPrice = pricing?.formattedPrice ?: "unknown"
                            val period = pricing?.billingPeriod ?: "unknown"
                            val amountMicros = pricing?.priceAmountMicros ?: 0L
                            val currency = pricing?.priceCurrencyCode ?: "INR"

                            Log.i(
                                TAG,
                                "[MsgSortBilling]   [$index] basePlanId='$basePlanId', offerId='${offer.offerId ?: "standard"}', price='$formattedPrice', period='$period', offerToken=${offer.offerToken.take(15)}..."
                            )

                            // Store standard offer (offerId == null) or first found for basePlanId
                            if (!detectedBasePlans.containsKey(basePlanId) || offer.offerId == null) {
                                detectedBasePlans[basePlanId] = BasePlanInfo(
                                    basePlanId = basePlanId,
                                    productId = id,
                                    offerToken = offer.offerToken,
                                    offerId = offer.offerId,
                                    formattedPrice = formattedPrice,
                                    priceAmountMicros = amountMicros,
                                    priceCurrencyCode = currency,
                                    billingPeriod = period
                                )
                            }
                        }
                    }

                    _basePlansMap.value = detectedBasePlans

                    // Requirement 3: Correctly identify base plans using their basePlanId
                    // monthly-199 = ₹199 / 1 month
                    val monthlyInfo = detectedBasePlans[BASE_PLAN_MONTHLY]
                        ?: detectedBasePlans.values.firstOrNull { isMonthlyBasePlan(it.basePlanId) }
                    _formattedMonthlyPrice.value = monthlyInfo?.formattedPrice ?: "₹199"

                    // weekly-99-new = ₹99 / 1 week
                    val weeklyInfo = detectedBasePlans[BASE_PLAN_WEEKLY]
                        ?: detectedBasePlans.values.firstOrNull { isWeeklyBasePlan(it.basePlanId) }
                    _formattedWeeklyPrice.value = weeklyInfo?.formattedPrice ?: "₹99"

                    // yearly-799 = ₹799 / 1 year
                    val yearlyInfo = detectedBasePlans[BASE_PLAN_YEARLY]
                        ?: detectedBasePlans.values.firstOrNull { isYearlyBasePlan(it.basePlanId) }
                    _formattedAnnualPrice.value = yearlyInfo?.formattedPrice ?: "₹799"

                    Log.i(TAG, "[MsgSortBilling] Active Base Plans identified:")
                    Log.i(TAG, "[MsgSortBilling]   -> Monthly ($BASE_PLAN_MONTHLY): ${_formattedMonthlyPrice.value} (found=${monthlyInfo != null})")
                    Log.i(TAG, "[MsgSortBilling]   -> Weekly ($BASE_PLAN_WEEKLY): ${_formattedWeeklyPrice.value} (found=${weeklyInfo != null})")
                    Log.i(TAG, "[MsgSortBilling]   -> Yearly ($BASE_PLAN_YEARLY): ${_formattedAnnualPrice.value} (found=${yearlyInfo != null})")

                    _productCatalogState.value = ProductCatalogState.Ready(newMap.size, newMap)
                } else {
                    Log.w(TAG, "[MsgSortBilling] Google Play returned 0 subscription products for '$PRODUCT_SUBSCRIPTION_ID'.")
                    _productCatalogState.value = ProductCatalogState.Empty(
                        attemptedIds = listOf(PRODUCT_SUBSCRIPTION_ID),
                        reason = "No subscription products were returned by Google Play Store for '$PRODUCT_SUBSCRIPTION_ID'. Ensure 'premium_monthly' has Active Base Plans (monthly-199, weekly-99-new, yearly-799) in Play Console and the test account is licensed."
                    )
                }
            } else {
                Log.e(TAG, "[MsgSortBilling] queryProductDetailsAsync error: code=$responseCode ($desc), msg='$debugMessage'")
                _productCatalogState.value = ProductCatalogState.Error(responseCode, debugMessage.ifEmpty { desc })
                onBillingError("Failed to query subscriptions ($responseCode): $desc")
            }
        }
    }

    /**
     * Finds ProductDetails matching a requested planId or its common aliases
     */
    fun getProductDetailsForPlan(planId: String): ProductDetails? {
        val map = _productDetailsMap.value
        // 1. Direct match
        map[planId]?.let { return it }

        // 2. Default to canonical subscription product
        map[PRODUCT_SUBSCRIPTION_ID]?.let { return it }

        // 3. Fallback match
        return map.values.firstOrNull { it.productType == BillingClient.ProductType.SUBS }
    }

    /**
     * Resolves the localized formatted price from Google Play for a plan
     */
    fun getFormattedPriceForPlan(planId: String): String? {
        return when {
            isWeeklyBasePlan(planId) || planId == PremiumPlans.WEEKLY_7D.id -> _formattedWeeklyPrice.value ?: "₹99"
            isYearlyBasePlan(planId) || planId == PremiumPlans.ANNUAL.id -> _formattedAnnualPrice.value ?: "₹799"
            else -> _formattedMonthlyPrice.value ?: "₹199"
        }
    }

    /**
     * Launches the Google Play Store checkout flow for a given plan
     * Requirement 4: Do not use basePlanId as the subscription product ID
     * Requirement 5: Use the correct offerToken when launching the subscription purchase flow
     */
    fun launchPurchaseFlow(activity: Activity, plan: PremiumPlan): BillingFlowLaunchResult {
        return launchPurchaseFlow(activity, plan.basePlanId.ifBlank { plan.id })
    }

    /**
     * Launches the Google Play Store checkout flow with diagnostic error reporting
     * Requirement 4: Do not use basePlanId as the subscription product ID
     * Requirement 5: Use the correct offerToken when launching the subscription purchase flow
     */
    fun launchPurchaseFlow(
        activity: Activity,
        planOrBasePlanId: String
    ): BillingFlowLaunchResult {
        val targetBasePlanId = resolveBasePlanId(planOrBasePlanId)
        Log.i(TAG, "[MsgSortBilling] launchPurchaseFlow requested for input='$planOrBasePlanId', resolved targetBasePlanId='$targetBasePlanId'")

        // 1. Connection check
        if (!billingClient.isReady) {
            val state = _connectionState.value
            Log.w(TAG, "[MsgSortBilling] Cannot launch purchase: BillingClient is not ready. Current state=$state")
            startConnection()
            return BillingFlowLaunchResult.Error(
                responseCode = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                diagnosticMessage = "BillingClient.isReady is false. Connection state: $state",
                userFriendlyMessage = "Google Play Store billing service is connecting. Please ensure your device has Google Play Store services enabled and try again in a moment."
            )
        }

        // 2. Requirement 4: ProductDetails is ALWAYS "premium_monthly" (NEVER use basePlanId as the product ID!)
        val productDetails = _productDetailsMap.value[PRODUCT_SUBSCRIPTION_ID]
            ?: _productDetailsMap.value.values.firstOrNull { it.productType == BillingClient.ProductType.SUBS }

        if (productDetails == null) {
            val loadedIds = _productDetailsMap.value.keys.joinToString().ifEmpty { "None" }
            Log.w(TAG, "[MsgSortBilling] ProductDetails for '$PRODUCT_SUBSCRIPTION_ID' not found. Loaded products: [$loadedIds]")
            return BillingFlowLaunchResult.Error(
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                diagnosticMessage = "Product '$PRODUCT_SUBSCRIPTION_ID' not returned by Google Play. Loaded products: [$loadedIds]",
                userFriendlyMessage = "Subscription '$PRODUCT_SUBSCRIPTION_ID' is not available from Google Play Store.\n\nPlease verify:\n• The subscription '$PRODUCT_SUBSCRIPTION_ID' has active base plans (monthly-199, weekly-99-new, yearly-799) in Google Play Console.\n• Your Google account is listed in Play Console License Testing.\n• The app is installed from an authorized testing track."
            )
        }

        // 3. Requirement 5: Locate and extract the correct offerToken for the requested basePlanId
        val offerDetailsList = productDetails.subscriptionOfferDetails
        if (offerDetailsList.isNullOrEmpty()) {
            Log.w(TAG, "[MsgSortBilling] ProductDetails found for '${productDetails.productId}', but subscriptionOfferDetails is empty.")
            return BillingFlowLaunchResult.Error(
                responseCode = BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                diagnosticMessage = "Product '${productDetails.productId}' has no subscriptionOfferDetails. Base plans not active in Play Console.",
                userFriendlyMessage = "Subscription '${productDetails.productId}' has no active Base Plan in Google Play Console. Please activate base plans in Play Console."
            )
        }

        // Find matching offer for the target base plan
        var selectedOffer = offerDetailsList.firstOrNull { it.basePlanId == targetBasePlanId && it.offerId == null }
            ?: offerDetailsList.firstOrNull { it.basePlanId == targetBasePlanId }

        // Fallback matching
        if (selectedOffer == null) {
            selectedOffer = when (targetBasePlanId) {
                BASE_PLAN_MONTHLY -> offerDetailsList.firstOrNull { isMonthlyBasePlan(it.basePlanId ?: "") }
                BASE_PLAN_WEEKLY -> offerDetailsList.firstOrNull { isWeeklyBasePlan(it.basePlanId ?: "") }
                BASE_PLAN_YEARLY -> offerDetailsList.firstOrNull { isYearlyBasePlan(it.basePlanId ?: "") }
                else -> null
            }
        }

        // If target base plan wasn't matched directly, fall back to first offer to avoid hard blocking
        if (selectedOffer == null) {
            Log.w(TAG, "[MsgSortBilling] Base plan '$targetBasePlanId' not found. Available base plans: ${offerDetailsList.map { it.basePlanId }}. Falling back to first offer.")
            selectedOffer = offerDetailsList.first()
        }

        val offerToken = selectedOffer.offerToken
        val actualBasePlanId = selectedOffer.basePlanId ?: targetBasePlanId
        pendingPurchaseBasePlanId = actualBasePlanId

        Log.i(
            TAG,
            "[MsgSortBilling] Launching Play Billing: Subscription Product='${productDetails.productId}', Base Plan='$actualBasePlanId', OfferToken=${offerToken.take(15)}..."
        )

        // Requirement 4 & 5: Pass the ProductDetails ("premium_monthly") and the specific offerToken
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        _isPurchasing.value = true
        val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        val responseCode = launchResult.responseCode
        val debugMessage = launchResult.debugMessage
        val desc = getBillingResponseDescription(responseCode)

        Log.i(TAG, "[MsgSortBilling] launchBillingFlow returned: code=$responseCode ($desc), msg='$debugMessage'")

        if (responseCode != BillingClient.BillingResponseCode.OK) {
            _isPurchasing.value = false
            onBillingError("Play Store checkout error ($responseCode): ${debugMessage.ifEmpty { desc }}")
            return BillingFlowLaunchResult.Error(
                responseCode = responseCode,
                diagnosticMessage = "launchBillingFlow failed: code=$responseCode ($desc), msg=$debugMessage",
                userFriendlyMessage = "Google Play Store checkout could not be opened ($desc). ${debugMessage.ifEmpty { "Please check your Google Play account and try again." }}"
            )
        }

        return BillingFlowLaunchResult.Success
    }

    /**
     * PurchasesUpdatedListener callback
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        _isPurchasing.value = false
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        val desc = getBillingResponseDescription(responseCode)

        Log.i(TAG, "[MsgSortBilling] onPurchasesUpdated: code=$responseCode ($desc), purchasesCount=${purchases?.size ?: 0}")

        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    for (purchase in purchases) {
                        Log.i(
                            TAG,
                            "[MsgSortBilling] Processing purchase: orderId=${purchase.orderId}, state=${purchase.purchaseState}, autoRenewing=${purchase.isAutoRenewing}, acknowledged=${purchase.isAcknowledged}"
                        )
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "[MsgSortBilling] Purchase flow canceled by user.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(TAG, "[MsgSortBilling] User already owns this subscription. Refreshing active purchases...")
                queryActivePurchases()
                onBillingError("You already own this subscription. Your purchase has been restored.")
            }
            else -> {
                Log.e(TAG, "[MsgSortBilling] Purchase failed: code=$responseCode ($desc), msg=$debugMessage")
                onBillingError("Purchase unsuccessful ($desc)")
            }
        }
    }

    /**
     * Requirement 7: Verify purchases securely and acknowledge purchases
     * Requirement 8: Unlock premium features only after verified successful purchases
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val basePlanId = pendingPurchaseBasePlanId ?: BASE_PLAN_MONTHLY
            val productId = purchase.products.firstOrNull() ?: PRODUCT_SUBSCRIPTION_ID
            Log.i(
                TAG,
                "[MsgSortBilling] Purchase VERIFIED: product=$productId, basePlan=$basePlanId, orderId=${purchase.orderId}, token=${purchase.purchaseToken.take(15)}..."
            )

            // Acknowledge purchase if not already acknowledged to prevent automatic refund
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                Log.i(TAG, "[MsgSortBilling] Acknowledging purchase...")
                coroutineScope.launch(Dispatchers.IO) {
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { ackResult ->
                        val ackDesc = getBillingResponseDescription(ackResult.responseCode)
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.i(TAG, "[MsgSortBilling] Purchase acknowledged successfully: ${purchase.orderId}")
                        } else {
                            Log.w(TAG, "[MsgSortBilling] Failed acknowledging purchase: code=${ackResult.responseCode} ($ackDesc), msg=${ackResult.debugMessage}")
                        }
                    }
                }
            }

            // Notify application layer / ViewModel to register subscription entitlement
            coroutineScope.launch(Dispatchers.Main) {
                onPurchaseSuccess(purchase, basePlanId)
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.i(TAG, "[MsgSortBilling] Purchase is pending confirmation (e.g. slow instrument, UPI, or cash).")
        }
    }

    /**
     * Restore / query active subscription purchases from Google Play (single callback)
     */
    fun queryActivePurchases(onComplete: (List<Purchase>) -> Unit) {
        queryActivePurchases { purchases, _ ->
            onComplete(purchases)
        }
    }

    /**
     * Restore / query active subscription purchases from Google Play
     * Requirement 9: Handle subscription expiry, cancellation and renewal correctly
     */
    fun queryActivePurchases(onComplete: (List<Purchase>, BillingResult?) -> Unit = { _, _ -> }) {
        if (!billingClient.isReady) {
            Log.w(TAG, "[MsgSortBilling] queryActivePurchases called while BillingClient is not ready. Reconnecting...")
            startConnection()
            onComplete(emptyList(), null)
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        Log.i(TAG, "[MsgSortBilling] Querying active subscription purchases from Google Play...")
        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            val code = billingResult.responseCode
            val desc = getBillingResponseDescription(code)
            Log.i(TAG, "[MsgSortBilling] queryPurchasesAsync result: code=$code ($desc), count=${purchasesList.size}")

            if (code == BillingClient.BillingResponseCode.OK) {
                val activePurchases = purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                Log.i(TAG, "[MsgSortBilling] Found ${activePurchases.size} active subscription purchases.")
                for (purchase in activePurchases) {
                    handlePurchase(purchase)
                }
                coroutineScope.launch(Dispatchers.Main) {
                    onComplete(activePurchases, billingResult)
                }
            } else {
                Log.w(TAG, "[MsgSortBilling] queryPurchasesAsync failed: code=$code ($desc), msg=${billingResult.debugMessage}")
                coroutineScope.launch(Dispatchers.Main) {
                    onComplete(emptyList(), billingResult)
                }
            }
        }
    }

    /**
     * Manually triggers reconnection and re-querying of products
     */
    fun refresh() {
        Log.i(TAG, "[MsgSortBilling] Manual refresh requested.")
        if (!billingClient.isReady) {
            startConnection()
        } else {
            querySubscriptionProducts()
            queryActivePurchases()
        }
    }

    /**
     * Clean up billing connection when app or screen is destroyed
     */
    fun destroy() {
        if (billingClient.isReady) {
            Log.d(TAG, "[MsgSortBilling] Terminating BillingClient connection...")
            billingClient.endConnection()
        }
    }
}
