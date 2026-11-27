package com.awd.teledrive.core

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

object BiometricHelper {
    /**
     * Shows a biometric prompt that is bound to a KeyStore [cipher].
     *
     * [onAuthenticated] is only called with the cipher returned in the CryptoObject of the result.
     * A prompt that succeeds without a CryptoObject is treated as an error, never as a success.
     * [onError] receives the BiometricPrompt error code and a message; the system retries
     * failed fingerprints inside the same prompt, so those do not reach [onError].
     */
    fun showPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher,
        onAuthenticated: (Cipher) -> Unit,
        onError: (errorCode: Int, message: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) {
                        onAuthenticated(authenticatedCipher)
                    } else {
                        onError(BiometricPrompt.ERROR_VENDOR, "Biometric verification failed")
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}
