@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.local.liveexpedition

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.time.*
import kotlinx.coroutines.launch

@Composable
fun NamedReference(label: String, value: String, choices: List<Item>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label：${choices.firstOrNull{it.id==value}?.title?:"選択してください"}", Modifier.weight(1f))
        Icon(Icons.Outlined.ExpandMore, null)
    }
    if (open)
        ChoiceSheet(
            label,
            choices.map { it.id to it.title },
            setOf(value),
            false,
            { keys ->
                keys.firstOrNull()?.let(onChange)
                open = false
            },
            { open = false },
        )
}

@Composable
private fun OptionalDate(label: String, value: String, onChange: (String) -> Unit) {
    FieldInput(label, value, onChange, dateType = "date")
    if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("$label を解除") }
}

@Composable
fun FinanceEditor(
    item: Item,
    all: List<Item>,
    onDraft: (Item) -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onRelated: (Item) -> Unit,
    onSave: suspend (Item) -> Unit,
) {
    val accounts = Ledger.accounts(all)
    val cards = all.filter { it.kind == "credit_card" }
    val values =
        remember(item.id, item["_section"]) {
            mutableStateMapOf<String, String>().apply {
                putAll(item.data)
                if (
                    item.kind in
                        listOf("transaction", "balance_setting", "card_debit", "subscription")
                )
                    putIfAbsent("date", LocalDate.now().toString())
                if (
                    item.kind in
                        listOf("transaction", "balance_setting", "credit_card", "card_debit")
                )
                    putIfAbsent("accountId", accounts.first().id)
                when (item.kind) {
                    "transaction" -> {
                        putIfAbsent("type", "支出")
                        putIfAbsent("method", "現金")
                    }
                    "balance_setting" -> {
                        putIfAbsent("title", "残高を設定")
                    }
                    "credit_card" -> {
                        putIfAbsent("closingDay", "31")
                        putIfAbsent("debitDay", "27")
                        putIfAbsent("debitOffset", "1")
                        putIfAbsent("debitRule", "日付どおり")
                    }
                    "subscription" -> {
                        putIfAbsent("cycle", "月額")
                        putIfAbsent("status", "契約中")
                        putIfAbsent("renew", "はい")
                        putIfAbsent(
                            "registeredOn",
                            if (item["registeredOn"].isNotEmpty()) item["registeredOn"]
                            else if (all.any { it.id == item.id }) ""
                            else LocalDate.now().toString(),
                        )
                        val anchor =
                            runCatching {
                                    LocalDate.parse(
                                        get("lastBill").orEmpty().ifEmpty { get("date").orEmpty() }
                                    )
                                }
                                .getOrDefault(LocalDate.now())
                        putIfAbsent("billDay", anchor.dayOfMonth.toString())
                        putIfAbsent("billMonth", anchor.monthValue.toString())
                    }
                    "card_debit" -> {
                        putIfAbsent("title", "カード引落し")
                        putIfAbsent(
                            "usageIds",
                            Ledger.outstanding(all, get("cardId")).joinToString(",") { it.id },
                        )
                    }
                }
            }
        }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun current() = item.copy(data = values.toMap())
    SideEffect { if (!busy) onDraft(current()) }
    val section = values["_section"].orEmpty()
    val heading =
        when (item.kind) {
            "transaction" -> "${values["type"]}を${if(all.any{it.id==item.id})"編集"else "登録"}"
            else -> Catalog.kind(item.kind).label
        }
    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(heading) },
                        navigationIcon = {
                            IconButton(onClick = onClose, enabled = !busy) {
                                Icon(Icons.Outlined.Close, "閉じる（自動保存）")
                            }
                        },
                        actions = {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            var result =
                                                current()
                                                    .copy(
                                                        data =
                                                            values.filterKeys {
                                                                !it.startsWith("_")
                                                            }
                                                    )
                                            if (item.kind == "subscription")
                                                result = SubscriptionBilling.normalize(result)
                                            if (item.kind == "card_debit") {
                                                val ids = result["usageIds"].split(',')
                                                val amount =
                                                    Ledger.outstanding(all, result["cardId"])
                                                        .filter { it.id in ids }
                                                        .sumOf { it.amount }
                                                result =
                                                    result.copy(
                                                        data =
                                                            result.data +
                                                                ("amount" to amount.toString())
                                                    )
                                            }
                                            Rules.validate(result)
                                            onSave(result)
                                        } catch (e: Exception) {
                                            error = e.message?.take(180) ?: "入力を確認してください"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) {
                                Text(if (item.kind == "card_debit") "引落しを確定" else "保存")
                            }
                        },
                    )
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Caption("入力内容は暗号化して自動保存します")
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                    fun set(key: String, value: String) {
                        values[key] = value
                    }
                    @Composable
                    fun field(
                        key: String,
                        label: String,
                        date: Boolean = false,
                        number: Boolean = false,
                    ) {
                        FieldInput(
                            label,
                            values[key].orEmpty(),
                            { set(key, it) },
                            dateType = if (date) "date" else "",
                            numeric = number,
                        )
                    }
                    @Composable
                    fun account(label: String = "口座") {
                        if (accounts.size > 1)
                            NamedReference(
                                label,
                                values["accountId"].orEmpty(),
                                accounts,
                                { set("accountId", it) },
                            )
                        else Caption("$label：${accounts.first().title}")
                    }
                    when (item.kind) {
                        "account" -> field("title", "口座名")
                        "balance_setting" -> {
                            account()
                            field("amount", "対象口座の実残高 円（負の値も可）")
                            field("memo", "メモ（任意）")
                            Caption("ここまでの取引履歴は残し、残高の基準を保存時点で設定します。カードの未引落し額は残ります。")
                        }
                        "credit_card" -> {
                            field("title", "カード名")
                            SelectionField(
                                "締め日",
                                values["closingDay"].orEmpty(),
                                (1..31).map(Int::toString),
                                { set("closingDay", it) },
                            )
                            SelectionField(
                                "引落し日",
                                values["debitDay"].orEmpty(),
                                (1..31).map(Int::toString),
                                { set("debitDay", it) },
                            )
                            val offsets =
                                linkedMapOf("0" to "締め月の当月", "1" to "締め月の翌月", "2" to "締め月の翌々月")
                            SelectionField(
                                "引落し月",
                                offsets[values["debitOffset"]].orEmpty(),
                                offsets.values.toList(),
                                { label ->
                                    set(
                                        "debitOffset",
                                        offsets.entries.first { it.value == label }.key,
                                    )
                                },
                            )
                            SelectionField(
                                "休日の扱い",
                                values["debitRule"].orEmpty(),
                                listOf("日付どおり", "土日は翌平日"),
                                { set("debitRule", it) },
                            )
                            account("引落し口座")
                            Caption(
                                "存在しない29〜31日は月末です。祝日・銀行休業日は自動判定せず、利用ごとの予定日を手動変更できます。予定日だけでは引落し済みにしません。"
                            )
                        }
                        "transaction" -> {
                            field("date", "日付", date = true)
                            field("amount", "金額 円", number = true)
                            field("title", "内容")
                            SelectionField(
                                "カテゴリ",
                                values["category"].orEmpty(),
                                (all.filter { it.kind == "category" }.map { it.title } +
                                        listOf("ライブ", "遠征", "サブスク", "生活費", "給与"))
                                    .distinct(),
                                { set("category", it) },
                                true,
                            )
                            if (values["type"] != "収入") {
                                SelectionField(
                                    "支払方法",
                                    values["method"].orEmpty(),
                                    listOf("現金", "銀行振込", "電子マネー", "クレジットカード"),
                                    {
                                        set("method", it)
                                        if (it != "クレジットカード") {
                                            values.remove("cardId")
                                            values.remove("dueDate")
                                        }
                                    },
                                )
                            }
                            if (values["method"] == "クレジットカード" && values["type"] != "収入") {
                                if (cards.isNotEmpty())
                                    NamedReference(
                                        "カード",
                                        values["cardId"].orEmpty(),
                                        cards,
                                        { id ->
                                            set("cardId", id)
                                            cards
                                                .first { it.id == id }
                                                .let { set("accountId", it["accountId"]) }
                                        },
                                    )
                                else
                                    TextButton(
                                        onClick = {
                                            onRelated(Item(kind = "credit_card", data = emptyMap()))
                                        }
                                    ) {
                                        Text("カードを登録")
                                    }
                                cards
                                    .firstOrNull { it.id == values["cardId"] }
                                    ?.let { card ->
                                        Caption(
                                            "引落し口座：${accounts.firstOrNull{it.id==card["accountId"]}?.title.orEmpty()}"
                                        )
                                        runCatching {
                                                CardDates.due(
                                                    card,
                                                    LocalDate.parse(values["date"]),
                                                    values["dueDate"].orEmpty(),
                                                )
                                            }
                                            .getOrNull()
                                            ?.let {
                                                Caption(
                                                    "支払予定：${UiPolicy.date(it.toString())}（引落し確定は別操作）"
                                                )
                                            }
                                        OptionalDate(
                                            "支払予定日の個別変更",
                                            values["dueDate"].orEmpty(),
                                            { set("dueDate", it) },
                                        )
                                    }
                            } else account()
                            field("memo", "メモ（任意）")
                            if (values["type"] == "予定") Caption("予定は実残高・カード債務・確定支出に計上しません。")
                        }
                        "subscription" -> {
                            if (section != "billing") {
                                SectionTitle("登録内容")
                                field("title", "サービス名")
                                field("amount", "料金 円", number = true)
                                SelectionField(
                                    "プラン",
                                    values["cycle"].orEmpty(),
                                    listOf("月額", "年額"),
                                    { set("cycle", it) },
                                )
                                SelectionField(
                                    "契約状態",
                                    values["status"].orEmpty(),
                                    listOf("契約中", "解約済"),
                                    { set("status", it) },
                                )
                                field("memo", "メモ（任意）")
                            }
                            if (section != "registration") {
                                SectionTitle("請求情報")
                                if (values["cycle"] == "年額")
                                    SelectionField(
                                        "請求月",
                                        values["billMonth"].orEmpty(),
                                        (1..12).map(Int::toString),
                                        { set("billMonth", it) },
                                    )
                                SelectionField(
                                    if (values["cycle"] == "年額") "請求日" else "毎月の請求日",
                                    values["billDay"].orEmpty(),
                                    (1..31).map(Int::toString),
                                    { set("billDay", it) },
                                )
                                Caption("存在しない29〜31日は、その月の末日です。")
                                if (values["cycle"] == "年額")
                                    OptionalDate(
                                        "前回請求日",
                                        values["lastBill"].orEmpty(),
                                        {
                                            set("lastBill", it)
                                            if (it.isNotEmpty()) {
                                                val paid = LocalDate.parse(it)
                                                set("billMonth", paid.monthValue.toString())
                                                set("billDay", paid.dayOfMonth.toString())
                                            }
                                        },
                                    )
                                val next =
                                    runCatching { SubscriptionBilling.next(current()) }.getOrNull()
                                Text("次回の請求予定：${next?.let{UiPolicy.date(it.toString())}?:"予定なし"}")
                                OptionalDate(
                                    "次回請求日の個別変更",
                                    values["manualNext"].orEmpty(),
                                    { set("manualNext", it) },
                                )
                                OptionalDate(
                                    "解約予定日",
                                    values["cancelOn"].orEmpty(),
                                    { set("cancelOn", it) },
                                )
                                Caption("請求の予測だけを表示します。取引やカード債務を自動登録しません。外部サービスの解約は別途行ってください。")
                            }
                        }
                        "card_debit" -> {
                            val card = cards.firstOrNull { it.id == values["cardId"] }
                            Text(
                                card?.title ?: "カードを選び直してください",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            field("date", "実際の引落し日", date = true)
                            Caption("実際に口座から引き落とされた利用だけを選び、上の「引落しを確定」を押してください。")
                            val pending = Ledger.outstanding(all, values["cardId"])
                            val selected =
                                values["usageIds"]
                                    .orEmpty()
                                    .split(',')
                                    .filter(String::isNotBlank)
                                    .toSet()
                            pending.forEach { use ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        val next =
                                            if (use.id in selected) selected - use.id
                                            else selected + use.id
                                        set("usageIds", next.joinToString(","))
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(use.id in selected, null)
                                    Column(Modifier.weight(1f)) {
                                        Text(use.title)
                                        Caption(UiPolicy.date(use["date"]))
                                    }
                                    MoneyAmount(
                                        "amount:${use.id}",
                                        use.amount,
                                        size = 18,
                                        modifier = Modifier.widthIn(max = 150.dp),
                                    )
                                }
                            }
                            MoneyAmount(
                                "debit:${item.id}",
                                pending.filter { it.id in selected }.sumOf { it.amount },
                                "今回の引落し合計",
                                24,
                            )
                        }
                    }
                    TextButton(onClick = onDiscard, enabled = !busy) {
                        Text("この入力を破棄", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
