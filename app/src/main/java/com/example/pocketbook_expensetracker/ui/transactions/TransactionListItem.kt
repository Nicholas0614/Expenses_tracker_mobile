package com.example.pocketbook_expensetracker.ui.transactions

import com.example.pocketbook_expensetracker.data.model.Transaction

sealed class TransactionListItem {
    data class Header(val date: String, val total: Double) : TransactionListItem()
    data class Entry(val transaction: Transaction) : TransactionListItem()
}
