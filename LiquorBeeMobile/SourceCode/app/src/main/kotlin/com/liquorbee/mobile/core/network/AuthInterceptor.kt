package com.liquorbee.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches "Authorization: Bearer {token}" to every request that doesn't already carry its own
 * Authorization header - the login call passes its own Basic-auth header explicitly (see
 * ApiService.login) and must pass through untouched. A 401 anywhere means the stored token is
 * gone/expired (the backend has no refresh mechanism - see plan notes), so [onUnauthorized] is
 * invoked to clear it and route back to the login screen.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val onUnauthorized: () -> Unit
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = if (original.header("Authorization") == null) {
            val token = tokenStore.token
            if (token != null) {
                original.newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else {
                original
            }
        } else {
            original
        }

        val response = chain.proceed(request)
        if (response.code == 401) {
            onUnauthorized()
        }
        return response
    }
}
