
package com.example.pocketbook_expensetracker.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PocketBookDatabase(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        "pocketbook_v2.db",
        null,
        DATABASE_VERSION
    ) {

    companion object {
        private const val DATABASE_VERSION = 6
    }

    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE transactions (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                amount REAL NOT NULL CHECK(amount > 0),
                type TEXT NOT NULL CHECK(
                    type IN ('Income','Expense')
                ),
                payment_method TEXT NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL DEFAULT '12:00',
                icon TEXT NOT NULL,
                note TEXT NOT NULL,
                wallet_id TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX transaction_date_idx
            ON transactions(date)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX transaction_wallet_idx
            ON transactions(wallet_id)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE wallets (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL UNIQUE,
                kind TEXT NOT NULL,
                opening_balance REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE budgets (
                id TEXT PRIMARY KEY NOT NULL,
                category TEXT NOT NULL,
                month TEXT NOT NULL,
                limit_amount REAL NOT NULL
                    CHECK(limit_amount > 0),
                UNIQUE(category, month)
            )
            """.trimIndent()
        )

        createTransfersTable(db)

        db.execSQL(
            """
            INSERT INTO wallets(
                id, name, kind, opening_balance
            )
            VALUES(
                'cash', 'Cash', 'Cash', 0
            )
            """.trimIndent()
        )

        migrateMoneyColumns(db)
        createCustomCategoriesTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (oldVersion < 2) {
            db.execSQL(
                """
                ALTER TABLE transactions
                ADD COLUMN wallet_id TEXT
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX transaction_wallet_idx
                ON transactions(wallet_id)
                """.trimIndent()
            )
        }

        if (oldVersion < 3) {
            createTransfersTable(db)
        }

        if (oldVersion < 4) {
            migrateMoneyColumns(db)
        }
        if (oldVersion < 5) {
            createCustomCategoriesTable(db)
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN time TEXT NOT NULL DEFAULT '12:00'")
        }
    }

    private fun createCustomCategoriesTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS custom_categories (
                name TEXT PRIMARY KEY NOT NULL COLLATE NOCASE,
                CHECK(length(trim(name)) BETWEEN 1 AND 32)
            )""".trimIndent()
        )
    }

    private fun createTransfersTable(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transfers (
                id TEXT PRIMARY KEY NOT NULL,
                from_wallet_id TEXT NOT NULL,
                to_wallet_id TEXT NOT NULL,
                amount_sen INTEGER NOT NULL
                    CHECK(amount_sen > 0),
                date TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                CHECK(from_wallet_id != to_wallet_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS transfer_from_idx
            ON transfers(from_wallet_id)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS transfer_to_idx
            ON transfers(to_wallet_id)
            """.trimIndent()
        )
    }

    private fun migrateMoneyColumns(
        db: SQLiteDatabase
    ) {

        // Validate existing monetary values before migration.
        validateLegacyMoney(
            db,
            "transactions",
            "amount"
        )

        validateLegacyMoney(
            db,
            "wallets",
            "opening_balance"
        )

        validateLegacyMoney(
            db,
            "budgets",
            "limit_amount"
        )

        // Add integer-sen columns.
        db.execSQL(
            """
            ALTER TABLE transactions
            ADD COLUMN amount_sen INTEGER
            """.trimIndent()
        )

        db.execSQL(
            """
            ALTER TABLE wallets
            ADD COLUMN opening_balance_sen INTEGER
            """.trimIndent()
        )

        db.execSQL(
            """
            ALTER TABLE budgets
            ADD COLUMN limit_amount_sen INTEGER
            """.trimIndent()
        )

        // Convert existing amounts to integer sen.
        db.execSQL(
            """
            UPDATE transactions
            SET amount_sen =
                CAST(ROUND(amount * 100) AS INTEGER)
            """.trimIndent()
        )

        db.execSQL(
            """
            UPDATE wallets
            SET opening_balance_sen =
                CAST(
                    ROUND(opening_balance * 100)
                    AS INTEGER
                )
            """.trimIndent()
        )

        db.execSQL(
            """
            UPDATE budgets
            SET limit_amount_sen =
                CAST(
                    ROUND(limit_amount * 100)
                    AS INTEGER
                )
            """.trimIndent()
        )

        createMoneySyncTriggers(db)
    }

    private fun createMoneySyncTriggers(
        db: SQLiteDatabase
    ) {

        // Transactions: insert.
        db.execSQL(
            """
            CREATE TRIGGER sync_transaction_sen_insert
            AFTER INSERT ON transactions
            BEGIN

                SELECT CASE
                    WHEN NEW.amount IS NULL
                      OR ABS(NEW.amount) > 90000000000000
                      OR ABS(
                          NEW.amount * 100 -
                          ROUND(NEW.amount * 100)
                      ) > 0.00001
                    THEN RAISE(
                        ABORT,
                        'Invalid transaction precision'
                    )
                END;

                UPDATE transactions
                SET amount_sen =
                    CAST(
                        ROUND(NEW.amount * 100)
                        AS INTEGER
                    )
                WHERE id = NEW.id;

            END
            """.trimIndent()
        )

        // Transactions: edit.
        db.execSQL(
            """
            CREATE TRIGGER sync_transaction_sen_update
            AFTER UPDATE OF amount ON transactions
            BEGIN

                SELECT CASE
                    WHEN NEW.amount IS NULL
                      OR ABS(NEW.amount) > 90000000000000
                      OR ABS(
                          NEW.amount * 100 -
                          ROUND(NEW.amount * 100)
                      ) > 0.00001
                    THEN RAISE(
                        ABORT,
                        'Invalid transaction precision'
                    )
                END;

                UPDATE transactions
                SET amount_sen =
                    CAST(
                        ROUND(NEW.amount * 100)
                        AS INTEGER
                    )
                WHERE id = NEW.id;

            END
            """.trimIndent()
        )

        // Wallets: insert.
        db.execSQL(
            """
            CREATE TRIGGER sync_wallet_sen_insert
            AFTER INSERT ON wallets
            BEGIN

                SELECT CASE
                    WHEN NEW.opening_balance IS NULL
                      OR ABS(NEW.opening_balance) > 90000000000000
                      OR ABS(
                          NEW.opening_balance * 100 -
                          ROUND(NEW.opening_balance * 100)
                      ) > 0.00001
                    THEN RAISE(
                        ABORT,
                        'Invalid wallet precision'
                    )
                END;

                UPDATE wallets
                SET opening_balance_sen =
                    CAST(
                        ROUND(NEW.opening_balance * 100)
                        AS INTEGER
                    )
                WHERE id = NEW.id;

            END
            """.trimIndent()
        )

        // Wallets: edit.
        db.execSQL(
            """
            CREATE TRIGGER sync_wallet_sen_update
            AFTER UPDATE OF opening_balance ON wallets
            BEGIN

                SELECT CASE
                    WHEN NEW.opening_balance IS NULL
                      OR ABS(NEW.opening_balance) > 90000000000000
                      OR ABS(
                          NEW.opening_balance * 100 -
                          ROUND(NEW.opening_balance * 100)
                      ) > 0.00001
                    THEN RAISE(
                        ABORT,
                        'Invalid wallet precision'
                    )
                END;

                UPDATE wallets
                SET opening_balance_sen =
                    CAST(
                        ROUND(NEW.opening_balance * 100)
                        AS INTEGER
                    )
                WHERE id = NEW.id;

            END
            """.trimIndent()
        )

        // Budgets: insert.
        db.execSQL(
            """
            CREATE TRIGGER sync_budget_sen_insert
            AFTER INSERT ON budgets
            BEGIN

                SELECT CASE
                    WHEN NEW.limit_amount IS NULL
                      OR ABS(NEW.limit_amount) > 90000000000000
                      OR ABS(
                          NEW.limit_amount * 100 -
                          ROUND(NEW.limit_amount * 100)
                      ) > 0.00001
                    THEN RAISE(
                        ABORT,
                        'Invalid budget precision'
                    )
                END;

                UPDATE budgets
                SET limit_amount_sen =
                    CAST(
                        ROUND(NEW.limit_amount * 100)
                        AS INTEGER
                    )
                WHERE id = NEW.id;

            END
            """.trimIndent()
        )

        // Budgets: edit.
        db.execSQL(
            """
            CREATE TRIGGER sync_budget_sen_update
            AFTER UPDATE OF limit_amount ON budgets
            BEGIN

                SELECT CASE
                    WHEN NEW.limit_amount IS NULL
                      OR ABS(NEW.limit_amount) > 90000000000000
                      OR ABS(
                          NEW.limit_amount * 100 -
                          ROUND(NEW.limit_amount * 100)
                      ) > 0.00001
                    THEN RAISE(
                        ABORT,
                        'Invalid budget precision'
                    )
                END;

                UPDATE budgets
                SET limit_amount_sen =
                    CAST(
                        ROUND(NEW.limit_amount * 100)
                        AS INTEGER
                    )
                WHERE id = NEW.id;

            END
            """.trimIndent()
        )
    }

    private fun validateLegacyMoney(
        db: SQLiteDatabase,
        table: String,
        column: String
    ) {

        // Table and column names are internal constants.
        val query = """
            SELECT COUNT(*)
            FROM $table
            WHERE $column IS NULL
               OR ABS($column) > 90000000000000
               OR ABS(
                    $column * 100 -
                    ROUND($column * 100)
               ) > 0.00001
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            cursor.moveToFirst()

            check(cursor.getLong(0) == 0L) {
                "Cannot migrate $table: invalid monetary values. " +
                        "Your original database has not been upgraded."
            }
        }
    }
}
