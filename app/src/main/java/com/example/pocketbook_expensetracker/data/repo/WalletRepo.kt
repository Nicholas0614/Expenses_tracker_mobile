package com.example.pocketbook_expensetracker.data.repo

import com.example.pocketbook_expensetracker.data.model.Wallet
import kotlinx.coroutines.flow.Flow

interface WalletRepo {

    fun getWallets(): Flow<List<Wallet>>

    suspend fun getWalletById(id: String): Wallet?

    suspend fun addWallet(wallet: Wallet)

    suspend fun updateWallet(wallet: Wallet)
}