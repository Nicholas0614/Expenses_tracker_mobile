package com.example.pocketbook_expensetracker.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

data class CategoryReport(
    val category: String,
    val amount: Double,
    val percentage: Double
)


data class ReportUiState(
    val period: String = "Month",
    val totalSpending: Double = 0.0,
    val categories: List<CategoryReport> = emptyList(),
    val expenses: List<Transaction> = emptyList(),
    val trendValues: List<Double> = emptyList(),
    val trendLabels: List<String> = emptyList()
)


class ReportsViewModel : ViewModel() {

    private val repo = TransactionRepo.getInstance()
    private val selectedPeriod = MutableStateFlow("Month")

    val uiState: StateFlow<ReportUiState> = combine(
        repo.getTransactions(), selectedPeriod
    ) { transactions, period ->

        val today = LocalDate.now()

        val expenses = transactions.filter { transaction ->
            val date = runCatching { LocalDate.parse(transaction.date) }.getOrNull()
            transaction.type == "Expense" && date != null && when (period) {
                "Week" -> {
                    val weekFields = WeekFields.of(Locale.getDefault())
                    val start = today.with(weekFields.dayOfWeek(), 1)
                    !date.isBefore(start) && date.isBefore(start.plusDays(7))
                }
                "Month" -> date.year == today.year && date.month == today.month
                "Year" -> date.year == today.year
                else -> false
            }
        }

        val total = expenses.sumOf { it.amount }

        val categories = expenses.groupBy { it.category }.map { (category, items) ->
            val amount = items.sumOf { it.amount }
            CategoryReport(category, amount, if (total > 0) amount / total * 100 else 0.0)
        }.sortedByDescending { it.amount }


        val (trendValues, trendLabels) = calculateTrend(expenses, period, today)
        ReportUiState(period, total, categories, expenses, trendValues, trendLabels)


    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportUiState())

    fun setPeriod(period: String) {
        if (period in listOf("Week", "Month", "Year")) selectedPeriod.value = period
    }


    private fun calculateTrend(expenses: List<Transaction>, period: String, today: LocalDate): Pair<List<Double>, List<String>> {
        val dates = expenses.mapNotNull { transaction ->
            runCatching { LocalDate.parse(transaction.date) }.getOrNull()?.let { it to transaction.amount }
        }

        return when (period) {
            "Week" -> {
                val start = today.with(java.time.DayOfWeek.MONDAY)
                val days = (0..6).map { start.plusDays(it.toLong()) }
                days.map { day -> dates.filter { it.first == day }.sumOf { it.second } } to
                        days.map { it.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
            }
            "Month" -> {
                val weeks = (1..5).toList()
                weeks.map { week ->
                    dates.filter { ((it.first.dayOfMonth - 1) / 7) + 1 == week }.sumOf { it.second }
                } to weeks.map { "W$it" }
            }
            else -> {
                val months = (1..12).toList()
                months.map { month ->
                    dates.filter { it.first.monthValue == month }.sumOf { it.second }
                } to months.map { java.time.Month.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
            }
        }
    }

}
