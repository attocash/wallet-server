package cash.atto

import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.toHex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ChaCha20 {
    private const val NONCE_BYTE_SIZE = 12
    private const val SECRET_KEY_BIT_SIZE = 256

    private val secureRandom = SecureRandom.getInstanceStrong()

    fun generateKey(): String {
        val key = ByteArray(SECRET_KEY_BIT_SIZE / 8)
        secureRandom.nextBytes(key)
        return key.toHex()
    }

    fun encrypt(
        toEncrypt: ByteArray,
        hexKey: String,
    ): ByteArray {
        val nonce = ByteArray(NONCE_BYTE_SIZE)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, hexKey.fromHexToSecretKey(), IvParameterSpec(nonce))

        val cipherText = cipher.doFinal(toEncrypt)

        return nonce + cipherText
    }

    fun decrypt(
        toDecrypt: ByteArray,
        hexKey: String,
    ): ByteArray {
        val nonce = toDecrypt.copyOfRange(0, NONCE_BYTE_SIZE)
        val cipherText = toDecrypt.copyOfRange(NONCE_BYTE_SIZE, toDecrypt.size)

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, hexKey.fromHexToSecretKey(), IvParameterSpec(nonce))

        return cipher.doFinal(cipherText)
    }

    private fun String.fromHexToSecretKey(): SecretKeySpec = SecretKeySpec(this.fromHexToByteArray(), "ChaCha20")
}
