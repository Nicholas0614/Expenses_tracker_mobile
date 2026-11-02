
package com.example.pocketbook_expensetracker.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import com.example.pocketbook_expensetracker.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.LocalDate
import java.util.UUID
import com.example.pocketbook_expensetracker.util.Money

class LocalTransactionRepo private constructor(
    context: Context
) : TransactionRepo {

    private val helper = PocketBookDatabase(context)
    private val updates = MutableStateFlow(readAll())

    override fun getTransactions(): Flow<List<Transaction>> =
        updates.asStateFlow()

    override suspend fun getTransactionById(
        id: String
    ): Transaction? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "transactions", null, "id=?",
            arrayOf(id), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toTransaction()
            } else null
        }
    }

    override suspend fun addTransaction(
        transaction: Transaction
    ) = withContext(Dispatchers.IO) {
        validate(transaction)

        synchronized(helper) {
            validateWallet(transaction.walletId)

            val item = transaction.copy(
                id = UUID.randomUUID().toString()
            )

            helper.writableDatabase.insertOrThrow(
                "transactions",
                null,
                values(item)
            )

            updates.value = readAll()
        }
        Unit
    }

    override suspend fun updateTransaction(
        transaction: Transaction
    ) = withContext(Dispatchers.IO) {
        validate(transaction)

        require(transaction.id.isNotBlank()) {
            "Missing transaction ID"
        }

        synchronized(helper) {
            validateWallet(transaction.walletId)

            val changed = helper.writableDatabase.update(
                "transactions",
                values(transaction),
                "id=?",
                arrayOf(transaction.id)
            )

            require(changed == 1) {
                "Transaction not found"
            }

            updates.value = readAll()
        }
        Unit
    }

    override suspend fun deleteTransaction(
        id: String
    ) = withContext(Dispatchers.IO) {
        synchronized(helper) {
            helper.writableDatabase.delete(
                "transactions",
                "id=?",
                arrayOf(id)
            )

            updates.value = readAll()
        }
        Unit
    }

    private fun validate(item: Transaction) {
        require(Money.fromDouble(item.amount) > 0L) {
            "Amount must be greater than zero"
        }

        require(item.type in listOf("Income", "Expense")) {
            "Invalid transaction type"
        }

        require(item.name.isNotBlank()) {
            "Please enter a description"
        }

        require(
            runCatching {
                LocalDate.parse(item.date)
            }.isSuccess
        ) {
            "Invalid date"
        }

        require(runCatching { LocalTime.parse(item.time) }.isSuccess) {
            "Invalid transaction time"
        }

        require(
            item.walletId == null ||
                    item.walletId.isNotBlank()
        ) {
            "Invalid wallet ID"
        }
    }

    private fun validateWallet(id: String?) {
        // Null is permitted for transactions imported from V1.
        if (id == null) return

        helper.readableDatabase.query(
            "wallets",
            arrayOf("id"),
            "id=?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            require(cursor.moveToFirst()) {
                "Selected wallet no longer exists"
            }
        }
    }

    private fun values(item: Transaction) =
        ContentValues().apply {
            put("id", item.id)
            put("name", item.name)
            put("category", item.category)
            put("amount", Money.toDecimal(Money.fromDouble(item.amount)).toDouble())
            put("amount_sen", Money.fromDouble(item.amount))
            put("type", item.type)
            put("payment_method", item.paymentMethod)
            put("date", item.date)
            put("time", item.time)
            put("icon", item.icon)
            put("note", item.note)

            if (item.walletId == null) {
                putNull("wallet_id")
            } else {
                put("wallet_id", item.walletId)
            }
        }

    private fun readAll(): List<Transaction> {
        return helper.readableDatabase.query(
            "transactions",
            null,
            null,
            null,
            null,
            null,
            "date DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toTransaction())
                }
            }
        }
    }

    private fun Cursor.toTransaction(): Transaction =
        Transaction(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            category = getString(
                getColumnIndexOrThrow("category")
            ),
            amount = Money.toDecimal(getLong(
                getColumnIndexOrThrow("amount_sen")
            )).toDouble(),
            type = getString(getColumnIndexOrThrow("type")),
            paymentMethod = getString(
                getColumnIndexOrThrow("payment_method")
            ),
            date = getString(getColumnIndexOrThrow("date")),
            time = getString(getColumnIndexOrThrow("time")),
            icon = getString(getColumnIndexOrThrow("icon")),
            note = getString(getColumnIndexOrThrow("note")),
            walletId = getString(
                getColumnIndexOrThrow("wallet_id")
            )
        )

    companion object {
        @Volatile
        private var instance: LocalTransactionRepo? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LocalTransactionRepo(
                            context.applicationContext
                        )
                    }
                }
            }
        }

        fun getInstance(): LocalTransactionRepo =
            checkNotNull(instance) {
                "Initialize LocalTransactionRepo in MainActivity"
            }
    }

    fun refresh() {
        updates.value = readAll()
    }


}
