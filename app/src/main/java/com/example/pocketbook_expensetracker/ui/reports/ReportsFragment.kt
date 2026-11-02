
package com.example.pocketbook_expensetracker.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.databinding.FragmentReportsBinding
import com.example.pocketbook_expensetracker.databinding.ItemCategoryReportBinding
import kotlinx.coroutines.launch
import java.util.Locale

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels()

    private val teal = Color.rgb(20, 127, 121)
    private val grey = Color.rgb(113, 132, 138)

    private val colours = listOf(
        Color.rgb(12, 144, 147),
        Color.rgb(229, 172, 102),
        Color.rgb(124, 145, 202),
        Color.rgb(216, 92, 101),
        Color.rgb(142, 111, 211),
        Color.rgb(70, 185, 150)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(
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

        binding.navHome.setOnClickListener {
            openTab(R.id.homeFragment)
        }

        binding.navActivity.setOnClickListener {
            openTab(R.id.transactionsFragment)
        }

        binding.tabWeek.setOnClickListener {
            viewModel.setPeriod("Week")
        }

        binding.tabMonth.setOnClickListener {
            viewModel.setPeriod("Month")
        }

        binding.tabYear.setOnClickListener {
            viewModel.setPeriod("Year")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    updateReport(state)
                }
            }
        }
    }

    private fun updateReport(state: ReportUiState) {
        updateTabs(state.period)

        binding.tvTotalSpending.text =
            money(state.totalSpending)

        binding.tvPeriodDescription.text = when (state.period) {
            "Week" -> "This week"
            "Year" -> "This year"
            else -> "This month"
        }

        binding.tvChartCenter.text =
            "${state.period}\nSpending"

        val count = state.categories.size

        binding.tvCategoryCount.text =
            "$count ${if (count == 1) "category" else "categories"}"

        updateCategories(state.categories)

        binding.donutChart.setData(
            state.categories.map { it.amount }
        )

        binding.spendingTrendChart.setData(
            state.trendValues,
            state.trendLabels
        )
    }

    private fun updateTabs(selectedPeriod: String) {
        val tabs = listOf(
            "Week" to binding.tabWeek,
            "Month" to binding.tabMonth,
            "Year" to binding.tabYear
        )

        tabs.forEach { (period, tab) ->
            val selected = period == selectedPeriod

            tab.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_teal_square
                } else {
                    0
                }
            )

            tab.setTextColor(
                if (selected) Color.WHITE else grey
            )

            tab.setTypeface(
                null,
                if (selected) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL
            )
        }
    }

    private fun updateCategories(
        categories: List<CategoryReport>
    ) {
        binding.categoryContainer.removeAllViews()

        if (categories.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No expenses for this period"
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(grey)
                setPadding(0, 32, 0, 32)
            }

            binding.categoryContainer.addView(empty)
            return
        }

        categories.forEachIndexed { index, category ->
            val row = ItemCategoryReportBinding.inflate(
                layoutInflater,
                binding.categoryContainer,
                false
            )

            row.tvCategory.text = category.category

            row.tvPercentage.text = String.format(
                Locale.US,
                "%.1f%%",
                category.percentage
            )

            row.tvAmount.text = money(category.amount)

            row.viewColor.setBackgroundColor(
                colours[index % colours.size]
            )

            binding.categoryContainer.addView(row.root)
        }
    }

    private fun openTab(destination: Int) {
        val controller = findNavController()

        if (controller.currentDestination?.id == destination) {
            return
        }

        controller.navigate(
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

    private fun money(value: Double): String =
        String.format(
            Locale.US,
            "RM %,.2f",
            value
        )

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
