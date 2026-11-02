
package com.example.pocketbook_expensetracker

import android.os.Bundle
import android.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import com.example.pocketbook_expensetracker.data.repo.LocalTransactionRepo
import com.example.pocketbook_expensetracker.data.repo.LocalWalletRepo
import com.example.pocketbook_expensetracker.data.repo.LocalTransferRepo
import com.example.pocketbook_expensetracker.data.repo.LocalBudgetRepo
import com.example.pocketbook_expensetracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LocalTransactionRepo.initialize(
            applicationContext
        )

        LocalWalletRepo.initialize(
            applicationContext
        )

        LocalTransferRepo.initialize(
            applicationContext
        )

        LocalBudgetRepo.initialize(
            applicationContext
        )

        binding = ActivityMainBinding.inflate(
            layoutInflater
        )

        // Android 15 / Samsung edge-to-edge: reserve the status and gesture areas.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val root = binding.root
        val originalLeft = root.paddingLeft
        val originalRight = root.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                originalLeft + safe.left,
                safe.top,
                originalRight + safe.right,
                safe.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
