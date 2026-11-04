package jp.local.liveexpedition

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test
    fun encryptedRoundTripAndRandomSalt() {
        val bytes = "非常に機密性の高い情報".toByteArray()
        val p = "a-long-unique-passphrase".toCharArray()
        val a = Crypto.backup(bytes, p)
        val b = Crypto.backup(bytes, p)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(bytes, Crypto.restore(a, p))
        assertFalse(a.toString(Charsets.UTF_8).contains("非常に"))
    }

    @Test
    fun wrongPassphraseAndTamperingRejected() {
        val a = Crypto.backup("secret".toByteArray(), "a-long-unique-passphrase".toCharArray())
        assertTrue(
            runCatching { Crypto.restore(a, "wrong-long-passphrase".toCharArray()) }.isFailure
        )
        a[a.lastIndex] = (a.last().toInt() xor 1).toByte()
        assertTrue(
            runCatching { Crypto.restore(a, "a-long-unique-passphrase".toCharArray()) }.isFailure
        )
    }

    @Test
    fun invalidSizeAndShortPasswordRejected() {
        assertTrue(runCatching { Crypto.backup(byteArrayOf(1), "short".toCharArray()) }.isFailure)
        assertTrue(
            runCatching { Crypto.restore(ByteArray(20), "a-long-unique-passphrase".toCharArray()) }
                .isFailure
        )
        assertTrue(
            runCatching {
                    Crypto.restore(
                        ByteArray(Crypto.MAX_BACKUP + 1),
                        "a-long-unique-passphrase".toCharArray(),
                    )
                }
                .isFailure
        )
    }
}
