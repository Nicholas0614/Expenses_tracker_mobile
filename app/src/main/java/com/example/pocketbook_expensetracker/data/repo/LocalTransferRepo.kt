
package com.example.pocketbook_expensetracker.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import com.example.pocketbook_expensetracker.data.model.WalletTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class LocalTransferRepo private constructor(
    context: Context
) {

    private val helper = PocketBookDatabase(context)

    private val updates = MutableStateFlow(readAll())

    fun getTransfers(): Flow<List<WalletTransfer>> =
        updates.asStateFlow()

    suspend fun addTransfer(
        fromId: String,
        toId: String,
        amountSen: Long,
        note: String
    ) = withContext(Dispatchers.IO) {

        require(amountSen > 0) {
            "Amount must be greater than zero"
        }

        require(fromId != toId) {
            "Choose two different wallets"
        }

        synchronized(helper) {
            val db = helper.writableDatabase

            db.beginTransaction()
            try {
                require(walletExists(db, fromId)) {
                    "Source wallet not found"
                }

                require(walletExists(db, toId)) {
                    "Destination wallet not found"
                }

                val available = balanceSen(db, fromId)

                require(available >= amountSen) {
                    "Insufficient wallet balance"
                }

                val values = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("from_wallet_id", fromId)
                    put("to_wallet_id", toId)
                    put("amount_sen", amountSen)
                    put("date", LocalDate.now().toString())
                    put("note", note.trim())
                }

                db.insertOrThrow("transfers", null, values)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            updates.value = readAll()
        }

        Unit
    }

    suspend fun deleteTransfer(
        id: String
    ) = withContext(Dispatchers.IO) {

        synchronized(helper) {
            val changed = helper.writableDatabase.delete(
                "transfers",
                "id=?",
                arrayOf(id)
            )

            require(changed == 1) {
                "Transfer not found"
            }

            updates.value = readAll()
        }

        Unit
    }

    private fun walletExists(
        db: SQLiteDatabase,
        id: String
    ): Boolean {
        db.query(
            "wallets",
            arrayOf("id"),
            "id=?",
            arrayOf(id),
            null,
            null,
            null
        ).use {
            return it.moveToFirst()
        }
    }

    private fun balanceSen(
        db: SQLiteDatabase,
        walletId: String
    ): Long {

        val openingSen = db.rawQuery(
            "SELECT opening_balance_sen FROM wallets WHERE id=?",
            arrayOf(walletId)
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Wallet not found" }
            cursor.getLong(0)
        }

        val transactionChangeSen = db.rawQuery(
            """
            SELECT COALESCE(SUM(
                CASE WHEN type='Income' THEN amount_sen
                ELSE -amount_sen END
            ), 0)
            FROM transactions WHERE wallet_id=?
            """.trimIndent(),
            arrayOf(walletId)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

        val incoming = db.rawQuery(
            """
            SELECT COALESCE(SUM(amount_sen), 0)
            FROM transfers
            WHERE to_wallet_id=?
            """.trimIndent(),
            arrayOf(walletId)
        ).use {
            it.moveToFirst()
            it.getLong(0)
        }

        val outgoing = db.rawQuery(
            """
            SELECT COALESCE(SUM(amount_sen), 0)
            FROM transfers
            WHERE from_wallet_id=?
            """.trimIndent(),
            arrayOf(walletId)
        ).use {
            it.moveToFirst()
            it.getLong(0)
        }

        return Math.subtractExact(
            Math.addExact(
                Math.addExact(openingSen, transactionChangeSen),
                incoming
            ), outgoing
        )
    }

    private fun readAll(): List<WalletTransfer> {
        return helper.readableDatabase.query(
            "transfers",
            null,
            null,
            null,
            null,
            null,
            "date DESC, rowid DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toTransfer())
                }
            }
        }
    }

    private fun Cursor.toTransfer(): WalletTransfer =
        WalletTransfer(
            id = getString(getColumnIndexOrThrow("id")),
            fromWalletId = getString(
                getColumnIndexOrThrow("from_wallet_id")
            ),
            toWalletId = getString(
                getColumnIndexOrThrow("to_wallet_id")
            ),
            amountSen = getLong(
                getColumnIndexOrThrow("amount_sen")
            ),
            date = getString(getColumnIndexOrThrow("date")),
            note = getString(getColumnIndexOrThrow("note"))
        )

    companion object {
        @Volatile
        private var instance: LocalTransferRepo? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LocalTransferRepo(
                            context.applicationContext
                        )
                    }
                }
            }
        }

        fun getInstance(): LocalTransferRepo =
            checkNotNull(instance) {
                "Initialize LocalTransferRepo first"
            }
    }


    fun refresh() {
        updates.value = readAll()
    }

}
