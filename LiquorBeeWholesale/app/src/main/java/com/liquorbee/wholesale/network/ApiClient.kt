package com.liquorbee.wholesale.network

import android.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/** Same dev API the desktop scanner and the web app already point at - see the approved plan
 * (sprightly-waddling-pearl.md) for confirmation this is the shared, already-live environment. */
object ApiConfig {
    const val BASE_URL = "https://dev-liquorbee-ege6cweqduhmayej.canadacentral-01.azurewebsites.net/"
}

class AuthInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.jwtToken
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

// Completes the "remember me" story SessionManager's own doc comment already promised (it stores
// username/password specifically "to support a silent re-login after a 401") but nothing ever
// actually called back into. Without this, a user who is technically still "remembered"
// (isLoggedIn checks jwtToken alone) gets bounced back to typing credentials the moment their JWT
// expires, which reads exactly like "it never remembered me" even though it did.
class AuthAuthenticator(private val session: SessionManager) : Authenticator {
    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        // Never retry more than once per request - a fresh token that still gets a 401 means the
        // stored password itself is no longer valid (changed server-side), not a transient expiry.
        if (responseCount(response) >= 2) return null

        val username = session.username
        val password = session.password
        if (username.isNullOrBlank() || password.isNullOrBlank()) return null

        val freshToken = try {
            // authenticate() already runs on an OkHttp background thread (never the UI thread) -
            // blocking here for one small token call is the standard pattern for this exact case.
            runBlocking { ApiClient.buildAnonymousApi().getUserToken(ApiClient.basicAuthHeader(username, password)) }
        } catch (e: Exception) {
            null
        } ?: return null

        session.jwtToken = freshToken
        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}

object ApiClient {

    fun buildAuthenticatedApi(session: SessionManager): HennyAdminApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(AuthAuthenticator(session))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // page uploads can be several MB each
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            // Scalars first: GetUserToken returns a raw plain-text JWT (not JSON), same
            // 'responseType: text' the web login already uses - Gson would try (and fail) to
            // parse it as JSON if this were registered after Gson instead of before it.
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HennyAdminApi::class.java)
    }

    /** No auth interceptor - GetUserToken supplies its own Basic auth header per-call. */
    fun buildAnonymousApi(): HennyAdminApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            // Scalars first: GetUserToken returns a raw plain-text JWT (not JSON), same
            // 'responseType: text' the web login already uses - Gson would try (and fail) to
            // parse it as JSON if this were registered after Gson instead of before it.
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HennyAdminApi::class.java)
    }

    fun basicAuthHeader(username: String, password: String): String {
        val raw = "$username:$password"
        val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }
}
