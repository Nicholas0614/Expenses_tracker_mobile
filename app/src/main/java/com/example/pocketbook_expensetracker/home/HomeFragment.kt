
package com.example.pocketbook_expensetracker.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.data.model.Budget
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.model.Wallet
import com.example.pocketbook_expensetracker.data.model.WalletTransfer
import com.example.pocketbook_expensetracker.data.repo.LocalBudgetRepo
import com.example.pocketbook_expensetracker.data.repo.LocalTransferRepo
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import com.example.pocketbook_expensetracker.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class DashboardData(
    val wallets: List<Wallet>,
    val transactions: List<Transaction>,
    val transfers: List<WalletTransfer>,
    val budgets: List<Budget>
)

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val walletRepo by lazy {
        LocalWalletRepo.getInstance()
    }

    private val transactionRepo by lazy {
        TransactionRepo.getInstance()
    }

    private val transferRepo by lazy {
        LocalTransferRepo.getInstance()
    }

    private val budgetRepo by lazy {
        LocalBudgetRepo.getInstance()
    }

    private val navy = Color.rgb(20, 33, 63)
    private val grey = Color.rgb(102, 119, 149)
    private val teal = Color.rgb(12, 144, 147)
    private val red = Color.rgb(233, 54, 85)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
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

        val today = LocalDate.now()

        binding.tvDateHeader.text = today.format(
            DateTimeFormatter.ofPattern(
                "EEEE, d MMMM",
                Locale.ENGLISH
            )
        ).uppercase(Locale.ENGLISH)

        // Navigation
        binding.btnWallets.setOnClickListener {
            findNavController().navigate(
                R.id.action_homeFragment_to_walletsFragment
            )
        }

        binding.btnBudgets.setOnClickListener {
            openBudgets()
        }

        binding.btnViewBudgets.setOnClickListener {
            openBudgets()
        }

        binding.btnBackup.setOnClickListener {
            findNavController().navigate(
                R.id.action_homeFragment_to_backupFragment
            )
        }

        binding.btnSeeAll.setOnClickListener {
            openTab(R.id.transactionsFragment)
        }

        binding.navActivity.setOnClickListener {
            openTab(R.id.transactionsFragment)
        }

        binding.navReports.setOnClickListener {
            openTab(R.id.reportsFragment)
        }

        binding.btnAddExpense.setOnClickListener {
            findNavController().navigate(
                R.id.action_homeFragment_to_addExpenseFragment
            )
        }

        // Observe the four local repositories.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    walletRepo.getWallets(),
                    transactionRepo.getTransactions(),
                    transferRepo.getTransfers(),
                    budgetRepo.getBudgets()
                ) { wallets, transactions, transfers, budgets ->
                    DashboardData(
                        wallets,
                        transactions,
                        transfers,
                        budgets
                    )
                }.collect { data ->
                    updateDashboard(data)
                }
            }
        }
    }

    private fun openBudgets() {
        findNavController().navigate(
            R.id.action_homeFragment_to_budgetsFragment
        )
    }

    private fun openTab(destination: Int) {
        findNavController().navigate(
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

    private fun updateDashboard(data: DashboardData) {
        val today = LocalDate.now()
        val month = YearMonth.from(today)

        val monthlyTransactions =
            data.transactions.filter { transaction ->
                transaction.date.startsWith(
                    "${month}-"
                )
            }

        val monthlyIncome = monthlyTransactions
            .filter { it.type == "Income" }
            .sumOf { it.amount }

        val monthlyExpenses = monthlyTransactions
            .filter { it.type == "Expense" }
            .sumOf { it.amount }

        val totalBalance = data.wallets.sumOf { wallet ->
            calculateWalletBalance(
                wallet,
                data.transactions,
                data.transfers
            )
        }

        binding.tvTotalBalance.text = money(totalBalance)
        binding.tvMonthlyIncome.text = money(monthlyIncome)
        binding.tvMonthlyExpense.text = money(monthlyExpenses)

        binding.tvMonthNet.text =
            "Monthly net: ${money(monthlyIncome - monthlyExpenses)}"

        val unassignedCount = data.transactions.count {
            it.walletId == null
        }

        binding.tvUnassigned.visibility =
            if (unassignedCount > 0) View.VISIBLE
            else View.GONE

        binding.tvUnassigned.text =
            "$unassignedCount transactions are not assigned to a wallet"

        updateBudget(
            month,
            data.budgets,
            monthlyTransactions
        )

        displayRecentTransactions(
            data.transactions,
            data.wallets
        )
    }

    private fun calculateWalletBalance(
        wallet: Wallet,
        transactions: List<Transaction>,
        transfers: List<WalletTransfer>
    ): Double {

        val income = transactions
            .filter {
                it.walletId == wallet.id &&
                        it.type == "Income"
            }
            .sumOf { it.amount }

        val expenses = transactions
            .filter {
                it.walletId == wallet.id &&
                        it.type == "Expense"
            }
            .sumOf { it.amount }

        val incoming = transfers
            .filter { it.toWalletId == wallet.id }
            .sumOf { it.amountSen }

        val outgoing = transfers
            .filter { it.fromWalletId == wallet.id }
            .sumOf { it.amountSen }

        return wallet.openingBalance +
                income -
                expenses +
                (incoming - outgoing) / 100.0
    }

    private fun updateBudget(
        month: YearMonth,
        budgets: List<Budget>,
        transactions: List<Transaction>
    ) {
        val current = budgets.filter {
            it.month == month.toString()
        }

        val categories = current.map {
            it.category
        }.toSet()

        val totalLimit = current.sumOf {
            it.limitAmount
        }

        val spent = transactions
            .filter {
                it.type == "Expense" &&
                        it.category in categories
            }
            .sumOf { it.amount }

        binding.tvBudgetSpent.text =
            "${money(spent)} spent"

        binding.tvBudgetLimit.text =
            "of ${money(totalLimit)} budget"

        if (current.isEmpty()) {
            binding.progressBudget.progress = 0
            binding.tvBudgetStatus.text =
                "Set your first monthly budget"
            binding.tvBudgetStatus.setTextColor(grey)
            return
        }

        val percentage = if (totalLimit > 0) {
            (spent / totalLimit * 1000)
                .toInt()
                .coerceIn(0, 1000)
        } else {
            0
        }

        val ratio = if (totalLimit > 0) {
            spent / totalLimit
        } else {
            0.0
        }

        val progressColour = when {
            ratio >= 1.0 -> red
            ratio >= 0.8 -> Color.rgb(220, 139, 42)
            else -> teal
        }

        binding.progressBudget.progress = percentage
        binding.progressBudget.progressTintList =
            ColorStateList.valueOf(progressColour)

        val remaining = totalLimit - spent

        binding.tvBudgetStatus.text = when {
            remaining < 0 ->
                "${money(-remaining)} over budget"

            ratio >= 0.8 ->
                "Near your monthly limit · ${money(remaining)} left"

            else ->
                "${money(remaining)} remaining this month"
        }

        binding.tvBudgetStatus.setTextColor(progressColour)
    }

    private fun displayRecentTransactions(
        transactions: List<Transaction>,
        wallets: List<Wallet>
    ) {
        binding.recentContainer.removeAllViews()

        val recent = transactions
            .sortedByDescending { it.date }
            .take(4)

        if (recent.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No transactions yet.\nTap Add to get started."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(grey)
                setPadding(
                    12.dp(), 32.dp(),
                    12.dp(), 32.dp()
                )
            }

            binding.recentContainer.addView(empty)
            return
        }

        recent.forEachIndexed { index, transaction ->
            if (index > 0) {
                val divider = View(requireContext()).apply {
                    setBackgroundColor(
                        Color.rgb(230, 235, 240)
                    )
                }

                binding.recentContainer.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        -1,
                        1.dp()
                    )
                )
            }

            binding.recentContainer.addView(
                createTransactionRow(transaction, wallets)
            )
        }
    }

    private fun createTransactionRow(
        transaction: Transaction,
        wallets: List<Wallet>
    ): View {

        val context = requireContext()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                6.dp(), 15.dp(),
                6.dp(), 15.dp()
            )
        }

        val symbol = TextView(context).apply {
            text = transaction.icon.ifBlank { "•" }
            textSize = 23f
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                setColor(Color.rgb(228, 244, 243))
                cornerRadius = 14.dp().toFloat()
            }
        }

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val name = TextView(context).apply {
            text = transaction.name
            textSize = 15f
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
            setTextColor(navy)
            maxLines = 1
        }

        val walletName = wallets.firstOrNull {
            it.id == transaction.walletId
        }?.name ?: "Unassigned"

        val subtitle = TextView(context).apply {
            text = "${transaction.category} · $walletName"
            textSize = 12f
            setTextColor(grey)
            maxLines = 1
        }

        details.addView(name)
        details.addView(subtitle)

        val isIncome = transaction.type == "Income"

        val amount = TextView(context).apply {
            text = (
                    if (isIncome) "+" else "−"
                    ) + money(transaction.amount)

            textSize = 14f
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
            setTextColor(
                if (isIncome) teal else red
            )
            gravity = Gravity.END
        }

        row.addView(
            symbol,
            LinearLayout.LayoutParams(
                48.dp(), 48.dp()
            )
        )

        row.addView(
            details,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 12.dp()
                marginEnd = 8.dp()
            }
        )

        row.addView(
            amount,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        row.setOnClickListener {
            openTab(R.id.transactionsFragment)
        }

        return row
    }

    private fun money(amount: Double): String {
        return String.format(
            Locale.US,
            "RM %,.2f",
            amount
        )
    }

    private fun Int.dp(): Int {
        return (
                this * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
