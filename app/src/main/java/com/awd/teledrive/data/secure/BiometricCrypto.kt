package com.awd.teledrive.data.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KeyStore-backed biometric cryptography.
 *
 * A single AES/GCM key is kept in the Android KeyStore with
 * userAuthenticationRequired = true and invalidatedByBiometricEnrollment = true.
 * Biometric success is therefore only useful when the BiometricPrompt CryptoObject
 * cipher actually decrypts (or, on first enrollment, encrypts) the stored master
 * password. A forged onAuthenticationSucceeded callback with no CryptoObject, or
 * with a cipher that was not authorized by a real biometric, cannot decrypt anything
 * and must not unlock anything.
 *
 * Migration is lazy and non-destructive: existing vault/file data, the stored master
 * password hash, and the password fallback are never touched here. Only an extra
 * biometric ciphertext/IV pair (for the stored master password) is written, and only
 * after a real biometric authentication. If the KeyStore key is invalidated, for
 * example by enrolling a new fingerprint, the biometric credential is dropped and
 * the caller falls back to the master password, which can then re-enroll biometric.
 */
@Singleton
class BiometricCrypto @Inject constructor(
    private val secureSettings: SecureSettings
) {
    enum class Mode { DECRYPT, ENROLL }

    data class Crypto(val cryptoObject: BiometricPrompt.CryptoObject, val mode: Mode)

    companion object {
        private const val KEY_ALIAS = "telenest_biometric_key"
        private const val PREF_CIPHERTEXT = "biometric_ciphertext"
        private const val PREF_IV = "biometric_iv"
    }

    fun hasBiometricCredential(): Boolean {
        val ciphertext = secureSettings.getString(PREF_CIPHERTEXT, "") ?: ""
        val iv = secureSettings.getString(PREF_IV, "") ?: ""
        return ciphertext.isNotEmpty() && iv.isNotEmpty()
    }

    fun createCrypto(): Crypto? {
        return try {
            if (hasBiometricCredential()) {
                val key = getKey() ?: return null
                val iv = Base64.decode(secureSettings.getString(PREF_IV, "") ?: "", Base64.DEFAULT)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                Crypto(BiometricPrompt.CryptoObject(cipher), Mode.DECRYPT)
            } else {
                val key = getOrCreateKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                Crypto(BiometricPrompt.CryptoObject(cipher), Mode.ENROLL)
            }
        } catch (e: Exception) {
            clearBiometricCredential()
            null
        }
    }

    fun decryptPassword(cipher: Cipher): String? {
        return try {
            val ciphertext = secureSettings.getString(PREF_CIPHERTEXT, "") ?: ""
            if (ciphertext.isEmpty()) return null
            val bytes = Base64.decode(ciphertext, Base64.DEFAULT)
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun enrollPassword(password: String, cipher: Cipher): Boolean {
        return try {
            val iv = cipher.iv ?: return false
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            secureSettings.saveString(PREF_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            secureSettings.saveString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearBiometricCredential() {
        try {
            secureSettings.saveString(PREF_CIPHERTEXT, "")
            secureSettings.saveString(PREF_IV, "")
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
        }
    }

    private fun getKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
