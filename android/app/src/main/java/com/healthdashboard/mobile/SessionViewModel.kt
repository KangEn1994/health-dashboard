package com.healthdashboard.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.healthdashboard.mobile.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val apiBaseUrl: String = "",
)

class SessionViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isAuthenticated = authRepository.hasToken(),
                apiBaseUrl = authRepository.apiBaseUrl(),
            )
        }
    }

    fun login(password: String, apiBaseUrl: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                authRepository.saveApiBaseUrl(apiBaseUrl)
                authRepository.login(password)
            }.onSuccess {
                _state.value = SessionUiState(
                    isAuthenticated = true,
                    apiBaseUrl = authRepository.apiBaseUrl(),
                )
            }.onFailure { error ->
                _state.value = SessionUiState(
                    isAuthenticated = false,
                    errorMessage = error.message ?: "登录失败",
                    apiBaseUrl = authRepository.apiBaseUrl(),
                )
            }
        }
    }

    fun saveApiBaseUrl(apiBaseUrl: String) {
        viewModelScope.launch {
            authRepository.saveApiBaseUrl(apiBaseUrl)
            _state.value = _state.value.copy(apiBaseUrl = authRepository.apiBaseUrl())
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = SessionUiState(
                isAuthenticated = false,
                apiBaseUrl = authRepository.apiBaseUrl(),
            )
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SessionViewModel(authRepository) as T
                }
            }
    }
}
