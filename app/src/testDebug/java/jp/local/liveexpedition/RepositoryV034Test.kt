package jp.local.liveexpedition

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryV034Test {
    private lateinit var db: VaultDatabase
    private lateinit var repo: Repository

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    VaultDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        repo = Repository(db)
    }

    @After
    fun close() {
        db.close()
    }

    private suspend fun rows() = db.records().all().map(Record::item)

    private suspend fun card(): Item {
        val card =
            Item(
                kind = "credit_card",
                data =
                    mapOf(
                        "title" to "MyCard",
                        "accountId" to Ledger.DEFAULT_ACCOUNT,
                        "closingDay" to "15",
                        "debitDay" to "27",
                        "debitOffset" to "1",
                        "debitRule" to "日付どおり",
                    ),
            )
        repo.save(card)
        return rows().first { it.id == card.id }
    }

    @Test
    fun legacyRowsAndImagesSurviveMigrationAndRoundTrip() = runBlocking {
        val image = java.util.UUID.randomUUID().toString()
        val old =
            Item(
                kind = "transaction",
                data =
                    mapOf(
                        "title" to "旧支出",
                        "date" to "2026-10-01",
                        "type" to "支出",
                        "amount" to "1200",
                        "memo" to "以前の入力",
                    ),
            )
        val live =
            Item(
                kind = "live",
                data =
                    mapOf(
                        "title" to "公演",
                        "artist" to "登録した名前",
                        "date" to "2026-11-04",
                        "image" to image,
                    ),
            )
        db.records().put(Record.of(old))
        db.records().put(Record.of(live))
        repo.initializeFinance()
        val migrated = rows()
        old.data.forEach { (key, value) ->
            assertEquals(value, migrated.first { it.id == old.id }[key])
        }
        assertEquals(live, migrated.first { it.id == live.id })
        assertEquals(1, migrated.count { it.kind == "account" })
        assertEquals(-1200L, Rules.balance(migrated))
        repo.initializeFinance()
        assertEquals(migrated, rows())
        val backup = repo.snapshot()
        repo.restore(backup)
        assertEquals(migrated, rows())
        val before = rows()
        assertTrue(runCatching { repo.restore("broken".toByteArray()) }.isFailure)
        assertEquals(before, rows())
    }

    @Test
    fun onlyTwoAccountsAndRevisionGuardAreAtomic() = runBlocking {
        repo.initializeFinance()
        val account = Item(kind = "account", data = mapOf("title" to "サブ口座"))
        repo.save(account)
        assertTrue(
            runCatching { repo.save(Item(kind = "account", data = mapOf("title" to "3つ目"))) }
                .isFailure
        )
        assertEquals(2, rows().count { it.kind == "account" })
        val saved = rows().first { it.id == account.id }
        repo.save(saved.copy(data = saved.data + ("title" to "変更")))
        assertTrue(runCatching { repo.save(saved) }.isFailure)
        assertEquals("変更", rows().first { it.id == account.id }.title)
    }

    @Test
    fun linkedLiveCreditPaymentAndDebitAreNeverDoubleCounted() = runBlocking {
        repo.initializeFinance()
        val card = card()
        repo.save(
            Item(
                kind = "balance_setting",
                data =
                    mapOf(
                        "title" to "残高を設定",
                        "date" to LocalDate.now().toString(),
                        "accountId" to Ledger.DEFAULT_ACCOUNT,
                        "amount" to "100000",
                    ),
            )
        )
        val live =
            Item(
                kind = "live",
                data =
                    mapOf(
                        "title" to "公演",
                        "artist" to "アーティスト",
                        "date" to LocalDate.now().toString(),
                    ),
            )
        repo.save(live)
        val ticket =
            Item(
                kind = "ticket",
                data =
                    mapOf(
                        "title" to "受付",
                        "parent" to live.id,
                        "quantity" to "1",
                        "amount" to "10000",
                        "fee" to "500",
                        "method" to "クレジットカード",
                        "cardId" to card.id,
                    ),
            )
        repo.save(ticket)
        repo.pay(ticket.id, LocalDate.now())
        assertEquals(100000L, Ledger.totals(rows()).current)
        assertEquals(89500L, Ledger.totals(rows()).projected)
        assertTrue(runCatching { repo.pay(ticket.id, LocalDate.now()) }.isFailure)
        val use = Ledger.outstanding(rows()).single()
        val debit =
            Item(
                kind = "card_debit",
                data =
                    mapOf(
                        "title" to "引落し",
                        "date" to LocalDate.now().toString(),
                        "accountId" to Ledger.DEFAULT_ACCOUNT,
                        "cardId" to card.id,
                        "usageIds" to use.id,
                    ),
            )
        repo.confirmDebit(debit)
        assertEquals(89500L, Ledger.totals(rows()).current)
        assertEquals(89500L, Ledger.totals(rows()).projected)
        assertTrue(Ledger.outstanding(rows()).isEmpty())
        val before = rows()
        assertTrue(
            runCatching {
                    repo.confirmDebit(debit.copy(id = java.util.UUID.randomUUID().toString()))
                }
                .isFailure
        )
        assertEquals(before, rows())
        assertEquals(10500L, Finance.month(rows(), java.time.YearMonth.now()).expense)
    }

    @Test
    fun subscriptionDeletionKeepsLegacyPaymentsAndUndoWorks() = runBlocking {
        val tx =
            Item(
                kind = "transaction",
                data =
                    mapOf(
                        "title" to "旧サブスク支払",
                        "date" to "2026-10-26",
                        "type" to "支出",
                        "amount" to "1400",
                    ),
            )
        val sub =
            Item(
                kind = "subscription",
                data =
                    mapOf(
                        "title" to "サービス",
                        "date" to "2026-10-26",
                        "cycle" to "月額",
                        "status" to "契約中",
                        "amount" to "1400",
                        "paid" to tx.id,
                        "payments" to tx.id,
                        "billingDates" to "2026-10-26",
                    ),
            )
        db.records().put(Record.of(tx))
        db.records().put(Record.of(sub))
        repo.initializeFinance()
        assertTrue(runCatching { repo.pay(sub.id, LocalDate.now()) }.isFailure)
        repo.delete(sub)
        assertEquals("true", rows().first { it.id == sub.id }["archived"])
        assertEquals(-1400L, Rules.balance(rows()))
        assertEquals(tx.data, rows().first { it.id == tx.id }.data.filterKeys { it in tx.data })
        assertEquals(0L, SubscriptionBilling.forecast(rows(), java.time.YearMonth.now()))
        repo.undoDelete(sub)
        assertTrue(SubscriptionBilling.active(rows().first { it.id == sub.id }))
        val changed = rows().first { it.id == sub.id }
        repo.save(changed.copy(data = changed.data + ("amount" to "1600")))
        assertEquals("1400", rows().first { it.id == tx.id }["amount"])
        assertEquals(-1400L, Rules.balance(rows()))
    }
}
