package jp.local.liveexpedition

import java.time.*
import kotlin.math.min

data class AccountBalance(val account: Item, val current: Long, val outstanding: Long) {
    val projected
        get() = current - outstanding
}

data class LedgerTotals(val accounts: List<AccountBalance>) {
    val current
        get() = accounts.sumOf { it.current }

    val outstanding
        get() = accounts.sumOf { it.outstanding }

    val projected
        get() = current - outstanding
}

object Ledger {
    const val DEFAULT_ACCOUNT = "00000000-0000-4000-8000-000000000001"

    fun defaultAccount() = Item(DEFAULT_ACCOUNT, "account", mapOf("title" to "メイン口座"))

    fun accounts(all: List<Item>) =
        all.filter { it.kind == "account" }
            .ifEmpty { listOf(defaultAccount()) }
            .sortedBy { if (it.id == DEFAULT_ACCOUNT) "" else it.id }

    fun accountId(item: Item) = item["accountId"].ifEmpty { DEFAULT_ACCOUNT }

    fun seq(item: Item) = item["seq"].toLongOrNull() ?: 0L

    fun nextSeq(all: List<Item>) = (all.maxOfOrNull(::seq) ?: 0) + 1

    fun isCardUse(item: Item) =
        item.kind == "transaction" && item["type"] == "支出" && item["cardId"].isNotEmpty()

    fun settledIds(all: List<Item>) =
        all.filter { it.kind == "card_debit" }
            .flatMap { it["usageIds"].split(',').filter(String::isNotBlank) }
            .toSet()

    fun outstanding(all: List<Item>, card: String? = null): List<Item> {
        val settled = settledIds(all)
        return all.filter {
            isCardUse(it) && it.id !in settled && (card == null || it["cardId"] == card)
        }
    }

    fun totals(all: List<Item>): LedgerTotals {
        val pending = outstanding(all)
        return LedgerTotals(
            accounts(all).map { account ->
                val setting =
                    all.filter { it.kind == "balance_setting" && accountId(it) == account.id }
                        .maxByOrNull(::seq)
                val cut = setting?.get("date").orEmpty()
                val changes =
                    all.filter {
                            accountId(it) == account.id &&
                                (setting == null || (seq(it) > seq(setting) && it["date"] >= cut))
                        }
                        .sumOf { item ->
                            when (item.kind) {
                                "transaction" ->
                                    when {
                                        isCardUse(item) -> 0L
                                        item["type"] == "収入" -> item.amount
                                        item["type"] == "支出" -> -item.amount
                                        else -> 0L
                                    }
                                "adjustment" ->
                                    if (item["type"] == "減額") -item.amount else item.amount
                                "card_debit" -> -item.amount
                                else -> 0L
                            }
                        }
                val due =
                    pending
                        .filter { use ->
                            all.firstOrNull { it.id == use["cardId"] && it.kind == "credit_card" }
                                ?.get("accountId") == account.id
                        }
                        .sumOf { it.amount }
                AccountBalance(account, (setting?.amount ?: 0L) + changes, due)
            }
        )
    }

    fun upgrade(old: List<Item>): List<Item> {
        val result = old.toMutableList()
        if (result.none { it.kind == "account" }) result.add(defaultAccount())
        require(result.count { it.kind == "account" } <= 2) { "口座は最大2つです" }
        var next = nextSeq(result)
        val order =
            old.filter {
                    it.kind in
                        listOf("transaction", "adjustment", "balance_setting", "card_debit") &&
                        it["seq"].isEmpty()
                }
                .sortedWith(compareBy<Item> { it["date"] }.thenBy { it.id })
        val assigned = order.associate { it.id to next++ }
        return result.map { item ->
            if (item.kind in listOf("transaction", "adjustment", "balance_setting", "card_debit"))
                item.copy(
                    data =
                        item.data +
                            buildMap {
                                if (item["accountId"].isEmpty()) put("accountId", DEFAULT_ACCOUNT)
                                assigned[item.id]?.let { put("seq", it.toString()) }
                            }
                )
            else item
        }
    }

