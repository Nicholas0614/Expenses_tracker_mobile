package com.example.pocketbook_expensetracker.util

object TransactionValidator {
    fun validateAmount(amount: String): String? {
        val value = amount.toDoubleOrNull()
        return when {
            value == null || !value.isFinite() -> "Please enter a valid amount"
            value <= 0 -> "Amount must be greater than zero"
            else -> null
        }
    }
}
