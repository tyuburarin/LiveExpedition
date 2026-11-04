@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.local.liveexpedition

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*

data class MonthSummary(val income: Long, val expense: Long, val balance: Long, val budget: Long) {
    val net
        get() = income - expense
}

object Finance {
    fun month(all: List<Item>, month: YearMonth): MonthSummary {
        val rows =
            all.filter { it.kind == "transaction" && it["date"].startsWith(month.toString()) }
        return MonthSummary(
            rows.filter { it["type"] == "収入" }.sumOf { it.amount },
            rows.filter { it["type"] == "支出" }.sumOf { it.amount },
            Rules.balance(all),
            all.filter {
                    it.kind == "budget" &&
                        it["month"] == month.toString() &&
                        it["category"].isEmpty()
                }
                .sumOf { it.amount },
        )
    }

    fun annualSubscriptions(all: List<Item>) =
        all.filter { SubscriptionBilling.active(it) }
            .sumOf { if (it["cycle"] == "年額") it.amount else it.amount * 12 }

    fun monthlySubscriptions(all: List<Item>) =
        all.filter { SubscriptionBilling.active(it) }
            .sumOf { if (it["cycle"] == "年額") it.amount / 12 else it.amount }
}

@Composable
fun FinanceDashboard(
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onDetail: (Item) -> Unit,
    onSubscriptions: () -> Unit,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var query by rememberSaveable { mutableStateOf("") }
    val month = YearMonth.parse(monthText)
    val summary = Finance.month(all, month)
    val rows =
        all.filter {
                it.kind in listOf("transaction", "card_debit") &&
                    it["date"].startsWith(monthText) &&
                    (query.isBlank() || it.data.values.any { v -> v.contains(query, true) })
            }
            .sortedWith(
                compareByDescending<Item> { it["date"] }.thenByDescending { Ledger.seq(it) }
            )
    val groups =
        all.filter {
                it.kind == "transaction" && it["type"] == "支出" && it["date"].startsWith(monthText)
            }
            .groupBy { it["category"].ifEmpty { "未分類" } }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onEdit(Item(kind = "transaction", data = defaults("transaction")))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("＋支出")
                }
                Button(
                    onClick = {
                        onEdit(
                            Item(
                                kind = "transaction",
                                data = defaults("transaction") + ("type" to "収入"),
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("＋収入")
                }
                TextButton(
                    onClick = {
                        onEdit(
                            Item(
                                kind = "transaction",
                                data = defaults("transaction") + ("type" to "予定"),
                            )
                        )
                    }
                ) {
                    Text("予定")
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronLeft, "前月")
                }
                Text(UiPolicy.date(monthText))
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronRight, "翌月")
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                MoneyAmount("income:$monthText", summary.income, "今月の収入", 24, true)
                MoneyAmount("expense:$monthText", summary.expense, "今月の支出", 24)
                MoneyAmount("net:$monthText", summary.net, "今月の収支差額", 24, true)
                Caption("カード利用を支出に集計し、実際の引落しは重ねて集計しません。予定と請求予測も対象外です。")
                if (summary.budget > 0) {
                    MoneyAmount("budget:$monthText", summary.budget, "予算", 18)
                    MoneyAmount(
                        "remaining:$monthText",
                        summary.budget - summary.expense,
                        "予算の残り",
                        18,
                        true,
                    )
                    if (summary.expense > summary.budget) StatusPill("予算超過")
                }
                TextButton(
                    onClick = {
                        onEdit(
                            all.firstOrNull {
                                it.kind == "budget" &&
                                    it["month"] == monthText &&
                                    it["category"].isEmpty()
                            }
                                ?: Item(
                                    kind = "budget",
                                    data = mapOf("title" to "月の予算", "month" to monthText),
                                )
                        )
                    }
                ) {
                    Text("予算を設定・編集")
                }
            }
        }
        if (groups.isNotEmpty())
            item {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("カテゴリ別の支出")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(110.dp)) {
                            var angle = -90f
                            groups.forEachIndexed { n, g ->
                                val sweep =
                                    if (summary.expense == 0L) 0f
                                    else g.value.toFloat() / summary.expense * 360
                                drawArc(
                                    DesignTokens.chart[n % DesignTokens.chart.size],
                                    angle,
                                    (sweep - 1).coerceAtLeast(0f),
                                    false,
                                    style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(20.dp.toPx()),
                                )
                                angle += sweep
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            groups.forEach { g ->
                                MoneyAmount(
                                    "category:$monthText:${g.key.take(30)}",
                                    g.value,
                                    g.key,
                                    16,
                                )
                            }
                        }
                    }
                }
            }
        item {
            OutlinedTextField(
                query,
                { query = it.take(100) },
                placeholder = { Text("取引を検索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(rows, key = { it.id }) { row ->
            Panel(Modifier.fillMaxWidth(), onClick = { onDetail(row) }) {
                Text(row.title, fontWeight = FontWeight.SemiBold)
                Caption(
                    "${UiPolicy.date(row["date"])}・${Ledger.accounts(all).firstOrNull{it.id==Ledger.accountId(row)}?.title.orEmpty()}"
                )
                MoneyAmount("amount:${row.id}", row.amount, size = 22)
                when {
                    row.kind == "card_debit" -> Caption("引落し（支出集計の対象外）")
                    row["type"] == "振替" -> Caption("旧振替履歴（集計対象外）")
                    row["type"] == "予定" -> StatusPill("予定・未確定")
                    Ledger.isCardUse(row) ->
                        StatusPill(if (row.id in Ledger.settledIds(all)) "引落し済み" else "未引落し")
                    else -> Caption(row["type"])
                }
            }
        }
        if (rows.isEmpty()) item { Text("この月の取引はありません。") }
    }
}

@Composable
fun SubscriptionScreen(
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onDetail: (Item) -> Unit,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var filter by rememberSaveable { mutableStateOf("すべて") }
    val month = YearMonth.parse(monthText)
    val services =
        all.filter {
            it.kind == "subscription" &&
                it["archived"] != "true" &&
                (filter == "すべて" || it["cycle"] == filter)
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronLeft, "前月")
                }
                Text(UiPolicy.date(monthText))
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronRight, "翌月")
                }
                IconButton(
                    onClick = {
                        onEdit(Item(kind = "subscription", data = defaults("subscription")))
                    }
                ) {
                    Icon(Icons.Outlined.Add, "サブスクを追加")
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                MoneyAmount(
                    "subscription:forecast:$monthText",
                    SubscriptionBilling.forecast(all, month),
                    "選択月の請求予測",
                    30,
                )
                Caption("予測のみ。実残高・取引・カード債務へ自動反映しません。")
                MoneyAmount("subscriptions:annual", Finance.annualSubscriptions(all), "年間換算の合計", 18)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("すべて", "月額", "年額").forEach { f ->
                    AccentChip(filter == f, { filter = f }, label = { Text(f) })
                }
            }
        }
        items(services, key = { it.id }) { s ->
            Panel(Modifier.fillMaxWidth(), onClick = { onDetail(s) }) {
                Text(s.title, fontWeight = FontWeight.SemiBold)
                MoneyAmount("amount:${s.id}", s.amount, if (s["cycle"] == "年額") "年額" else "月額", 22)
                Caption(
                    "次回 ${SubscriptionBilling.next(s)?.let{UiPolicy.date(it.toString())}?:"予定なし"}"
                )
                if (s["cancelOn"].isNotEmpty())
                    StatusPill("解約予定 ${UiPolicy.shortDate(s["cancelOn"])}")
                if (SubscriptionBilling.occurrence(s, month) == null) Caption("選択月の請求予測なし")
            }
        }
        if (services.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.CloudQueue,
                    "サブスクを登録しましょう",
                    "請求予測と解約予定をまとめます。",
                    "サブスクを追加",
                ) {
                    onEdit(Item(kind = "subscription", data = defaults("subscription")))
                }
            }
    }
}
