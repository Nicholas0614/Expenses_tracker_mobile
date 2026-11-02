
package com.example.pocketbook_expensetracker.ui.transactions

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.data.model.Transaction
import com.example.pocketbook_expensetracker.data.model.Wallet
import com.example.pocketbook_expensetracker.databinding.ItemDateHeaderBinding
import com.example.pocketbook_expensetracker.databinding.ItemTransactionBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TransactionAdapter(
    private var items: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var wallets: List<Wallet> = emptyList()

    private var displayItems: List<TransactionListItem> =
        buildDisplayList(items)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TRANSACTION = 1
    }

    inner class HeaderViewHolder(
        val binding: ItemDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root)

    inner class TransactionViewHolder(
        val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (displayItems[position] is TransactionListItem.Header) {
            TYPE_HEADER
        } else {
            TYPE_TRANSACTION
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(
                ItemDateHeaderBinding.inflate(
                    inflater, parent, false
                )
            )
        } else {
            TransactionViewHolder(
                ItemTransactionBinding.inflate(
                    inflater, parent, false
                )
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val item = displayItems[position]) {

            is TransactionListItem.Header -> {
                val binding = (holder as HeaderViewHolder).binding

                binding.tvDate.text = formatDate(item.date)

                val positive = item.total >= 0

                binding.tvDailyTotal.text =
                    "${if (positive) "+" else "−"}" +
                            money(kotlin.math.abs(item.total))

                binding.tvDailyTotal.setTextColor(
                    holder.itemView.context.getColor(
                        if (positive)
                            R.color.pocket_green
                        else
                            R.color.pocket_red
                    )
                )
            }

            is TransactionListItem.Entry -> {
                val binding =
                    (holder as TransactionViewHolder).binding

                val transaction = item.transaction
                val isIncome = transaction.type == "Income"

                val walletName = wallets.firstOrNull {
                    it.id == transaction.walletId
                }?.name ?: "Unassigned"

                binding.tvIcon.text =
                    transaction.icon.ifBlank { "•" }

                binding.tvName.text = transaction.name

                binding.tvDetails.text =
                    "${transaction.category} · $walletName"

                binding.tvAmount.text =
                    "${if (isIncome) "+" else "−"}" +
                            money(transaction.amount)

                binding.tvAmount.setTextColor(
                    holder.itemView.context.getColor(
                        if (isIncome)
                            R.color.pocket_green
                        else
                            R.color.pocket_red
                    )
                )

                holder.itemView.setOnClickListener {
                    onItemClick(transaction)
                }
            }
        }
    }

    override fun getItemCount(): Int =
        displayItems.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(
        newItems: List<Transaction>,
        newWallets: List<Wallet>
    ) {
        items = newItems
        wallets = newWallets
        displayItems = buildDisplayList(newItems)

        notifyDataSetChanged()
    }

    private fun buildDisplayList(
        transactions: List<Transaction>
    ): List<TransactionListItem> {

        val result = mutableListOf<TransactionListItem>()
        val groups = linkedMapOf<String, MutableList<Transaction>>()

        transactions.forEach { transaction ->
            groups.getOrPut(transaction.date) {
                mutableListOf()
            }.add(transaction)
        }

        groups.forEach { (date, group) ->
            val dailyTotal = group.sumOf {
                if (it.type == "Income") {
                    it.amount
                } else {
                    -it.amount
                }
            }

            result.add(
                TransactionListItem.Header(
                    date,
                    dailyTotal
                )
            )

            group.forEach {
                result.add(
                    TransactionListItem.Entry(it)
                )
            }
        }

        return result
    }

    private fun formatDate(date: String): String {
        return try {
            val parsed = LocalDate.parse(date)

            when (parsed) {
                LocalDate.now() -> "Today"
                LocalDate.now().minusDays(1) -> "Yesterday"

                else -> parsed.format(
                    DateTimeFormatter.ofPattern(
                        "d MMM yyyy",
                        Locale.ENGLISH
                    )
                )
            }
        } catch (e: Exception) {
            date
        }
    }

    private fun money(amount: Double): String =
        String.format(
            Locale.US,
            "RM %,.2f",
            amount
        )
}
