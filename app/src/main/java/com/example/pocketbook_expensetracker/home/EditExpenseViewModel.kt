package com.example.pocketbook_expensetracker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import com.example.pocketbook_expensetracker.util.TransactionValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditExpenseViewModel : ViewModel() {

    private val repo = TransactionRepo.getInstance()

    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadTransaction(id: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _transaction.value = repo.getTransactionById(id)
                if (_transaction.value == null) _error.value = "Transaction not found"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load transaction"
            } finally {
                _loading.value = false
            }
        }
    }

    fun validateAmount(amount: String): String? = TransactionValidator.validateAmount(amount)

    fun updateTransaction(transaction: Transaction, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.updateTransaction(transaction)
                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update transaction"
            }
        }
    }

    fun deleteTransaction(id: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteTransaction(id)
                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete transaction"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
