
package com.example.pocketbook_expensetracker.data.model

data class WalletTransfer(
    val id: String = "",
    val fromWalletId: String = "",
    val toWalletId: String = "",
    val amountSen: Long = 0L,
    val date: String = "",
    val note: String = ""
)
