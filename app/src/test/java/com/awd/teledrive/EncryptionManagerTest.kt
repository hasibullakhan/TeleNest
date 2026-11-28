package com.awd.teledrive

import com.awd.teledrive.data.secure.EncryptionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val manager = EncryptionManager()

    private fun sampleBytes(size: Int): ByteArray = ByteArray(size).also { java.util.Random(42).nextBytes(it) }

    @Test
    fun roundTrip_restoresOriginalContent() = runBlocking {
        val plain = tmp.newFile("plain.bin").apply { writeBytes(sampleBytes(200_000)) }
        val enc = tmp.newFile("enc.bin")
        val dec = tmp.newFile("dec.bin")

        manager.encryptFile(plain, enc, "correct horse battery staple")
        manager.decryptFile(enc, dec, "correct horse battery staple")

        assertArrayEquals(plain.readBytes(), dec.readBytes())
    }

    @Test
    fun newFiles_startWithMagicHeader() = runBlocking {
        val plain = tmp.newFile("p.bin").apply { writeBytes(sampleBytes(100)) }
        val enc = tmp.newFile("e.bin")
        manager.encryptFile(plain, enc, "pw")
        assertEquals("TNESTENC", String(enc.readBytes().copyOfRange(0, 8), Charsets.US_ASCII))
    }

    @Test
    fun wrongPassword_isRejected() = runBlocking {
        val plain = tmp.newFile("p.bin").apply { writeBytes(sampleBytes(5000)) }
        val enc = tmp.newFile("e.bin")
        val dec = tmp.newFile("d.bin").apply { writeText("existing download") }
        manager.encryptFile(plain, enc, "right-password")
        try {
            manager.decryptFile(enc, dec, "wrong-password")
            fail("Expected decryption with a wrong password to fail")
        } catch (e: Exception) {
            // expected: GCM authentication failure
        }
        assertEquals("existing download", dec.readText())
    }

    @Test
    fun tamperedHeader_isRejected() = runBlocking {
        val plain = tmp.newFile("p.bin").apply { writeBytes(sampleBytes(5000)) }
        val enc = tmp.newFile("e.bin")
        val dec = tmp.newFile("d.bin")
        manager.encryptFile(plain, enc, "pw")
        val bytes = enc.readBytes()
        // Change the stored iteration count (bytes 9..12); the header is authenticated.
        bytes[12] = (bytes[12].toInt() xor 0x01).toByte()
        enc.writeBytes(bytes)
        try {
            manager.decryptFile(enc, dec, "pw")
            fail("Expected tampered header to be rejected")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun legacyFormat_stillDecrypts() = runBlocking {
        // Re-create a file in the OLD format: salt(16) | iv(12) | AES-GCM ciphertext, PBKDF2 x 10,000.
        val password = "legacy-password"
        val plainBytes = sampleBytes(70_000)
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val key = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(password.toCharArray(), salt, 10_000, 256)).encoded,
            "AES"
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val legacy: File = tmp.newFile("legacy.bin")
        legacy.writeBytes(salt + iv + cipher.doFinal(plainBytes))

        val dec = tmp.newFile("legacy-dec.bin")
        manager.decryptFile(legacy, dec, password)

        assertTrue(plainBytes.contentEquals(dec.readBytes()))
    }
}
