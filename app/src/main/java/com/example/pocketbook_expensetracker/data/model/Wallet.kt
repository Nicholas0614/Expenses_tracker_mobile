package com.example.pocketbook_expensetracker.data.model

data class Wallet(
    val id: String = "",
    val name: String = "",
    val kind: String = "Cash",
    val openingBalance: Double = 0.0
)
