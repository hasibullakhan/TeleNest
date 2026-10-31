package com.awd.teledrive.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.teledrive.R
import com.awd.teledrive.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

sealed class LoginUiState {
    object InitialSetup : LoginUiState()
    object EnteringCode : LoginUiState()
    object EnteringPassword : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _selectedCountry = MutableStateFlow(CountryCodeRepository.getInitialDeviceCountry(context))
    val selectedCountry: StateFlow<CountryDialCode> = _selectedCountry.asStateFlow()
    private var userManuallySelectedCountry = false

    private fun isBenignNotice(error: String?): Boolean {
        if (error.isNullOrEmpty()) return true
        return error.contains("Unexpected setTdlibParameters", ignoreCase = true) ||
            error.contains("Another authorization query has started", ignoreCase = true)
    }

    val authError: StateFlow<String?> = authRepository.authError
        .map { error ->
            if (isBenignNotice(error)) {
                null
            } else {
                mapTechnicalError(error!!)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun mapTechnicalError(error: String): String {
        return when {
            error.contains("PHONE_NUMBER_INVALID", ignoreCase = true) -> context.getString(R.string.err_phone_invalid)
            error.contains("PHONE_CODE_INVALID", ignoreCase = true) -> context.getString(R.string.err_code_invalid)
            error.contains("PHONE_CODE_EXPIRED", ignoreCase = true) -> context.getString(R.string.err_code_expired)
            error.contains("PASSWORD_HASH_INVALID", ignoreCase = true) -> context.getString(R.string.err_password_invalid)
            error.contains("FLOOD_WAIT", ignoreCase = true) -> context.getString(R.string.err_flood_wait)
            error.contains("API_ID_INVALID", ignoreCase = true) || error.contains("API_HASH_INVALID", ignoreCase = true) -> context.getString(R.string.err_api_invalid)
            error.contains("Connection error", ignoreCase = true) -> context.getString(R.string.err_connection)
            else -> context.getString(R.string.err_unknown, error)
        }
    }

    val uiState: StateFlow<LoginUiState> = authRepository.authState
        .map { state ->
            when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters,
                is TdApi.AuthorizationStateWaitPhoneNumber -> LoginUiState.InitialSetup
                is TdApi.AuthorizationStateWaitCode -> LoginUiState.EnteringCode
                is TdApi.AuthorizationStateWaitPassword -> LoginUiState.EnteringPassword
                is TdApi.AuthorizationStateReady -> LoginUiState.Success
                null -> {
                    if (!authRepository.isApiConfigured()) LoginUiState.InitialSetup
                    else LoginUiState.Loading
                }
                else -> LoginUiState.Loading
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoginUiState.Loading)

    init {
        authRepository.setLocalError(null)

        // Automatically detect user's country via IP geolocation
        viewModelScope.launch {
            val ipCountry = CountryCodeRepository.detectCountryFromIp()
            if (ipCountry != null && !userManuallySelectedCountry) {
                _selectedCountry.value = ipCountry
            }
        }

        // Stop loading indicator and clear any stale error once state transitions to OTP / Password / Ready
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                if (state is TdApi.AuthorizationStateWaitCode ||
                    state is TdApi.AuthorizationStateWaitPassword ||
                    state is TdApi.AuthorizationStateReady
                ) {
                    _isProcessing.value = false
                    authRepository.setLocalError(null)
                }
            }
        }

        // Watch for errors to stop loading indicator
        viewModelScope.launch {
            authRepository.authError.collect { error ->
                if (!isBenignNotice(error)) {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun selectCountry(country: CountryDialCode) {
        userManuallySelectedCountry = true
        _selectedCountry.value = country
        authRepository.setLocalError(null)
    }

    fun clearError() {
        if (authRepository.authError.value != null) {
            authRepository.setLocalError(null)
        }
    }

    fun onLoginSubmit(apiId: String, apiHash: String, phoneNumber: String) {
        if (_isProcessing.value) return
        if (apiId.isBlank() || apiHash.isBlank() || phoneNumber.isBlank()) {
            authRepository.setLocalError(context.getString(R.string.err_all_fields_required))
            return
        }

        val id = apiId.trim().toIntOrNull()
        if (id == null || id <= 0) {
            authRepository.setLocalError(context.getString(R.string.err_api_invalid))
            return
        }

        val cleanHash = apiHash.trim()
        if (cleanHash.length < 10) {
            authRepository.setLocalError(context.getString(R.string.err_api_invalid))
            return
        }

        val fullPhoneNumber = CountryCodeRepository.formatFullPhoneNumber(
            selectedCountry = _selectedCountry.value,
            rawPhone = phoneNumber
        )
        if (fullPhoneNumber.length < 7) {
            authRepository.setLocalError(context.getString(R.string.err_phone_invalid))
            return
        }

        _isProcessing.value = true
        authRepository.submitCredentialsAndPhone(
            apiId = id,
            apiHash = cleanHash,
            phoneNumber = fullPhoneNumber
        )
    }

    val isAddingNewAccount: StateFlow<Boolean> = authRepository.isAddingNewAccount
    val savedAccounts = authRepository.savedAccounts

    fun getConfiguredApiId(): String {
        val id = authRepository.getApiId()
        return if (id > 0) id.toString() else ""
    }

    fun getConfiguredApiHash(): String {
        return authRepository.getApiHash()
    }

    fun isApiConfigured(): Boolean {
        return authRepository.isApiConfigured()
    }

    fun cancelAddNewAccount(): Boolean {
        _isProcessing.value = false
        authRepository.setLocalError(null)
        return authRepository.cancelAddNewAccount()
    }

    fun onResetApi() {
        _isProcessing.value = false
        authRepository.clearApiCredentials()
    }

    fun onCodeSubmit(code: String) {
        if (_isProcessing.value) return
        val clean = code.trim()
        if (clean.isEmpty()) return
        _isProcessing.value = true
        authRepository.checkAuthenticationCode(clean)
    }

    fun onPasswordSubmit(password: String) {
        if (_isProcessing.value) return
        if (password.isEmpty()) return
        _isProcessing.value = true
        authRepository.checkPassword(password)
    }
}
