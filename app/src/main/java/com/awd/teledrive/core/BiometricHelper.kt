package com.awd.teledrive.core

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {
    fun showPrompt(
        activity: FragmentActivity,
        title: String,
        cryptoObject: BiometricPrompt.CryptoObject,
        onAuthenticated: (BiometricPrompt.CryptoObject) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val authenticatedCryptoObject = result.cryptoObject ?: run {
                        onError("Authentication failed")
                        return
                    }
                    onAuthenticated(authenticatedCryptoObject)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use master password")
            .build()
        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}
