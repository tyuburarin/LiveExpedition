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

object BalanceLayouts {
    val keys = setOf("money", "accounts", "credit", "subscription", "transactions")
    val defaults =
        listOf(
            Tile("money", 2, 1),
            Tile("accounts", 2, 1),
            Tile("credit"),
            Tile("subscription"),
            Tile("transactions", 2, 1),
        )
}

@Composable
fun BalanceDashboard(
    model: AppModel,
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onRoute: (String) -> Unit,
) {
    val tiles by model.balanceTiles.collectAsState(initial = BalanceLayouts.defaults)
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selected = YearMonth.parse(month)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onEdit(Item(kind = "transaction", data = defaults("transaction"))) },
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
        }
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
        ) {
            TextButton(onClick = { onRoute("transactions") }) { Text("収支一覧") }
            TextButton(onClick = { onRoute("accounts") }) { Text("口座") }
            TextButton(onClick = { onRoute("cards") }) { Text("カード") }
            TextButton(
                onClick = {
                    onEdit(
                        Item(
                            kind = "balance_setting",
                            data =
                                mapOf(
                                    "title" to "残高を設定",
                                    "accountId" to Ledger.DEFAULT_ACCOUNT,
                                    "date" to LocalDate.now().toString(),
                                ),
                        )
                    )
                }
            ) {
                Text("残高を設定")
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = selected.minusMonths(1).toString() }) {
                Icon(Icons.Outlined.ChevronLeft, "前月")
            }
            Text("集計・請求予測：${UiPolicy.date(month)}", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { month = selected.plusMonths(1).toString() }) {
                Icon(Icons.Outlined.ChevronRight, "翌月")
            }
        }
        Box(Modifier.weight(1f)) {
            DashboardScreen(
                tiles,
                all,
                money,
                model::balanceTiles,
                { key, _ ->
                    when (key) {
                        "money" ->
                            onEdit(
                                Item(
                                    kind = "balance_setting",
                                    data =
                                        mapOf(
                                            "title" to "残高を設定",
                                            "accountId" to Ledger.DEFAULT_ACCOUNT,
                                            "date" to LocalDate.now().toString(),
                                        ),
                                )
                            )
                        "accounts" -> onRoute("accounts")
                        "credit" -> onRoute("cards")
                        "subscription" -> onRoute("subscription")
                        else -> onRoute("transactions")
                    }
                },
                scope = "balance",
                selectedMonth = selected,
                allowedKeys = BalanceLayouts.keys,
            )
        }
    }
}

@Composable
fun AccountManager(all: List<Item>, onEdit: (Item) -> Unit) {
    val totals = Ledger.totals(all)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(totals.accounts, key = { it.account.id }) { balance ->
            Panel(Modifier.fillMaxWidth(), onClick = { onEdit(balance.account) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(balance.account.title, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.Edit, "口座名を編集")
                }
                MoneyAmount("account:${balance.account.id}", balance.current, "現在残高", 24, true)
                TextButton(
                    onClick = {
                        onEdit(
                            Item(
                                kind = "balance_setting",
                                data =
                                    mapOf(
                                        "title" to "残高を設定",
                                        "date" to LocalDate.now().toString(),
                                        "accountId" to balance.account.id,
                                    ),
                            )
                        )
                    }
                ) {
                    Text("この口座の残高を設定")
                }
            }
        }
        item {
            OutlinedButton(
                enabled = totals.accounts.size < 2,
                onClick = { onEdit(Item(kind = "account", data = emptyMap())) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("＋口座を追加（最大2つ）")
            }
            Caption("口座名はカードをタップして変更できます。")
            Panel(Modifier.fillMaxWidth()) {
                MoneyAmount("balance", totals.current, "全口座の合計残高", 28, true)
            }
        }
        val history =
            all.filter { it.kind in listOf("balance_setting", "adjustment") }
                .sortedByDescending { Ledger.seq(it) }
        if (history.isNotEmpty()) item { SectionTitle("残高設定の履歴") }
        items(history, key = { it.id }) { setting ->
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    Ledger.accounts(all)
                        .firstOrNull { it.id == Ledger.accountId(setting) }
                        ?.title
                        .orEmpty()
                )
                Caption(UiPolicy.date(setting["recordedAt"].ifEmpty { setting["date"] }))
                MoneyAmount(
                    "amount:${setting.id}",
                    if (setting["type"] == "減額") -setting.amount else setting.amount,
                    setting.title,
                    20,
                )
            }
        }
    }
}

