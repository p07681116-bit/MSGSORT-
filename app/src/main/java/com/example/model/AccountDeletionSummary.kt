package com.example.model

/**
 * Summary of records removed during an account deletion operation.
 */
data class AccountDeletionSummary(
    val sortCodesDeleted: Int = 0,
    val storageFilesDeleted: Int = 0,
    val profileDeleted: Boolean = false,
    val message: String = ""
)
