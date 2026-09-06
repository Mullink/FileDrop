package com.liquorbee.mobile.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Matches the live web app's actual deployed backend - environment.prod.ts's baseApiUrl (the
    // Angular build config that's actually shipped), and the same URL
    // LiquorBeeInvoiceScannerAndroid's ApiClient.BASE_URL uses. hennyadmin.azurewebsites.net is
    // environment.ts's (non-prod build config) URL - an old/local-only slot, not the live backend.
    private const val BASE_URL = "https://dev-liquorbee-ege6cweqduhmayej.canadacentral-01.azurewebsites.net/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // kotlinx.serialization's default is to OMIT any property that equals its Kotlin-declared
        // default value (e.g. an empty-string default) from the outgoing JSON entirely, rather
        // than sending it as "". The backend DTOs here (see ItemComboDto.LabelFormat/
        // LabelPrinterFormat) are non-nullable C# strings with implicit "required" model
        // validation, so an omitted property fails with "the X field is required" even though the
        // Kotlin code clearly set a value - explicitly encoding defaults sends every field always.
        encodeDefaults = true
    }

    fun createApiService(tokenStore: TokenStore, onUnauthorized: () -> Unit): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore, onUnauthorized))
            .addInterceptor(logging)
            // OkHttp's 10s default read timeout is too short for a few genuinely heavy endpoints -
            // creating a full-store ("entire store count") inventory session enumerates the whole
            // catalog server-side, which reliably took longer than 10s and surfaced as a client-side
            // SocketTimeoutException even though the server would have eventually responded fine.
            // Applied client-wide (not per-call) since submitting a multi-page invoice scan or a
            // large label-print batch can be similarly slow.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
