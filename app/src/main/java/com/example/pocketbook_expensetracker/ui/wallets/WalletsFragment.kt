
package com.example.pocketbook_expensetracker.ui.wallets

import com.example.pocketbook_expensetracker.R
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.model.Wallet
import com.example.pocketbook_expensetracker.data.model.WalletTransfer
import com.example.pocketbook_expensetracker.data.repo.LocalTransferRepo
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import com.example.pocketbook_expensetracker.data.repo.TransactionRepo
import com.example.pocketbook_expensetracker.databinding.FragmentWalletsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WalletsFragment : Fragment() {

    private var _binding: FragmentWalletsBinding? = null
    private val binding get() = _binding!!

    private val walletRepo by lazy {
        LocalWalletRepo.getInstance()
    }

    private val transferRepo by lazy {
        LocalTransferRepo.getInstance()
    }

    private val transactionRepo by lazy {
        TransactionRepo.getInstance()
    }

    private val navy = Color.rgb(20, 33, 63)
    private val teal = Color.rgb(12, 144, 147)
    private val grey = Color.rgb(102, 119, 149)
    private val red = Color.rgb(233, 54, 85)

    private val walletTypes = listOf(
        "Cash", "Bank", "eWallet"
    )

    private var wallets: List<Wallet> = emptyList()
    private var transfers: List<WalletTransfer> = emptyList()
    private var transactions: List<Transaction> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletsBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnAddWallet.setOnClickListener {
            showWalletDialog(null)
        }

        binding.btnTransfer.setOnClickListener {
            showTransferDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                combine(
                    walletRepo.getWallets(),
                    transactionRepo.getTransactions(),
                    transferRepo.getTransfers()
                ) { walletList, transactionList, transferList ->
                    Triple(
                        walletList,
                        transactionList,
                        transferList
                    )
                }.collect { (w, t, tr) ->
                    wallets = w
                    transactions = t
                    transfers = tr
                    render()
                }
            }
        }
    }

    // BALANCE CALCULATIONS

    private fun balance(wallet: Wallet): Double {
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

        val incomingSen = transfers
            .filter {
                it.toWalletId == wallet.id
            }
            .sumOf { it.amountSen }

        val outgoingSen = transfers
            .filter {
                it.fromWalletId == wallet.id
            }
            .sumOf { it.amountSen }

        return wallet.openingBalance +
                income -
                expenses +
                (incomingSen - outgoingSen) / 100.0
    }

    private fun render() {
        val total = wallets.sumOf {
            balance(it)
        }

        binding.tvTotalBalance.text = money(total)
        binding.tvWalletCount.text =
            "${wallets.size} active wallets"

        val unassigned = transactions.count {
            it.walletId == null
        }

        binding.tvLegacyWarning.visibility =
            if (unassigned > 0) View.VISIBLE
            else View.GONE

        binding.tvLegacyWarning.text =
            "$unassigned older transactions are not " +
                    "assigned to a wallet. Edit them to assign one."

        binding.walletContainer.removeAllViews()

        if (wallets.isEmpty()) {
            binding.walletContainer.addView(
                emptyMessage("No wallets yet. Add your first wallet.")
            )
        } else {
            wallets.forEach { wallet ->
                binding.walletContainer.addView(
                    createWalletCard(wallet)
                )
            }
        }

        renderTransfers()
    }

    // ACCOUNT CARDS

    private fun createWalletCard(wallet: Wallet): View {
        val context = requireContext()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                16.dp(), 16.dp(),
                16.dp(), 16.dp()
            )
            background = rounded(Color.WHITE)
            elevation = 2.dp().toFloat()

            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply {
                bottomMargin = 12.dp()
            }
        }

        val symbol = when (wallet.kind) {
            "Bank" -> "🏦"
            "eWallet" -> "📱"
            else -> "💵"
        }

        val iconBackground = when (wallet.kind) {
            "Bank" -> Color.rgb(232, 238, 251)
            "eWallet" -> Color.rgb(231, 245, 240)
            else -> Color.rgb(251, 241, 226)
        }

        val icon = TextView(context).apply {
            text = symbol
            textSize = 25f
            gravity = Gravity.CENTER
            background = rounded(iconBackground, 14)

            layoutParams = LinearLayout.LayoutParams(
                52.dp(), 52.dp()
            )
        }

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val name = text(
            wallet.name, 16f, navy, true
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val kind = text(
            when (wallet.kind) {
                "Bank" -> "Bank account"
                "eWallet" -> "eWallet"
                else -> "Cash"
            },
            12f,
            grey
        )

        details.addView(name)
        details.addView(kind)

        val amountColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        val currentBalance = balance(wallet)

        val amount = text(
            money(currentBalance),
            15f,
            if (currentBalance < 0) red else navy,
            true
        ).apply {
            gravity = Gravity.END
        }

        val edit = text(
            "Edit ›",
            12f,
            teal,
            true
        ).apply {
            gravity = Gravity.END
            setPadding(0, 5.dp(), 0, 0)
        }

        amountColumn.addView(amount)
        amountColumn.addView(edit)

        card.addView(icon)

        card.addView(
            details,
            LinearLayout.LayoutParams(
                0, -2, 1f
            ).apply {
                marginStart = 12.dp()
                marginEnd = 6.dp()
            }
        )

        card.addView(amountColumn)

        card.setOnClickListener {
            showWalletDialog(wallet)
        }

        return card
    }

    // TRANSFER HISTORY

    private fun renderTransfers() {
        binding.transferContainer.removeAllViews()

        binding.tvTransferCount.text =
            "${transfers.size} transfers"

        if (transfers.isEmpty()) {
            binding.transferContainer.addView(
                emptyMessage(
                    "No transfers yet.\nMove money between wallets using Transfer."
                )
            )
            return
        }

        transfers.forEach { transfer ->
            binding.transferContainer.addView(
                createTransferCard(transfer)
            )
        }
    }

    private fun createTransferCard(
        transfer: WalletTransfer
    ): View {
        val context = requireContext()

        val from = wallets.firstOrNull {
            it.id == transfer.fromWalletId
        }?.name ?: "Unknown wallet"

        val to = wallets.firstOrNull {
            it.id == transfer.toWalletId
        }?.name ?: "Unknown wallet"

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                14.dp(), 16.dp(),
                14.dp(), 16.dp()
            )
            background = rounded(Color.WHITE)

            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply {
                bottomMargin = 10.dp()
            }
        }

        val icon = TextView(context).apply {
            text = "↔"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(teal)
            background = rounded(
                Color.rgb(228, 244, 243),
                12
            )
        }

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = text(
            "$from → $to",
            14f,
            navy,
            true
        ).apply {
            maxLines = 2
        }

        val dateText = runCatching {
            LocalDate.parse(transfer.date).format(
                DateTimeFormatter.ofPattern(
                    "d MMM yyyy",
                    Locale.ENGLISH
                )
            )
        }.getOrDefault(transfer.date)

        val subtitle = text(
            if (transfer.note.isBlank()) dateText
            else "$dateText · ${transfer.note}",
            12f,
            grey
        ).apply {
            maxLines = 2
        }

        details.addView(title)
        details.addView(subtitle)

        val amount = text(
            money(transfer.amountSen / 100.0),
            14f,
            navy,
            true
        ).apply {
            gravity = Gravity.END
        }

        card.addView(
            icon,
            LinearLayout.LayoutParams(
                46.dp(), 46.dp()
            )
        )

        card.addView(
            details,
            LinearLayout.LayoutParams(
                0, -2, 1f
            ).apply {
                marginStart = 12.dp()
                marginEnd = 8.dp()
            }
        )

        card.addView(amount)

        card.setOnClickListener {
            confirmDeleteTransfer(transfer)
        }

        return card
    }

    // ADD / EDIT WALLET

    private fun showWalletDialog(wallet: Wallet?) {
        val form = PocketFormUi.form(requireContext())

        val nameInput = EditText(requireContext()).apply {
            hint = "e.g. Maybank or TNG eWallet"
            setSingleLine(true)
            setText(wallet?.name ?: "")
        }

        val typeSpinner = spinner(walletTypes).apply {
            setSelection(
                walletTypes.indexOf(
                    wallet?.kind ?: "Cash"
                ).coerceAtLeast(0)
            )
        }

        val openingInput = EditText(requireContext()).apply {
            hint = "e.g. 500.00"
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL or
                        InputType.TYPE_NUMBER_FLAG_SIGNED

            wallet?.let {
                setText(
                    BigDecimal.valueOf(it.openingBalance)
                        .stripTrailingZeros()
                        .toPlainString()
                )
            }
        }

        form.addView(PocketFormUi.label(requireContext(), "Wallet name"))
        PocketFormUi.styleInput(nameInput)
        form.addView(nameInput)
        form.addView(PocketFormUi.label(requireContext(), "Account type"))
        PocketFormUi.styleSpinner(typeSpinner)
        form.addView(typeSpinner)
        form.addView(PocketFormUi.label(requireContext(), "Opening balance (RM)"))
        PocketFormUi.styleInput(openingInput)
        form.addView(openingInput)

        val dialog = PocketFormUi.dialog(requireContext())
            .setTitle(
                if (wallet == null) "Add Wallet"
                else "Edit Wallet"
            )
            .setView(form)
            .setPositiveButton(
                if (wallet == null) "Add Wallet"
                else "Save Changes",
                null
            )
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            )

            saveButton.setOnClickListener {
                val name = nameInput.text.toString().trim()

                if (name.isBlank()) {
                    nameInput.error = "Enter wallet name"
                    return@setOnClickListener
                }

                val openingBalance = try {
                    BigDecimal(
                        openingInput.text.toString().trim()
                    ).setScale(
                        2,
                        RoundingMode.UNNECESSARY
                    ).toDouble().also {
                        require(it.isFinite())
                    }
                } catch (e: Exception) {
                    openingInput.error =
                        "Enter a valid amount (max 2 decimals)"
                    return@setOnClickListener
                }

                val updated = Wallet(
                    id = wallet?.id ?: "",
                    name = name,
                    kind = typeSpinner.selectedItem.toString(),
                    openingBalance = openingBalance
                )

                saveButton.isEnabled = false

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        if (wallet == null) {
                            walletRepo.addWallet(updated)
                        } else {
                            walletRepo.updateWallet(updated)
                        }

                        dialog.dismiss()
                        toast("Wallet saved")
                    } catch (e: Exception) {
                        showError(e)
                        saveButton.isEnabled = true
                    }
                }
            }
        }

        dialog.show()
        PocketFormUi.styleDialog(dialog)
    }

    // WALLET TRANSFERS

    private fun showTransferDialog() {
        if (wallets.size < 2) {
            toast("Create at least two wallets first")
            return
        }

        val form = PocketFormUi.form(requireContext())
        val walletNames = wallets.map { it.name }

        val fromSpinner = spinner(walletNames)
        val toSpinner = spinner(walletNames).apply {
            setSelection(1)
        }

        val amountInput = EditText(requireContext()).apply {
            hint = "Amount in RM"
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val noteInput = EditText(requireContext()).apply {
            hint = "e.g. Top up eWallet"
            setSingleLine(true)
        }

        val information = text(
            "Transfers move money between your wallets. " +
                    "They do not count as income or expenses.",
            12f,
            grey
        ).apply {
            setPadding(
                0, 8.dp(),
                0, 12.dp()
            )
        }

        form.addView(information)
        form.addView(PocketFormUi.label(requireContext(), "From wallet"))
        PocketFormUi.styleSpinner(fromSpinner)
        form.addView(fromSpinner)
        form.addView(PocketFormUi.label(requireContext(), "To wallet"))
        PocketFormUi.styleSpinner(toSpinner)
        form.addView(toSpinner)
        form.addView(PocketFormUi.label(requireContext(), "Amount (RM)"))
        PocketFormUi.styleInput(amountInput)
        form.addView(amountInput)
        form.addView(PocketFormUi.label(requireContext(), "Note (optional)"))
        PocketFormUi.styleInput(noteInput)
        form.addView(noteInput)

        val dialog = PocketFormUi.dialog(requireContext())
            .setTitle("Transfer Money")
            .setView(form)
            .setPositiveButton("Transfer", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val transferButton = dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            )

            transferButton.setOnClickListener {
                val from = wallets.getOrNull(
                    fromSpinner.selectedItemPosition
                )
                val to = wallets.getOrNull(
                    toSpinner.selectedItemPosition
                )

                if (from == null || to == null) {
                    toast("Choose both wallets")
                    return@setOnClickListener
                }

                if (from.id == to.id) {
                    toast("Choose two different wallets")
                    return@setOnClickListener
                }

                val amountSen = try {
                    BigDecimal(
                        amountInput.text.toString().trim()
                    ).setScale(
                        2,
                        RoundingMode.UNNECESSARY
                    ).movePointRight(2)
                        .longValueExact()
                } catch (e: Exception) {
                    null
                }

                if (amountSen == null || amountSen <= 0L) {
                    amountInput.error =
                        "Enter a positive amount (max 2 decimals)"
                    return@setOnClickListener
                }

                transferButton.isEnabled = false

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        transferRepo.addTransfer(
                            fromId = from.id,
                            toId = to.id,
                            amountSen = amountSen,
                            note = noteInput.text.toString()
                        )

                        dialog.dismiss()
                        toast("Transfer successful")
                    } catch (e: Exception) {
                        showError(e)
                        transferButton.isEnabled = true
                    }
                }
            }
        }

        dialog.show()
        PocketFormUi.styleDialog(dialog)
    }

    private fun confirmDeleteTransfer(
        transfer: WalletTransfer
    ) {
        PocketFormUi.dialog(requireContext())
            .setTitle("Delete transfer?")
            .setMessage(
                "This will reverse the transfer's effect " +
                        "on both wallet balances.\n\n" +
                        "Your ordinary income and expense " +
                        "records will not be deleted."
            )
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        transferRepo.deleteTransfer(transfer.id)
                        toast("Transfer deleted")
                    } catch (e: Exception) {
                        showError(e)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // UI HELPERS

    private fun dialogForm() =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                24.dp(), 10.dp(),
                24.dp(), 12.dp()
            )
        }

    private fun fieldLabel(label: String) =
        text(label, 13f, grey, true).apply {
            setPadding(
                0, 16.dp(),
                0, 4.dp()
            )
        }

    private fun spinner(items: List<String>) =
        Spinner(requireContext()).apply {
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                items
            )

            adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )

            this.adapter = adapter
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

    private fun emptyMessage(message: String) =
        text(message, 14f, grey).apply {
            gravity = Gravity.CENTER
            setPadding(
                12.dp(), 24.dp(),
                12.dp(), 24.dp()
            )
            background = rounded(Color.WHITE)

            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            )
        }

    private fun rounded(
        colour: Int,
        radius: Int = 18
    ) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = radius.dp().toFloat()
    }

    private fun money(value: Double): String =
        String.format(
            Locale.US,
            "RM %,.2f",
            value
        )

    private fun toast(message: String) {
        if (isAdded) {
            Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showError(error: Exception) {
        toast(error.message ?: "Operation failed")
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
