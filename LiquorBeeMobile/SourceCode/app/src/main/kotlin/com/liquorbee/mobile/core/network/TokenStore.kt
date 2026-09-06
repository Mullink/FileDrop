package com.liquorbee.mobile.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Mirrors the web app's localStorage id_token/username pair (see AuthService), but backed by
 * EncryptedSharedPreferences since this is a native app with real at-rest storage, not a browser.
 * There is no refresh token to store - the web app doesn't have one either (see plan notes): a
 * 401 anywhere just means "clear this and show the login screen again".
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "liquorbee_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) { prefs.edit().putString(KEY_TOKEN, value).apply() }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) { prefs.edit().putString(KEY_USERNAME, value).apply() }

    // "Remember Me" credentials - deliberately survive signOut()/clear() (which only tears down the
    // active session) since the whole point is to skip retyping on the NEXT login. Stored in the
    // same EncryptedSharedPreferences instance as the token, so this is no less secure than the
    // token itself already stored here.
    var rememberMeEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, false)
        set(value) { prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply() }

    var rememberedUsername: String?
        get() = prefs.getString(KEY_REMEMBERED_USERNAME, null)
        set(value) { prefs.edit().putString(KEY_REMEMBERED_USERNAME, value).apply() }

    var rememberedPassword: String?
        get() = prefs.getString(KEY_REMEMBERED_PASSWORD, null)
        set(value) { prefs.edit().putString(KEY_REMEMBERED_PASSWORD, value).apply() }

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    // Only tears down the active session (token/username) - remembered credentials are untouched
    // so a "Remember Me" login survives a logout.
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply()
    }

    fun clearRememberedCredentials() {
        prefs.edit()
            .remove(KEY_REMEMBER_ME)
            .remove(KEY_REMEMBERED_USERNAME)
            .remove(KEY_REMEMBERED_PASSWORD)
            .apply()
    }

    private companion object {
        const val KEY_TOKEN = "id_token"
        const val KEY_USERNAME = "username"
        const val KEY_REMEMBER_ME = "remember_me_enabled"
        const val KEY_REMEMBERED_USERNAME = "remembered_username"
        const val KEY_REMEMBERED_PASSWORD = "remembered_password"
    }
}
