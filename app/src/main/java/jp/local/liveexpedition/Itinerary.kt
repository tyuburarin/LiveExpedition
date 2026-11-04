package jp.local.liveexpedition

import java.time.*
import org.json.JSONArray
import org.json.JSONObject

data class Leg(
    val mode: String = "電車",
    val from: String = "",
    val to: String = "",
    val start: String = "",
    val end: String = "",
    val fare: String = "",
    val stops: String = "",
)

data class ScheduleEvent(
    val id: String,
    val source: Item,
    val title: String,
    val type: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val from: String = "",
    val to: String = "",
    val leg: Int? = null,
)

data class PlanTotals(
    val traffic: Long,
    val hotel: Long,
    val other: Long,
    val missingPrices: Int,
    val tentativePrices: Int,
    val missingFields: Int,
    val start: String?,
    val end: String?,
    val travelMinutes: Long,
    val transfers: Int,
    val warnings: List<String>,
) {
    val total
        get() = traffic + hotel + other
}

object Itinerary {
    fun knownCost(item: Item): Long =
        decode(item["segments"]).takeIf { it.isNotEmpty() }?.sumOf { it.fare.toLongOrNull() ?: 0L }
            ?: item.amount

    val transport = listOf("電車", "バス", "飛行機")

    fun encode(legs: List<Leg>): String {
        require(legs.size <= 20)
        return JSONArray()
            .apply {
                legs.forEach {
                    put(
                        JSONObject(
                            mapOf(
                                "mode" to it.mode,
                                "from" to it.from,
                                "to" to it.to,
                                "start" to it.start,
                                "end" to it.end,
                                "fare" to it.fare,
                                "stops" to it.stops,
                            )
                        )
                    )
                }
            }
            .toString()
    }

    fun decode(raw: String): List<Leg> {
        if (raw.isBlank()) return emptyList()
        require(raw.length <= 20000)
        JsonSafety.checkDepth(raw)
        val a = JSONArray(raw)
        require(a.length() in 1..20)
        return (0 until a.length())
            .map {
                val j = a.getJSONObject(it)
                require(
                    j.length() == 7 &&
                        j.keys().asSequence().all {
                            it in listOf("mode", "from", "to", "start", "end", "fare", "stops")
                        }
                )
                Leg(
                    j.getString("mode"),
                    j.getString("from"),
                    j.getString("to"),
                    j.getString("start"),
                    j.getString("end"),
                    j.getString("fare"),
                    j.getString("stops"),
                )
            }
            .also { list -> list.forEach(::validate) }
    }

    fun validate(leg: Leg) {
        require(leg.mode in transport)
        require(
            leg.from.isNotBlank() &&
                leg.to.isNotBlank() &&
                leg.from.length <= 256 &&
                leg.to.length <= 256 &&
                leg.stops.length <= 1000
        ) {
            "区間の出発地・到着地を入力してください"
        }
        val s = LocalDateTime.parse(leg.start)
        val e = LocalDateTime.parse(leg.end)
        require(s.year in 2000..2100 && e.year in 2000..2100 && e >= s) { "区間の日時を確認してください" }
        require(leg.fare.isEmpty() || leg.fare.toLongOrNull() in 0L..Rules.MAX_AMOUNT) {
            "区間料金は円の整数で入力してください"
        }
    }

    fun normalize(item: Item): Item {
        if (item.kind != "candidate" || item["segments"].isEmpty()) return item
        val legs = decode(item["segments"])
        return item.copy(
            data =
                item.data +
                    mapOf(
                        "type" to "複合",
                        "from" to legs.first().from,
                        "to" to legs.last().to,
                        "start" to legs.first().start,
                        "end" to legs.last().end,
                        "amount" to
                            if (legs.all { it.fare.isNotEmpty() })
                                legs.sumOf { it.fare.toLong() }.toString()
                            else "",
                        "transfers" to (legs.size - 1).toString(),
                    )
        )
    }

    fun connectionWarnings(legs: List<Leg>): List<String> =
        legs.zipWithNext().flatMap { (a, b) ->
            val gap =
                Duration.between(LocalDateTime.parse(a.end), LocalDateTime.parse(b.start))
                    .toMinutes()
            val guide =
                if (a.mode == "飛行機" || b.mode == "飛行機") 90
                else if (a.mode == "バス" || b.mode == "バス") 20 else 10
            buildList {
                if (gap < 0) add("${a.to}：次の出発が到着より${-gap}分早く、時刻が重複しています")
                else if (gap < guide) add("${a.to}：乗り換え${gap}分。${guide}分未満のため要確認（交通手段による目安・接続保証なし）")
                if (a.to.trim() != b.from.trim())
                    add("${a.to} → ${b.from}：乗り換え地点が異なります。地点間の移動時間を確認してください")
            }
        }

