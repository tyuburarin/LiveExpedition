package jp.local.liveexpedition

import java.time.*

data class Deadline(
    val id: String,
    val source: Item,
    val label: String,
    val at: LocalDateTime,
    val complete: Boolean = false,
)

object Connections {
    fun deadlines(all: List<Item>): List<Deadline> =
        all.flatMap { item ->
                buildList {
                    val keys =
                        when (item.kind) {
                            "ticket" ->
                                listOf("end" to "申込締切", "resultAt" to "当落発表", "due" to "支払期限")
                            "candidate" -> listOf("due" to "予約・支払期限")
                            else -> emptyList()
                        }
                    keys.forEach { (key, label) ->
                        if (item[key].isNotEmpty())
                            add(
                                Deadline(
                                    "${item.id}:$key",
                                    item,
                                    label,
                                    LocalDateTime.parse(item[key]),
                                    when {
                                        item["status"] == "辞退" -> true
                                        key == "resultAt" -> item["result"] in listOf("当選", "落選")
                                        key == "due" ->
                                            item["paid"].isNotEmpty() || item["result"] == "落選"
                                        else ->
                                            key == "end" &&
                                                item["status"].contains("申込") &&
                                                item["status"] != "未申込"
                                    },
                                )
                            )
                    }
                    if (item.kind == "subscription" && item["status"] == "契約中")
                        add(
                            Deadline(
                                "${item.id}:bill",
                                item,
                                "更新・請求日",
                                LocalDate.parse(item["date"]).atStartOfDay(),
                                item["paid"].isNotEmpty(),
                            )
                        )
                }
            }
            .sortedBy { it.at }

    fun events(all: List<Item>): List<ScheduleEvent> {
        val base = Itinerary.events(all)
        val limits =
            deadlines(all)
                .filterNot { it.complete }
                .map {
                    ScheduleEvent(
                        it.id,
                        it.source,
                        "${it.label}｜${it.source.title}",
                        "期限",
                        it.at,
                        null,
                    )
                }
        val untimed =
            all.filter { it.kind == "live" && it["start"].isEmpty() && it["end"].isEmpty() }
                .map {
                    ScheduleEvent(
                        it.id,
                        it,
                        it.title,
                        "公演（日付のみ）",
                        LocalDate.parse(it["date"]).atStartOfDay(),
                        null,
                        to = it["venue"],
                    )
                }
        return (base + limits + untimed).distinctBy { it.id }.sortedBy { it.start }
    }

    fun linkedShows(plan: Item, all: List<Item>) =
        all.filter { it.kind == "live" && it.id in plan["liveRefs"].split(',') }

    fun artistNames(all: List<Item>) =
        (all.filter { it.kind == "artist" }.map { it.title } +
                all.filter { it.kind in listOf("live", "tour") }.map { it["artist"] })
            .filter(String::isNotBlank)
            .distinct()
            .sorted()

    fun tours(artist: String, all: List<Item>) =
        (all.filter { it.kind == "tour" && it["artist"] == artist }.map { it.title } +
                all.filter { it.kind == "live" && it["artist"] == artist }
                    .map { it["tour"].ifEmpty { "単発公演" } })
            .distinct()
            .sorted()
}
