
package com.example.pocketbook_expensetracker.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.pocketbook_expensetracker.data.local.PocketBookDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import com.example.pocketbook_expensetracker.util.Money

data class BackupSummary(
    val wallets: Int,
    val transactions: Int,
    val transfers: Int,
    val budgets: Int
) {

    override fun toString(): String {
        return "Wallets: $wallets\n" +
                "Transactions: $transactions\n" +
                "Transfers: $transfers\n" +
                "Budgets: $budgets"
    }
}

class PocketBookBackup(context: Context) {

    private val helper = PocketBookDatabase(
        context.applicationContext
    )

    private val tables = listOf(
        "wallets",
        "transactions",
        "transfers",
        "budgets"
    )

    // Keep version-1 backups compatible with the
    // new database version 4.
    //
    // We export only the original fields.
    // Integer-sen columns are derived by SQLite triggers
    // when version-1 backups are restored.
    private val backupColumns = mapOf(
        "wallets" to arrayOf(
            "id",
            "name",
            "kind",
            "opening_balance",
            "opening_balance_sen"
        ),

        "transactions" to arrayOf(
            "id",
            "name",
            "category",
            "amount",
            "type",
            "payment_method",
            "date",
            "time",
            "icon",
            "note",
            "wallet_id",
            "amount_sen"
        ),

        "transfers" to arrayOf(
            "id",
            "from_wallet_id",
            "to_wallet_id",
            "amount_sen",
            "date",
            "note"
        ),

        "budgets" to arrayOf(
            "id",
            "category",
            "month",
            "limit_amount",
            "limit_amount_sen"
        )
    )

    // EXPORT BACKUP

    fun exportJson(): String {

        val db = helper.writableDatabase
        val root = JSONObject()

        db.beginTransactionNonExclusive()

        try {
            root.put("app", "PocketBook")
            root.put("schemaVersion", 4)

            root.put(
                "exportedAt",
                java.time.Instant.now().toString()
            )

            tables.forEach { table ->

                val rows = JSONArray()

                db.query(
                    table,
                    backupColumns.getValue(table),
                    null,
                    null,
                    null,
                    null,
                    null
                ).use { cursor ->

                    while (cursor.moveToNext()) {
                        rows.put(cursorToJson(cursor))
                    }
                }

                root.put(table, rows)
            }

            val custom = JSONArray()
            db.query("custom_categories", arrayOf("name"), null, null,
                null, null, "name COLLATE NOCASE ASC").use { cursor ->
                while (cursor.moveToNext()) custom.put(cursor.getString(0))
            }
            root.put("customCategories", custom)

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }

        return root.toString(2)
    }

    // PREVIEW WITHOUT CHANGING DATA

    fun preview(json: String): BackupSummary {

        val root = parseAndValidate(json)

        return BackupSummary(
            wallets = root
                .getJSONArray("wallets")
                .length(),

            transactions = root
                .getJSONArray("transactions")
                .length(),

            transfers = root
                .getJSONArray("transfers")
                .length(),

            budgets = root
                .getJSONArray("budgets")
                .length()
        )
    }

    // RESTORE BACKUP

    fun restore(json: String): BackupSummary {

        // Validate everything before replacing records.
        val root = parseAndValidate(json)

        val summary = BackupSummary(
            wallets = root
                .getJSONArray("wallets")
                .length(),

            transactions = root
                .getJSONArray("transactions")
                .length(),

            transfers = root
                .getJSONArray("transfers")
                .length(),

            budgets = root
                .getJSONArray("budgets")
                .length()
        )

        val db = helper.writableDatabase

        db.beginTransaction()

        try {

            // Delete dependant records first.
            listOf(
                "transfers",
                "budgets",
                "transactions",
                "wallets",
                "custom_categories"
            ).forEach { table ->

                db.delete(
                    table,
                    null,
                    null
                )
            }

            // Restore wallets before their linked records.
            tables.forEach { table ->

                val rows = root.getJSONArray(table)

                for (index in 0 until rows.length()) {

                    val row = rows.getJSONObject(index)
                    val values = ContentValues()

                    val keys = row.keys()

                    while (keys.hasNext()) {

                        val key = keys.next()
                        val value = row.get(key)

                        when (value) {

                            JSONObject.NULL -> {
                                values.putNull(key)
                            }

                            is Int -> {
                                values.put(key, value)
                            }

                            is Long -> {
                                values.put(key, value)
                            }

                            is Double -> {
                                values.put(key, value)
                            }

                            is String -> {
                                values.put(key, value)
                            }

                            else -> {
                                error(
                                    "Invalid backup field: $key"
                                )
                            }
                        }
                    }

                    // The version-4 SQLite triggers will
                    // populate the integer-sen columns.
                    db.insertOrThrow(
                        table,
                        null,
                        values
                    )
                }
            }

            root.optJSONArray("customCategories")?.let { list ->
                for (index in 0 until list.length()) {
                    val values = ContentValues().apply {
                        put("name", list.getString(index))
                    }
                    db.insertOrThrow("custom_categories", null, values)
                }
            }

            // All records must succeed before committing.
            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }

        return summary
    }

