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
        all.filter { it.kind == "subscription" && it["status"] == "契約中" }
            .sumOf { if (it["cycle"] == "年額") it.amount else it.amount * 12 }

    fun monthlySubscriptions(all: List<Item>) =
        all.filter { it.kind == "subscription" && it["status"] == "契約中" }
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
    val month = YearMonth.parse(monthText)
    var query by rememberSaveable { mutableStateOf("") }
    var balances by remember { mutableStateOf(false) }
    var aggregate by rememberSaveable { mutableStateOf(false) }
    val summary = Finance.month(all, month)
    val rows =
        all.filter {
            it.kind == "transaction" &&
                it["date"].startsWith(monthText) &&
                (query.isBlank() || it.data.values.any { v -> v.contains(query, true) })
        }
    val groups =
        rows
            .filter { it["type"] == "支出" }
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
            Panel(Modifier.fillMaxWidth(), onClick = { balances = true }) {
                MoneyAmount("balance", summary.balance, "現在残高", 36, true)
                MoneyAmount("net:$monthText", summary.net, "今月の収支差額", 18, true)
                Caption("タップして初期残高・調整履歴を確認")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = semanticColor(Meaning.POSITIVE).copy(alpha = .2f),
                            contentColor = semanticColor(Meaning.POSITIVE),
                        ),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Text("収入を追加")
                }
                Button(
                    onClick = {
                        onEdit(Item(kind = "transaction", data = defaults("transaction")))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Remove, null)
                    Text("支出を追加")
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronLeft, "前月")
                }
                Text(UiPolicy.date(monthText), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronRight, "翌月")
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                MoneyAmount("expense:$monthText", summary.expense, "今月の支出", 28)
                if (groups.isNotEmpty()) {
                    val colors = DesignTokens.chart
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(110.dp)) {
                            var angle = -90f
                            groups.forEachIndexed { index, g ->
                                val sweep =
                                    if (summary.expense > 0)
                                        g.value.toFloat() / summary.expense * 360
                                    else 0f
                                drawArc(
                                    colors[index % colors.size],
                                    angle,
                                    (sweep - 1f).coerceAtLeast(0f),
                                    false,
                                    style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 20.dp.toPx()
                                        ),
                                )
                                angle += sweep
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            groups.take(5).forEach { g ->
                                Caption(
                                    "${g.key} ${if(summary.expense>0)g.value*100/summary.expense else 0}%"
                                )
                            }
                        }
                    }
                }
                MoneyAmount("income:$monthText", summary.income, "今月の収入", 24, true)
                if (summary.budget > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (summary.expense.toFloat() / summary.budget).coerceIn(0f, 1f)
                        },
                        color =
                            if (summary.expense > summary.budget) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MoneyAmount("budget:$monthText", summary.budget, "予算", 18)
                    MoneyAmount(
                        "remaining:$monthText",
                        summary.budget - summary.expense,
                        "残り",
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
                                    data =
                                        mapOf(
                                            "title" to "${month.monthValue}月の予算",
                                            "month" to monthText,
                                        ),
                                )
                        )
                    }
                ) {
                    Text("予算を設定・編集")
                }
            }
        }
        item {
            OutlinedButton(onClick = onSubscriptions, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CloudQueue, null)
                Text("サブスク管理")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentChip(!aggregate, { aggregate = false }, label = { Text("取引一覧") })
                AccentChip(aggregate, { aggregate = true }, label = { Text("カテゴリ別・予算") })
            }
        }
        if (aggregate) {
            items(groups, key = { it.key }) { g ->
                Panel(Modifier.fillMaxWidth()) {
                    MoneyAmount(
                        "group:${java.util.UUID.nameUUIDFromBytes(g.key.toByteArray())}:$monthText",
                        g.value,
                        g.key,
                        20,
                    )
                }
            }
            items(
                all.filter { it.kind == "budget" && it["month"] == monthText },
                key = { it.id },
            ) { b ->
                val spent =
                    rows
                        .filter {
                            it["type"] == "支出" &&
                                (b["category"].isEmpty() || it["category"] == b["category"])
                        }
                        .sumOf { it.amount }
                Panel(Modifier.fillMaxWidth(), onClick = { onDetail(b) }) {
                    Text(b.title)
                    MoneyAmount("amount:${b.id}", b.amount, "予算", 18)
                    MoneyAmount("remaining:${b.id}", b.amount - spent, "残り", 18, true)
                    if (spent > b.amount) StatusPill("予算超過")
                }
            }
        } else {
            item {
                OutlinedTextField(
                    query,
                    { query = it.take(100) },
                    label = { Text("取引・カテゴリ・支払方法を検索") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (rows.isEmpty())
                item {
                    EmptyState(
                        Icons.Outlined.AccountBalanceWallet,
                        "この月の取引はありません",
                        "実際の支払と予定を分けて登録できます。",
                        "取引を追加",
                    ) {
                        onEdit(Item(kind = "transaction", data = defaults("transaction")))
                    }
                }
            rows
                .sortedByDescending { it["date"] }
                .groupBy { it["date"] }
                .forEach { (date, txns) ->
                    item { SectionTitle(UiPolicy.date(date)) }
                    items(txns, key = { it.id }) { t ->
                        Panel(Modifier.fillMaxWidth(), onClick = { onDetail(t) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (t["type"] == "収入") Icons.Outlined.NorthEast
                                    else if (t["type"] == "支出") Icons.Outlined.SouthWest
                                    else Icons.Outlined.SwapHoriz,
                                    null,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(t.title, fontWeight = FontWeight.SemiBold)
                                    Caption("${t["category"]}・${t["method"]}・${t["type"]}")
                                }
                                MoneyAmount(
                                    "amount:${t.id}",
                                    t.amount,
                                    size = 18,
                                    modifier = Modifier.widthIn(max = 165.dp),
                                )
                            }
                        }
                    }
                }
        }
    }
    if (balances)
        ModalBottomSheet(onDismissRequest = { balances = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("初期残高・調整履歴")
                Caption("調整は独立した履歴として残し、過去の収入・支出は変更しません。")
                Button(
                    onClick = {
                        balances = false
                        onEdit(
                            Item(
                                kind = "adjustment",
                                data =
                                    mapOf(
                                        "title" to
                                            if (all.none { it.kind == "adjustment" }) "初期残高"
                                            else "残高調整",
                                        "type" to "増額",
                                        "date" to LocalDate.now().toString(),
                                    ),
                            )
                        )
                    }
                ) {
                    Text("＋ 初期残高・増減を記録")
                }
                all.filter { it.kind == "adjustment" }
                    .sortedByDescending { it["date"] }
                    .forEach { a ->
                        Panel(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            onClick = {
                                balances = false
                                onDetail(a)
                            },
                        ) {
                            Text(a.title)
                            Caption("${UiPolicy.date(a["date"])}・${a["type"]}")
                            MoneyAmount(
                                "amount:${a.id}",
                                if (a["type"] == "減額") -a.amount else a.amount,
                                size = 22,
                                semanticSign = true,
                            )
                        }
                    }
            }
        }
}

