package com.awd.teledrive.data.secure

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM file encryption with a password-derived key.
 *
 * File formats:
 *  - v2 (written now):  MAGIC(8) | version(1) | iterations(4, big endian) | salt(16) | iv(12) | ciphertext+tag
 *    The header is authenticated as GCM associated data, so the iteration count cannot be tampered with.
 *  - legacy (v1, still readable): salt(16) | iv(12) | ciphertext+tag, derived with 10,000 PBKDF2 iterations.
 *
 * Old encrypted files keep working: the reader detects the format from the magic bytes.
 */
@Singleton
class EncryptionManager @Inject constructor() {
    private val ALGORITHM = "AES/GCM/NoPadding"
    private val TAG_LENGTH_BIT = 128
    private val IV_LENGTH_BYTE = 12
    private val SALT_LENGTH_BYTE = 16
    private val KEY_LENGTH_BIT = 256

    private val LEGACY_ITERATION_COUNT = 10_000
    private val CURRENT_ITERATION_COUNT = 250_000
    private val MAX_ACCEPTED_ITERATIONS = 2_000_000

    private val MAGIC = byteArrayOf('T'.code.toByte(), 'N'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(),
        'T'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte())
    private val FORMAT_VERSION: Byte = 2
    private val HEADER_FIXED_LENGTH = 8 + 1 + 4

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BIT)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    suspend fun encryptFile(inputFile: File, outputFile: File, password: String, onProgress: (Float) -> Unit = {}) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTE).also { random.nextBytes(it) }

        val header = ByteBuffer.allocate(HEADER_FIXED_LENGTH)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .putInt(CURRENT_ITERATION_COUNT)
            .array()

        val key = deriveKey(password, salt, CURRENT_ITERATION_COUNT)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        cipher.updateAAD(header)

        val totalSize = inputFile.length()
        var processedBytes = 0L
        var lastReportedProgress = -1f

        FileOutputStream(outputFile).use { fos ->
            fos.write(header)
            fos.write(salt)
            fos.write(iv)

            CipherOutputStream(fos, cipher).use { cos ->
                inputFile.inputStream().use { fis ->
                    val buffer = ByteArray(65536)
                    var bytesRead = fis.read(buffer)
                    while (bytesRead != -1) {
                        cos.write(buffer, 0, bytesRead)
                        processedBytes += bytesRead

                        if (totalSize > 0) {
                            val currentProgress = processedBytes.toFloat() / totalSize.toFloat()
                            // Only callback every 2% to reduce overhead during fast processing
                            if (currentProgress - lastReportedProgress >= 0.02f || currentProgress >= 1f) {
                                onProgress(currentProgress)
                                lastReportedProgress = currentProgress
                                kotlinx.coroutines.yield()
                            }
                        }
                        bytesRead = fis.read(buffer)
                    }
                }
            }
        }
    }

    suspend fun decryptFile(encryptedFile: File, outputFile: File, password: String, onProgress: (Float) -> Unit = {}) {
        var processedBytes = 0L
        var lastReportedProgress = -1f

        FileInputStream(encryptedFile).use { fis ->
            val firstBlock = readFully(fis, 8) ?: throw Exception("Invalid encrypted file")

            val salt: ByteArray
            val iterations: Int
            var header: ByteArray? = null
            var headerBytes = 0

            if (firstBlock.contentEquals(MAGIC)) {
                val rest = readFully(fis, 1 + 4) ?: throw Exception("Invalid encrypted file header")
                val buf = ByteBuffer.wrap(rest)
                val version = buf.get()
                if (version != FORMAT_VERSION) throw Exception("Unsupported encrypted file version: $version")
                iterations = buf.int
                if (iterations < 1 || iterations > MAX_ACCEPTED_ITERATIONS) {
                    throw Exception("Invalid key derivation settings in encrypted file")
                }
                header = firstBlock + rest
                headerBytes = HEADER_FIXED_LENGTH
                salt = readFully(fis, SALT_LENGTH_BYTE) ?: throw Exception("Invalid salt in encrypted file")
            } else {
                // Legacy format: the 8 bytes we read are the first half of the salt.
                val secondHalf = readFully(fis, SALT_LENGTH_BYTE - 8) ?: throw Exception("Invalid salt in encrypted file")
                salt = firstBlock + secondHalf
                iterations = LEGACY_ITERATION_COUNT
            }

            val iv = readFully(fis, IV_LENGTH_BYTE) ?: throw Exception("Invalid IV in encrypted file")
            val totalSize = encryptedFile.length() - headerBytes - SALT_LENGTH_BYTE - IV_LENGTH_BYTE

            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            if (header != null) cipher.updateAAD(header)

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(65536)
                    var bytesRead = cis.read(buffer)
                    while (bytesRead != -1) {
                        fos.write(buffer, 0, bytesRead)
                        processedBytes += bytesRead

                        if (totalSize > 0) {
                            val currentProgress = processedBytes.toFloat() / totalSize.toFloat()
                            if (currentProgress - lastReportedProgress >= 0.02f || currentProgress >= 1f) {
                                onProgress(currentProgress)
                                lastReportedProgress = currentProgress
                                kotlinx.coroutines.yield()
                            }
                        }
                        bytesRead = cis.read(buffer)
                    }
                }
            }
        }
    }

    /** Reads exactly [length] bytes, or returns null if the stream ends first. */
    private fun readFully(input: InputStream, length: Int): ByteArray? {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(out, offset, length - offset)
            if (n < 0) return null
            offset += n
        }
        return out
    }
}