    // CONVERT DATABASE ROW TO JSON

    private fun cursorToJson(
        cursor: Cursor
    ): JSONObject {

        val row = JSONObject()

        for (index in 0 until cursor.columnCount) {

            val name = cursor.getColumnName(index)

            when (cursor.getType(index)) {

                Cursor.FIELD_TYPE_NULL -> {
                    row.put(
                        name,
                        JSONObject.NULL
                    )
                }

                Cursor.FIELD_TYPE_INTEGER -> {
                    row.put(
                        name,
                        cursor.getLong(index)
                    )
                }

                Cursor.FIELD_TYPE_FLOAT -> {
                    row.put(
                        name,
                        cursor.getDouble(index)
                    )
                }

                Cursor.FIELD_TYPE_STRING -> {
                    row.put(
                        name,
                        cursor.getString(index)
                    )
                }

                else -> {
                    error(
                        "Unsupported database field: $name"
                    )
                }
            }
        }

        return row
    }

    // BACKUP VALIDATION

    private fun parseAndValidate(
        json: String
    ): JSONObject {

        require(json.length <= 8_000_000) {
            "Backup is too large"
        }

        val root = JSONObject(json)

        require(
            root.optString("app") == "PocketBook"
        ) {
            "This is not a PocketBook backup"
        }

        require(
            root.optInt("schemaVersion") in 1..4
        ) {
            "Unsupported backup version"
        }

        tables.forEach { table ->

            require(
                root.optJSONArray(table) != null
            ) {
                "Backup is missing $table"
            }
        }

        val version = root.getInt("schemaVersion")
        if (version >= 3) {
            val custom = root.optJSONArray("customCategories")
            require(custom != null) { "Missing custom categories" }
            require(custom.length() <= 1000) { "Too many custom categories" }
            val seen = mutableSetOf<String>()
            for (i in 0 until custom.length()) {
                val value = custom.getString(i)
                require(value == value.trim() && value.length in 1..32 &&
                    value !in listOf("Food", "Groceries", "Transport", "Bills", "Fun", "Health", "Shopping", "New") &&
                    seen.add(value.lowercase())) {
                    "Invalid or duplicate custom category"
                }
            }
        }

        val walletIds = mutableSetOf<String>()
        val walletNames = mutableSetOf<String>()

        val transactionIds = mutableSetOf<String>()
        val transferIds = mutableSetOf<String>()

        val budgetIds = mutableSetOf<String>()
        val budgetKeys = mutableSetOf<String>()

        // VALIDATE WALLETS

        forEachRow(root, "wallets") { row ->

            exactFields(
                row,
                setOf(
                    "id",
                    "name",
                    "kind",
                    "opening_balance"
                ) + if (version >= 2) setOf("opening_balance_sen") else emptySet()
            )

            val id = validId(
                row.getString("id")
            )

            val name = row
                .getString("name")
                .trim()

            require(name.isNotBlank()) {
                "Wallet name cannot be empty"
            }

            require(
                row.getString("kind") in listOf(
                    "Cash",
                    "Bank",
                    "eWallet"
                )
            ) {
                "Invalid wallet type"
            }

            checkMoneyPair(row, "opening_balance", "opening_balance_sen", version)

            require(
                walletIds.add(id)
            ) {
                "Duplicate wallet ID"
            }

            require(
                walletNames.add(
                    name.lowercase()
                )
            ) {
                "Duplicate wallet name"
            }
        }

        // VALIDATE TRANSACTIONS

        forEachRow(root, "transactions") { row ->

            exactFields(
                row,
                setOf(
                    "id",
                    "name",
                    "category",
                    "amount",
                    "type",
                    "payment_method",
                    "date",
                    "icon",
                    "note",
                    "wallet_id"
                ) + (if (version >= 2) setOf("amount_sen") else emptySet()) +
                    (if (version >= 4) setOf("time") else emptySet())
            )

            require(
                transactionIds.add(
                    validId(
                        row.getString("id")
                    )
                )
            ) {
                "Duplicate transaction ID"
            }

            require(
                row.getString("name").isNotBlank()
            ) {
                "Invalid transaction name"
            }

            require(
                row.getString("type") in listOf(
                    "Income",
                    "Expense"
                )
            ) {
                "Invalid transaction type"
            }

            require(
                checkMoneyPair(row, "amount", "amount_sen", version) > 0
            ) {
                "Invalid transaction amount"
            }

            LocalDate.parse(
                row.getString("date")
            )
            if (version >= 4) {
                java.time.LocalTime.parse(row.getString("time"))
            }

            val walletId = row.opt("wallet_id")

            if (
                walletId != null &&
                walletId != JSONObject.NULL
            ) {
                require(
                    walletId is String &&
                            walletId in walletIds
                ) {
                    "Transaction references a missing wallet"
                }
            }

            listOf(
                "category",
                "payment_method",
                "icon",
                "note"
            ).forEach { field ->
                row.getString(field)
            }
        }

        // VALIDATE TRANSFERS

        forEachRow(root, "transfers") { row ->

            exactFields(
                row,
                setOf(
                    "id",
                    "from_wallet_id",
                    "to_wallet_id",
                    "amount_sen",
                    "date",
                    "note"
                )
            )

            require(
                transferIds.add(
                    validId(
                        row.getString("id")
                    )
                )
            ) {
                "Duplicate transfer ID"
            }

            val from = row.getString(
                "from_wallet_id"
            )

            val to = row.getString(
                "to_wallet_id"
            )

            require(
                from in walletIds &&
                        to in walletIds &&
                        from != to
            ) {
                "Invalid transfer wallets"
            }

            require(
                row.getLong("amount_sen") > 0
            ) {
                "Invalid transfer amount"
            }

            LocalDate.parse(
                row.getString("date")
            )

            row.getString("note")
        }

        // VALIDATE BUDGETS

        forEachRow(root, "budgets") { row ->

            exactFields(
                row,
                setOf(
                    "id",
                    "category",
                    "month",
                    "limit_amount"
                ) + if (version >= 2) setOf("limit_amount_sen") else emptySet()
            )

            require(
                budgetIds.add(
                    validId(
                        row.getString("id")
                    )
                )
            ) {
                "Duplicate budget ID"
            }

            val category = row.getString(
                "category"
            )

            val month = row.getString(
                "month"
            )

            require(
                category.isNotBlank()
            ) {
                "Invalid budget category"
            }

            YearMonth.parse(month)

            require(
                checkMoneyPair(row, "limit_amount", "limit_amount_sen", version) > 0
            ) {
                "Invalid budget amount"
            }

            require(
                budgetKeys.add(
                    "$category|$month"
                )
            ) {
                "Duplicate category budget"
            }
        }

        return root
    }

