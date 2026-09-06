package com.liquorbee.invoicescanner.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liquorbee.invoicescanner.R
import com.liquorbee.invoicescanner.databinding.ActivityHomeBinding
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val ADMIN_MODE_PASSWORD = "LiquorBee444"
private const val TAP_COUNT_TO_UNLOCK = 5
private const val TAP_WINDOW_MS = 2000L
private const val UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000L
// Matches ScanActivity's READY_FOR_REVIEW_POLL_MS - Home is the screen a POS terminal actually
// sits on between actions, so it needs its own poll rather than relying on ScanActivity still
// being open by the time the server-side OCR job finishes (see ReadyForReviewNotifier).
private const val READY_FOR_REVIEW_POLL_MS = 60_000L

/**
 * The real home screen (Scan / Receive tiles) - post-login landing screen for a plain app launch.
 * The liquorbeescanner://scan deep link still bypasses this straight to ScanActivity (see
 * LoginActivity.goToNextScreen) since that's an explicit "go scan this PO now" action from the web,
 * not a general app open.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager
    private var tapCount = 0
    private var firstTapAt = 0L
    private var wasOutdated = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way - see AppNotifications.show() */ }

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

        binding.tileScan.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)) }
        binding.tileReceive.setOnClickListener { startActivity(Intent(this, PurchaseOrderListActivity::class.java)) }
        binding.textModeLabel.setOnClickListener { onModeLabelTapped() }
        binding.buttonLogout.setOnClickListener { logout() }
        binding.buttonSync.setOnClickListener { runUpdateCheck(announce = true) }
        // Tapping the status text itself ("Up to date"/"Update available") also forces an
        // immediate check, same as the 🔄 icon - previously only the icon was wired, so tapping
        // the text people actually read did nothing and left them waiting on the passive 5-minute
        // background poll instead.
        binding.textSyncStatus.setOnClickListener { runUpdateCheck(announce = true) }

        refreshModeLabel()
        loadStoreName()
        loadStagedOrdersCount()
        requestNotificationPermissionIfNeeded()

        // Auto-checks every 5 minutes while this screen is alive - quietly updates the status text
        // either way (no repeated pop-ups every 5 minutes, that would just interrupt whatever the
        // user is doing) and only interrupts with a real dialog the FIRST time it flips to
        // outdated. Tapping the sync icon manually always shows an explicit result (see
        // runUpdateCheck's announce param) since that's a deliberate action, not a background poll.
        lifecycleScope.launch {
            while (isActive) {
                runUpdateCheck(announce = false)
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }

        lifecycleScope.launch {
            while (isActive) {
                ReadyForReviewNotifier.checkAndNotify(this@HomeActivity, session)
                delay(READY_FOR_REVIEW_POLL_MS)
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
                    // A manual Sync tap always shows the dialog regardless of Later/history. The
                    // automatic background poll respects both: !wasOutdated stops it from popping
                    // again every 5 minutes within this same session, and wasDismissed stops it from
                    // popping again on a future app relaunch once the user has already said Later
                    // for this exact version - a genuinely newer version still gets its own popup.
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
        // Picks up a mode change made via a tap sequence that finished while this screen was
        // already showing, and reflects it immediately if the user backed out of a child screen.
        refreshModeLabel()
        // Refreshes the Receiving badge after backing out of that screen (an order just received
        // there should no longer count).
        loadStagedOrdersCount()
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
                // Already unlocked - tapping 5x again locks it back to Standard Mode, no password
                // needed to turn it back off.
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
                // Wrong password - dialog just closes, no error shown (don't hint at the real one).
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

    // Matches the web home page's Receiving tile badge exactly (home-page.ts's
    // getStagedOrdersCount/stagedOrdersCount): orange when there's something staged and ready to
    // receive, green at zero. Tooltip text matches the web's matTooltip verbatim.
    private fun loadStagedOrdersCount() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val count = api.getStagedOrdersCount()
                binding.textReceiveBadge.visibility = View.VISIBLE
                binding.textReceiveBadge.text = count.toString()
                binding.textReceiveBadge.setBackgroundResource(if (count > 0) R.drawable.bg_badge_orange else R.drawable.bg_badge_green)
                TooltipCompat.setTooltipText(binding.textReceiveBadge, "Purchase orders that have been staged and are ready to receive.")
            } catch (e: Exception) {
                binding.textReceiveBadge.visibility = View.GONE
            }
        }
    }

    // "Ready for review" / "Purchase order created" notifications (AppNotifications) are silently
    // no-ops without this - Android 13+ requires an explicit runtime grant before any notification
    // can show at all. Asked once here, right after login, rather than at the exact moment a
    // notification is about to fire (which would mean the very first one is always missed).
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun logout() {
        // Preserves the cached username/password for LoginActivity to prefill when Remember Me was
        // on (see SessionManager.logout()) - a full wipe here would defeat Remember Me's whole
        // point the moment anyone actually logged out.
        session.logout()
        AdminModeState.isEnabled = false
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
