package jp.local.liveexpedition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val rng = SecureRandom()

    fun random(n: Int) = ByteArray(n).also(rng::nextBytes)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("vault-wrap-v1", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "vault-wrap-v1",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()
    }

    private fun encrypt(key: SecretKey, plain: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(aad)
        return c.iv + c.doFinal(plain)
    }

    private fun decrypt(key: SecretKey, data: ByteArray, aad: ByteArray): ByteArray {
        require(data.size >= 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        c.updateAAD(aad)
        return c.doFinal(data, 12, data.size - 12)
    }

    fun seal(plain: ByteArray, purpose: String) = encrypt(key(), plain, purpose.toByteArray())

    fun open(data: ByteArray, purpose: String) = decrypt(key(), data, purpose.toByteArray())

    @Synchronized
    fun databaseKey(context: Context): ByteArray {
        val f = File(context.noBackupFilesDir, "vault-key.v1")
        if (f.exists()) return open(f.readBytes(), "database-key-v1")
        require(!context.getDatabasePath("vault.db").exists()) { "既存DBの鍵がありません。自動再作成は行いません。" }
        val bytes = random(32)
        val atomic = android.util.AtomicFile(f)
        val stream = atomic.startWrite()
        try {
            stream.write(seal(bytes, "database-key-v1"))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            bytes.fill(0)
            throw e
        }
        return bytes
    }

    private val magic = "LIVEBAK1".toByteArray()
    const val MAX_BACKUP = 24 * 1024 * 1024

    private fun derive(pass: CharArray, salt: ByteArray): SecretKey {
        require(pass.size in 16..256) { "パスフレーズは16〜256文字です" }
        val spec = PBEKeySpec(pass, salt, 600_000, 256)
        return try {
            val b =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(b, "AES").also { b.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    fun backup(plain: ByteArray, pass: CharArray): ByteArray {
        require(plain.size <= MAX_BACKUP - 128)
        val salt = random(16)
        val header = magic + salt
        return header + encrypt(derive(pass, salt), plain, header)
    }

    fun restore(data: ByteArray, pass: CharArray): ByteArray {
        require(data.size in 52..MAX_BACKUP && data.copyOfRange(0, 8).contentEquals(magic)) {
            "バックアップ形式が不正です"
        }
        val header = data.copyOfRange(0, 24)
        return decrypt(
            derive(pass, data.copyOfRange(8, 24)),
            data.copyOfRange(24, data.size),
            header,
        )
    }
}
