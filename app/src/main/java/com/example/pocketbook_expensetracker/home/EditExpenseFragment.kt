
package com.example.pocketbook_expensetracker.home

import android.app.AlertDialog
import com.example.pocketbook_expensetracker.ui.common.PocketFormUi
import com.example.pocketbook_expensetracker.ui.common.CategoryTileGrid
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.EditText
import com.example.pocketbook_expensetracker.data.repo.LocalCategoryRepo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.model.Wallet
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import com.example.pocketbook_expensetracker.databinding.FragmentEditExpenseBinding
import com.example.pocketbook_expensetracker.databinding.ItemCategoryBinding
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.pocketbook_expensetracker.ui.common.TransactionCalculator

class EditExpenseFragment : Fragment() {

    private var _binding: FragmentEditExpenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditExpenseViewModel by viewModels()

    private val categoryRepo by lazy { LocalCategoryRepo(requireContext().applicationContext) }
    private var extraCategories: List<String> = emptyList()

    private val walletRepo by lazy {
        LocalWalletRepo.getInstance()
    }

    private lateinit var categories:
            List<Pair<String, ItemCategoryBinding>>

    private var wallets: List<Wallet> = emptyList()
    private var currentTransaction: Transaction? = null

    private var selectedCategory = "Food"
    private var selectedType = "Expense"
    private var selectedWalletId: String? = null
    private var selectedDate = LocalDate.now()
    private var selectedTime = LocalTime.now().withSecond(0).withNano(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditExpenseBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val form = binding.editForm

        TransactionCalculator.install(
            form.etAmount
        )

        form.tvTitle.text = "Edit expense"
        form.btnSaveExpense.visibility = View.VISIBLE
        form.btnSaveExpense.setOnClickListener { updateExpense() }
        updateDate()
        updateTimeLabel()

        categories = listOf(
            "Food" to form.categoryFood,
            "Groceries" to form.categoryGroceries,
            "Transport" to form.categoryTransport,
            "Bills" to form.categoryBills,
            "Fun" to form.categoryFun,
            "Health" to form.categoryHealth,
            "Shopping" to form.categoryShopping,
            "New" to form.categoryNew
        )

        val icons = listOf(
            "🍜", "🛒", "🚌", "💡",
            "🎬", "🩺", "🛍", "+"
        )

        categories.forEachIndexed { index, (name, tile) ->
            tile.tvCategoryName.text = name
            tile.tvCategoryIcon.text = icons[index]

            tile.root.setOnClickListener {
                if (name == "New") showNewCategoryDialog()
                else { selectedCategory = name; updateCategory() }
            }
        }

        updateCategory()

        form.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        form.tvIncomeTab.setOnClickListener {
            selectedType = "Income"
            form.tvType.text = selectedType
            TransactionCalculator.showType(form.etAmount, selectedType)
        }
        form.tvExpenseTab.setOnClickListener {
            selectedType = "Expense"
            form.tvType.text = selectedType
            TransactionCalculator.showType(form.etAmount, selectedType)
        }

        form.boxDate.setOnClickListener {
            showDatePicker()
        }
        form.boxTime.setOnClickListener { showTimePicker() }

        form.boxPayment.setOnClickListener {
            showWalletDialog()
        }

        binding.btnUpdateExpense.setOnClickListener {
            updateExpense()
        }

        binding.btnDeleteExpense.setOnClickListener {
            showDeleteDialog()
        }

        val id = requireArguments()
            .getString("transactionId")
            .orEmpty()

        viewModel.loadTransaction(id)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    categoryRepo.categories.collect { list ->
                        extraCategories = list
                        renderExtraCategories()
                    }
                }
                launch {
                    walletRepo.getWallets().collect { list ->
                        wallets = list
                        updateWalletLabel()
                    }
                }

