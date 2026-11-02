
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
import android.widget.TextView
import android.widget.LinearLayout
import com.example.pocketbook_expensetracker.data.repo.LocalCategoryRepo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.data.model.Wallet
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import com.example.pocketbook_expensetracker.databinding.FragmentAddExpenseBinding
import com.example.pocketbook_expensetracker.databinding.ItemCategoryBinding
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.pocketbook_expensetracker.ui.common.TransactionCalculator


class AddExpenseFragment : Fragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddExpenseViewModel by viewModels()

    private val categoryRepo by lazy { LocalCategoryRepo(requireContext().applicationContext) }
    private var extraCategories: List<String> = emptyList()

    private val walletRepo by lazy {
        LocalWalletRepo.getInstance()
    }

    private lateinit var categories:
            List<Pair<String, ItemCategoryBinding>>

    private var selectedDate = LocalDate.now()
    private var selectedTime = LocalTime.now().withSecond(0).withNano(0)

    private var wallets: List<Wallet> = emptyList()

    private var selectedWalletId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        categories = listOf(
            "Food" to binding.categoryFood,
            "Groceries" to binding.categoryGroceries,
            "Transport" to binding.categoryTransport,
            "Bills" to binding.categoryBills,
            "Fun" to binding.categoryFun,
            "Health" to binding.categoryHealth,
            "Shopping" to binding.categoryShopping,
            "New" to binding.categoryNew
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
                else viewModel.setCategory(name)
            }
        }

        binding.etAmount.setText("0")

        TransactionCalculator.install(
            binding.etAmount
        )

        binding.tvDate.text = formatDate(selectedDate)
        updateTimeLabel()
        binding.tvPayment.text = "Select wallet"

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tvIncomeTab.setOnClickListener { viewModel.setType("Income") }
        binding.tvExpenseTab.setOnClickListener { viewModel.setType("Expense") }

        binding.boxDate.setOnClickListener {
            showDatePicker()
        }
        binding.boxTime.setOnClickListener { showTimePicker() }

        binding.boxPayment.setOnClickListener {
            showWalletDialog()
        }

        binding.btnSaveExpense.setOnClickListener {
            validateForm()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    walletRepo.getWallets().collect { list ->
                        wallets = list
                        refreshSelectedWallet()
                    }
                }

                launch {
                    categoryRepo.categories.collect { names ->
                        extraCategories = names
                        renderExtraCategories(viewModel.selectedCategory.value)
                    }
                }

                launch {
                    viewModel.selectedCategory.collect {
                        updateCategory(it)
                    }
                }

                launch {
                    viewModel.selectedType.collect {
                        binding.tvType.text = it
                        TransactionCalculator.showType(binding.etAmount, it)
                    }
                }

                launch {
                    viewModel.saveResult.collect { result ->
                        if (result != null) {
                            viewModel.clearSaveResult()

                            if (result == "Success") {
                                Toast.makeText(
                                    requireContext(),
                                    "Transaction saved",
                                    Toast.LENGTH_SHORT
                                ).show()

                                findNavController().popBackStack()
                            } else {
                                binding.btnSaveExpense.isEnabled = true

                                Toast.makeText(
                                    requireContext(),
                                    result,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun refreshSelectedWallet() {
        if (
            selectedWalletId != null &&
            wallets.none { it.id == selectedWalletId }
        ) {
            selectedWalletId = null
        }

        // Default to Cash, or the first available wallet.
        if (selectedWalletId == null) {
            selectedWalletId =
                wallets.firstOrNull { it.id == "cash" }?.id
                    ?: wallets.firstOrNull()?.id
        }

        binding.tvPayment.text =
            wallets.firstOrNull {
                it.id == selectedWalletId
            }?.name ?: "Select wallet"
    }

    private fun showWalletDialog() {
        if (wallets.isEmpty()) {
            Toast.makeText(requireContext(), "Create a wallet first", Toast.LENGTH_SHORT).show()
            return
        }
        PocketFormUi.choose(
            requireContext(), "Select wallet",
            wallets.map { "${it.name} (${it.kind})" },
            wallets.indexOfFirst { it.id == selectedWalletId }
        ) { index ->
            selectedWalletId = wallets[index].id
            binding.tvPayment.text = wallets[index].name
        }
    }


    private fun updateCategory(selected: String) {

        categories.forEach { (name, tile) ->
            tile.root.setBackgroundResource(
                if (name == selected)
                    R.drawable.bg_category_selected
                else
                    R.drawable.bg_expense_field
            )
        }

        renderExtraCategories(selected)

        // Show selected category on Add Expense form.
        // Also automatically close the category popup.
        TransactionCalculator.categorySelected(
            binding.etAmount,
            selected
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
                    val created = categoryRepo.addCategory(name)
                    viewModel.setCategory(created)
                    dialog.dismiss()
                } catch (e: Exception) {
                    field.error = e.message ?: "Cannot create category"
                }
            }
        }
    }

    private fun renderExtraCategories(selected: String) {
        if (_binding == null) return
        val names = extraCategories.filter { it != "New" }
        CategoryTileGrid.render(
            container = binding.customCategoryContainer,
            names = names,
            selected = selected
        ) { name -> viewModel.setCategory(name) }
    }

    private fun showTypeDialog() {
        val types = listOf("Expense", "Income")
        PocketFormUi.choose(requireContext(), "Transaction type", types,
            -1
        ) { index -> viewModel.setType(types[index]) }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = LocalDate.of(
                    year, month + 1, day
                )
                binding.tvDate.text = formatDate(selectedDate)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun formatDate(date: LocalDate): String {
        return date.format(
            DateTimeFormatter.ofPattern(
                "MMM d, yyyy",
                Locale.ENGLISH
            )
        )
    }

    private fun updateTimeLabel() {
        binding.tvTime.text = selectedTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    }

    private fun showTimePicker() {
        TimePickerDialog(requireContext(), { _, hour, minute ->
            selectedTime = LocalTime.of(hour, minute)
            updateTimeLabel()
        }, selectedTime.hour, selectedTime.minute, false).show()
    }

    private fun validateForm() {
        val name = binding.etNote.text.toString().trim()
        val amount = binding.etAmount.text.toString().trim()

        if (name.isBlank()) {
            binding.etNote.error = "Please enter a note"
            return
        }

        val error = viewModel.validateAmount(amount)

        if (error != null) {
            binding.etAmount.error = error
            return
        }

        val wallet = wallets.firstOrNull {
            it.id == selectedWalletId
        }

        if (wallet == null) {
            Toast.makeText(
                requireContext(),
                "Please select a wallet",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.btnSaveExpense.isEnabled = false

        viewModel.saveTransaction(
            note = name,
            amount = amount,
            date = selectedDate.toString(),
            time = selectedTime.toString(),
            walletId = wallet.id,
            walletName = wallet.name
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

