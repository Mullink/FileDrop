package com.liquorbee.wholesale.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liquorbee.wholesale.databinding.ActivityHomeBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val ADMIN_MODE_PASSWORD = "LiquorBee444"
private const val TAP_COUNT_TO_UNLOCK = 5
private const val TAP_WINDOW_MS = 2000L
private const val UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000L

/** Post-login landing screen - one tile into Wholesale Manager, same shell (admin-mode gate,
 * update checker, store name/logout header) as LiquorBee Receiving. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private var tapCount = 0
    private var firstTapAt = 0L
    private var wasOutdated = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        if (!session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.tileWholesale.setOnClickListener { startActivity(Intent(this, WholesaleManagerActivity::class.java)) }
        binding.textModeLabel.setOnClickListener { onModeLabelTapped() }
        binding.buttonLogout.setOnClickListener { logout() }
        binding.buttonSync.setOnClickListener { runUpdateCheck(announce = true) }
        binding.textSyncStatus.setOnClickListener { runUpdateCheck(announce = true) }

        refreshModeLabel()
        loadStoreName()
        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            while (isActive) {
                runUpdateCheck(announce = false)
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun runUpdateCheck(announce: Boolean) {
        lifecycleScope.launch {
            val result = UpdateChecker.check(this@HomeActivity)
            when (result) {
                is UpdateChecker.Result.UpdateAvailable -> {
                    binding.textSyncStatus.text = "⚠ Update available"
                    binding.textSyncStatus.setTextColor(0xFFC62828.toInt())
                    if (announce || (!wasOutdated && !UpdateChecker.wasDismissed(this@HomeActivity, result.latestVersionCode))) {
                        UpdateChecker.showResult(this@HomeActivity, result, announceUpToDateAndFailure = true)
                    }
                    wasOutdated = true
                }
                UpdateChecker.Result.UpToDate -> {
                    binding.textSyncStatus.text = "✓ Up to date"
                    binding.textSyncStatus.setTextColor(0xFF2E7D32.toInt())
                    if (announce) UpdateChecker.showResult(this@HomeActivity, result, announceUpToDateAndFailure = true)
                    wasOutdated = false
                }
                UpdateChecker.Result.CheckFailed -> {
                    binding.textSyncStatus.text = ""
                    if (announce) UpdateChecker.showResult(this@HomeActivity, result, announceUpToDateAndFailure = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshModeLabel()
    }

    private fun onModeLabelTapped() {
        val now = System.currentTimeMillis()
        if (now - firstTapAt > TAP_WINDOW_MS) {
            tapCount = 0
            firstTapAt = now
        }
        tapCount++
        if (tapCount >= TAP_COUNT_TO_UNLOCK) {
            tapCount = 0
            if (AdminModeState.isEnabled) {
                AdminModeState.isEnabled = false
                refreshModeLabel()
            } else {
                promptForAdminPassword()
            }
        }
    }

    private fun promptForAdminPassword() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(this)
            .setTitle("Admin Mode")
            .setMessage("Enter the admin password to unlock diagnostics.")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString() == ADMIN_MODE_PASSWORD) {
                    AdminModeState.isEnabled = true
                    refreshModeLabel()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshModeLabel() {
        binding.textModeLabel.text = if (AdminModeState.isEnabled) "Admin Mode" else "Standard Mode"
    }

    private fun loadStoreName() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val settings = api.getAccountSettings()
                binding.textStoreName.text = "Store: ${settings.storeName ?: session.username ?: ""}"
            } catch (e: Exception) {
                binding.textStoreName.text = session.username?.let { "Store: $it" } ?: ""
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun logout() {
        session.logout()
        AdminModeState.isEnabled = false
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
