package com.example.pocketbook_expensetracker.ui.common

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.pocketbook_expensetracker.R

/**
 * Downloadable fonts are not guaranteed to be available synchronously.
 * Always retain a safe system fallback for programmatically created views.
 */
object PocketFont {
    fun bold(context: Context): Typeface {
        val poppins = try {
            ResourcesCompat.getFont(context, R.font.poppins_bold)
        } catch (_: Exception) {
            null
        }
        return poppins ?: Typeface.create("sans-serif", Typeface.BOLD)
    }
}
