package com.example.pocketbook_expensetracker

import com.example.pocketbook_expensetracker.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test fun convertsExactly() {
        assertEquals(1L, Money.parseSen("0.01"))
        assertEquals(1250L, Money.parseSen("12.50"))
        assertEquals(-250L, Money.parseSen("-2.50"))
        assertEquals(1850L, Money.fromDouble(18.50))
        assertEquals("RM 18.50", Money.format(1850L))
    }

    @Test fun rejectsFractionsOfSen() {
        assertThrows(ArithmeticException::class.java) {
            Money.parseSen("12.345")
        }
        assertThrows(ArithmeticException::class.java) {
            Money.fromDouble(12.345)
        }
    }

    @Test fun additionIsExact() {
        assertEquals(150L, Money.add(100L, 50L))
        assertThrows(ArithmeticException::class.java) {
            Money.add(Long.MAX_VALUE, 1L)
        }
    }
}