@Composable
fun SubscriptionScreen(
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onDetail: (Item) -> Unit,
) {
    var filter by remember { mutableStateOf("すべて") }
    val services =
        all.filter { it.kind == "subscription" && (filter == "すべて" || it["cycle"] == filter) }
            .sortedBy { it["date"] }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f)) {
                    Caption("月あたりの負担額")
                    MoneyAmount(
                        "subscriptions:monthly",
                        Finance.monthlySubscriptions(all),
                        size = 24,
                    )
                    Caption("年額の月割りを含む")
                }
                Panel(Modifier.weight(1f)) {
                    Caption("年間換算の合計")
                    MoneyAmount("subscriptions:annual", Finance.annualSubscriptions(all), size = 24)
                    Caption("継続時の参考額")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("すべて", "月額", "年額").forEach { f ->
                        AccentChip(filter == f, { filter = f }, label = { Text(f) })
                    }
                }
                IconButton(
                    onClick = {
                        onEdit(Item(kind = "subscription", data = defaults("subscription")))
                    }
                ) {
                    Icon(Icons.Outlined.Add, "サブスクを追加")
                }
            }
        }
        if (services.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.CloudQueue,
                    "サブスクをまとめましょう",
                    "更新日と月額・年間の負担を確認できます。",
                    "サブスクを追加",
                ) {
                    onEdit(Item(kind = "subscription", data = defaults("subscription")))
                }
            }
        items(services, key = { it.id }) { s ->
            Panel(Modifier.fillMaxWidth(), onClick = { onDetail(s) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.CloudQueue, null, Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontWeight = FontWeight.SemiBold)
                        MoneyAmount(
                            "amount:${s.id}",
                            s.amount,
                            if (s["cycle"] == "年額") "年額" else "月額",
                            20,
                        )
                        Caption("次回 ${UiPolicy.date(s["date"])}・${s["method"]}")
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
                if (s["status"] == "解約済") StatusPill("解約済")
                else if (s["paid"].isNotEmpty()) StatusPill("支払済み")
            }
        }
    }
}