    fun prepare(
        item: Item,
        old: Item?,
        all: List<Item>,
        now: LocalDateTime = LocalDateTime.now(),
    ): Item {
        if (item.kind !in listOf("transaction", "adjustment", "balance_setting", "card_debit"))
            return item
        val account =
            if (isCardUse(item))
                all.firstOrNull { it.kind == "credit_card" && it.id == item["cardId"] }
                    ?.get("accountId") ?: accountId(item)
            else accountId(item)
        require(accounts(all).any { it.id == account }) { "口座を選び直してください" }
        if (item.kind == "transaction" && item["type"] == "支出" && item["method"] == "クレジットカード")
            require(item["cardId"].isNotEmpty() || old != null) { "クレジットカードを選択してください" }
        if (item["cardId"].isNotEmpty())
            require(all.any { it.kind == "credit_card" && it.id == item["cardId"] }) {
                "カードを選び直してください"
            }
        if (old == null && item.kind == "transaction") {
            require(item["type"] != "振替") { "新しい振替は登録できません" }
            require(item["type"] == "予定" || LocalDate.parse(item["date"]) <= now.toLocalDate()) {
                "将来の支出は「予定」で登録してください"
            }
        }
        return item.copy(
            data =
                item.data +
                    mapOf(
                        "accountId" to account,
                        "seq" to
                            (old?.get("seq")?.takeIf(String::isNotEmpty)
                                ?: nextSeq(all).toString()),
                        "recordedAt" to
                            (old?.get("recordedAt")?.takeIf(String::isNotEmpty) ?: now.toString()),
                    )
        )
    }

    fun validateReferences(all: List<Item>) {
        val accounts = accounts(all)
        require(accounts.size <= 2)
        val sequences =
            all.filter {
                    it.kind in listOf("transaction", "adjustment", "balance_setting", "card_debit")
                }
                .map(::seq)
        require(sequences.all { it > 0 } && sequences.distinct().size == sequences.size) {
            "会計記録の保存順が不正です"
        }
        val settled = mutableSetOf<String>()
        all.forEach { item ->
            if (
                item.kind in
                    listOf(
                        "transaction",
                        "adjustment",
                        "balance_setting",
                        "credit_card",
                        "card_debit",
                    )
            )
                require(accounts.any { it.id == accountId(item) }) { "参照先の口座がありません" }
            if (item["cardId"].isNotEmpty())
                require(all.any { it.kind == "credit_card" && it.id == item["cardId"] })
            if (item.kind == "transaction" && item["cardId"].isNotEmpty())
                require(item["type"] in listOf("支出", "予定") && item["method"] == "クレジットカード")
            if (item.kind == "card_debit") {
                val ids = item["usageIds"].split(',').filter(String::isNotBlank)
                require(ids.isNotEmpty() && ids.distinct().size == ids.size)
                val uses =
                    ids.map { id ->
                        requireNotNull(
                            all.firstOrNull {
                                it.id == id && isCardUse(it) && it["cardId"] == item["cardId"]
                            }
                        )
                    }
                require(uses.sumOf { it.amount } == item.amount)
                require(ids.all { settled.add(it) }) { "同じカード利用の引落しが重複しています" }
            }
        }
    }
}

object CardDates {
    fun clamped(month: YearMonth, day: Int) = month.atDay(min(day, month.lengthOfMonth()))

    fun due(card: Item, useDate: LocalDate, override: String = ""): LocalDate {
        if (override.isNotBlank()) return LocalDate.parse(override)
        val closing = card["closingDay"].toInt()
        var month = YearMonth.from(useDate)
        if (useDate > clamped(month, closing)) month = month.plusMonths(1)
        month = month.plusMonths(card["debitOffset"].toLongOrNull() ?: 1L)
        var date = clamped(month, card["debitDay"].toInt())
        if (card["debitRule"] == "土日は翌平日")
            while (date.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) date =
                date.plusDays(1)
        return date
    }
}
