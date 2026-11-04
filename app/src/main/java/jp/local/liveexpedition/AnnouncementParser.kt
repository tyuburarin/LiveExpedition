package jp.local.liveexpedition

import java.time.*

object AnnouncementParser {
    fun extract(text: String): Map<String, String> {
        require(text.length <= 8000)
        val result = mutableMapOf<String, String>()
        val labels =
            mapOf(
                "title" to "公演名|公演|タイトル",
                "artist" to "アーティスト|出演者|出演",
                "tour" to "ツアー名|ツアー",
                "venue" to "会場|場所",
            )
        labels.forEach { (key, label) ->
            Regex("(?m)^\\s*(?:$label)\\s*[:：]\\s*(.+?)\\s*$")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.takeIf { it.length <= 256 }
                ?.let { result[key] = it }
        }
        val date =
            Regex("(?<!\\d)(20\\d{2})(?:年|[-/])(\\d{1,2})(?:月|[-/])(\\d{1,2})(?:日)?(?!\\d)")
                .find(text)
                ?.let {
                    runCatching {
                            LocalDate.of(
                                it.groupValues[1].toInt(),
                                it.groupValues[2].toInt(),
                                it.groupValues[3].toInt(),
                            )
                        }
                        .getOrNull()
                }
        if (date != null) {
            result["date"] = date.toString()
            listOf("start" to "開場|OPEN", "end" to "開演|START", "finish" to "終演予定|終演").forEach {
                (key, label) ->
                Regex("(?:$label)\\s*[:：]?\\s*([0-2]?\\d)[:：]([0-5]\\d)", RegexOption.IGNORE_CASE)
                    .find(text)
                    ?.let { match ->
                        runCatching {
                                date.atTime(
                                    match.groupValues[1].toInt(),
                                    match.groupValues[2].toInt(),
                                )
                            }
                            .getOrNull()
                            ?.let { result[key] = it.toString() }
                    }
            }
        }
        Regex("https?://[^\\s<>\"）)]+", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.value.trimEnd('。', '、', '.', ',') }
            .firstOrNull(Rules::url)
            ?.let { result["url"] = it }
        return result
    }
}
