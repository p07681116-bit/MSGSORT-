package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class UserSubscription(
    @DocumentId val id: String = "",
    val userId: String = "",
    val status: String = "inactive", // "active", "cancelled", "expired", "past_due", "inactive", "refunded"
    val planId: String = "", // "premium_monthly", "premium_annual"
    val planName: String = "", // "Monthly Premium", "Annual Premium"
    val priceInr: Int = 0, // 199 or 799
    val billingPeriod: String = "monthly", // "monthly" or "annual"
    val startDate: Timestamp? = null,
    val expiryDate: Timestamp? = null,
    val provider: String = "google_play", // "google_play" or "razorpay"
    val transactionId: String = "",
    val autoRenewing: Boolean = true,
    val isTest: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isPremiumActive(now: Date = Date()): Boolean {
        if (status != "active" && status != "cancelled") return false
        val exp = expiryDate?.toDate() ?: return false
        return exp.after(now)
    }

    fun formattedExpiryDate(): String {
        val exp = expiryDate?.toDate() ?: return "N/A"
        val format = java.text.SimpleDateFormat("MMM dd, yyyy • h:mm a", java.util.Locale.getDefault())
        return format.format(exp)
    }

    fun formattedRemainingTime(now: Date = Date()): String {
        val exp = expiryDate?.toDate() ?: return "Expired"
        val diffMs = exp.time - now.time
        if (diffMs <= 0) return "Expired"
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs)
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diffMs) % 24
        val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60
        return when {
            days > 0 -> "$days days, $hours hrs left"
            hours > 0 -> "$hours hrs, $mins mins left"
            else -> "$mins mins left"
        }
    }
}

data class PremiumPlan(
    val id: String,
    val name: String,
    val priceInr: Int,
    val period: String,
    val description: String,
    val savingsBadge: String? = null,
    val basePlanId: String = "monthly-199",
    val productId: String = "premium_monthly"
)

object PremiumPlans {
    // Canonical Google Play subscription product ID
    const val SUBSCRIPTION_PRODUCT_ID = "premium_monthly"

    // Base Plan IDs inside 'premium_monthly'
    const val BASE_PLAN_MONTHLY = "monthly-199"
    const val BASE_PLAN_WEEKLY = "weekly-99-new"
    const val BASE_PLAN_YEARLY = "yearly-799"

    val WEEKLY_7D = PremiumPlan(
        id = "premium_weekly_7d",
        name = "7-Day Premium Pass",
        priceInr = 99,
        period = "7 days",
        description = "₹99 for 1 week. Unlocks all premium MsgSort features.",
        savingsBadge = "⚡ ₹99 FOR 7 DAYS",
        basePlanId = BASE_PLAN_WEEKLY,
        productId = SUBSCRIPTION_PRODUCT_ID
    )

    val MONTHLY = PremiumPlan(
        id = "premium_monthly",
        name = "Monthly Premium",
        priceInr = 199,
        period = "month",
        description = "Billed monthly at ₹199/month. Cancel anytime.",
        basePlanId = BASE_PLAN_MONTHLY,
        productId = SUBSCRIPTION_PRODUCT_ID
    )

    val ANNUAL = PremiumPlan(
        id = "premium_annual",
        name = "Annual Premium",
        priceInr = 799,
        period = "year",
        description = "Billed annually at ₹799/year (~₹66/month).",
        savingsBadge = "Save 66%",
        basePlanId = BASE_PLAN_YEARLY,
        productId = SUBSCRIPTION_PRODUCT_ID
    )

    val ALL = listOf(WEEKLY_7D, MONTHLY, ANNUAL)
}
