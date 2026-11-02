
package com.example.pocketbook_expensetracker.ui.common

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.pocketbook_expensetracker.R
import com.example.pocketbook_expensetracker.data.repo.LocalCategoryRepo
import com.example.pocketbook_expensetracker.databinding.ItemCategoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CategoryTileGrid {

    private data class TileState(
        val originalParent: ViewGroup,
        val originalIndex: Int,
        val newTile: View,
        val originalParams: ViewGroup.LayoutParams,
        var firstCustomTile: View? = null
    )

    fun render(
        container: LinearLayout,
        names: List<String>,
        selected: String,
        onCategoryClick: (String) -> Unit
    ) {
        val context = container.context
        val inflater = LayoutInflater.from(context)

        val gap = dp(context, 8)
        val rowGap = dp(context, 10)
        val height = dp(context, 68)

        val customNames = names
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter {
                !it.equals("New", ignoreCase = true)
            }
            .distinct()

        var state = container.tag as? TileState

        if (state == null) {
            val newTile = container.rootView
                .findViewById<View>(R.id.categoryNew)

            val parent = newTile?.parent as? ViewGroup

            if (newTile != null && parent != null) {
                state = TileState(
                    originalParent = parent,
                    originalIndex = parent.indexOfChild(newTile),
                    newTile = newTile,
                    originalParams = newTile.layoutParams
                )
                container.tag = state
            }
        }

        container.removeAllViews()

        state?.firstCustomTile?.let { tile ->
            (tile.parent as? ViewGroup)?.removeView(tile)
        }
        state?.firstCustomTile = null

        if (customNames.isEmpty()) {
            state?.let { saved ->
                (saved.newTile.parent as? ViewGroup)
                    ?.removeView(saved.newTile)

                saved.originalParent.addView(
                    saved.newTile,
                    saved.originalIndex.coerceAtMost(
                        saved.originalParent.childCount
                    ),
                    saved.originalParams
                )
            }

            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE

        val remaining = customNames.toMutableList()

        state?.let { saved ->
            (saved.newTile.parent as? ViewGroup)
                ?.removeView(saved.newTile)

            val firstName = remaining.removeAt(0)

            val firstTile = createTile(
                inflater = inflater,
                parent = saved.originalParent,
                name = firstName,
                selected = selected,
                onCategoryClick = onCategoryClick
            )

            saved.originalParent.addView(
                firstTile,
                saved.originalIndex.coerceAtMost(
                    saved.originalParent.childCount
                ),
                saved.originalParams
            )

            saved.firstCustomTile = firstTile
        }

        val tiles = mutableListOf<View>()

        remaining.forEach { name ->
            tiles.add(
                createTile(
                    inflater = inflater,
                    parent = container,
                    name = name,
                    selected = selected,
                    onCategoryClick = onCategoryClick
                )
            )
        }

        // New (+) always comes last.
        state?.let { saved ->
            (saved.newTile.parent as? ViewGroup)
                ?.removeView(saved.newTile)

            tiles.add(saved.newTile)
        }

        tiles.chunked(4).forEachIndexed { rowIndex, group ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }

            group.forEachIndexed { column, tile ->
                row.addView(
                    tile,
                    LinearLayout.LayoutParams(
                        0,
                        height,
                        1f
                    ).apply {
                        if (column > 0) marginStart = gap
                    }
                )
            }

            repeat(4 - group.size) {
                row.addView(
                    View(context).apply {
                        visibility = View.INVISIBLE
                    },
                    LinearLayout.LayoutParams(
                        0,
                        height,
                        1f
                    ).apply {
                        marginStart = gap
                    }
                )
            }

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex > 0) topMargin = rowGap
                }
            )
        }
    }

    private fun createTile(
        inflater: LayoutInflater,
        parent: ViewGroup,
        name: String,
        selected: String,
        onCategoryClick: (String) -> Unit
    ): View {
        val tile = ItemCategoryBinding.inflate(
            inflater,
            parent,
            false
        )

        tile.tvCategoryIcon.text = "🏷️"
        tile.tvCategoryName.text = name
        tile.tvCategoryName.maxLines = 2
        tile.tvCategoryName.ellipsize =
            TextUtils.TruncateAt.END

        tile.root.setBackgroundResource(
            if (name == selected) {
                R.drawable.bg_category_selected
            } else {
                R.drawable.bg_expense_field
            }
        )

        // Normal tap selects the category.
        tile.root.setOnClickListener {
            onCategoryClick(name)
        }

        // Long press opens Rename / Delete.
        tile.root.setOnLongClickListener {
            showEditDialog(
                context = parent.context,
                name = name,
                wasSelected = name == selected,
                onCategoryClick = onCategoryClick
            )
            true
        }

        return tile.root
    }

    private fun showEditDialog(
        context: Context,
        name: String,
        wasSelected: Boolean,
        onCategoryClick: (String) -> Unit
    ) {
        val repo = LocalCategoryRepo(
            context.applicationContext
        )

        val input = EditText(context).apply {
            setText(name)
            setSelection(text.length)
            hint = "Category name"

            filters = arrayOf(
                InputFilter.LengthFilter(32)
            )

            setSingleLine(true)
        }

        PocketFormUi.styleInput(input)

        val padding = dp(context, 22)

        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                padding,
                dp(context, 10),
                padding,
                dp(context, 12)
            )
        }

        form.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 54)
            )
        )

        val editDialog = PocketFormUi.dialog(context)
            .setTitle("Edit Category")
            .setView(form)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete", null)
            .create()

        editDialog.setOnShowListener {
            PocketFormUi.styleDialog(editDialog)

            editDialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                val newName = input.text.toString().trim()

                if (newName.isBlank()) {
                    input.error = "Enter a category name"
                    return@setOnClickListener
                }

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val renamed = repo.renameCategory(
                            name,
                            newName
                        )

                        editDialog.dismiss()

                        if (wasSelected) {
                            onCategoryClick(renamed)
                        }

                        Toast.makeText(
                            context,
                            "Category renamed",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        input.error =
                            e.message ?: "Cannot rename category"
                    }
                }
            }

            editDialog.getButton(
                AlertDialog.BUTTON_NEUTRAL
            ).setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Delete Category?")
                    .setMessage(
                        "Delete \"$name\"? Categories used by " +
                                "transactions or budgets cannot be deleted."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                repo.deleteCategory(name)

                                editDialog.dismiss()

                                if (wasSelected) {
                                    onCategoryClick("Food")
                                }

                                Toast.makeText(
                                    context,
                                    "Category deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                AlertDialog.Builder(context)
                                    .setTitle("Cannot Delete Category")
                                    .setMessage(
                                        e.message ?: "Delete failed"
                                    )
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        }

        editDialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams
                .SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )

        editDialog.show()
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {
        return (
                context.resources.displayMetrics.density *
                        value
                ).toInt()
    }
}
