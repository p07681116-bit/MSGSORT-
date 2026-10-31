package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.concurrent.TimeUnit

@IgnoreExtraProperties
data class SortCodeItem(
    @DocumentId val id: String = "",
    val code: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val contentType: String = "text", // "text", "document", "code", "file", "image"
    val textContent: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val fileMimeType: String = "",
    val fileBase64: String = "",
    val totalChunks: Int = 0,
    val storagePath: String = "",
    val imageUrl: String = "",
    val fileUrl: String = "",
    val thumbnailBase64: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val status: String = "active", // "active", "expired", "revoked"
    val passwordProtected: Boolean = false,
    val passwordHash: String = "",
    val downloadAllowed: Boolean = true,
    val description: String = "",
    val viewsCount: Long = 0L
) {
    fun isExpired(now: Date = Date()): Boolean {
        val exp = expiresAt?.toDate() ?: return false
        return exp.before(now)
    }

    fun isRevoked(): Boolean {
        return status == "revoked"
    }

    fun isAccessible(now: Date = Date()): Boolean {
        return status == "active" && !isExpired(now)
    }

    fun getComputedStatus(now: Date = Date()): String {
        return when {
            status == "revoked" -> "revoked"
            isExpired(now) -> "expired"
            else -> "active"
        }
    }

    fun formattedRemainingTime(now: Date = Date()): String {
        if (status == "revoked") return "Revoked"
        val exp = expiresAt?.toDate() ?: return "Unknown"
        val diffMillis = exp.time - now.time
        if (diffMillis <= 0) return "Expired"

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""} left"
            hours > 0 -> "$hours hr ${minutes % 60}m left"
            minutes > 0 -> "$minutes min left"
            else -> "$seconds sec left"
        }
    }

    /**
     * Clear and human-readable countdown string:
     * e.g., "Expires in 5 minutes", "Expires in 10 minutes", "Expires in 45 seconds"
     */
    fun remainingTimeDescription(now: Date = Date()): String {
        if (status == "revoked") return "Revoked"
        val exp = expiresAt?.toDate() ?: return "Unknown"
        val diffMillis = exp.time - now.time
        if (diffMillis <= 0) return "Expired"

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            days > 0 -> "Expires in $days day${if (days > 1) "s" else ""}"
            hours > 0 -> {
                val remMins = minutes % 60
                if (remMins > 0) "Expires in $hours hr $remMins min" else "Expires in $hours hour${if (hours > 1) "s" else ""}"
            }
            minutes > 0 -> {
                val remSecs = seconds % 60
                if (minutes <= 2 && remSecs > 0) {
                    "Expires in $minutes min $remSecs sec"
                } else {
                    "Expires in $minutes minute${if (minutes > 1) "s" else ""}"
                }
            }
            else -> "Expires in $seconds second${if (seconds > 1) "s" else ""}"
        }
    }

    fun isPdf(): Boolean = contentType == "application/pdf" ||
            fileMimeType.equals("application/pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true)
}
