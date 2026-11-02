
package com.example.pocketbook_expensetracker.ui.budgets

import android.app.AlertDialog
import com.example.pocketbook_expensetracker.ui.common.PocketFormUi
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import com.example.pocketbook_expensetracker.R
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.data.model.Budget
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.repo.LocalBudgetRepo
import com.example.pocketbook_expensetracker.data.repo.LocalCategoryRepo
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class BudgetsFragment : Fragment() {

    private val categoryRepo by lazy { LocalCategoryRepo(requireContext().applicationContext) }

    private val budgetRepo by lazy {
        LocalBudgetRepo.getInstance()
    }

    private val transactionRepo by lazy {
        TransactionRepo.getInstance()
    }

    private val navy = Color.rgb(24, 47, 57)
    private val teal = Color.rgb(20, 127, 121)
    private val grey = Color.rgb(113, 132, 138)
    private val red = Color.rgb(216, 92, 101)
    private val amber = Color.rgb(210, 145, 55)

    private val pageBackgroundColor = Color.rgb(244, 247, 248)

    private var month = YearMonth.now()
    private var budgets: List<Budget> = emptyList()
    private var transactions: List<Transaction> = emptyList()

    private lateinit var monthText: TextView
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getString("budget_month")?.let {
            month = runCatching {
                YearMonth.parse(it)
            }.getOrDefault(YearMonth.now())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackgroundColor)
            setPadding(20.dp(), 22.dp(), 20.dp(), 12.dp())
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_pb_chevron_left)
            imageTintList = ColorStateList.valueOf(navy)
            background = rounded(Color.rgb(244, 247, 248))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = "Back"
            setOnClickListener {
                findNavController().popBackStack()
            }
        }

        val titleColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleColumn.addView(
            label("MONTHLY PLANNING", 11f, grey, true)
        )
        titleColumn.addView(
            label("Budgets", 25f, navy, true)
        )

        header.addView(
            back,
            LinearLayout.LayoutParams(38.dp(), 48.dp())
        )
        header.addView(titleColumn)

        root.addView(header)

        val monthRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20.dp(), 0, 18.dp())
        }

        val previous = monthArrow("‹") {
            month = month.minusMonths(1)
            render()
        }

        monthText = label("", 17f, navy, true).apply {
            gravity = Gravity.CENTER
        }

        val next = monthArrow("›") {
            month = month.plusMonths(1)
            render()
        }

        monthRow.addView(
            previous,
            LinearLayout.LayoutParams(46.dp(), 46.dp())
        )
        monthRow.addView(
            monthText,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        monthRow.addView(
            next,
            LinearLayout.LayoutParams(46.dp(), 46.dp())
        )

        root.addView(monthRow)

        val scroll = ScrollView(requireContext()).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
        }

        listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 18.dp())
        }

        scroll.addView(listContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val add = Button(requireContext()).apply {
            text = "+ Add Monthly Budget"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(teal)
            setOnClickListener {
                openBudgetDialog(null)
            }
        }

        root.addView(
            add,
            LinearLayout.LayoutParams(-1, 54.dp())
        )

        return root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        render()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    budgetRepo.getBudgets(),
                    transactionRepo.getTransactions()
                ) { b, t -> b to t }
                    .collect { (b, t) ->
                        budgets = b
                        transactions = t
                        render()
                    }
            }
        }
    }

    private fun render() {
        monthText.text = month.format(
            DateTimeFormatter.ofPattern(
                "MMMM yyyy",
                Locale.ENGLISH
            )
        )

        listContainer.removeAllViews()

        val currentBudgets = budgets.filter {
            it.month == month.toString()
        }.sortedBy {
            it.category
        }

        val expenses = transactions.filter {
            it.type == "Expense" &&
                    it.date.startsWith("${month}-")
        }

        val categories = currentBudgets.map {
            it.category
        }.toSet()

        val limit = currentBudgets.sumOf {
            it.limitAmount
        }

        val spent = expenses
            .filter { it.category in categories }
            .sumOf { it.amount }

        addSummary(spent, limit, currentBudgets.size)

        listContainer.addView(
            label("Category Budgets", 18f, navy, true).apply {
                setPadding(0, 22.dp(), 0, 14.dp())
            }
        )

        if (currentBudgets.isEmpty()) {
            listContainer.addView(
                label(
                    "No budgets set for this month.\n" +
                            "Create one using the button below.",
                    14f,
                    grey
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(
                        12.dp(), 42.dp(),
                        12.dp(), 42.dp()
                    )
                    background = rounded(Color.WHITE)
                }
            )
        }

        currentBudgets.forEach { budget ->
            val used = expenses
                .filter { it.category == budget.category }
                .sumOf { it.amount }

            addBudgetCard(budget, used)
        }

        val unbudgeted = expenses
            .filter { it.category !in categories }
            .sumOf { it.amount }

        if (unbudgeted > 0) {
            listContainer.addView(
                label(
                    "Other spending not covered by budgets: " +
                            money(unbudgeted),
                    12f,
                    grey
                ).apply {
                    setPadding(
                        4.dp(), 14.dp(),
                        4.dp(), 12.dp()
                    )
                }
            )
        }
    }

    private fun addSummary(
        spent: Double,
        limit: Double,
        count: Int
    ) {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(navy)
            setPadding(
                22.dp(), 22.dp(),
                22.dp(), 22.dp()
            )
        }

        val remaining = limit - spent

        card.addView(
            label("BUDGET REMAINING", 12f, Color.LTGRAY)
        )

        val mainAmount = label(
            money(remaining),
            30f,
            Color.WHITE,
            true
        ).apply {
            setPadding(0, 10.dp(), 0, 10.dp())
        }

        card.addView(mainAmount)

        val progress = makeProgress(
            spent, limit,
            if (remaining < 0) red else Color.rgb(121, 205, 182)
        )

        card.addView(progress)

        card.addView(
            label(
                "${money(spent)} spent of ${money(limit)}",
                13f,
                Color.LTGRAY
            ).apply {
                setPadding(0, 12.dp(), 0, 4.dp())
            }
        )

        card.addView(
            label(
                "$count categories budgeted" +
                        if (remaining < 0) " · Over budget" else "",
                12f,
                Color.LTGRAY
            )
        )

        listContainer.addView(card)
    }

    private fun addBudgetCard(
        budget: Budget,
        spent: Double
    ) {
        val remaining = budget.limitAmount - spent
        val ratio = spent / budget.limitAmount

        val colour = when {
            ratio >= 1.0 -> red
            ratio >= 0.8 -> amber
            else -> teal
        }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE)
            elevation = 2.dp().toFloat()
            setPadding(
                18.dp(), 18.dp(),
                18.dp(), 18.dp()
            )
            setOnClickListener {
                openBudgetDialog(budget)
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            label(budget.category, 17f, navy, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        row.addView(
            label(
                "${(ratio * 100).toInt()}%",
                14f,
                colour,
                true
            )
        )

        card.addView(row)

        card.addView(
            label(
                "${money(spent)} of ${money(budget.limitAmount)}",
                13f,
                grey
            ).apply {
                setPadding(0, 10.dp(), 0, 10.dp())
            }
        )

        card.addView(
            makeProgress(spent, budget.limitAmount, colour)
        )

        val message = when {
            remaining < 0 ->
                "${money(-remaining)} over budget"
            ratio >= 0.8 ->
                "Near the limit · ${money(remaining)} remaining"
            else ->
                "${money(remaining)} remaining"
        }

        card.addView(
            label(message, 13f, colour).apply {
                setPadding(0, 12.dp(), 0, 0)
            }
        )

        listContainer.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 12.dp()
            }
        )
    }

    private fun openBudgetDialog(existing: Budget?) {
        val form = PocketFormUi.form(requireContext())

        val categoryOptions = (
                listOf(
                    "Food", "Groceries", "Transport",
                    "Bills", "Fun", "Health",
                    "Shopping"
                ) +
                        transactions.map { it.category }
                            .filter { it.isNotBlank() } +
                        budgets.map { it.category } +
                        categoryRepo.categories.value
                ).distinct().sorted()

        val categorySpinner = Spinner(requireContext())
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categoryOptions
        )

        categoryAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        categorySpinner.adapter = categoryAdapter

        if (existing != null) {
            categorySpinner.setSelection(
                categoryOptions.indexOf(existing.category)
                    .coerceAtLeast(0)
            )
            categorySpinner.isEnabled = false
        }

        val amountInput = EditText(requireContext()).apply {
            hint = "e.g. 500.00"
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL

            existing?.let {
                setText(it.limitAmount.toString())
            }
        }

        form.addView(PocketFormUi.label(requireContext(), "Category"))
        PocketFormUi.styleSpinner(categorySpinner)
        form.addView(categorySpinner)
        form.addView(PocketFormUi.label(requireContext(), "Monthly limit (RM)"))
        PocketFormUi.styleInput(amountInput)
        form.addView(amountInput)

        val dialog = PocketFormUi.dialog(requireContext())
            .setTitle(
                if (existing == null) "Add Budget"
                else "Edit Budget"
            )
            .setView(form)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .apply {
                if (existing != null) {
                    setNeutralButton("Delete", null)
                }
            }
            .create()

        dialog.setOnShowListener {
            val save = dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            )

            save.setOnClickListener {
                val amount = try {
                    BigDecimal(
                        amountInput.text.toString().trim()
                    ).setScale(
                        2, RoundingMode.UNNECESSARY
                    ).also {
                        require(it.signum() > 0)
                    }.toDouble().also {
                        require(it.isFinite())
                    }
                } catch (e: Exception) {
                    amountInput.error =
                        "Enter a positive amount with max 2 decimals"
                    return@setOnClickListener
                }

                save.isEnabled = false

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        budgetRepo.saveBudget(
                            category =
                                categorySpinner.selectedItem.toString(),
                            month = existing?.month
                                ?: month.toString(),
                            amount = amount
                        )
                        dialog.dismiss()
                    } catch (e: Exception) {
                        toast(e.message ?: "Cannot save budget")
                        save.isEnabled = true
                    }
                }
            }

            if (existing != null) {
                dialog.getButton(
                    AlertDialog.BUTTON_NEUTRAL
                ).setOnClickListener {
                    dialog.dismiss()

                    PocketFormUi.dialog(requireContext())
                        .setTitle("Delete budget?")
                        .setMessage(
                            "The budget will be removed. " +
                                    "Your expenses will remain."
                        )
                        .setPositiveButton("Delete") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    budgetRepo.deleteBudget(existing.id)
                                } catch (e: Exception) {
                                    toast(
                                        e.message ?: "Cannot delete budget"
                                    )
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        dialog.show()
        PocketFormUi.styleDialog(dialog)
    }

    private fun makeProgress(
        spent: Double,
        limit: Double,
        colour: Int
    ) = ProgressBar(
        requireContext(),
        null,
        android.R.attr.progressBarStyleHorizontal
    ).apply {
        max = 1000
        progress = if (limit <= 0) 0 else {
            (spent / limit * 1000)
                .toInt()
                .coerceIn(0, 1000)
        }
        progressTintList = ColorStateList.valueOf(colour)
    }

    private fun monthArrow(
        symbol: String,
        action: () -> Unit
    ) = ImageButton(requireContext()).apply {
        setImageResource(if (symbol == "‹") R.drawable.ic_pb_chevron_left else R.drawable.ic_pb_chevron_right)
        imageTintList = ColorStateList.valueOf(teal)
        scaleType = android.widget.ImageView.ScaleType.CENTER
        background = rounded(Color.WHITE)
        contentDescription = if (symbol == "‹") "Previous month" else "Next month"
        setOnClickListener { action() }
    }

    private fun label(
        value: String,
        size: Float,
        colour: Int,
        bold: Boolean = false
    ) = TextView(requireContext()).apply {
        text = value
        textSize = size
        setTextColor(colour)
        if (bold) {
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
        }
    }

    private fun rounded(colour: Int) =
        GradientDrawable().apply {
            setColor(colour)
            cornerRadius = 18.dp().toFloat()
        }

    private fun money(value: Double) =
        String.format(Locale.US, "RM %,.2f", value)

    private fun toast(message: String) {
        if (isAdded) {
            Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("budget_month", month.toString())
        super.onSaveInstanceState(outState)
    }
}
