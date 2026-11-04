package jp.local.liveexpedition

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LedgerV034Test {
    private val a = Ledger.defaultAccount()
    private val b = Item(kind = "account", data = mapOf("title" to "サブ口座"))

    private fun entry(
        type: String,
        amount: Long,
        seq: Int,
        date: String = "2026-11-04",
        account: String = a.id,
        card: String = "",
    ) =
        Item(
            kind = "transaction",
            data =
                mapOf(
                    "title" to "取引",
                    "type" to type,
                    "amount" to "$amount",
                    "seq" to "$seq",
                    "date" to date,
                    "accountId" to account,
                    "cardId" to card,
                ),
        )

    private fun setting(amount: Long, seq: Int, account: String = a.id) =
        Item(
            kind = "balance_setting",
            data =
                mapOf(
                    "title" to "残高を設定",
                    "amount" to "$amount",
                    "seq" to "$seq",
                    "date" to "2026-11-04",
                    "accountId" to account,
                ),
        )

    private val card =
        Item(
            kind = "credit_card",
            data =
                mapOf(
                    "title" to "カード",
                    "accountId" to a.id,
                    "closingDay" to "15",
                    "debitDay" to "27",
                    "debitOffset" to "1",
                    "debitRule" to "日付どおり",
                ),
        )

    @Test
    fun accountsAreIndependentAndTotalIsSum() {
        val totals =
            Ledger.totals(
                listOf(
                    a,
                    b,
                    setting(100000, 1),
                    setting(50000, 2, b.id),
                    entry("支出", 3000, 3),
                    entry("収入", 1000, 4, account = b.id),
                )
            )
        assertEquals(97000L, totals.accounts.first { it.account.id == a.id }.current)
        assertEquals(51000L, totals.accounts.first { it.account.id == b.id }.current)
        assertEquals(148000L, totals.current)
    }

    @Test
    fun resettingBalancePreservesMonthlyHistoryAndExcludesOlderTransactions() {
        val old = entry("支出", 10000, 1, "2026-11-01")
        val all =
            listOf(
                a,
                old,
                setting(100000, 2),
                entry("支出", 2000, 3),
                entry("収入", 3000, 4),
                entry("支出", 4000, 5, "2026-11-02"),
            )
        assertEquals(101000L, Ledger.totals(all).current)
        assertEquals(16000L, Finance.month(all, YearMonth.of(2026, 11)).expense)
        assertEquals("10000", old["amount"])
    }

    @Test
    fun priorMonthCardUseRemainsDebtUntilExplicitDebitEvenAfterDueDate() {
        val use = entry("支出", 30000, 1, "2026-10-03", card = card.id)
        val all = listOf(a, card, use, setting(100000, 2))
        assertEquals(100000L, Ledger.totals(all).current)
        assertEquals(30000L, Ledger.totals(all).outstanding)
        assertEquals(70000L, Ledger.totals(all).projected)
        val debit =
            Item(
                kind = "card_debit",
                data =
                    mapOf(
                        "amount" to "30000",
                        "accountId" to a.id,
                        "cardId" to card.id,
                        "usageIds" to use.id,
                        "seq" to "3",
                        "date" to "2026-11-05",
                    ),
            )
        val after = Ledger.totals(all + debit)
        assertEquals(70000L, after.current)
        assertEquals(0L, after.outstanding)
        assertEquals(70000L, after.projected)
        assertEquals(30000L, Finance.month(all + debit, YearMonth.of(2026, 10)).expense)
        assertEquals(0L, Finance.month(all + debit, YearMonth.of(2026, 11)).expense)
    }

    @Test
    fun forecastsPlansAndLegacyTransfersDoNotChangeCash() {
        val sub =
            Item(
                kind = "subscription",
                data =
                    mapOf(
                        "title" to "サービス",
                        "cycle" to "月額",
                        "amount" to "900",
                        "date" to "2026-11-10",
                        "status" to "契約中",
                    ),
            )
        val all = listOf(a, setting(10000, 1), entry("予定", 1000, 2), entry("振替", 3000, 3), sub)
        assertEquals(10000L, Rules.balance(all))
        assertEquals(900L, SubscriptionBilling.forecast(all, YearMonth.of(2026, 11)))
        assertEquals(0L, Finance.month(all, YearMonth.of(2026, 11)).expense)
    }

    @Test
    fun legacyUpgradeRetainsFieldsImagesIdsAndIsIdempotent() {
        val old =
            entry("支出", 2000, 1).let {
                it.copy(data = it.data - "accountId" - "seq" + ("memo" to "保存するメモ"))
            }
        val live =
            Item(
                kind = "live",
                data =
                    mapOf(
                        "title" to "公演",
                        "artist" to "本人入力",
                        "date" to "2026-11-04",
                        "image" to java.util.UUID.randomUUID().toString(),
                    ),
            )
        val migrated = Ledger.upgrade(listOf(old, live))
        assertEquals(live, migrated.first { it.id == live.id })
        old.data.forEach { (k, v) -> assertEquals(v, migrated.first { it.id == old.id }[k]) }
        assertEquals(-2000L, Ledger.totals(migrated).current)
        assertEquals(migrated, Ledger.upgrade(migrated))
    }

    @Test
    fun cardClosingAndWeekendRulesAreExplicitAndOverrideWins() {
        assertEquals(LocalDate.of(2027, 1, 27), CardDates.due(card, LocalDate.of(2026, 12, 15)))
        assertEquals(LocalDate.of(2027, 2, 27), CardDates.due(card, LocalDate.of(2026, 12, 16)))
        val end = card.copy(data = card.data + mapOf("closingDay" to "31", "debitDay" to "31"))
        assertEquals(LocalDate.of(2027, 2, 28), CardDates.due(end, LocalDate.of(2027, 1, 31)))
        val weekend = end.copy(data = end.data + ("debitRule" to "土日は翌平日"))
        assertEquals(LocalDate.of(2027, 3, 1), CardDates.due(weekend, LocalDate.of(2027, 1, 31)))
        assertEquals(
            LocalDate.of(2027, 3, 3),
            CardDates.due(weekend, LocalDate.of(2027, 1, 31), "2027-03-03"),
        )
    }

    @Test
    fun monthEndAnnualLeapAndCancellationForecasts() {
        val sub =
            Item(
                kind = "subscription",
                data =
                    mapOf(
                        "title" to "月額",
                        "cycle" to "月額",
                        "amount" to "900",
                        "date" to "2026-10-31",
                        "billDay" to "31",
                        "status" to "契約中",
                    ),
            )
        assertEquals(
            LocalDate.of(2026, 11, 30),
            SubscriptionBilling.next(sub, LocalDate.of(2026, 11, 4)),
        )
        assertEquals(
            LocalDate.of(2027, 2, 28),
            SubscriptionBilling.next(sub, LocalDate.of(2027, 2, 1)),
        )
        val annual =
            sub.copy(
                data =
                    sub.data +
                        mapOf(
                            "cycle" to "年額",
                            "date" to "2026-10-26",
                            "lastBill" to "2026-10-26",
                            "billDay" to "26",
                            "billMonth" to "10",
                        )
            )
        assertEquals(
            LocalDate.of(2027, 10, 26),
            SubscriptionBilling.next(annual, LocalDate.of(2026, 11, 4)),
        )
        val leap =
            annual.copy(
                data =
                    annual.data +
                        mapOf("billDay" to "29", "billMonth" to "2", "lastBill" to "2024-02-29")
            )
        assertEquals(
            LocalDate.of(2027, 2, 28),
            SubscriptionBilling.next(leap, LocalDate.of(2026, 11, 4)),
        )
        val cancelled = sub.copy(data = sub.data + ("cancelOn" to "2026-11-15"))
        assertNull(SubscriptionBilling.next(cancelled, LocalDate.of(2026, 11, 4)))
        assertEquals(0L, SubscriptionBilling.forecast(listOf(cancelled), YearMonth.of(2026, 11)))
    }

    @Test
    fun cancellationIsDeferredOnlyForOneDayAndNeverDeletedAutomatically() {
        val sub =
            Item(
                kind = "subscription",
                data =
                    mapOf(
                        "status" to "契約中",
                        "cancelOn" to "2026-11-01",
                        "cancelDeferred" to "2026-11-04",
                    ),
            )
        assertNull(SubscriptionBilling.cancellationDue(listOf(sub), LocalDate.of(2026, 11, 4)))
        assertEquals(
            sub,
            SubscriptionBilling.cancellationDue(listOf(sub), LocalDate.of(2026, 11, 5)),
        )
        assertEquals("契約中", sub["status"])
    }
}