    private fun checkMoneyPair(
        row: JSONObject,
        realColumn: String,
        senColumn: String,
        version: Int
    ): Long {
        val exactSen = Money.fromDouble(finiteAmount(row, realColumn))
        if (version >= 2) {
            val rawSen = row.get(senColumn)
            require(rawSen is Number && rawSen.toString().matches(Regex("-?\\d+"))) {
                "Invalid integer-sen field: $senColumn"
            }
            val savedSen = rawSen.toString().toLong()
            require(savedSen == exactSen) {
                "Inconsistent monetary values in $realColumn / $senColumn"
            }
        }
        return exactSen
    }

    private fun exactFields(
        row: JSONObject,
        expected: Set<String>
    ) {

        val found = mutableSetOf<String>()
        val keys = row.keys()

        while (keys.hasNext()) {
            found.add(keys.next())
        }

        require(found == expected) {
            "Backup has missing or unknown fields"
        }
    }

    private fun validId(
        value: String
    ): String {

        require(value.isNotBlank()) {
            "Missing record ID"
        }

        return value
    }

    private fun finiteAmount(
        row: JSONObject,
        field: String
    ): Double {

        val value = row.getDouble(field)

        require(value.isFinite()) {
            "Invalid amount in $field"
        }

        return value
    }

    private inline fun forEachRow(
        root: JSONObject,
        table: String,
        action: (JSONObject) -> Unit
    ) {

        val array = root.getJSONArray(table)

        require(
            array.length() <= 100_000
        ) {
            "Too many records in $table"
        }

        for (index in 0 until array.length()) {
            action(
                array.getJSONObject(index)
            )
        }
    }
}
