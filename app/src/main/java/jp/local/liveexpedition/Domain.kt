package jp.local.liveexpedition

import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONObject

data class Field(val key: String, val label: String, val required: Boolean = false)

data class Kind(val key: String, val label: String, val fields: List<Field>)

private fun f(key: String, label: String, required: Boolean = false) = Field(key, label, required)

object Catalog {
    val reminderKeys =
        listOf("remindApply", "remindResult", "remindPay", "remindSubscription", "remindTravel")
    val kinds =
        listOf(
            Kind(
                "artist",
                "アーティスト",
                listOf(f("title", "アーティスト名", true), f("url", "公式URL"), f("memo", "メモ")),
            ),
            Kind(
                "tour",
                "ツアー・イベント",
                listOf(
                    f("title", "ツアー・イベント名", true),
                    f("artist", "アーティスト", true),
                    f("artistRef", "アーティストID"),
                    f("url", "公式URL"),
                    f("memo", "メモ"),
                ),
            ),
            Kind(
                "adjustment",
                "残高調整",
                listOf(
                    f("title", "調整理由", true),
                    f("date", "日付", true),
                    f("type", "増額/減額", true),
                    f("amount", "金額 円", true),
                    f("memo", "メモ"),
                ),
            ),
            Kind(
                "live",
                "公演",
                listOf(
                    f("title", "公演名", true),
                    f("artist", "アーティスト", true),
                    f("artistRef", "アーティストID"),
                    f("tour", "ツアー"),
                    f("tourRef", "ツアーID"),
                    f("date", "公演日 YYYY-MM-DD", true),
                    f("venue", "会場"),
                    f("start", "開場 YYYY-MM-DDTHH:mm"),
                    f("end", "開演 YYYY-MM-DDTHH:mm"),
                    f("finish", "終演予定 YYYY-MM-DDTHH:mm"),
                    f("attendance", "参加予定/参加済み/不参加"),
                    f("url", "公式URL https://"),
                    f("memo", "メモ"),
                ),
            ),
            Kind(
                "ticket",
                "チケット",
                listOf(
                    f("title", "受付名・先行種別", true),
                    f("parent", "公演ID", true),
                    f("start", "受付開始 YYYY-MM-DDTHH:mm"),
                    f("end", "受付締切 YYYY-MM-DDTHH:mm"),
                    f("resultAt", "当落発表 YYYY-MM-DDTHH:mm"),
                    f("status", "申込状態"),
                    f("result", "当落結果"),
                    f("quantity", "枚数", true),
                    f("amount", "チケット代合計 円", true),
                    f("fee", "手数料 円"),
                    f("due", "支払期限 YYYY-MM-DDTHH:mm"),
                    f("method", "支払方法"),
                    f("memo", "メモ"),
                ),
            ),
            Kind(
                "candidate",
                "遠征候補",
                listOf(
                    f("title", "候補名", true),
                    f("type", "種類: 電車/バス/飛行機/ホテル/複合/その他", true),
                    f("start", "出発・開始 YYYY-MM-DDTHH:mm", true),
                    f("end", "到着・終了 YYYY-MM-DDTHH:mm", true),
                    f("from", "出発地"),
                    f("to", "到着地・宿泊先"),
                    f("amount", "料金 円"),
                    f("transfers", "乗り換え回数"),
                    f("status", "予約状態"),
                    f("direction", "往路/復路/片道"),
                    f("priceState", "料金: 確定/概算/未確定"),
                    f("due", "支払・予約期限"),
                    f("method", "支払方法"),
                    f("url", "URL https://"),
                    f("memo", "メモ"),
                ),
            ),
            Kind("plan", "遠征プラン", listOf(f("title", "プラン名", true), f("memo", "メモ"))),
            Kind(
                "transaction",
                "取引",
                listOf(
                    f("title", "内容", true),
                    f("date", "日付 YYYY-MM-DD", true),
                    f("type", "種類: 収入/支出/振替/予定", true),
                    f("amount", "金額 円", true),
                    f("category", "カテゴリ"),
                    f("method", "支払方法"),
                    f("memo", "メモ"),
                ),
            ),
            Kind("category", "カテゴリ", listOf(f("title", "カテゴリ名", true))),
            Kind(
                "budget",
                "月別予算",
                listOf(
                    f("title", "予算名", true),
                    f("month", "対象月 YYYY-MM", true),
                    f("category", "カテゴリ（空欄で全体）"),
                    f("amount", "予算 円", true),
                ),
            ),
            Kind(
                "subscription",
                "サブスク",
                listOf(
                    f("title", "サービス名", true),
                    f("cycle", "月額/年額", true),
                    f("amount", "料金 円", true),
                    f("date", "次回請求日 YYYY-MM-DD", true),
                    f("method", "支払方法"),
                    f("renew", "自動更新: はい/いいえ"),
                    f("status", "契約中/解約済", true),
                    f("memo", "メモ"),
                ),
            ),
            Kind(
                "schedule",
                "その他の予定",
                listOf(
                    f("title", "予定名", true),
                    f("start", "開始 YYYY-MM-DDTHH:mm", true),
                    f("end", "終了 YYYY-MM-DDTHH:mm", true),
                    f("memo", "メモ"),
                ),
            ),
        )

    fun kind(key: String) = kinds.first { it.key == key }
}

data class Item(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val data: Map<String, String>,
) {
    operator fun get(key: String) = data[key].orEmpty()

    val title
        get() = get("title")

    val amount
        get() = get("amount").toLongOrNull() ?: 0L
}

object Rules {
    const val MAX_AMOUNT = 999_999_999L

