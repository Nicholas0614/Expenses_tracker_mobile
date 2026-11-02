
package com.example.pocketbook_expensetracker.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import com.example.pocketbook_expensetracker.data.model.Budget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.util.UUID
import com.example.pocketbook_expensetracker.util.Money

class LocalBudgetRepo private constructor(
    context: Context
) {

    private val helper = PocketBookDatabase(
        context.applicationContext
    )

    private val updates = MutableStateFlow(readAll())

    fun getBudgets(): Flow<List<Budget>> =
        updates.asStateFlow()

    suspend fun saveBudget(
        category: String,
        month: String,
        amount: Double
    ) = withContext(Dispatchers.IO) {

        require(category.isNotBlank()) {
            "Please select a category"
        }

        require(
            runCatching {
                YearMonth.parse(month)
            }.isSuccess
        ) {
            "Invalid budget month"
        }

        require(Money.fromDouble(amount) > 0L) {
            "Budget must be greater than zero"
        }

        require(
            java.math.BigDecimal.valueOf(amount)
                .scale() <= 2
        ) {
            "Maximum 2 decimal places"
        }

        synchronized(helper) {
            val db = helper.writableDatabase

            db.beginTransaction()

            try {
                val existingId = db.query(
                    "budgets",
                    arrayOf("id"),
                    "category=? AND month=?",
                    arrayOf(category, month),
                    null, null, null
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }

                val values = ContentValues().apply {
                    put("category", category)
                    put("month", month)
                    put("limit_amount", Money.toDecimal(Money.fromDouble(amount)).toDouble())
                    put("limit_amount_sen", Money.fromDouble(amount))
                }

                if (existingId == null) {
                    values.put(
                        "id",
                        UUID.randomUUID().toString()
                    )

                    db.insertOrThrow(
                        "budgets",
                        null,
                        values
                    )
                } else {
                    val changed = db.update(
                        "budgets",
                        values,
                        "id=?",
                        arrayOf(existingId)
                    )

                    check(changed == 1) {
                        "Unable to update budget"
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            updates.value = readAll()
        }

        Unit
    }

    suspend fun deleteBudget(
        id: String
    ) = withContext(Dispatchers.IO) {

        synchronized(helper) {
            val changed = helper.writableDatabase.delete(
                "budgets",
                "id=?",
                arrayOf(id)
            )

            require(changed == 1) {
                "Budget not found"
            }

            updates.value = readAll()
        }

        Unit
    }

    private fun readAll(): List<Budget> {
        return helper.readableDatabase.query(
            "budgets",
            null,
            null,
            null,
            null,
            null,
            "month DESC, category ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBudget())
                }
            }
        }
    }

    private fun Cursor.toBudget(): Budget {
        return Budget(
            id = getString(
                getColumnIndexOrThrow("id")
            ),
            category = getString(
                getColumnIndexOrThrow("category")
            ),
            month = getString(
                getColumnIndexOrThrow("month")
            ),
            limitAmount = Money.toDecimal(getLong(
                getColumnIndexOrThrow("limit_amount_sen")
            )).toDouble()
        )
    }

    companion object {
        @Volatile
        private var instance: LocalBudgetRepo? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LocalBudgetRepo(
                            context.applicationContext
                        )
                    }
                }
            }
        }

        fun getInstance(): LocalBudgetRepo =
            checkNotNull(instance) {
                "Initialize LocalBudgetRepo first"
            }
    }


    fun refresh() {
        updates.value = readAll()
    }

}
