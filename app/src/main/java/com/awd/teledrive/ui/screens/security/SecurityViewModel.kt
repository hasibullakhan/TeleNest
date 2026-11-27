package com.awd.teledrive.ui.screens.security

import androidx.lifecycle.ViewModel
import com.awd.teledrive.data.secure.BiometricCrypto
import com.awd.teledrive.data.secure.MasterPasswordService
import com.awd.teledrive.data.secure.SecureSessionManager
import com.awd.teledrive.data.secure.SecureSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val masterPasswordService: MasterPasswordService,
    private val secureSettings: SecureSettings,
    private val secureSessionManager: SecureSessionManager,
    private val biometricCrypto: BiometricCrypto
) : ViewModel() {

    private val _isPasswordSet = MutableStateFlow(masterPasswordService.isPasswordSet())
    val isPasswordSet = _isPasswordSet.asStateFlow()

    private val _isSecurityEnabled = MutableStateFlow(secureSettings.isSecurityEnabled())
    val isSecurityEnabled = _isSecurityEnabled.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(secureSettings.getBoolean("biometric_enabled"))
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked = _isLocked.asStateFlow()

    fun setSecurityEnabled(enabled: Boolean) {
        if (enabled && !_isPasswordSet.value) {
            // Requirement: If enabling and no password, trigger set password flow
            // This is a UI state trigger usually, but we update the preference
            // and the UI should react to !isPasswordSet
        }
        secureSettings.setSecurityEnabled(enabled)
        _isSecurityEnabled.value = enabled
    }

    fun setLocked(locked: Boolean) {
        if (_isSecurityEnabled.value && _isPasswordSet.value) {
            _isLocked.value = locked
            if (locked) {
                secureSessionManager.clearSession()
            }
        }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun unlockSecureMode(password: String): Boolean {
        return if (masterPasswordService.verifyPassword(password)) {
            secureSessionManager.setSessionPassword(password)
            // Ensure raw password is saved for future biometric use
            masterPasswordService.setPassword(password) 
            biometricCrypto.clearInvalidation()
            true
        } else false
    }

    fun lockSecureMode() {
        secureSessionManager.clearSession()
    }

    /**
     * Returns a KeyStore cipher to bind the biometric prompt to, or null when biometric unlock is
     * not possible right now (no password, key invalidated by a new fingerprint, nothing to enroll).
     * The caller then falls back to the master password.
     */
    fun createBiometricCipher(): Cipher? {
        if (!masterPasswordService.isPasswordSet() || biometricCrypto.isInvalidated()) return null
        return if (biometricCrypto.hasCredential()) {
            biometricCrypto.createUnlockCipher()
        } else if (!masterPasswordService.getStoredPassword().isNullOrEmpty()) {
            biometricCrypto.createEnrollCipher()
        } else {
            null
        }
    }

    /** Recovers the master password with an authenticated cipher and checks it is still valid. */
    private fun passwordFromBiometric(cipher: Cipher): String? {
        val password = try {
            if (biometricCrypto.hasCredential()) {
                biometricCrypto.decrypt(cipher)
            } else {
                val stored = masterPasswordService.getStoredPassword()
                if (stored.isNullOrEmpty()) {
                    null
                } else {
                    biometricCrypto.enroll(cipher, stored)
                    stored
                }
            }
        } catch (e: Exception) {
            null
        } ?: return null

        if (!masterPasswordService.verifyPassword(password)) {
            // Stored credential is out of date (password changed): drop it, master password re-enrolls.
            biometricCrypto.reset()
            return null
        }
        return password
    }

    /** App-lock screen: biometric success only counts when the KeyStore cipher really worked. */
    fun unlockAppWithBiometricCipher(cipher: Cipher): Boolean {
        passwordFromBiometric(cipher) ?: return false
        _error.value = null
        _isLocked.value = false
        return true
    }

    /** Secure folder: unlock the vault session using an authenticated KeyStore cipher. */
    fun unlockWithBiometricCipher(cipher: Cipher): Boolean {
        val password = passwordFromBiometric(cipher) ?: return false
        secureSessionManager.setSessionPassword(password)
        return true
    }

    val isSecureModeActive = secureSessionManager.decryptedPassword.map { it != null }

    fun setPassword(password: String) {
        masterPasswordService.setPassword(password)
        _isPasswordSet.value = true
        setSecurityEnabled(true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        secureSettings.saveBoolean("biometric_enabled", enabled)
        _isBiometricEnabled.value = enabled
    }

    fun verifyPassword(password: String): Boolean {
        val isValid = masterPasswordService.verifyPassword(password)
        if (!isValid) {
            _error.value = "Incorrect password"
        } else {
            _error.value = null
            _isLocked.value = false
            // Re-save to ensure biometric has access to the raw key
            masterPasswordService.setPassword(password)
            biometricCrypto.clearInvalidation()
        }
        return isValid
    }
}
