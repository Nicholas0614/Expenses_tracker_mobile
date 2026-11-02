package com.example.pocketbook_expensetracker.data.repo

import com.example.pocketbook_expensetracker.data.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepo {
    fun getTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: String): Transaction?
    suspend fun addTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: String)

    companion object {
        fun getInstance(): TransactionRepo = LocalTransactionRepo.getInstance()
    }
}
