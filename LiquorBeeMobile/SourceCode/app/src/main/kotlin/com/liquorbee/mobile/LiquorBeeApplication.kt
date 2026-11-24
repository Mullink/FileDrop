package com.liquorbee.mobile

import android.app.Application
import com.liquorbee.mobile.carts.CartStore
import com.liquorbee.mobile.carts.CatalogRepository
import com.liquorbee.mobile.carts.RestockRepository
import com.liquorbee.mobile.carts.ReorderRepository
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

    // Shared by the Scan and Carts screens so both always see the same carts/catalog.
    val cartStore: CartStore by lazy { CartStore(this) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(apiService) }
    val restockRepository: RestockRepository by lazy { RestockRepository(apiService) }
    val reorderRepository: ReorderRepository by lazy { ReorderRepository(this, apiService, catalogRepository) }
    val itemInsightsRepository: com.liquorbee.mobile.carts.ItemInsightsRepository by lazy { com.liquorbee.mobile.carts.ItemInsightsRepository(apiService) }
    /** Whether this store sells wholesale (web's Wholesale Manager switch), plus its saved orders list. */
    val wholesaleAccess: com.liquorbee.mobile.wholesale.WholesaleAccess by lazy { com.liquorbee.mobile.wholesale.WholesaleAccess(this, apiService) }

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
        // Carts belong to the store that was signed in - don't carry them over to the next login.
        cartStore.clearAll()
        catalogRepository.clear()
        restockRepository.reset()
        reorderRepository.reset()
        itemInsightsRepository.reset()
        wholesaleAccess.reset()
        _isLoggedIn.value = false
    }
}
