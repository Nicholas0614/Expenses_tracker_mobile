
package com.example.pocketbook_expensetracker.data.integrity

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import com.example.pocketbook_expensetracker.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

enum class AuditLevel {
    ERROR, WARNING, INFO
}

data class AuditIssue(
    val level: AuditLevel,
    val message: String
)

data class AuditReport(
    val walletCount: Int,
    val transactionCount: Int,
    val transferCount: Int,
    val budgetCount: Int,
    val openingSen: Long,
    val transactionNetSen: Long,
    val transferNetSen: Long,
    val calculatedTotalSen: Long,
    val issues: List<AuditIssue>
) {
    val errorCount: Int
        get() = issues.count {
            it.level == AuditLevel.ERROR
        }

    val warningCount: Int
        get() = issues.count {
            it.level == AuditLevel.WARNING
        }
}

class FinanceIntegrityChecker(context: Context) {

    private val helper = PocketBookDatabase(
        context.applicationContext
    )

    suspend fun runAudit(): AuditReport =
        withContext(Dispatchers.IO) {
            audit(helper.readableDatabase)
        }

    private fun audit(db: SQLiteDatabase): AuditReport {
        val issues = mutableListOf<AuditIssue>()

        fun error(message: String) {
            issues.add(AuditIssue(AuditLevel.ERROR, message))
        }

        fun warning(message: String) {
            issues.add(AuditIssue(AuditLevel.WARNING, message))
        }

        fun info(message: String) {
            issues.add(AuditIssue(AuditLevel.INFO, message))
        }

        // Check SQLite's internal database structure.
        db.rawQuery("PRAGMA integrity_check", null).use {
            while (it.moveToNext()) {
                val result = it.getString(0)

                if (result != "ok") {
                    error("SQLite integrity: $result")
                }
            }
        }

        // Use a snapshot for all financial totals.
        db.beginTransactionNonExclusive()

        try {
            val walletIds = mutableSetOf<String>()
            val walletBalances = mutableMapOf<String, Long>()

            var walletCount = 0
            var openingTotal = 0L

            db.rawQuery(
                """
                SELECT id, name, kind, opening_balance, opening_balance_sen
                FROM wallets
                """.trimIndent(),
                null
            ).use { cursor ->

                while (cursor.moveToNext()) {
                    walletCount++

                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    val kind = cursor.getString(2)
                    val opening = cursor.getDouble(3)

                    if (id.isBlank() || name.isBlank()) {
                        error("Wallet has a missing ID or name")
                    }

                    if (!walletIds.add(id)) {
                        error("Duplicate wallet ID: $id")
                    }

                    if (kind !in listOf(
                            "Cash", "Bank", "eWallet"
                        )
                    ) {
                        error("Invalid wallet type: $name")
                    }

                    val amountSen = if (cursor.isNull(4)) null else cursor.getLong(4)
                    val validMirror = runCatching {
                        Money.fromDouble(opening) == amountSen
                    }.getOrDefault(false)
                    if (!validMirror) error("Wallet '$name' has inconsistent money columns")

                    if (amountSen == null) {
                        error(
                            "Wallet '$name' has an amount " +
                                    "that cannot be represented in sen"
                        )
                    } else {
                        openingTotal = Money.add(
                            openingTotal,
                            amountSen
                        )

                        walletBalances[id] = amountSen
                    }
                }
            }

            var transactionCount = 0
            var unassigned = 0
            var transactionNet = 0L

            db.rawQuery(
                """
                SELECT id, wallet_id, type, amount, date, amount_sen
                FROM transactions
                """.trimIndent(),
                null
            ).use { cursor ->

                while (cursor.moveToNext()) {
                    transactionCount++

                    val id = cursor.getString(0)
                    val walletId = cursor.getString(1)
                    val type = cursor.getString(2)
                    val amount = cursor.getDouble(3)
                    val date = cursor.getString(4)

                    if (walletId == null) {
                        unassigned++
                    } else if (walletId !in walletIds) {
                        error(
                            "Transaction $id references " +
                                    "a missing wallet"
                        )
                    }

                    if (type != "Income" && type != "Expense") {
                        error("Transaction $id has invalid type")
                        continue
                    }

                    if (runCatching {
                            LocalDate.parse(date)
                        }.isFailure
                    ) {
                        error("Transaction $id has invalid date")
                    }

                    val amountSen = if (cursor.isNull(5)) null else cursor.getLong(5)
                    val validMirror = runCatching {
                        Money.fromDouble(amount) == amountSen
                    }.getOrDefault(false)
                    if (!validMirror) error("Transaction $id has inconsistent money columns")

                    if (amountSen == null || amountSen <= 0L) {
                        error(
                            "Transaction $id has invalid " +
                                    "amount or decimal precision"
                        )
                        continue
                    }

                    val signed = if (type == "Income") {
                        amountSen
                    } else {
                        -amountSen
                    }

                    if (walletId != null &&
                        walletId in walletBalances
                    ) {
                        walletBalances[walletId] = Money.add(
                            walletBalances.getValue(walletId),
                            signed
                        )

                        transactionNet = Money.add(
                            transactionNet,
                            signed
                        )
                    }
                }
            }

            if (unassigned > 0) {
                warning(
                    "$unassigned older transaction(s) " +
                            "have no assigned wallet"
                )
            }

            var transferCount = 0
            var outgoingTotal = 0L
            var incomingTotal = 0L

            db.rawQuery(
                """
                SELECT id, from_wallet_id,
                       to_wallet_id, amount_sen, date
                FROM transfers
                """.trimIndent(),
                null
            ).use { cursor ->

                while (cursor.moveToNext()) {
                    transferCount++

                    val id = cursor.getString(0)
                    val fromId = cursor.getString(1)
                    val toId = cursor.getString(2)
                    val amountSen = cursor.getLong(3)
                    val date = cursor.getString(4)

                    if (fromId == toId) {
                        error("Transfer $id uses the same wallet")
                        continue
                    }

                    if (fromId !in walletIds ||
                        toId !in walletIds
                    ) {
                        error(
                            "Transfer $id references " +
                                    "a missing wallet"
                        )
                        continue
                    }

                    if (amountSen <= 0L) {
                        error("Transfer $id has invalid amount")
                        continue
                    }

                    if (runCatching {
                            LocalDate.parse(date)
                        }.isFailure
                    ) {
                        error("Transfer $id has invalid date")
                    }

                    walletBalances[fromId] = Money.subtract(
                        walletBalances.getValue(fromId),
                        amountSen
                    )

                    walletBalances[toId] = Money.add(
                        walletBalances.getValue(toId),
                        amountSen
                    )

                    outgoingTotal = Money.add(
                        outgoingTotal,
                        amountSen
                    )

                    incomingTotal = Money.add(
                        incomingTotal,
                        amountSen
                    )
                }
            }

            val transferNet = Money.subtract(
                incomingTotal,
                outgoingTotal
            )

            if (transferNet != 0L) {
                error(
                    "Transfer reconciliation failed: " +
                            Money.format(transferNet)
                )
            }

            var budgetCount = 0
            val budgetKeys = mutableSetOf<String>()

            db.rawQuery(
                """
                SELECT id, category, month, limit_amount, limit_amount_sen
                FROM budgets
                """.trimIndent(),
                null
            ).use { cursor ->

                while (cursor.moveToNext()) {
                    budgetCount++

                    val id = cursor.getString(0)
                    val category = cursor.getString(1)
                    val month = cursor.getString(2)
                    val amount = cursor.getDouble(3)

                    val key = "$category|$month"

                    if (!budgetKeys.add(key)) {
                        error(
                            "Duplicate budget for " +
                                    "$category in $month"
                        )
                    }

                    if (category.isBlank() ||
                        runCatching {
                            YearMonth.parse(month)
                        }.isFailure
                    ) {
                        error("Budget $id has invalid details")
                    }

                    val amountSen = if (cursor.isNull(4)) null else cursor.getLong(4)
                    val validMirror = runCatching {
                        Money.fromDouble(amount) == amountSen
                    }.getOrDefault(false)
                    if (!validMirror) error("Budget $id has inconsistent money columns")

                    if (amountSen == null || amountSen <= 0L) {
                        error(
                            "Budget $id has an invalid amount"
                        )
                    }
                }
            }

            val total = walletBalances.values.fold(0L) {
                    sum, amount -> Money.add(sum, amount)
            }

            walletBalances.forEach { (id, amount) ->
                if (amount < 0L) {
                    warning(
                        "Wallet $id has negative balance: " +
                                Money.format(amount)
                    )
                }
            }

            val expected = Money.add(
                Money.add(openingTotal, transactionNet),
                transferNet
            )

            if (total != expected) {
                error(
                    "Wallet reconciliation mismatch: " +
                            "expected ${Money.format(expected)}, " +
                            "found ${Money.format(total)}"
                )
            }

            if (issues.none { it.level == AuditLevel.ERROR }) {
                info(
                    "No structural or financial " +
                            "reconciliation errors detected"
                )
            }

            return AuditReport(
                walletCount = walletCount,
                transactionCount = transactionCount,
                transferCount = transferCount,
                budgetCount = budgetCount,
                openingSen = openingTotal,
                transactionNetSen = transactionNet,
                transferNetSen = transferNet,
                calculatedTotalSen = total,
                issues = issues
            )
        } finally {
            db.endTransaction()
        }
    }
}
