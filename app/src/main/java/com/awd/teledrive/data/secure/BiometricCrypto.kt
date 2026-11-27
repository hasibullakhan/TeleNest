package com.awd.teledrive.data.secure

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Binds biometric unlock to a hardware-backed Android KeyStore key.
 *
 * The key requires a fresh biometric authentication for every use and is invalidated when a new
 * fingerprint/face is enrolled on the device. The stored master password is kept encrypted under
 * this key, so a biometric prompt only unlocks the vault when the KeyStore cipher really works
 * (a bypassed or faked prompt cannot produce a working cipher).
 */
@Singleton
class BiometricCrypto @Inject constructor(
    private val secureSettings: SecureSettings
) {
    companion object {
        private const val TAG = "BiometricCrypto"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "telenest_biometric_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREF_CIPHERTEXT = "biometric_ciphertext"
        private const val PREF_IV = "biometric_iv"
        private const val PREF_INVALIDATED = "biometric_invalidated"
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** True when a biometric-bound credential has already been enrolled. */
    fun hasCredential(): Boolean {
        return try {
            !secureSettings.getString(PREF_CIPHERTEXT).isNullOrEmpty() &&
                !secureSettings.getString(PREF_IV).isNullOrEmpty() &&
                keyStore().containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /** True when the key was invalidated (new biometric enrolled). Master password is then required. */
    fun isInvalidated(): Boolean = secureSettings.getBoolean(PREF_INVALIDATED, false)

    /** Called after the user proves knowledge of the master password: allows biometric re-enrollment. */
    fun clearInvalidation() {
        if (isInvalidated()) {
            reset()
        }
    }

    /** Removes the KeyStore key and the stored ciphertext. */
    fun reset() {
        try {
            val ks = keyStore()
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete biometric key", e)
        }
        secureSettings.saveString(PREF_CIPHERTEXT, "")
        secureSettings.saveString(PREF_IV, "")
        secureSettings.saveBoolean(PREF_INVALIDATED, false)
    }

    private fun markInvalidated() {
        try {
            val ks = keyStore()
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
        secureSettings.saveString(PREF_CIPHERTEXT, "")
        secureSettings.saveString(PREF_IV, "")
        secureSettings.saveBoolean(PREF_INVALIDATED, true)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Timeout 0 = authentication required for every single use of the key.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** Cipher used to enroll (encrypt) the stored master password on the first biometric unlock. */
    fun createEnrollCipher(): Cipher? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            markInvalidated()
            null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot create enroll cipher", e)
            null
        }
    }

    /** Cipher used to decrypt the stored master password on later biometric unlocks. */
    fun createUnlockCipher(): Cipher? {
        if (!hasCredential()) return null
        return try {
            val iv = Base64.decode(secureSettings.getString(PREF_IV).orEmpty(), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            markInvalidated()
            null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot create unlock cipher", e)
            null
        }
    }

    /** Encrypts [password] with an authenticated [cipher] and stores only the ciphertext + IV. */
    fun enroll(cipher: Cipher, password: String) {
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        secureSettings.saveString(PREF_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
        secureSettings.saveString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        secureSettings.saveBoolean(PREF_INVALIDATED, false)
    }

    /** Decrypts the stored master password with an authenticated [cipher]; null if it fails. */
    fun decrypt(cipher: Cipher): String? {
        return try {
            val encrypted = Base64.decode(secureSettings.getString(PREF_CIPHERTEXT).orEmpty(), Base64.NO_WRAP)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Biometric decrypt failed", e)
            null
        }
    }
}
