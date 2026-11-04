package jp.local.liveexpedition

import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class FinanceTest {
    @Test
    fun monthlyNetAndCurrentBalanceHaveDifferentScopes() {
        fun t(date: String, type: String, amount: String) =
            Item(
                kind = "transaction",
                data = mapOf("date" to date, "type" to type, "amount" to amount),
            )
        val all =
            listOf(
                t("2026-10-01", "収入", "10000"),
                t("2026-11-01", "収入", "5000"),
                t("2026-11-02", "支出", "2000"),
                t("2026-11-02", "振替", "3000"),
                t("2026-11-02", "予定", "4000"),
            )
        val s = Finance.month(all, YearMonth.of(2026, 11))
        assertEquals(3000L, s.net)
        assertEquals(13000L, s.balance)
    }

    @Test
    fun annualSubscriptionConversionDoesNotCreateTransactions() {
        val s =
            Item(
                kind = "subscription",
                data = mapOf("cycle" to "年額", "amount" to "12000", "status" to "契約中"),
            )
        assertEquals(1000L, Finance.monthlySubscriptions(listOf(s)))
        assertEquals(12000L, Finance.annualSubscriptions(listOf(s)))
        assertEquals(0L, Rules.balance(listOf(s)))
        assertEquals(0L, Finance.month(listOf(s), YearMonth.of(2026, 11)).expense)
    }
}
