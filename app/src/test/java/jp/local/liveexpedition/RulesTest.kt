package jp.local.liveexpedition

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private fun rejected(action: () -> Unit) {
        assertTrue(runCatching(action).isFailure)
    }

    @Test
    fun dangerousUrlsRejected() {
        listOf(
                "javascript:alert(1)",
                "file:///data/data/a",
                "content://secret",
                "intent://a",
                "https://user:pass@a.com",
                "https://a.com\n",
            )
            .forEach { assertFalse(Rules.url(it)) }
        assertTrue(Rules.url("https://example.org/live?q=1"))
    }

    @Test
    fun amountsDatesAndFieldsValidated() {
        val base =
            Item(
                kind = "transaction",
                data =
                    mapOf(
                        "title" to "テスト",
                        "date" to "2026-11-04",
                        "type" to "支出",
                        "amount" to "1000",
                    ),
            )
        Rules.validate(base)
        listOf("-1", "1.5", "1000000000", "99999999999999999999999").forEach { v ->
            rejected { Rules.validate(base.copy(data = base.data + ("amount" to v))) }
        }
        rejected { Rules.validate(base.copy(data = base.data + ("date" to "2026-02-30"))) }
        rejected { Rules.validate(base.copy(data = base.data + ("path" to "../../secret"))) }
        rejected { Rules.validate(base.copy(id = "../../secret")) }
    }

    @Test
    fun transfersAndPlannedCostsExcluded() {
        fun tx(type: String, amount: String) =
            Item(kind = "transaction", data = mapOf("type" to type, "amount" to amount))
        assertEquals(
            800L,
            Rules.balance(
                listOf(
                    tx("収入", "1000"),
                    tx("支出", "200"),
                    tx("予定", "500"),
                    tx("振替", "300"),
                    Item(kind = "ticket", data = mapOf("amount" to "10000", "result" to "当選")),
                )
            ),
        )
    }

    @Test
    fun overnightAndOverlapSupported() {
        val a =
            Item(
                kind = "schedule",
                data =
                    mapOf(
                        "title" to "夜行バス",
                        "start" to "2026-11-04T23:00",
                        "end" to "2026-11-05T06:00",
                    ),
            )
        Rules.validate(a)
        assertEquals(
            2,
            Rules.active(
                    listOf(a, a.copy(id = java.util.UUID.randomUUID().toString())),
                    LocalDateTime.parse("2026-11-05T01:00"),
                )
                .size,
        )
        rejected { Rules.validate(a.copy(data = a.data + ("end" to "2026-11-04T06:00"))) }
    }

    @Test
    fun jsonRoundTripKeepsUntrustedTextAsData() {
        val data = mapOf("title" to "'; DROP TABLE records;--", "memo" to "$(whoami)\n日本語")
        assertEquals(data, Rules.decode(Rules.encode(data)))
    }
}
