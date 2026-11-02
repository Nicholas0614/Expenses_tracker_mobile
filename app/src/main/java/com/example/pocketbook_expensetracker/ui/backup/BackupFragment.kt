
package com.example.pocketbook_expensetracker.ui.backup

import android.app.AlertDialog
import com.example.pocketbook_expensetracker.ui.common.PocketFormUi
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.data.backup.PocketBookBackup
import com.example.pocketbook_expensetracker.data.repo.LocalBudgetRepo
import com.example.pocketbook_expensetracker.data.repo.LocalTransactionRepo
import com.example.pocketbook_expensetracker.data.repo.LocalTransferRepo
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import com.example.pocketbook_expensetracker.R

class BackupFragment : Fragment() {

    private lateinit var status: TextView
    private lateinit var exportButton: Button
    private lateinit var restoreButton: Button

    private val manager by lazy {
        PocketBookBackup(requireContext().applicationContext)
    }

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/json"
        )
    ) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                setBusy(true)

                try {
                    val summary = withContext(Dispatchers.IO) {
                        val json = manager.exportJson()

                        require(json.isNotBlank()) {
                            "Nothing to export"
                        }

                        require(json.length <= 8_000_000) {
                            "Backup exceeds size limit"
                        }

                        requireNotNull(
                            requireContext().contentResolver
                                .openOutputStream(uri, "w")
                        ).use { stream ->
                            stream.write(
                                json.toByteArray(Charsets.UTF_8)
                            )
                            stream.flush()
                        }

                        manager.preview(json)
                    }

                    status.text =
                        "Backup exported successfully!\n\n$summary"

                    showToast("Backup saved")
                } catch (e: Exception) {
                    status.text =
                        "Export failed: ${e.message}"
                } finally {
                    setBusy(false)
                }
            }
        }
    }

    private val openBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                setBusy(true)

                try {
                    val result = withContext(Dispatchers.IO) {
                        val bytes = requireNotNull(
                            requireContext().contentResolver
                                .openInputStream(uri)
                        ).use { input ->

                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0

                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break

                                total += count

                                require(total <= 8_000_000) {
                                    "Backup is too large"
                                }

                                output.write(buffer, 0, count)
                            }

                            output.toByteArray()
                        }

                        val json = String(
                            bytes,
                            Charsets.UTF_8
                        )

                        val summary = manager.preview(json)

                        json to summary
                    }

                    setBusy(false)

                    confirmRestore(
                        result.first,
                        result.second.toString()
                    )
                } catch (e: Exception) {
                    status.text =
                        "Invalid backup: ${e.message}"
                    setBusy(false)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun d(value: Int) = (value * density).toInt()
        val ink = Color.rgb(24, 47, 57)
        val muted = Color.rgb(108, 128, 134)
        val teal = Color.rgb(20, 127, 121)
        val pale = Color.rgb(232, 246, 243)
        fun rounded(color: Int, radius: Int = 18) = android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = d(radius).toFloat()
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(context).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
            }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 248))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(16), d(15), d(20), d(12))
        }
        val back = android.widget.ImageButton(context).apply {
            setImageResource(R.drawable.ic_pb_chevron_left)
            imageTintList = android.content.res.ColorStateList.valueOf(teal)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = rounded(Color.WHITE, 14)
            contentDescription = "Back"
            setOnClickListener { findNavController().popBackStack() }
        }
        header.addView(back, LinearLayout.LayoutParams(d(46), d(46)))
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(12), 0, 0, 0)
        }
        heading.addView(label("DATA PROTECTION", 11f, muted, true))
        heading.addView(label("Backup & Restore", 23f, ink, true))
        header.addView(heading)
        root.addView(header)

        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(12), d(20), d(30))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val intro = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(ink)
            setPadding(d(20), d(22), d(20), d(22))
        }
        intro.addView(label("YOUR RECORDS, YOUR CONTROL", 11f, Color.rgb(183, 211, 213), true))
        intro.addView(label("Keep your money history safe", 21f, Color.WHITE, true).apply {
            setPadding(0, d(10), 0, d(8))
        })
        intro.addView(label(
            "Save a copy of your wallets, transactions, transfers and budgets. Restore it when you need to.",
            13f, Color.rgb(215, 228, 231)
        ))
        content.addView(intro)

        content.addView(label("Your backup options", 18f, ink, true).apply {
            setPadding(0, d(25), 0, d(12))
        })

        fun actionCard(
            headingText: String,
            description: String,
            buttonText: String,
            filled: Boolean,
            onAction: () -> Unit
        ): Pair<LinearLayout, Button> {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(18), d(18), d(18), d(18))
                background = rounded(Color.WHITE)
            }
            card.addView(label(headingText, 17f, ink, true))
            card.addView(label(description, 13f, muted).apply {
                setPadding(0, d(6), 0, d(12))
            })
            val button = Button(context).apply {
                text = buttonText
                isAllCaps = false
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(if (filled) Color.WHITE else teal)
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (filled) teal else pale)
                setOnClickListener { onAction() }
            }
            card.addView(button, LinearLayout.LayoutParams(-1, d(52)))
            return card to button
        }

        val (exportCard, export) = actionCard(
            "Export a backup",
            "Create a portable JSON copy in Downloads or your chosen storage provider.",
            "Export Backup", true
        ) {
            createBackup.launch("PocketBook-backup-${LocalDate.now()}.json")
        }
        exportButton = export
        content.addView(exportCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = d(12)
        })

        val (restoreCard, restore) = actionCard(
            "Restore your records",
            "Import a previous backup. You'll see a confirmation before current records are replaced.",
            "Choose Backup File", false
        ) {
            openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        restoreButton = restore
        content.addView(restoreCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = d(12)
        })

        val (auditCard, _) = actionCard(
            "Financial Audit",
            "Check record integrity and wallet balance reconciliation without changing data.",
            "Run Financial Audit", false
        ) {
            findNavController().navigate(R.id.integrityFragment)
        }
        content.addView(auditCard)

        content.addView(label("LAST OPERATION", 11f, muted, true).apply {
            setPadding(d(2), d(24), 0, d(8))
        })
        status = label("No backup operation performed.", 13f, muted).apply {
            background = rounded(Color.WHITE)
            setPadding(d(16), d(16), d(16), d(16))
        }
        content.addView(status, LinearLayout.LayoutParams(-1, -2))

        val warningCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(255, 245, 224))
            setPadding(d(16), d(16), d(16), d(16))
        }
        warningCard.addView(label("Before you restore", 14f, Color.rgb(132, 87, 28), true))
        warningCard.addView(label(
            "Restoring replaces current local records. Export your latest backup first. JSON backups are not encrypted: store them privately.",
            12f, Color.rgb(132, 87, 28)
        ).apply { setPadding(0, d(6), 0, 0) })
        content.addView(warningCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = d(16)
        })
        return root
    }

    private fun confirmRestore(
        json: String,
        summary: String
    ) {
        if (!isAdded) return

        PocketFormUi.dialog(requireContext())
            .setTitle("Restore PocketBook backup?")
            .setMessage(
                "The selected backup contains:\n\n" +
                        summary +
                        "\n\nThis will REPLACE all current " +
                        "wallets, transactions, transfers " +
                        "and budgets.\n\n" +
                        "Export your current data first " +
                        "if you want to keep it."
            )
            .setPositiveButton("Restore") { _, _ ->
                performRestore(json)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRestore(json: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            setBusy(true)

            try {
                val summary = withContext(Dispatchers.IO) {
                    val restored = manager.restore(json)

                    // Refresh the already-initialised repositories.
                    LocalWalletRepo.getInstance().refresh()
                    LocalTransactionRepo.getInstance().refresh()
                    LocalTransferRepo.getInstance().refresh()
                    LocalBudgetRepo.getInstance().refresh()

                    restored
                }

                status.text =
                    "Restore completed!\n\n$summary"

                showToast("Backup restored successfully")
            } catch (e: Exception) {
                status.text =
                    "Restore failed: ${e.message}"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        if (!isAdded || !::status.isInitialized) return

        exportButton.isEnabled = !busy
        restoreButton.isEnabled = !busy

        if (busy) {
            status.text = "Processing..."
        }
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun Int.dp(): Int {
        return (
                this * resources.displayMetrics.density
                ).toInt()
    }
}
