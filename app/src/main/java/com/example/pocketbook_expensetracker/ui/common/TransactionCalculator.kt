package com.example.pocketbook_expensetracker.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pocketbook_expensetracker.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.WeakHashMap

/** The reference-inspired layout is defined in fragment_add_expense.xml.
 * This helper only connects the calculator and the category popup.
 * It does not move or reconstruct the transaction form itself.
 */
object TransactionCalculator {
    private val openDialogs = WeakHashMap<EditText, Dialog>()
    private val labels = WeakHashMap<EditText, TextView>()
    private val types = WeakHashMap<EditText, Pair<TextView, TextView>>()

    fun categorySelected(amount: EditText, category: String) {
        labels[amount]?.text = category
        openDialogs.remove(amount)?.dismiss()
    }

    fun showType(amount: EditText, type: String) {
        val (income, expense) = types[amount] ?: return
        val teal = ContextCompat.getColor(amount.context, R.color.pocket_teal)
        val grey = ContextCompat.getColor(amount.context, R.color.pocket_grey)
        income.text = if (type == "Income") "✓  INCOME" else "INCOME"
        expense.text = if (type == "Expense") "✓  EXPENSE" else "EXPENSE"
        income.setTextColor(if (type == "Income") teal else grey)
        expense.setTextColor(if (type == "Expense") teal else grey)
    }

    fun install(amount: EditText) {
        val root = amount.rootView
        val context = amount.context
        val keypadHost = root.findViewById<LinearLayout>(R.id.calculatorHost) ?: return
        if (keypadHost.findViewWithTag<View>("pocket_calculator") != null) return

        val teal = ContextCompat.getColor(context, R.color.pocket_teal)
        val navy = ContextCompat.getColor(context, R.color.pocket_navy)
        val grey = ContextCompat.getColor(context, R.color.pocket_grey)
        val white = Color.WHITE
        val border = ContextCompat.getColor(context, R.color.pocket_border)
        val categoryLabel = root.findViewById<TextView>(R.id.tvCategorySelected)
        val incomeTab = root.findViewById<TextView>(R.id.tvIncomeTab)
        val expenseTab = root.findViewById<TextView>(R.id.tvExpenseTab)
        if (categoryLabel != null) labels[amount] = categoryLabel
        if (incomeTab != null && expenseTab != null) types[amount] = incomeTab to expenseTab
        showType(amount, "Expense")

        fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
        fun shape(fill: Int, borderColor: Int = border) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(9).toFloat()
            setStroke(dp(1), borderColor)
        }

