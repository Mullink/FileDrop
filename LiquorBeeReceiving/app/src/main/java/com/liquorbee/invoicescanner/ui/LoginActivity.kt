package com.liquorbee.invoicescanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.launch
import com.liquorbee.invoicescanner.databinding.ActivityLoginBinding

const val EXTRA_INVOICE_NUMBER = "extra_invoice_number"

/**
 * Launcher activity, and also the deep-link entry point for liquorbeescanner://scan?invoiceNumber=...
 * (must match exactly what pos-purchase-orders.ts's openMobileScanner() sends). If a session
 * already exists, skips straight to ScanActivity - login only happens once per install/logout.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private var pendingInvoiceNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        pendingInvoiceNumber = extractInvoiceNumberFromIntent(intent)
        updatePendingInvoiceBanner()

        if (session.isLoggedIn) {
            if (session.rememberMe) {
                goToNextScreen()
                return
            }
            // Remember Me was unchecked last time - this process's session died along with it, so
            // the leftover token/username are stale and should never be silently reused.
            session.clear()
        }

        // Showing the actual login form (no valid token, or Remember Me is off) - if there's a
        // cached username/password from before (e.g. right after tapping Logout, which preserves
        // them exactly for this), prefill both fields so the user only has to tap Log In, not
        // retype everything. This is the actual point of Remember Me on a shared store device.
        binding.checkboxRememberMe.isChecked = session.rememberMe
        if (session.rememberMe) {
            session.username?.let { binding.editUsername.setText(it) }
            session.password?.let { binding.editPassword.setText(it) }
        }

        binding.buttonLogin.setOnClickListener { attemptLogin() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launchMode means a second deep-link tap while this Activity is already on
        // top delivers here instead of creating a new instance. Confirmed via live emulator
        // testing that this path previously updated pendingInvoiceNumber but never refreshed the
        // on-screen banner (updatePendingInvoiceBanner() was only ever called from onCreate) -
        // tapping the deep link a second time while the app was already open silently dropped the
        // PO context from the visible UI even though the variable itself was still correct.
        extractInvoiceNumberFromIntent(intent)?.let {
            pendingInvoiceNumber = it
            updatePendingInvoiceBanner()
            if (session.isLoggedIn) goToNextScreen()
        }
    }

    private fun updatePendingInvoiceBanner() {
        if (pendingInvoiceNumber != null) {
            binding.textPendingInvoice.visibility = View.VISIBLE
            binding.textPendingInvoice.text = "Scanning for PO #$pendingInvoiceNumber"
        }
    }

    private fun extractInvoiceNumberFromIntent(intent: Intent?): String? =
        intent?.data?.getQueryParameter("invoiceNumber")

    private fun attemptLogin() {
        val username = binding.editUsername.text.toString().trim()
        val password = binding.editPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter a username and password.")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAnonymousApi()
                val header = ApiClient.basicAuthHeader(username, password)
                val token = api.getUserToken(header)
                val rememberMe = binding.checkboxRememberMe.isChecked

                session.jwtToken = token
                session.username = username
                // Only persisted when Remember Me is checked - this is what the silent-relogin
                // Authenticator (ApiClient.kt) keys off of, and leaving it unset when unchecked is
                // what stops it from ever kicking in.
                session.password = if (rememberMe) password else null
                session.rememberMe = rememberMe

                setLoading(false)
                goToNextScreen()
            } catch (e: Exception) {
                setLoading(false)
                showError(e.message ?: "Login failed. Check your username and password.")
            }
        }
    }

    // A deep-link-sourced invoice number means "go scan this PO now" - straight to ScanActivity,
    // bypassing the Home tiles. A plain login/app-launch (no pending invoice) lands on HomeActivity.
    private fun goToNextScreen() {
        if (pendingInvoiceNumber != null) {
            val intent = Intent(this, ScanActivity::class.java)
            intent.putExtra(EXTRA_INVOICE_NUMBER, pendingInvoiceNumber)
            startActivity(intent)
        } else {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.buttonLogin.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.textLoginError.text = message
        binding.textLoginError.visibility = View.VISIBLE
    }
}
