
package com.example.pocketbook_expensetracker.data.model

data class Transaction(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val type: String = "Expense",
    val paymentMethod: String = "",
    val date: String = "",
    val icon: String = "",
    val note: String = "",
    val walletId: String? = null,
    val time: String = "12:00"
)