        // Keep existing category views and their listeners: selection, creation and long-press edit.
        val grid = root.findViewById<LinearLayout>(R.id.categoryDefaultGrid)
        val extras = root.findViewById<LinearLayout>(R.id.customCategoryContainer)
        val boxCategory = root.findViewById<View>(R.id.boxCategory)
        boxCategory?.setOnClickListener {
            if (grid == null || extras == null || openDialogs[amount]?.isShowing == true) return@setOnClickListener
            val gridParent = grid.parent as? ViewGroup ?: return@setOnClickListener
            val extraParent = extras.parent as? ViewGroup ?: return@setOnClickListener
            val gridIndex = gridParent.indexOfChild(grid)
            val extraIndex = extraParent.indexOfChild(extras)
            val originalGridParams = grid.layoutParams
            val originalExtrasParams = extras.layoutParams
            gridParent.removeView(grid)
            extraParent.removeView(extras)
            grid.visibility = View.VISIBLE
            extras.visibility = View.VISIBLE

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(17), dp(16), dp(15))
                isFocusableInTouchMode = true
            }
            content.addView(TextView(context).apply {
                text = "Choose Category"
                textSize = 19f
                typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
                setTextColor(navy)
            })
            content.addView(TextView(context).apply {
                text = "Tap to select. Long-press your custom categories to edit."
                textSize = 12f
                setTextColor(grey)
                setPadding(0, dp(7), 0, dp(13))
            })
            val tiles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            tiles.addView(grid, LinearLayout.LayoutParams(-1, -2))
            tiles.addView(extras, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            val scroll = ScrollView(context).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                addView(tiles, ViewGroup.LayoutParams(-1, -2))
            }
            // Wrap its contents; only cap height if categories exceed the screen.
            content.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply {
                // A normal eight-tile grid is compact; large collections may scroll.
            })
            val close = TextView(context).apply {
                text = "Done"
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textSize = 14f
                typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
                setTextColor(teal)
                setPadding(dp(8), dp(12), dp(8), dp(3))
            }
            content.addView(close, LinearLayout.LayoutParams(-1, dp(48)))
            val dialog = Dialog(context)
            dialog.setContentView(content)
            dialog.window?.apply {
                setBackgroundDrawable(GradientDrawable().apply {
                    setColor(white)
                    cornerRadius = dp(22).toFloat()
                })
                setGravity(Gravity.CENTER)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                attributes = attributes.apply {
                    width = (context.resources.displayMetrics.widthPixels * 0.94f).toInt()
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    dimAmount = 0.46f
                    windowAnimations = 0
                }
            }
            dialog.setOnDismissListener {
                openDialogs.remove(amount)
                (grid.parent as? ViewGroup)?.removeView(grid)
                (extras.parent as? ViewGroup)?.removeView(extras)
                grid.visibility = View.VISIBLE
                extras.visibility = View.VISIBLE
                gridParent.addView(grid, gridIndex.coerceAtMost(gridParent.childCount), originalGridParams)
                extraParent.addView(extras, extraIndex.coerceAtMost(extraParent.childCount), originalExtrasParams)
            }
            close.setOnClickListener { dialog.dismiss() }
            openDialogs[amount] = dialog
            dialog.show()
        }

        amount.showSoftInputOnFocus = false
        amount.setOnClickListener {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(amount.windowToken, 0)
        }

        var first: BigDecimal? = null
        var operation: String? = null
        var replaceNext = false
        fun current() = amount.text.toString().ifBlank { "0" }
        fun display(value: String) {
            amount.setText(value)
            amount.setSelection(amount.text.length)
        }
        fun asNumber() = current().toBigDecimalOrNull()
        fun evaluate(): Boolean {
            val a = first ?: return false
            val b = asNumber() ?: return false
            val result = when (operation) {
                "+" -> a.add(b)
                "−" -> a.subtract(b)
                "×" -> a.multiply(b)
                "÷" -> {
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        amount.error = "Cannot divide by zero"
                        return false
                    }
                    a.divide(b, 2, RoundingMode.HALF_UP)
                }
                else -> return false
            }
            if (result.signum() < 0) {
                amount.error = "Amount cannot be negative"
                return false
            }
            display(result.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
            first = null
            operation = null
            return true
        }
        fun press(key: String) {
            when (key) {
                in "0".."9" -> {
                    val previous = if (replaceNext) "0" else current()
                    if (previous.count { it.isDigit() } >= 12) return
                    if (previous.contains('.') && previous.substringAfter('.').length >= 2) return
                    display(if (previous == "0") key else previous + key)
                    replaceNext = false
                }
                "." -> {
                    val previous = if (replaceNext) "0" else current()
                    if (!previous.contains('.')) display("$previous.")
                    replaceNext = false
                }
                "⌫" -> {
                    display(if (replaceNext) "0" else current().dropLast(1).ifBlank { "0" })
                    replaceNext = false
                }
                "C" -> { display("0"); first = null; operation = null; replaceNext = false }
                "+", "−", "×", "÷" -> {
                    if (first != null && !replaceNext && !evaluate()) return
                    first = asNumber()
                    operation = key
                    replaceNext = true
                }
                "=" -> { if (evaluate()) replaceNext = true }
            }
        }
        root.findViewById<View>(R.id.btnAmountBackspace)?.apply {
            setOnClickListener { press("⌫") }
            setOnLongClickListener { press("C"); true }
        }
        val keypad = LinearLayout(context).apply {
            tag = "pocket_calculator"
            orientation = LinearLayout.VERTICAL
        }
        val keys = listOf(
            listOf("+", "7", "8", "9"),
            listOf("−", "4", "5", "6"),
            listOf("×", "1", "2", "3"),
            listOf("÷", "0", ".", "=")
        )
        // Compact key height according to the screen's usable area.
        // The entire keypad should fit on common Samsung phones;
        // ScrollView remains usable on shorter screens and in Edit mode.

        val screenHeightDp =
            context.resources.displayMetrics.heightPixels /
                    context.resources.displayMetrics.density

        val keyHeightDp = when {
            screenHeightDp < 690f -> dp(44)
            screenHeightDp < 790f -> dp(50)
            else -> dp(57)
        }

// Keep references to the calculator rows.
// We will adjust their heights after layout.
        val calculatorRows = mutableListOf<LinearLayout>()


        keys.forEachIndexed { r, values ->

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }

            calculatorRows.add(row)

            values.forEachIndexed { c, key ->

                val accent = c == 0 || key == "="

                val button = TextView(context).apply {
                    text = key
                    textSize = 23f
                    gravity = Gravity.CENTER

                    typeface = PocketFont.bold(context)

                    setTextColor(
                        if (accent) white else navy
                    )

                    background = shape(
                        if (accent) teal else white,
                        if (accent) teal else border
                    )

                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        press(key)
                    }
                }

                row.addView(
                    button,
                    LinearLayout.LayoutParams(
                        0,
                        keyHeightDp,
                        1f
                    ).apply {
                        if (c > 0) {
                            marginStart = dp(6)
                        }
                    }
                )
            }

            keypad.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (r > 0) {
                        topMargin = dp(5)
                    }
                }
            )
        }

        keypadHost.addView(keypad)

// Adjust calculator height after Android
// has measured the available screen space.
        keypadHost.post {

            val form = keypadHost.parent as? LinearLayout
                ?: return@post

            val scrollView = form.parent as? ScrollView
                ?: return@post

            val timeBox = root.findViewById<View>(
                R.id.boxTime
            ) ?: return@post

            val dateTimeRow = timeBox.parent as? View
                ?: return@post

            // Available height inside the scrolling screen.
            val availableHeight = scrollView.height

            // Bottom of the date/time controls.
            val contentBottom =
                dateTimeRow.bottom +
                        form.paddingBottom

            // Space left below the form.
            val remainingSpace = (
                    availableHeight - contentBottom - dp(12)
                    ).coerceAtLeast(0)

            // Distribute remaining space across four rows.
            // Limit growth so buttons never become enormous.
            val extraPerRow = (
                    remainingSpace / calculatorRows.size
                    ).coerceAtMost(dp(28))

            if (extraPerRow > 0) {

                calculatorRows.forEach { row ->

                    for (i in 0 until row.childCount) {

                        val button = row.getChildAt(i)

                        val params =
                            button.layoutParams as
                                    LinearLayout.LayoutParams

                        params.height =
                            keyHeightDp + extraPerRow

                        button.layoutParams = params
                    }
                }
            }
        }

    }
}
