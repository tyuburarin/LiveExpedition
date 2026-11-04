package jp.local.liveexpedition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultTest {
    @Test
    fun encryptedDatabasePersistsAndPaymentsAreIdempotent() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.deleteDatabase("vault.db")
        val db = VaultDatabase.create(ctx)
        val repo = Repository(db)
        try {
            val live =
                Item(
                    kind = "live",
                    data = mapOf("title" to "秘密の公演", "artist" to "テスト", "date" to "2026-11-04"),
                )
            repo.save(live)
            val ticket =
                Item(
                    kind = "ticket",
                    data =
                        mapOf(
                            "title" to "先行",
                            "parent" to live.id,
                            "quantity" to "1",
                            "amount" to "5000",
                            "fee" to "300",
                        ),
                )
            repo.save(ticket)
            repo.pay(ticket.id, LocalDate.of(2026, 11, 4))
            assertTrue(runCatching { repo.save(ticket) }.isFailure)
            assertTrue(runCatching { repo.delete(ticket) }.isFailure)
            assertTrue(runCatching { repo.pay(ticket.id, LocalDate.of(2026, 11, 4)) }.isFailure)
            assertEquals(-5300L, Rules.balance(db.records().all().map(Record::item)))
            val snapshot = repo.snapshot()
            val before = db.records().all()
            assertTrue(runCatching { repo.restore("broken".toByteArray()) }.isFailure)
            assertEquals(before, db.records().all())
            repo.restore(snapshot)
            db.openHelper.writableDatabase.query("PRAGMA cipher_integrity_check").use {
                assertEquals(0, it.count)
            }
        } finally {
            db.close()
        }
        val header = ctx.getDatabasePath("vault.db").inputStream().use { it.readNBytes(16) }
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
        val reopened = VaultDatabase.create(ctx)
        try {
            assertEquals(-5300L, Rules.balance(reopened.records().all().map(Record::item)))
        } finally {
            reopened.close()
            ctx.deleteDatabase("vault.db")
        }
    }
}
