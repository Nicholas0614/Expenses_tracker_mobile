
package com.example.pocketbook_expensetracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {

    // "12.50" -> 1250 sen.
    // Rejects more than two decimal places.
    fun parseSen(input: String): Long {
        val amount = BigDecimal(input.trim())

        return amount
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }

    // Exact conversion for existing SQLite REAL values.
    // E.g. 12.5 -> 1250 sen.
    fun fromDouble(value: Double): Long {
        require(value.isFinite()) {
            "Amount is not finite"
        }

        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }

    fun toDecimal(sen: Long): BigDecimal {
        return BigDecimal.valueOf(sen, 2)
    }

    fun format(sen: Long): String {
        val symbols = DecimalFormatSymbols(Locale.US)

        val formatter = DecimalFormat(
            "#,##0.00",
            symbols
        )

        formatter.roundingMode = RoundingMode.UNNECESSARY

        return "RM ${formatter.format(toDecimal(sen))}"
    }

    fun add(a: Long, b: Long): Long =
        Math.addExact(a, b)

    fun subtract(a: Long, b: Long): Long =
        Math.subtractExact(a, b)
}
