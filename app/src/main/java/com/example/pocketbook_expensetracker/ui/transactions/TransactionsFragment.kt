
package com.example.pocketbook_expensetracker.ui.transactions

import android.app.AlertDialog
import com.example.pocketbook_expensetracker.ui.common.PocketFormUi
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.databinding.FragmentTransactionsBinding
import kotlinx.coroutines.launch
import java.util.Locale

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransactionsViewModel by viewModels()

    private lateinit var adapter: TransactionAdapter

    private var currentState = ActivityUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TransactionAdapter(emptyList()) { transaction ->
            val args = Bundle().apply {
                putString("transactionId", transaction.id)
            }

            findNavController().navigate(
                R.id.action_transactionsFragment_to_editExpenseFragment,
                args
            )
        }

        binding.rvTransactions.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvTransactions.adapter = adapter

        binding.btnAdd.setOnClickListener {
            findNavController().navigate(
                R.id.action_transactionsFragment_to_addExpenseFragment
            )
        }

        binding.navHome.setOnClickListener {
            openTab(R.id.homeFragment)
        }

        binding.navReports.setOnClickListener {
            openTab(R.id.reportsFragment)
        }

        binding.btnSort.setOnClickListener {
            viewModel.toggleSort()
        }

        binding.etSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    viewModel.search(s.toString())
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )

        binding.chipAll.setOnClickListener {
            viewModel.setFilter("All")
        }

        binding.chipExpenses.setOnClickListener {
            viewModel.setFilter("Expenses")
        }

        binding.chipIncome.setOnClickListener {
            viewModel.setFilter("Income")
        }

        binding.btnWalletFilter.setOnClickListener {
            showWalletFilter()
        }

        binding.btnCategoryFilter.setOnClickListener {
            showCategoryFilter()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    currentState = state
                    updateScreen(state)
                }
            }
        }
    }

    private fun updateScreen(state: ActivityUiState) {
        adapter.updateList(
            state.transactions,
            state.wallets
        )

        val count = state.transactions.size

        binding.tvCount.text =
            "$count ${if (count == 1) "transaction" else "transactions"}"

        val net = state.income - state.expenses

        binding.tvNet.text =
            "${if (net < 0) "−" else "+"}${money(kotlin.math.abs(net))}"

        binding.tvIncome.text =
            "Income ${money(state.income)}"

        binding.tvExpenses.text =
            "Expenses ${money(state.expenses)}"

        binding.tvSortLabel.text =
            if (state.ascending) "Oldest first"
            else "Newest first"

        binding.tvEmpty.visibility =
            if (count == 0) View.VISIBLE
            else View.GONE

        val selectedWallet = state.wallets.firstOrNull {
            it.id == state.selectedWalletId
        }

        binding.btnWalletFilter.text =
            if (state.selectedWalletId == null) {
                "All wallets"
            } else {
                "${if (state.selectedWalletId == UNASSIGNED_FILTER) "Unassigned" else selectedWallet?.name ?: "Unrecognised wallet"}"
            }

        binding.btnCategoryFilter.text =
            "${state.selectedCategory ?: "All categories"}"
    }

    private fun showWalletFilter() {
        val names = listOf("All wallets", "Unassigned") + currentState.wallets.map { it.name }
        val selected = when (currentState.selectedWalletId) {
            null -> 0
            UNASSIGNED_FILTER -> 1
            else -> currentState.wallets.indexOfFirst {
                it.id == currentState.selectedWalletId
            }.let { if (it < 0) 0 else it + 2 }
        }
        PocketFormUi.choose(requireContext(), "Filter by wallet", names, selected) { index ->
            when (index) {
                0 -> viewModel.setWallet(null)
                1 -> viewModel.setWallet(UNASSIGNED_FILTER)
                else -> viewModel.setWallet(currentState.wallets[index - 2].id)
            }
        }
    }

    private fun showCategoryFilter() {
        val names = listOf("All categories") + currentState.categories
        val selected = currentState.selectedCategory?.let { names.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        PocketFormUi.choose(requireContext(), "Filter by category", names, selected) { index ->
            viewModel.setCategory(if (index == 0) null else names[index])
        }
    }

    private fun openTab(destination: Int) {
        val navController = findNavController()

        if (navController.currentDestination?.id == destination) {
            return
        }

        navController.navigate(
            destination,
            null,
            androidx.navigation.navOptions {
                popUpTo(R.id.homeFragment) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        )
    }

    private fun money(amount: Double): String =
        String.format(
            Locale.US,
            "RM %,.2f",
            amount
        )

    override fun onDestroyView() {
        binding.rvTransactions.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val UNASSIGNED_FILTER =
            "__UNASSIGNED_WALLET__"
    }
}
