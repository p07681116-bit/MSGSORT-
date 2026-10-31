package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector

object SupportUtils {
    const val OFFICIAL_SUPPORT_EMAIL = "sortmsg@gmail.com"
    const val DEFAULT_SUPPORT_SUBJECT = "MsgSort Support Request"

    data class SupportCategory(
        val id: String,
        val title: String,
        val description: String,
        val icon: ImageVector,
        val emailSubject: String,
        val commonTips: List<String>
    )

    val supportCategories = listOf(
        SupportCategory(
            id = "login_issues",
            title = "Login Issues",
            description = "Google Sign-In, account credentials, or session troubles",
            icon = Icons.Outlined.LockReset,
            emailSubject = "MsgSort Support - Login Issues",
            commonTips = listOf(
                "Ensure your Google Play Services are up-to-date.",
                "Verify an active internet connection before signing in.",
                "Try signing out and back in if your session is interrupted."
            )
        ),
        SupportCategory(
            id = "file_sharing",
            title = "File Sharing",
            description = "File upload limits, supported formats, or download issues",
            icon = Icons.Outlined.FolderShared,
            emailSubject = "MsgSort Support - File Sharing",
            commonTips = listOf(
                "Free users have a 25 MB max file limit per upload.",
                "Supported formats include PDF, JPG, PNG, WEBP, DOCX, and TXT.",
                "Recipients must use the exact SortCode before it reaches expiration."
            )
        ),
        SupportCategory(
            id = "sortcode_problems",
            title = "SortCode Problems",
            description = "Code not found, expired codes, or early revocation",
            icon = Icons.Outlined.VpnKey,
            emailSubject = "MsgSort Support - SortCode Problems",
            commonTips = listOf(
                "SortCodes are case-insensitive 6-8 alphanumeric codes (e.g., MS-7X9K2).",
                "Expired or revoked codes are permanently purged and cannot be restored.",
                "Double-check with the creator if the password is required."
            )
        ),
        SupportCategory(
            id = "payment_issues",
            title = "Payment Issues",
            description = "Premium subscription, billing verification, or renewal questions",
            icon = Icons.Outlined.Payment,
            emailSubject = "MsgSort Support - Payment Issues",
            commonTips = listOf(
                "Purchases are managed through Google Play Billing.",
                "Use 'Restore Purchases' in Premium to re-sync active Google Play orders.",
                "Include your Google Play Order ID (GPA.XXXX-XXXX-XXXX-XXXXX) when emailing support."
            )
        ),
        SupportCategory(
            id = "privacy",
            title = "Privacy & Data Requests",
            description = "Personal data deletion, privacy rights, and security policies",
            icon = Icons.Outlined.PrivacyTip,
            emailSubject = "MsgSort Privacy & Personal Data Request",
            commonTips = listOf(
                "SortCode payloads are encrypted in transit and purged automatically upon expiration.",
                "You can delete your account and all owned records at any time from Profile.",
                "Contact our privacy team for certified GDPR/DPDP data inquiries."
            )
        ),
        SupportCategory(
            id = "general_questions",
            title = "General Questions",
            description = "Product feedback, feature suggestions, or general inquiries",
            icon = Icons.Outlined.HelpOutline,
            emailSubject = "MsgSort Support - General Questions",
            commonTips = listOf(
                "MsgSort works seamlessly for sharing text, documents, and images quickly.",
                "Check the FAQ section for immediate answers to common questions.",
                "We appreciate user feedback and feature suggestions!"
            )
        )
    )

    fun openSupportEmail(
        context: Context,
        subject: String = DEFAULT_SUPPORT_SUBJECT,
        body: String = ""
    ) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$OFFICIAL_SUPPORT_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, body)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Email MsgSort Support"))
        } catch (e: Exception) {
            // In case no email client is available
            copySupportEmail(context)
            Toast.makeText(
                context,
                "No email app found. Support email copied: $OFFICIAL_SUPPORT_EMAIL",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun copySupportEmail(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("MsgSort Support Email", OFFICIAL_SUPPORT_EMAIL)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "Support email copied to clipboard ($OFFICIAL_SUPPORT_EMAIL)",
            Toast.LENGTH_SHORT
        ).show()
    }
}
