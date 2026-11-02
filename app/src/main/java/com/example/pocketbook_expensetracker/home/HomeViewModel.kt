
package com.example.pocketbook_expensetracker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate


data class HomeUiState(
    val balance: Double = 0.0,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList()
)

class HomeViewModel : ViewModel() {

    private val repo = TransactionRepo.getInstance()


    val uiState: StateFlow<HomeUiState> = repo.getTransactions().map { transactions ->
        val today = LocalDate.now()
        val monthly = transactions.filter {
            val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
            date != null && date.year == today.year && date.month == today.month
        }

        val income = monthly.filter { it.type == "Income" }.sumOf { it.amount }
        val expenses = monthly.filter { it.type == "Expense" }.sumOf { it.amount }
        val recent = transactions.sortedByDescending { it.date }.take(4)

        HomeUiState(income - expenses, income, expenses, recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
