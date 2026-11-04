package jp.local.liveexpedition

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RevisionPolicyTest {
    @Test
    fun resolvedResultsAndLosingTicketsDoNotRemainPendingDeadlines() {
        val ticket =
            Item(
                kind = "ticket",
                data =
                    mapOf(
                        "result" to "落選",
                        "resultAt" to "2026-11-01T12:00",
                        "due" to "2026-11-03T23:59",
                    ),
            )
        assertTrue(Connections.deadlines(listOf(ticket)).all { it.complete })
        assertTrue(Connections.events(listOf(ticket)).isEmpty())
    }

    @Test
    fun adjustmentChangesBalanceWithoutChangingMonthlyIncomeOrExpense() {
        val income =
            Item(
                kind = "transaction",
                data = mapOf("date" to "2026-11-01", "type" to "収入", "amount" to "5000"),
            )
        val seed =
            Item(
                kind = "adjustment",
                data = mapOf("date" to "2026-11-02", "type" to "増額", "amount" to "10000"),
            )
        val correction =
            Item(
                kind = "adjustment",
                data = mapOf("date" to "2026-11-03", "type" to "減額", "amount" to "300"),
            )
        val summary = Finance.month(listOf(income, seed, correction), YearMonth.of(2026, 11))
        assertEquals(14700L, summary.balance)
        assertEquals(5000L, summary.income)
        assertEquals(0L, summary.expense)
        assertEquals("5000", income["amount"])
    }

    @Test
    fun stableMixedTileTargetsAndInvalidDrops() {
        val layout =
            listOf(
                Tile("money", 2, 1),
                Tile("live", 1, 2),
                Tile("plan"),
                Tile("recent"),
                Tile("subscription", 2, 2),
            )
        val moved = Tiles.dropOrder(layout, "live", 1.5f, 1.5f)
        assertEquals(
            listOf("money", "plan", "live", "recent", "subscription"),
            moved.map { it.key },
        )
        assertEquals(moved, Tiles.dropOrder(layout, "live", 1.5f, 1.5f))
        assertEquals(layout, Tiles.dropOrder(layout, "live", -0.1f, 2f))
        assertEquals(layout, Tiles.dropOrder(layout, "live", Float.NaN, 2f))
        assertEquals(moved, Tiles.decode(Tiles.encode(moved), null, null))
    }

    @Test
    fun navReleaseMustBeInsideAndSwipeMustRespectChild() {
        assertEquals(3, UiPolicy.navTarget(399f, 30f, 400f, 70f))
        assertNull(UiPolicy.navTarget(400f, 30f, 400f, 70f))
        assertNull(UiPolicy.navTarget(120f, -1f, 400f, 70f))
        assertEquals(2, UiPolicy.swipeTarget(1, -120f, 10f, 400f, false))
        assertEquals(1, UiPolicy.swipeTarget(1, -120f, 10f, 400f, true))
        assertEquals(1, UiPolicy.swipeTarget(1, -50f, 120f, 400f, false))
        assertEquals(3, UiPolicy.swipeTarget(3, -200f, 0f, 400f, false))
        assertEquals(0, UiPolicy.swipeTarget(0, 200f, 0f, 400f, false))
    }

    @Test
    fun legacyArtistHierarchyIsReadWithoutRewritingOrDuplicatingRecords() {
        val show =
            Item(
                kind = "live",
                data = mapOf("title" to "公演", "artist" to "SORA", "date" to "2026-11-04"),
            )
        val all = listOf(show)
        assertEquals(listOf("SORA"), Connections.artistNames(all))
        assertEquals(listOf("単発公演"), Connections.tours("SORA", all))
        assertEquals(1, all.size)
        assertEquals(show, all.single())
    }

    @Test
    fun scheduleUsesSourceIdsAndOmitsCompletedPaymentDeadlines() {
        val show = Item(kind = "live", data = mapOf("title" to "公演", "date" to "2026-11-04"))
        val ticket =
            Item(
                kind = "ticket",
                data =
                    mapOf(
                        "title" to "先行",
                        "parent" to show.id,
                        "due" to "2026-11-03T23:59",
                        "paid" to "paid-id",
                    ),
            )
        val events = Connections.events(listOf(show, ticket))
        assertEquals(1, events.size)
        assertEquals(show.id, events.single().source.id)
        assertTrue(Connections.deadlines(listOf(ticket)).single().complete)
    }

    @Test
    fun ocrExtractsOnlyExplicitConfirmedShapeAndNeverCreatesReferences() {
        val fields =
            OcrFields.extract(
                "受付名：最速先行\n受付締切：2026年11月20日 23:59\n支払期限：11月30日\nartistRef：不正",
                "ticket",
            )
        assertEquals("最速先行", fields["title"])
        assertEquals("2026-11-20T23:59", fields["end"])
        assertFalse(fields.containsKey("due"))
        assertFalse(fields.containsKey("artistRef"))
    }

    @Test
    fun japaneseDatesSemanticColorsAndIndependentPrivacy() {
        assertEquals("2026年11月4日（水）08:20", UiPolicy.date("2026-11-04T08:20"))
        assertEquals(Meaning.NEGATIVE, UiPolicy.meaning("乗換時間超過"))
        assertEquals(Meaning.WARNING, UiPolicy.meaning("未予約 1件"))
        assertEquals(Meaning.NEUTRAL, UiPolicy.meaning("支出"))
        val visibility = MoneyVisibility(true, mapOf("balance" to false))
        assertFalse(visibility.isHidden("balance"))
        assertTrue(visibility.isHidden("expense"))
    }
}
