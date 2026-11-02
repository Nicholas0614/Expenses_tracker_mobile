
package com.example.pocketbook_expensetracker.data.repo

import android.content.ContentValues
import android.content.Context
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalCategoryRepo(context: Context) {

    private val helper = PocketBookDatabase(
        context.applicationContext
    )

    // Shared across all repository instances so Add, Edit,
    // and Budgets observe the same category list.
    val categories = sharedCategories.asStateFlow()

    init {
        refresh()
    }

    suspend fun addCategory(
        rawName: String
    ): String = withContext(Dispatchers.IO) {
        val name = cleanName(rawName)

        synchronized(lock) {
            val db = helper.writableDatabase

            require(!exists(db, name)) {
                "This category already exists"
            }

            val values = ContentValues().apply {
                put("name", name)
            }

            db.insertOrThrow(
                "custom_categories",
                null,
                values
            )

            refreshLocked()
        }

        name
    }

    suspend fun renameCategory(
        oldName: String,
        rawNewName: String
    ): String = withContext(Dispatchers.IO) {
        val newName = cleanName(rawNewName)

        synchronized(lock) {
            val db = helper.writableDatabase

            require(!isDefault(oldName)) {
                "Default categories cannot be edited"
            }

            require(existsCustom(db, oldName)) {
                "Category no longer exists"
            }

            if (!oldName.equals(newName, ignoreCase = true)) {
                require(!exists(db, newName)) {
                    "This category already exists"
                }
            }

            db.beginTransaction()

            try {
                // Transaction categories are stored as text.
                db.execSQL(
                    """
                    UPDATE transactions
                    SET category = ?
                    WHERE category = ? COLLATE NOCASE
                    """.trimIndent(),
                    arrayOf(newName, oldName)
                )

                // Budget categories are also stored as text.
                db.execSQL(
                    """
                    UPDATE budgets
                    SET category = ?
                    WHERE category = ? COLLATE NOCASE
                    """.trimIndent(),
                    arrayOf(newName, oldName)
                )

                db.execSQL(
                    """
                    UPDATE custom_categories
                    SET name = ?
                    WHERE name = ? COLLATE NOCASE
                    """.trimIndent(),
                    arrayOf(newName, oldName)
                )

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            refreshLocked()
        }

        newName
    }

    suspend fun deleteCategory(
        name: String
    ): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val db = helper.writableDatabase

            require(!isDefault(name)) {
                "Default categories cannot be deleted"
            }

            require(existsCustom(db, name)) {
                "Category no longer exists"
            }

            db.beginTransaction()

            try {
                val transactionCount = countMatches(
                    db,
                    "transactions",
                    name
                )

                val budgetCount = countMatches(
                    db,
                    "budgets",
                    name
                )

                require(
                    transactionCount == 0 &&
                            budgetCount == 0
                ) {
                    "This category is used by transactions " +
                            "or budgets. Move those records to " +
                            "another category before deleting it."
                }

                db.delete(
                    "custom_categories",
                    "name = ? COLLATE NOCASE",
                    arrayOf(name)
                )

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            refreshLocked()
        }
    }

    fun refresh() {
        synchronized(lock) {
            refreshLocked()
        }
    }

    private fun refreshLocked() {
        sharedCategories.value = readAll()
    }

    private fun readAll(): List<String> {
        return helper.readableDatabase.query(
            "custom_categories",
            arrayOf("name"),
            null,
            null,
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
    }

    private fun cleanName(raw: String): String {
        val name = raw.trim()
            .replace(Regex("\\s+"), " ")

        require(name.length in 1..32) {
            "Category name must be 1–32 characters"
        }

        require(!isDefault(name)) {
            "Default category names are reserved"
        }

        require(!name.equals("New", ignoreCase = true)) {
            "New is a reserved category name"
        }

        return name
    }

    private fun isDefault(name: String): Boolean {
        return DEFAULTS.any {
            it.equals(name, ignoreCase = true)
        }
    }

    private fun exists(
        db: android.database.sqlite.SQLiteDatabase,
        name: String
    ): Boolean {
        return isDefault(name) || existsCustom(db, name)
    }

    private fun existsCustom(
        db: android.database.sqlite.SQLiteDatabase,
        name: String
    ): Boolean {
        return db.rawQuery(
            """
            SELECT 1 FROM custom_categories
            WHERE name = ? COLLATE NOCASE
            LIMIT 1
            """.trimIndent(),
            arrayOf(name)
        ).use {
            it.moveToFirst()
        }
    }

    private fun countMatches(
        db: android.database.sqlite.SQLiteDatabase,
        table: String,
        name: String
    ): Int {
        // 'table' is supplied only from the two fixed
        // internal names above, never from user input.
        return db.rawQuery(
            """
            SELECT COUNT(*) FROM $table
            WHERE category = ? COLLATE NOCASE
            """.trimIndent(),
            arrayOf(name)
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    companion object {
        val DEFAULTS = listOf(
            "Food",
            "Groceries",
            "Transport",
            "Bills",
            "Fun",
            "Health",
            "Shopping"
        )

        private val lock = Any()

        private val sharedCategories =
            MutableStateFlow<List<String>>(emptyList())
    }
}
