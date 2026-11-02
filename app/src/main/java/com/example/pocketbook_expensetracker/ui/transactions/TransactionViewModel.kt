
package com.example.pocketbook_expensetracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.model.Wallet
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActivityFilters(
    val query: String = "",
    val type: String = "All",
    val walletId: String? = null,
    val category: String? = null,
    val ascending: Boolean = false
)

data class ActivityUiState(
    val transactions: List<Transaction> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val categories: List<String> = emptyList(),
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val ascending: Boolean = false,
    val selectedWalletId: String? = null,
    val selectedCategory: String? = null
)

class TransactionsViewModel : ViewModel() {

    private val transactionRepo = TransactionRepo.getInstance()
    private val walletRepo = LocalWalletRepo.getInstance()

    private val filters = MutableStateFlow(ActivityFilters())

    val uiState: StateFlow<ActivityUiState> = combine(
        transactionRepo.getTransactions(),
        walletRepo.getWallets(),
        filters
    ) { allTransactions, wallets, filter ->

        val categories = (
                listOf(
                    "Food",
                    "Groceries",
                    "Transport",
                    "Bills",
                    "Fun",
                    "Health",
                    "Shopping",
                    "New"
                ) + allTransactions.map { it.category }
                    .filter { it.isNotBlank() }
                ).distinct().sorted()

        val filtered = allTransactions.filter { transaction ->

            val matchesQuery =
                filter.query.isBlank() ||
                        transaction.name.contains(
                            filter.query,
                            ignoreCase = true
                        ) ||
                        transaction.note.contains(
                            filter.query,
                            ignoreCase = true
                        ) ||
                        transaction.category.contains(
                            filter.query,
                            ignoreCase = true
                        ) ||
                        transaction.paymentMethod.contains(
                            filter.query,
                            ignoreCase = true
                        )

            val matchesType = when (filter.type) {
                "Expenses" -> transaction.type == "Expense"
                "Income" -> transaction.type == "Income"
                else -> true
            }


            val matchesWallet = when (filter.walletId) {
                null -> true
                "__UNASSIGNED_WALLET__" ->
                    transaction.walletId == null
                else -> transaction.walletId == filter.walletId
            }


            val matchesCategory =
                filter.category == null ||
                        transaction.category == filter.category

            matchesQuery &&
                    matchesType &&
                    matchesWallet &&
                    matchesCategory
        }

        val sorted = if (filter.ascending) {
            filtered.sortedWith(
                compareBy<Transaction> { it.date }
                    .thenBy { it.id }
            )
        } else {
            filtered.sortedWith(
                compareByDescending<Transaction> { it.date }
                    .thenByDescending { it.id }
            )
        }

        ActivityUiState(
            transactions = sorted,
            wallets = wallets,
            categories = categories,
            income = sorted
                .filter { it.type == "Income" }
                .sumOf { it.amount },
            expenses = sorted
                .filter { it.type == "Expense" }
                .sumOf { it.amount },
            ascending = filter.ascending,
            selectedWalletId = filter.walletId,
            selectedCategory = filter.category
        )

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ActivityUiState()
    )

    fun search(value: String) {
        filters.value = filters.value.copy(
            query = value
        )
    }

    fun setFilter(value: String) {
        filters.value = filters.value.copy(
            type = value
        )
    }

    fun setWallet(id: String?) {
        filters.value = filters.value.copy(
            walletId = id
        )
    }

    fun setCategory(value: String?) {
        filters.value = filters.value.copy(
            category = value
        )
    }

    fun toggleSort() {
        filters.value = filters.value.copy(
            ascending = !filters.value.ascending
        )
    }
}
