package jp.local.liveexpedition

import java.time.*

object OcrFields {
    fun extract(text: String, kind: String): Map<String, String> {
        val basic = AnnouncementParser.extract(text).toMutableMap()
        val labeled =
            mapOf(
                "受付名|先行名" to "title",
                "受付開始" to "start",
                "受付締切|申込締切" to "end",
                "当落発表" to "resultAt",
                "支払期限" to "due",
            )
        if (kind == "ticket")
            labeled.forEach { (label, key) ->
                val raw = Regex("(?:$label)\\s*[:：]?\\s*([^\\n]+)").find(text)?.groupValues?.get(1)
                if (raw != null && key == "title") basic[key] = raw.take(256)
                else if (raw != null) {
                    val match =
                        Regex(
                                "(20\\d{2})[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?\\s*([0-2]?\\d)[:：]([0-5]\\d)"
                            )
                            .find(raw)
                    if (match != null)
                        runCatching {
                                LocalDateTime.of(
                                        match.groupValues[1].toInt(),
                                        match.groupValues[2].toInt(),
                                        match.groupValues[3].toInt(),
                                        match.groupValues[4].toInt(),
                                        match.groupValues[5].toInt(),
                                    )
                                    .toString()
                            }
                            .getOrNull()
                            ?.let { basic[key] = it }
                }
            }
        if (kind == "artist") basic["artist"]?.let { basic["title"] = it }
        if (kind == "tour") basic["tour"]?.let { basic["title"] = it }
        val allowed = Catalog.kind(kind).fields.map { it.key }
        return basic.filterKeys { it in allowed && it !in listOf("artistRef", "tourRef", "parent") }
    }
}
