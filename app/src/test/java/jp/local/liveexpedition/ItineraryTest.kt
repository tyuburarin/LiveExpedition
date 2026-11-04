package jp.local.liveexpedition

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ItineraryTest {
    private val legs =
        listOf(
            Leg("電車", "博多", "空港", "2026-11-04T23:45", "2026-11-05T00:05", "500", "中洲川端"),
            Leg("バス", "空港", "会場", "2026-11-05T00:10", "2026-11-05T00:40", "300"),
        )

    @Test
    fun compoundCandidatesPreserveLegsAndCalculateOnlyKnownFares() {
        val c =
            Itinerary.normalize(
                Item(
                    kind = "candidate",
                    data = mapOf("title" to "夜行移動", "segments" to Itinerary.encode(legs)),
                )
            )
        Rules.validate(c)
        assertEquals(800L, c.amount)
        assertEquals("2026-11-05T00:40", c["end"])
        assertEquals(legs, Itinerary.decode(c["segments"]))
        val unknown =
            Itinerary.normalize(
                c.copy(
                    data =
                        c.data + ("segments" to Itinerary.encode(legs.map { it.copy(fare = "") }))
                )
            )
        assertEquals("", unknown["amount"])
        assertTrue(Itinerary.connectionWarnings(legs).any { it.contains("5分") })
    }

    @Test
    fun manualCurrentNextAndOverlappingSchedulesHaveExplicitPriority() {
        val source = Item(kind = "schedule", data = emptyMap())
        val now = LocalDateTime.parse("2026-11-05T00:00")
        val a = ScheduleEvent("a", source, "バス", "バス", now.minusHours(1), now.plusHours(2))
        val b = a.copy(id = "b", title = "宿泊", type = "ホテル")
        val next = a.copy(id = "next", start = now.plusHours(3), end = now.plusHours(4))
        assertEquals("a", Itinerary.priority(listOf(a, b, next), now, "")?.id)
        assertEquals("b", Itinerary.priority(listOf(a, b, next), now, "b")?.id)
        assertEquals("b", Itinerary.priority(listOf(a, b, next), now, "", "b")?.id)
        assertEquals("next", Itinerary.priority(listOf(next), now, "")?.id)
    }

    @Test
    fun planTotalsDoNotConfuseUnknownPriceWithConfirmedZero() {
        val a =
            Itinerary.normalize(
                Item(
                    kind = "candidate",
                    data =
                        mapOf(
                            "title" to "移動",
                            "segments" to Itinerary.encode(legs),
                            "priceState" to "概算",
                        ),
                )
            )
        val hotel =
            Item(
                kind = "candidate",
                data =
                    mapOf(
                        "type" to "ホテル",
                        "amount" to "9000",
                        "start" to "2026-11-05T01:00",
                        "end" to "2026-11-05T10:00",
                    ),
            )
        val p = Item(kind = "plan", data = mapOf("members" to "${a.id},${hotel.id}"))
        val t = Itinerary.totals(p, listOf(a, hotel))
        assertEquals(9800L, t.total)
        assertEquals(50L, t.travelMinutes)
        assertEquals(2, t.tentativePrices)
        assertTrue(t.warnings.any { it.contains("復路") })
    }
}
