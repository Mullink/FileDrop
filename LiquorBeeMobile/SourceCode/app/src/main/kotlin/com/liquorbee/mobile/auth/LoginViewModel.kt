package com.liquorbee.mobile.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquorbee.mobile.core.network.ApiService
import com.liquorbee.mobile.core.network.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import java.io.IOException

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val apiService: ApiService,
    private val tokenStore: TokenStore,
    private val onLoggedIn: () -> Unit
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        // Pre-fill from a prior "Remember Me" login, if any - survives logout (see TokenStore.clear).
        if (tokenStore.rememberMeEnabled) {
            LoginUiState(
                username = tokenStore.rememberedUsername.orEmpty(),
                password = tokenStore.rememberedPassword.orEmpty(),
                rememberMe = true
            )
        } else {
            LoginUiState()
        }
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onRememberMeToggled(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = value)
    }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter your username and password.")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // Matches the web login exactly: GET with an HTTP Basic-auth header, not a
                // POST/JSON body - see LoginController.GetUserToken on the backend.
                val basicAuth = Credentials.basic(state.username.trim(), state.password)
                val response = apiService.login(basicAuth)

                if (response.isSuccessful) {
                    val token = response.body()?.string()?.trim()
                    if (token.isNullOrBlank()) {
                        setError("Login failed. Please try again.")
                    } else {
                        tokenStore.token = token
                        tokenStore.username = state.username.trim()
                        if (state.rememberMe) {
                            tokenStore.rememberMeEnabled = true
                            tokenStore.rememberedUsername = state.username.trim()
                            tokenStore.rememberedPassword = state.password
                        } else {
                            tokenStore.clearRememberedCredentials()
                        }
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        onLoggedIn()
                    }
                } else {
                    // The backend returns the real reason (e.g. a suspended-account billing
                    // message, or bad credentials) as a plain-text error body - surface it as-is
                    // rather than a generic message.
                    val serverMessage = response.errorBody()?.string()?.trim()
                    setError(
                        if (!serverMessage.isNullOrBlank()) serverMessage
                        else "Login failed. Please check your username and password."
                    )
                }
            } catch (e: IOException) {
                setError("Couldn't reach the server. Check your connection and try again.")
            } catch (e: Exception) {
                setError("Something went wrong. Please try again.")
            }
        }
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
    }
}
