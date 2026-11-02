
package com.example.pocketbook_expensetracker.ui.common

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.example.pocketbook_expensetracker.R

object PocketFormUi {

    private val ink = Color.rgb(24, 47, 57)
    private val muted = Color.rgb(103, 121, 128)
    private val teal = Color.rgb(20, 127, 121)
    private val outline = Color.rgb(220, 231, 232)

    fun dialog(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(
            ContextThemeWrapper(
                context,
                R.style.PocketFormDialog
            )
        )

    fun background(
        color: Int,
        radius: Float,
        stroke: Boolean = false
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius

            if (stroke) {
                setStroke(1, outline)
            }
        }

    fun form(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(context, 24),
                dp(context, 8),
                dp(context, 24),
                dp(context, 8)
            )
        }

    fun label(
        context: Context,
        title: String
    ): TextView =
        TextView(context).apply {
            text = title.uppercase()
            textSize = 11f
            letterSpacing = 0.07f
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
            setTextColor(muted)

            setPadding(
                dp(context, 2),
                dp(context, 16),
                0,
                dp(context, 7)
            )
        }

    fun styleInput(field: EditText) {
        val context = field.context

        field.background = background(
            Color.rgb(248, 251, 251),
            dp(context, 14).toFloat(),
            true
        )

        field.setTextColor(ink)
        field.setHintTextColor(muted)
        field.textSize = 15f
        field.setSingleLine(true)

        field.setPadding(
            dp(context, 15),
            0,
            dp(context, 15),
            0
        )

        field.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 54)
        )
    }

    fun styleSpinner(spinner: Spinner) {
        val context = spinner.context

        spinner.background = background(
            Color.rgb(248, 251, 251),
            dp(context, 14).toFloat(),
            true
        )

        spinner.setPadding(
            dp(context, 12),
            0,
            dp(context, 10),
            0
        )

        spinner.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 54)
        )

        spinner.backgroundTintList = null
    }

    fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(
            background(
                Color.WHITE,
                dp(dialog.context, 22).toFloat()
            )
        )

        dialog.getButton(
            AlertDialog.BUTTON_POSITIVE
        )?.apply {
            setTextColor(teal)
            isAllCaps = false
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
        }

        dialog.getButton(
            AlertDialog.BUTTON_NEGATIVE
        )?.apply {
            setTextColor(muted)
            isAllCaps = false
        }

        dialog.getButton(
            AlertDialog.BUTTON_NEUTRAL
        )?.apply {
            setTextColor(Color.rgb(216, 92, 101))
            isAllCaps = false
        }
    }

    // Existing selection dialogs.
    fun choose(
        context: Context,
        title: String,
        options: List<String>,
        selectedIndex: Int = -1,
        onSelected: (Int) -> Unit
    ) {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(context, 20),
                dp(context, 16),
                dp(context, 20),
                dp(context, 12)
            )
        }

        wrapper.addView(
            TextView(context).apply {
                text = title
                textSize = 19f
                typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
                setTextColor(ink)

                setPadding(
                    dp(context, 4),
                    dp(context, 6),
                    0,
                    dp(context, 16)
                )
            }
        )

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
        }

        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sheet = dialog(context)
            .setView(wrapper)
            .create()

        options.forEachIndexed { index, option ->

            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL

                setPadding(
                    dp(context, 15),
                    dp(context, 5),
                    dp(context, 15),
                    dp(context, 5)
                )

                background = background(
                    if (index == selectedIndex) {
                        Color.rgb(231, 245, 242)
                    } else {
                        Color.rgb(248, 250, 250)
                    },
                    dp(context, 13).toFloat()
                )

                isClickable = true
                isFocusable = true
            }

            row.addView(
                TextView(context).apply {
                    text = option
                    textSize = 15f
                    setTextColor(ink)

                    setTypeface(
                        null,
                        if (index == selectedIndex) {
                            Typeface.BOLD
                        } else {
                            Typeface.NORMAL
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            if (index == selectedIndex) {
                row.addView(
                    TextView(context).apply {
                        text = "✓"
                        textSize = 20f
                        setTextColor(teal)
                        gravity = Gravity.CENTER
                    }
                )
            }

            row.setOnClickListener {
                sheet.dismiss()
                onSelected(index)
            }

            rows.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 49)
                ).apply {
                    bottomMargin = dp(context, 6)
                }
            )
        }

        scroll.addView(rows)

        val maxListHeight = (
                context.resources.displayMetrics.heightPixels *
                        0.55f
                ).toInt()

        val naturalListHeight =
            options.size * dp(context, 55)

        wrapper.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                naturalListHeight
                    .coerceAtMost(maxListHeight)
                    .coerceAtLeast(dp(context, 55))
            )
        )

        wrapper.addView(
            TextView(context).apply {
                text = "CANCEL"
                textSize = 13f
                typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
                setTextColor(teal)
                gravity = Gravity.CENTER

                setOnClickListener {
                    sheet.dismiss()
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
        )

        sheet.show()
        styleDialog(sheet)

        sheet.window?.apply {
            setSoftInputMode(
                WindowManager.LayoutParams
                    .SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )

            setLayout(
                (
                        context.resources.displayMetrics.widthPixels *
                                0.88f
                        ).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setGravity(Gravity.CENTER)
        }
    }

    // Custom fixed-layout category dialog.
    fun createCategory(
        context: Context,
        onCreate: (
            name: String,
            input: EditText,
            dialog: AlertDialog
        ) -> Unit
    ) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true

            setPadding(
                dp(context, 24),
                dp(context, 24),
                dp(context, 24),
                dp(context, 20)
            )
        }

        val title = TextView(context).apply {
            text = "Add Category"
            textSize = 21f
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
            setTextColor(ink)
        }

        card.addView(title)

        card.addView(
            TextView(context).apply {
                text = "Create a category for your transactions."
                textSize = 13f
                setTextColor(muted)

                setPadding(
                    0,
                    dp(context, 8),
                    0,
                    dp(context, 12)
                )
            }
        )

        card.addView(
            label(context, "Category Name")
        )

        val input = EditText(context).apply {
            hint = "e.g. Education, Pets, Rent"

            filters = arrayOf(
                InputFilter.LengthFilter(32)
            )

            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

            isSingleLine = true
        }

        styleInput(input)
        card.addView(input)

        card.addView(
            TextView(context).apply {
                text = "Available for future expenses, income and budgets."
                textSize = 12f
                setTextColor(muted)

                setPadding(
                    0,
                    dp(context, 12),
                    0,
                    dp(context, 12)
                )
            }
        )

        // Custom action row avoids AlertDialog's native
        // button bar being measured after display.
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END

            setPadding(
                0,
                dp(context, 8),
                0,
                0
            )
        }

        val cancelButton = TextView(context).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(
                dp(context, 12),
                0,
                dp(context, 12),
                0
            )
        }

        val createButton = TextView(context).apply {
            text = "Create"
            textSize = 14f
            typeface = com.example.pocketbook_expensetracker.ui.common.PocketFont.bold(this.context)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            background = background(
                teal,
                dp(context, 12).toFloat()
            )

            setPadding(
                dp(context, 20),
                0,
                dp(context, 20),
                0
            )
        }

        actions.addView(
            cancelButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 46)
            )
        )

        actions.addView(
            createButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 46)
            )
        )

        card.addView(actions)

        // No native title or AlertDialog buttons.
        val categoryDialog = dialog(context)
            .setView(card)
            .create()

        cancelButton.setOnClickListener {
            categoryDialog.dismiss()
        }

        createButton.setOnClickListener {
            onCreate(
                input.text.toString().trim(),
                input,
                categoryDialog
            )
        }

        // Avoid initial keyboard activation.
        card.requestFocus()
        input.clearFocus()

        categoryDialog.window?.apply {
            setGravity(Gravity.CENTER)

            // Don't resize the dialog when the IME appears.
            setSoftInputMode(
                WindowManager.LayoutParams
                    .SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                        WindowManager.LayoutParams
                            .SOFT_INPUT_ADJUST_NOTHING
            )

            // Disable window entrance animations,
            // which can look like repositioning on One UI.
            attributes = attributes.apply {
                windowAnimations = 0
            }
        }

        categoryDialog.show()

        // Only visual decoration here.
        // No setLayout() or other size change after show().
        categoryDialog.window?.setBackgroundDrawable(
            background(
                Color.WHITE,
                dp(context, 22).toFloat()
            )
        )
    }

    private fun dp(
        context: Context,
        number: Int
    ): Int =
        (
                number *
                        context.resources.displayMetrics.density
                ).toInt()
}
