package com.liquorbee.invoicescanner.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the per-user JWT (and the username, so a silent re-login is possible if the token
 * expires) encrypted at rest. Same login every HennyAdminOnline user already uses -
 * `login/GetUserToken` with HTTP Basic auth - there is no separate auth system to build for this
 * app; see AuthRepository.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "invoice_scanner_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // commit() (synchronous, blocks until the write actually hits disk), not apply() (queues the
    // write and returns immediately) - apply()'s write is only guaranteed to flush during a normal
    // Activity onPause/onStop. A POS terminal that gets power-cycled or force-killed rather than
    // gracefully closed can lose an apply()'d write entirely, which read back as "Remember Me
    // didn't actually remember anything" despite the login having succeeded moments before.
    var jwtToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_TOKEN, value).commit() }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) { prefs.edit().putString(KEY_USERNAME, value).commit() }

    // Stored only long enough to support a silent re-login after a 401 (JWT expiry) - never sent
    // anywhere except back to the same login/GetUserToken endpoint the user already trusts.
    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) { prefs.edit().putString(KEY_PASSWORD, value).commit() }

    // "Remember Me" unchecked at login: the current process still has a working token (so it isn't
    // logged out mid-use), but nothing survives past this process's death - LoginActivity checks
    // this alongside isLoggedIn and clears the stale session instead of skipping straight past the
    // login screen. Defaults true so an existing install from before this flag existed (already
    // logged in) keeps behaving exactly as it did.
    var rememberMe: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, true)
        set(value) { prefs.edit().putBoolean(KEY_REMEMBER_ME, value).commit() }

    val isLoggedIn: Boolean
        get() = !jwtToken.isNullOrBlank()

    // Full wipe - genuinely nothing left, including the cached username/password. Only appropriate
    // when the user explicitly does NOT want anything remembered (Remember Me was off).
    fun clear() {
        prefs.edit().clear().commit()
    }

    // What the Logout button actually calls: always ends the current session (clears the token, so
    // isLoggedIn goes false and the login form shows again), but - when Remember Me was on -
    // deliberately keeps the cached username/password so LoginActivity can prefill them. This is
    // the whole point of "Remember Me": logging out and back in on a shared store device should be
    // one tap on Log In, not retyping credentials every time. If Remember Me was off, this behaves
    // exactly like clear() - nothing was meant to survive anyway.
    fun logout() {
        if (rememberMe) {
            jwtToken = null
        } else {
            clear()
        }
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER_ME = "remember_me"
    }
}