    fun events(all: List<Item>): List<ScheduleEvent> {
        val adopted =
            all.filter { it.kind == "plan" && it["adopted"] == "true" }
                .flatMap { it["members"].split(',') }
                .toSet()
        return all.flatMap { i ->
                when {
                    i.kind == "candidate" && i.id in adopted -> {
                        val legs = decode(i["segments"])
                        if (legs.isEmpty())
                            listOf(
                                ScheduleEvent(
                                    i.id,
                                    i,
                                    i.title,
                                    i["type"],
                                    LocalDateTime.parse(i["start"]),
                                    LocalDateTime.parse(i["end"]),
                                    i["from"],
                                    i["to"],
                                )
                            )
                        else
                            legs.mapIndexed { n, l ->
                                ScheduleEvent(
                                    "${i.id}:$n",
                                    i,
                                    "${l.mode}｜${l.from} → ${l.to}",
                                    l.mode,
                                    LocalDateTime.parse(l.start),
                                    LocalDateTime.parse(l.end),
                                    l.from,
                                    l.to,
                                    n,
                                )
                            }
                    }
                    i.kind == "live" ->
                        buildList {
                            val doors =
                                i["start"].takeIf(String::isNotEmpty)?.let(LocalDateTime::parse)
                            val show =
                                i["end"].takeIf(String::isNotEmpty)?.let(LocalDateTime::parse)
                            val finish =
                                i["finish"].takeIf(String::isNotEmpty)?.let(LocalDateTime::parse)
                            if (doors != null)
                                add(
                                    ScheduleEvent(
                                        i.id + ":doors",
                                        i,
                                        "開場｜${i.title}",
                                        "ライブ",
                                        doors,
                                        show,
                                        to = i["venue"],
                                    )
                                )
                            if (show != null)
                                add(
                                    ScheduleEvent(
                                        i.id,
                                        i,
                                        i.title,
                                        "ライブ",
                                        show,
                                        finish,
                                        to = i["venue"],
                                    )
                                )
                        }
                    i.kind == "schedule" ->
                        listOf(
                            ScheduleEvent(
                                i.id,
                                i,
                                i.title,
                                "予定",
                                LocalDateTime.parse(i["start"]),
                                LocalDateTime.parse(i["end"]),
                            )
                        )
                    else -> emptyList()
                }
            }
            .sortedBy { it.start }
    }

    fun priority(
        events: List<ScheduleEvent>,
        now: LocalDateTime,
        pin: String,
        selected: String = "",
    ): ScheduleEvent? {
        events
            .firstOrNull { it.id == pin }
            ?.let {
                return it
            }
        val active = events.filter { it.start <= now && it.end != null && now < it.end }
        return active.firstOrNull { it.id == selected }
            ?: active.sortedBy { if (it.type == "ホテル") 1 else 0 }.firstOrNull()
            ?: events.firstOrNull { it.start > now }
            ?: events.lastOrNull { it.end == null }
    }

    fun totals(plan: Item, all: List<Item>): PlanTotals {
        val members = all.filter { it.id in plan["members"].split(',') }
        val transportItems = members.filter { it["type"] !in listOf("ホテル", "その他") }
        val legs =
            transportItems
                .flatMap { i ->
                    decode(i["segments"]).ifEmpty {
                        listOf(
                            Leg(
                                if (i["type"] in transport) i["type"] else "電車",
                                i["from"],
                                i["to"],
                                i["start"],
                                i["end"],
                                i["amount"],
                            )
                        )
                    }
                }
                .sortedBy { it.start }
        val warnings = connectionWarnings(legs).toMutableList()
        if (legs.isEmpty()) warnings += "交通区間が未登録です"
        else if (legs.size == 1 || legs.first().from.trim() != legs.last().to.trim())
            warnings += "復路または帰着区間を確認してください（自動判定のため片道旅行も含みます）"
        return PlanTotals(
            transportItems.sumOf { knownCost(it) },
            members.filter { it["type"] == "ホテル" }.sumOf { it.amount },
            members.filter { it["type"] == "その他" }.sumOf { it.amount },
            members.sumOf {
                val l = decode(it["segments"])
                if (l.isEmpty()) if (it["amount"].isEmpty()) 1 else 0
                else l.count { it.fare.isEmpty() }
            },
            members.count { it["priceState"] != "確定" },
            members.sumOf { listOf("from", "to", "status").count { f -> it[f].isEmpty() } },
            legs.firstOrNull()?.start,
            legs.lastOrNull()?.end,
            legs.sumOf {
                Duration.between(LocalDateTime.parse(it.start), LocalDateTime.parse(it.end))
                    .toMinutes()
            },
            transportItems.sumOf { it["transfers"].toIntOrNull() ?: 0 } +
                (transportItems.size - 1).coerceAtLeast(0),
            warnings,
        )
    }
}
