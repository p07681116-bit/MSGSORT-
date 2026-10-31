package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {
    // Exclude confusing characters like 0, O, 1, I
    private const val ALPHANUMERIC = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val secureRandom = SecureRandom()

    fun generateSortCode(): String {
        val length = 6
        val sb = StringBuilder(length + 3)
        sb.append("MS-")
        for (i in 0 until length) {
            val randomIndex = secureRandom.nextInt(ALPHANUMERIC.length)
            sb.append(ALPHANUMERIC[randomIndex])
        }
        return sb.toString()
    }

    fun normalizeCode(input: String): String {
        val trimmed = input.trim().uppercase()
        // If user entered without "MS-", prepend it if it's 6 chars, or leave as is if already has prefix
        return if (!trimmed.startsWith("MS-") && trimmed.length == 6) {
            "MS-$trimmed"
        } else {
            trimmed
        }
    }

    fun isValidSortCodeFormat(code: String): Boolean {
        val normalized = normalizeCode(code)
        val regex = Regex("^MS-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4,10}$")
        return regex.matches(normalized) || code.trim().length in 4..32
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
