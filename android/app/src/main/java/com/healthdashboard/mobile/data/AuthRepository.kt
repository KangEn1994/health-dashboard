package com.healthdashboard.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthdashboard.mobile.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private val Context.dataStore by preferencesDataStore(name = "health_dashboard_auth")

class AuthRepository(private val context: Context) {
    private val tokenKey = stringPreferencesKey("access_token")
    private val apiBaseUrlKey = stringPreferencesKey("api_base_url")

    private fun retrofit(baseUrl: String, token: String? = null): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            token?.takeIf { it.isNotBlank() }?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(requestBuilder.build())
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun login(password: String): String {
        val service = retrofit(apiBaseUrl()).create(ApiService::class.java)
        val response = service.login(LoginRequest(password))
        saveToken(response.access_token)
        return response.access_token
    }

    suspend fun service(): ApiService {
        val token = token()
        return retrofit(apiBaseUrl(), token).create(ApiService::class.java)
    }

    suspend fun token(): String? {
        return context.dataStore.data
            .map { preferences -> preferences[tokenKey] }
            .first()
    }

    suspend fun hasToken(): Boolean = !token().isNullOrBlank()

    suspend fun apiBaseUrl(): String {
        return context.dataStore.data
            .map { preferences -> preferences[apiBaseUrlKey] ?: BuildConfig.DEFAULT_API_BASE_URL }
            .first()
    }

    suspend fun saveApiBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[apiBaseUrlKey] = normalizeBaseUrl(baseUrl)
        }
    }

    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.remove(tokenKey)
        }
    }

    private suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[tokenKey] = token
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
