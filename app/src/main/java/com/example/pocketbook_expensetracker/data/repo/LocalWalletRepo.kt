package com.example.pocketbook_expensetracker.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import com.example.pocketbook_expensetracker.data.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import com.example.pocketbook_expensetracker.util.Money

class LocalWalletRepo private constructor(
    context: Context
) : WalletRepo {

    private val helper = PocketBookDatabase(
        context.applicationContext
    )

    private val wallets = MutableStateFlow(readAll())

    override fun getWallets(): Flow<List<Wallet>> {
        return wallets.asStateFlow()
    }

    override suspend fun getWalletById(
        id: String
    ): Wallet? = withContext(Dispatchers.IO) {

        helper.readableDatabase.query(
            "wallets",
            null,
            "id=?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toWallet()
            } else {
                null
            }
        }
    }

    override suspend fun addWallet(
        wallet: Wallet
    ) = withContext(Dispatchers.IO) {

        validate(wallet)

        synchronized(helper) {
            val newWallet = wallet.copy(
                id = UUID.randomUUID().toString()
            )

            helper.writableDatabase.insertOrThrow(
                "wallets",
                null,
                toValues(newWallet)
            )

            wallets.value = readAll()
        }

        Unit
    }

    override suspend fun updateWallet(
        wallet: Wallet
    ) = withContext(Dispatchers.IO) {

        validate(wallet)

        require(wallet.id.isNotBlank()) {
            "Missing wallet ID"
        }

        synchronized(helper) {
            val updated = helper.writableDatabase.update(
                "wallets",
                toValues(wallet),
                "id=?",
                arrayOf(wallet.id)
            )

            require(updated == 1) {
                "Wallet not found"
            }

            wallets.value = readAll()
        }

        Unit
    }

    private fun validate(wallet: Wallet) {
        require(wallet.name.isNotBlank()) {
            "Please enter a wallet name"
        }

        Money.fromDouble(wallet.openingBalance)

        require(wallet.kind in listOf(
            "Cash",
            "Bank",
            "eWallet"
        )) {
            "Invalid wallet type"
        }
    }

    private fun toValues(wallet: Wallet) =
        ContentValues().apply {
            put("id", wallet.id)
            put("name", wallet.name.trim())
            put("kind", wallet.kind)
            put(
                "opening_balance",
                Money.toDecimal(Money.fromDouble(wallet.openingBalance)).toDouble()
            )
            put("opening_balance_sen", Money.fromDouble(wallet.openingBalance))
        }

    private fun readAll(): List<Wallet> {
        return helper.readableDatabase.query(
            "wallets",
            null,
            null,
            null,
            null,
            null,
            "name ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toWallet())
                }
            }
        }
    }

    private fun Cursor.toWallet(): Wallet {
        return Wallet(
            id = getString(
                getColumnIndexOrThrow("id")
            ),
            name = getString(
                getColumnIndexOrThrow("name")
            ),
            kind = getString(
                getColumnIndexOrThrow("kind")
            ),
            openingBalance = Money.toDecimal(getLong(
                getColumnIndexOrThrow("opening_balance_sen")
            )).toDouble()
        )
    }

    companion object {
        @Volatile
        private var instance: LocalWalletRepo? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LocalWalletRepo(
                            context.applicationContext
                        )
                    }
                }
            }
        }

        fun getInstance(): LocalWalletRepo {
            return checkNotNull(instance) {
                "Initialize LocalWalletRepo first"
            }
        }
    }



    fun refresh() {
        wallets.value = readAll()
    }

}