    fun url(value: String): Boolean =
        runCatching {
                val u = URI(value)
                value.length <= 2048 &&
                    u.scheme in listOf("https", "http") &&
                    !u.host.isNullOrBlank() &&
                    u.rawUserInfo == null &&
                    value.none { it.isISOControl() }
            }
            .getOrDefault(false)

    fun validate(item: Item) {
        require(runCatching { UUID.fromString(item.id).toString() == item.id }.getOrDefault(false))
        val schema = Catalog.kind(item.kind)
        require(
            item.data.size <= 40 &&
                item.data.keys.all { k ->
                    k in
                        schema.fields.map { it.key } +
                            listOf(
                                "members",
                                "adopted",
                                "image",
                                "paid",
                                "notify",
                                "payments",
                                "billingDates",
                                "segments",
                                "liveRefs",
                            ) +
                            Catalog.reminderKeys
                }
        )
        schema.fields.forEach {
            require(!it.required || item[it.key].isNotBlank()) { "${it.label}を入力してください" }
        }
        item.data.forEach { (k, v) ->
            require(
                v.length <=
                    if (k == "memo") 8000
                    else if (k in listOf("members", "payments", "billingDates", "segments")) 20000
                    else 2048
            ) {
                "入力が長すぎます"
            }
            if (v.isNotBlank())
                when (k) {
                    "amount",
                    "fee" ->
                        require(v.toLongOrNull() in 0L..MAX_AMOUNT) { "金額を0〜999999999の整数で入力してください" }
                    "quantity" -> require(v.toIntOrNull() in 1..20) { "枚数は1〜20です" }
                    "transfers" -> require(v.toIntOrNull() in 0..99)
                    "date" -> require(LocalDate.parse(v).year in 2000..2100) { "日付は2000〜2100年です" }
                    "month" -> java.time.YearMonth.parse(v).also { require(it.year in 2000..2100) }
                    "start",
                    "end",
                    "finish",
                    "resultAt",
                    "due",
                    "remindApply",
                    "remindResult",
                    "remindPay",
                    "remindSubscription",
                    "remindTravel" ->
                        require(LocalDateTime.parse(v).year in 2000..2100) { "日時は2000〜2100年です" }
                    "url" -> require(url(v)) { "安全なhttp(s) URLを入力してください" }
                    "parent",
                    "artistRef",
                    "tourRef",
                    "image",
                    "paid" ->
                        require(
                            runCatching { UUID.fromString(v).toString() == v }.getOrDefault(false)
                        )
                    "members",
                    "liveRefs",
                    "payments" ->
                        require(
                            v.split(',').all {
                                runCatching { UUID.fromString(it).toString() == it }
                                    .getOrDefault(false)
                            }
                        )
                    "billingDates" ->
                        require(v.split(',').all { LocalDate.parse(it).year in 2000..2100 })
                    "adopted",
                    "notify" -> require(v in listOf("true", "false"))
                }
        }
        if (item["start"].isNotBlank() && item["end"].isNotBlank())
            require(LocalDateTime.parse(item["end"]) >= LocalDateTime.parse(item["start"])) {
                "終了は開始以降にしてください（日付をまたぐ場合は翌日を指定）"
            }
        when (item.kind) {
            "transaction" -> require(item["type"] in listOf("収入", "支出", "振替", "予定"))
            "candidate" -> require(item["type"] in listOf("電車", "バス", "飛行機", "ホテル", "複合", "その他"))
            "subscription" -> {
                require(item["cycle"] in listOf("月額", "年額"))
                require(item["status"] in listOf("契約中", "解約済"))
                require(item["renew"] in listOf("", "はい", "いいえ"))
            }
        }
        if (item["image"].isNotEmpty())
            require(
                item.kind in listOf("artist", "tour", "live", "ticket", "candidate", "schedule")
            )
        if (item.kind == "adjustment") require(item["type"] in listOf("増額", "減額"))
        if (item["segments"].isNotEmpty()) {
            require(item.kind == "candidate")
            Itinerary.decode(item["segments"])
            require(Itinerary.normalize(item) == item) { "複数区間の集計値が一致しません" }
        }
        if (item["finish"].isNotEmpty() && item["end"].isNotEmpty())
            require(LocalDateTime.parse(item["finish"]) >= LocalDateTime.parse(item["end"])) {
                "終演予定は開演以降を指定してください"
            }
        if (item["paid"].isNotEmpty() || item["payments"].isNotEmpty())
            require(item.kind in listOf("ticket", "candidate", "subscription"))
        listOf("members", "payments", "billingDates").forEach { key ->
            val parts = item[key].split(',').filter(String::isNotBlank)
            require(parts.distinct().size == parts.size)
        }
    }

    fun balance(items: List<Item>): Long =
        items
            .filter { it.kind in listOf("transaction", "adjustment") }
            .sumOf {
                when (it["type"]) {
                    "増額" -> it.amount
                    "減額" -> -it.amount
                    "収入" -> it.amount
                    "支出" -> -it.amount
                    else -> 0L
                }
            }

    fun active(items: List<Item>, now: LocalDateTime) =
        items
            .filter {
                it["start"].isNotEmpty() &&
                    it["end"].isNotEmpty() &&
                    now >= LocalDateTime.parse(it["start"]) &&
                    now <= LocalDateTime.parse(it["end"])
            }
            .sortedBy { it["end"] }

    fun encode(data: Map<String, String>) = JSONObject(data).toString()

    fun decode(data: String): Map<String, String> {
        val j = JSONObject(data)
        return j.keys().asSequence().associateWith {
            require(j.get(it) is String)
            j.getString(it)
        }
    }
}
