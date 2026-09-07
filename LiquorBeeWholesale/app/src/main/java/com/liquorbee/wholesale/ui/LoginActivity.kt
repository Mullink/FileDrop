package com.liquorbee.wholesale.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import kotlinx.coroutines.launch
import com.liquorbee.wholesale.databinding.ActivityLoginBinding

/**
 * Launcher activity - same login every HennyAdminOnline user already uses (login/GetUserToken).
 * If a session already exists, skips straight to HomeActivity.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

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
        // retype everything.
        binding.checkboxRememberMe.isChecked = session.rememberMe
        if (session.rememberMe) {
            session.username?.let { binding.editUsername.setText(it) }
            session.password?.let { binding.editPassword.setText(it) }
        }

        binding.buttonLogin.setOnClickListener { attemptLogin() }
    }

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

    private fun goToNextScreen() {
        startActivity(Intent(this, HomeActivity::class.java))
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