@Composable
fun CreditCardManager(all: List<Item>, onEdit: (Item) -> Unit, onDetail: (Item) -> Unit) {
    val cards = all.filter { it.kind == "credit_card" }
    val settled = Ledger.settledIds(all)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            OutlinedButton(
                onClick = { onEdit(Item(kind = "credit_card", data = emptyMap())) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("＋カードを登録")
            }
        }
        if (cards.isEmpty()) item { Text("カードを登録すると、利用額と実際の引落しを分けて管理できます。") }
        cards.forEach { card ->
            item(key = card.id) {
                Panel(Modifier.fillMaxWidth(), onClick = { onEdit(card) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(card.title, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Outlined.Edit, "カード設定を編集")
                    }
                    Caption(
                        "締め日 ${card["closingDay"]}日 ／ 引落し ${card["debitOffset"]}か月後の${card["debitDay"]}日"
                    )
                    Caption(
                        "引落し口座：${Ledger.accounts(all).firstOrNull{it.id==card["accountId"]}?.title.orEmpty()}"
                    )
                    val pending = Ledger.outstanding(all, card.id)
                    MoneyAmount(
                        "credit:${card.id}",
                        pending.sumOf { it.amount },
                        "未引落しの利用額（全期間）",
                        24,
                    )
                    if (pending.isNotEmpty())
                        Button(
                            onClick = {
                                onEdit(
                                    Item(
                                        kind = "card_debit",
                                        data =
                                            mapOf(
                                                "title" to "${card.title} 引落し",
                                                "cardId" to card.id,
                                                "accountId" to card["accountId"],
                                                "date" to LocalDate.now().toString(),
                                                "usageIds" to pending.joinToString(",") { it.id },
                                            ),
                                    )
                                )
                            }
                        ) {
                            Text("実際の引落しを確定")
                        }
                }
            }
            val uses =
                all.filter { Ledger.isCardUse(it) && it["cardId"] == card.id }
                    .sortedByDescending { it["date"] }
            items(uses, key = { it.id }) { use ->
                Panel(Modifier.fillMaxWidth(), onClick = { onDetail(use) }) {
                    Text(use.title)
                    MoneyAmount("amount:${use.id}", use.amount, size = 20)
                    Caption("利用日 ${UiPolicy.date(use["date"])}")
                    val due =
                        runCatching {
                                CardDates.due(card, LocalDate.parse(use["date"]), use["dueDate"])
                            }
                            .getOrNull()
                    if (due != null) Caption("支払予定 ${UiPolicy.date(due.toString())}")
                    StatusPill(if (use.id in settled) "引落し済み" else "未引落し")
                }
            }
        }
        item { Caption("予定日を過ぎても自動で引落し済みにはしません。銀行の祝日・休業日は利用ごとの予定日を確認してください。") }
    }
}

@Composable
fun SubscriptionDetail(item: Item, onEdit: (Item) -> Unit) {
    Text(
        item.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    MoneyAmount("amount:${item.id}", item.amount, if (item["cycle"] == "年額") "年額" else "月額", 30)
    Panel(
        Modifier.fillMaxWidth(),
        onClick = { onEdit(item.copy(data = item.data + ("_section" to "billing"))) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("請求情報")
            Icon(Icons.Outlined.Edit, "請求情報を編集")
        }
        Caption(
            if (item["cycle"] == "年額")
                "毎年 ${SubscriptionBilling.month(item)}月${SubscriptionBilling.day(item)}日"
            else "毎月 ${SubscriptionBilling.day(item)}日"
        )
        Text("次回：${SubscriptionBilling.next(item)?.let{UiPolicy.date(it.toString())}?:"請求予定なし"}")
        Caption("29〜31日がない月は月末扱い。請求予測のみ。")
    }
    Panel(
        Modifier.fillMaxWidth(),
        onClick = { onEdit(item.copy(data = item.data + ("_section" to "registration"))) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("登録内容")
            Icon(Icons.Outlined.Edit, "登録内容を編集")
        }
        Text("プラン：${item["cycle"]}")
        Text("契約状態：${item["status"]}")
        if (item["registeredOn"].isNotEmpty()) Caption("登録日 ${UiPolicy.date(item["registeredOn"])}")
        if (item["memo"].isNotEmpty()) Text(item["memo"])
    }
    Panel(
        Modifier.fillMaxWidth(),
        onClick = { onEdit(item.copy(data = item.data + ("_section" to "billing"))) },
    ) {
        SectionTitle("解約予定")
        Text(item["cancelOn"].takeIf(String::isNotEmpty)?.let(UiPolicy::date) ?: "未設定")
        Caption("予定日の翌日以降に削除を確認します。外部サービスの解約は別途必要です。")
    }
}