                launch {
                    viewModel.transaction.collect { transaction ->
                        if (
                            transaction != null &&
                            currentTransaction?.id != transaction.id
                        ) {
                            populateForm(transaction)
                        }
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        if (error != null) {
                            Toast.makeText(
                                requireContext(),
                                error,
                                Toast.LENGTH_LONG
                            ).show()

                            viewModel.clearError()
                            binding.editForm.btnSaveExpense.isEnabled = true
                            binding.btnDeleteExpense.isEnabled = true
                        }
                    }
                }
            }
        }
    }


    private fun updateCategory() {

        categories.forEach { (name, tile) ->
            tile.root.setBackgroundResource(
                if (name == selectedCategory)
                    R.drawable.bg_category_selected
                else
                    R.drawable.bg_expense_field
            )
        }

        renderExtraCategories()

        // Display selected category on Edit Expense form.
        TransactionCalculator.categorySelected(
            binding.editForm.etAmount,
            selectedCategory
        )
    }


    private fun showNewCategoryDialog() {
        PocketFormUi.createCategory(requireContext()) { name, field, dialog ->
            if (name.isBlank()) {
                field.error = "Enter a category name"
                return@createCategory
            }
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    selectedCategory = categoryRepo.addCategory(name)
                    updateCategory()
                    dialog.dismiss()
                } catch (e: Exception) {
                    field.error = e.message ?: "Cannot create category"
                }
            }
        }
    }

    private fun renderExtraCategories() {
        if (_binding == null) return
        // Keep categories assigned to older transactions even if they
        // are missing from the current custom-category list.
        val names = (extraCategories + listOf(selectedCategory).filter {
            it !in LocalCategoryRepo.DEFAULTS && it != "New"
        }).filter { it != "New" }.distinct()
        CategoryTileGrid.render(
            container = binding.editForm.customCategoryContainer,
            names = names,
            selected = selectedCategory
        ) { name ->
            selectedCategory = name
            updateCategory()
        }
    }

    private fun updateDate() {
        binding.editForm.tvDate.text =
            selectedDate.format(
                DateTimeFormatter.ofPattern(
                    "MMM d, yyyy"
                )
            )
    }

    private fun updateWalletLabel() {
        val wallet = wallets.firstOrNull {
            it.id == selectedWalletId
        }

        binding.editForm.tvPayment.text =
            wallet?.name ?: "Unassigned (legacy)"
    }

    private fun showWalletDialog() {
        if (wallets.isEmpty()) {
            Toast.makeText(requireContext(), "No wallets available", Toast.LENGTH_SHORT).show()
            return
        }
        PocketFormUi.choose(
            requireContext(), "Select wallet",
            wallets.map { "${it.name} (${it.kind})" },
            wallets.indexOfFirst { it.id == selectedWalletId }
        ) { index ->
            selectedWalletId = wallets[index].id
            updateWalletLabel()
        }
    }

    private fun showTypeDialog() {
        val types = listOf("Expense", "Income")
        PocketFormUi.choose(requireContext(), "Transaction type", types,
            types.indexOf(selectedType)
        ) { index ->
            selectedType = types[index]
            binding.editForm.tvType.text = selectedType
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = LocalDate.of(
                    year, month + 1, day
                )
                updateDate()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun updateTimeLabel() {
        binding.editForm.tvTime.text = selectedTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    private fun showTimePicker() {
        TimePickerDialog(requireContext(), { _, hour, minute ->
            selectedTime = LocalTime.of(hour, minute)
            updateTimeLabel()
        }, selectedTime.hour, selectedTime.minute, false).show()
    }

    private fun updateExpense() {
        val transaction = currentTransaction ?: return
        val form = binding.editForm

        val name = form.etNote.text.toString().trim()
        val amountText = form.etAmount.text.toString().trim()

        if (name.isBlank()) {
            form.etNote.error = "Please enter a note"
            return
        }

        val error = viewModel.validateAmount(amountText)

        if (error != null) {
            form.etAmount.error = error
            return
        }

        val wallet = wallets.firstOrNull {
            it.id == selectedWalletId
        }

        // A legacy transaction may remain unassigned
        // until the user explicitly chooses a wallet.
        if (selectedWalletId != null && wallet == null) {
            Toast.makeText(
                requireContext(),
                "Please select a valid wallet",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val icon = when (selectedCategory) {
            "Food" -> "🍜"
            "Groceries" -> "🛒"
            "Transport" -> "🚌"
            "Bills" -> "💡"
            "Fun" -> "🎬"
            "Health" -> "🩺"
            "Shopping" -> "🛍"
            else -> "💰"
        }

        val updated = transaction.copy(
            name = name,
            amount = amountText.toDouble(),
            category = selectedCategory,
            type = selectedType,
            paymentMethod =
                wallet?.name ?: transaction.paymentMethod,
            walletId = wallet?.id,
            date = selectedDate.toString(),
            time = selectedTime.toString(),
            note = name,
            icon = icon
        )

        binding.editForm.btnSaveExpense.isEnabled = false

        viewModel.updateTransaction(updated) {
            if (!isAdded) return@updateTransaction

            Toast.makeText(
                requireContext(),
                "Transaction updated",
                Toast.LENGTH_SHORT
            ).show()

            findNavController().popBackStack()
        }
    }

    private fun showDeleteDialog() {
        PocketFormUi.dialog(requireContext())
            .setTitle("Delete transaction")
            .setMessage(
                "Are you sure you want to delete this transaction?"
            )
            .setPositiveButton("Delete") { _, _ ->
                val id = currentTransaction?.id
                    ?: return@setPositiveButton

                binding.btnDeleteExpense.isEnabled = false

                viewModel.deleteTransaction(id) {
                    if (!isAdded) return@deleteTransaction

                    Toast.makeText(
                        requireContext(),
                        "Transaction deleted",
                        Toast.LENGTH_SHORT
                    ).show()

                    findNavController().popBackStack()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun populateForm(transaction: Transaction) {
        currentTransaction = transaction
        selectedCategory = transaction.category
        selectedType = transaction.type
        selectedWalletId = transaction.walletId

        selectedDate = runCatching {
            LocalDate.parse(transaction.date)
        }.getOrDefault(LocalDate.now())
        selectedTime = runCatching { LocalTime.parse(transaction.time) }.getOrDefault(LocalTime.NOON)

        val form = binding.editForm

        form.tvTitle.text = "Edit expense"
        form.etAmount.setText(transaction.amount.toString())
        form.etNote.setText(
            transaction.name.ifBlank {
                transaction.note
            }
        )
        form.tvType.text = selectedType
        TransactionCalculator.showType(form.etAmount, selectedType)

        updateWalletLabel()
        updateDate()
        updateTimeLabel()
        updateCategory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
