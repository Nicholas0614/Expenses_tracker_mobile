
package com.example.pocketbook_expensetracker.ui.integrity

import com.example.pocketbook_expensetracker.R
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.data.integrity.AuditLevel
import com.example.pocketbook_expensetracker.data.integrity.AuditReport
import com.example.pocketbook_expensetracker.data.integrity.FinanceIntegrityChecker
import com.example.pocketbook_expensetracker.util.Money
import kotlinx.coroutines.launch

class IntegrityFragment : Fragment() {

    private lateinit var results: LinearLayout
    private lateinit var runButton: Button

    private val checker by lazy {
        FinanceIntegrityChecker(
            requireContext().applicationContext
        )
    }

    private val navy = Color.rgb(24, 47, 57)
    private val teal = Color.rgb(20, 127, 121)
    private val grey = Color.rgb(113, 132, 138)
    private val red = Color.rgb(216, 92, 101)
    private val amber = Color.rgb(185, 120, 38)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 248))
            setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = Button(requireContext()).apply {
            text = "‹"
            textSize = 24f
            isAllCaps = false
            setOnClickListener {
                findNavController().popBackStack()
            }
        }

        val title = text(
            "Financial Audit",
            23f,
            navy,
            true
        )

        header.addView(
            back,
            LinearLayout.LayoutParams(55.dp(), 50.dp())
        )
        header.addView(title)
        root.addView(header)

        root.addView(
            text(
                "Check your local financial data for " +
                        "broken wallet links, invalid amounts " +
                        "and reconciliation problems.\n\n" +
                        "This audit never changes your records.",
                14f,
                grey
            ).apply {
                setPadding(0, 18.dp(), 0, 20.dp())
            }
        )

        runButton = Button(requireContext()).apply {
            text = "Run Financial Audit"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(teal)
            setOnClickListener { performAudit() }
        }

        root.addView(
            runButton,
            LinearLayout.LayoutParams(-1, 54.dp())
        )

        val scroll = ScrollView(requireContext()).apply {
            isVerticalScrollBarEnabled = false
        }

        results = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20.dp(), 0, 20.dp())
        }

        scroll.addView(results)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        return root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        performAudit()
    }

    private fun performAudit() {
        runButton.isEnabled = false
        results.removeAllViews()
        results.addView(
            text("Checking local database...", 14f, grey)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val report = checker.runAudit()

                if (view != null && isAdded) {
                    displayReport(report)
                }
            } catch (e: Exception) {
                if (view != null && isAdded) {
                    results.removeAllViews()
                    results.addView(
                        text(
                            "Audit could not complete:\n" +
                                    (e.message ?: "Unknown error"),
                            14f,
                            red
                        )
                    )
                }
            } finally {
                if (view != null && isAdded) {
                    runButton.isEnabled = true
                }
            }
        }
    }

    private fun displayReport(report: AuditReport) {
        results.removeAllViews()

        val status = when {
            report.errorCount > 0 ->
                "Errors detected"

            report.warningCount > 0 ->
                "Audit completed with warnings"

            else ->
                "Audit completed successfully"
        }

        val statusColour = when {
            report.errorCount > 0 -> red
            report.warningCount > 0 -> amber
            else -> teal
        }

        val summary = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE)
            setPadding(
                18.dp(), 18.dp(),
                18.dp(), 18.dp()
            )
        }

        summary.addView(
            text(status, 19f, statusColour, true)
        )

        summary.addView(
            text(
                "${report.errorCount} errors · " +
                        "${report.warningCount} warnings",
                13f,
                grey
            ).apply {
                setPadding(0, 8.dp(), 0, 16.dp())
            }
        )

        summary.addView(
            text(
                "Wallets: ${report.walletCount}\n" +
                        "Transactions: ${report.transactionCount}\n" +
                        "Transfers: ${report.transferCount}\n" +
                        "Budgets: ${report.budgetCount}",
                14f,
                navy
            )
        )

        results.addView(summary)

        addSection("Balance Reconciliation")

        val balances = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE)
            setPadding(
                18.dp(), 18.dp(),
                18.dp(), 18.dp()
            )
        }

        balances.addView(
            text(
                "Opening balances: " +
                        Money.format(report.openingSen),
                14f,
                navy
            )
        )

        balances.addView(
            text(
                "Linked income minus expenses: " +
                        Money.format(report.transactionNetSen),
                14f,
                navy
            )
        )

        balances.addView(
            text(
                "Net impact of transfers: " +
                        Money.format(report.transferNetSen),
                14f,
                navy
            )
        )

        balances.addView(
            text(
                "Calculated total wallet balance: " +
                        Money.format(report.calculatedTotalSen),
                16f,
                teal,
                true
            ).apply {
                setPadding(0, 14.dp(), 0, 0)
            }
        )

        results.addView(balances)

        addSection("Audit Findings")

        report.issues.forEach { issue ->
            val colour = when (issue.level) {
                AuditLevel.ERROR -> red
                AuditLevel.WARNING -> amber
                AuditLevel.INFO -> teal
            }

            val line = text(
                "${issue.level}: ${issue.message}",
                14f,
                colour
            ).apply {
                background = rounded(Color.WHITE)
                setPadding(
                    15.dp(), 15.dp(),
                    15.dp(), 15.dp()
                )
            }

            results.addView(
                line,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 8.dp()
                }
            )
        }

        results.addView(
            text(
                "A passing audit checks the current " +
                        "records; it does not replace backups " +
                        "or automated migration tests.",
                12f,
                grey
            ).apply {
                setPadding(2.dp(), 18.dp(), 2.dp(), 4.dp())
            }
        )
    }

    private fun addSection(value: String) {
        results.addView(
            text(value, 17f, navy, true).apply {
                setPadding(0, 22.dp(), 0, 12.dp())
            }
        )
    }

    private fun text(
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
            cornerRadius = 16.dp().toFloat()
        }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()
}
