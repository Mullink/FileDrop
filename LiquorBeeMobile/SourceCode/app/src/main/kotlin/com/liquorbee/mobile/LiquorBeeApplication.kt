package com.liquorbee.mobile

import android.app.Application
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.NetworkModule
import com.liquorbee.mobile.core.network.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the app-wide singletons (network client, token storage) and a single source of truth for
 * "are we logged in right now" that both the nav graph (start destination) and the 401 handler
 * (force sign-out from anywhere) drive. Kept intentionally simple (no DI framework) given the
 * current app size - revisit if/when this grows past Phase 1.
 */
class LiquorBeeApplication : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var apiService: ApiService
        private set

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> get() = _isLoggedIn

    // Mirrors the web app's sessionStorage "justLoggedIn" flag (see home-page.ts) - true for
    // exactly one Home screen creation right after a successful login, so the zoom-burst splash
    // plays once per login rather than every time Home is reached (e.g. after a 401 forces you
    // back to it, or the process is merely resumed).
    private var justLoggedIn = false

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        apiService = NetworkModule.createApiService(tokenStore) { signOut() }
        _isLoggedIn.value = tokenStore.isLoggedIn
    }

    fun signIn() {
        justLoggedIn = true
        _isLoggedIn.value = true
    }

    fun consumeJustLoggedIn(): Boolean {
        val value = justLoggedIn
        justLoggedIn = false
        return value
    }

    fun signOut() {
        tokenStore.clear()
        _isLoggedIn.value = false
    }
}
