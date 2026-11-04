@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package jp.local.liveexpedition

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.time.*

@Composable
fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    val visible = rows.filter { it.second.isNotBlank() }
    if (visible.isEmpty()) return
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(title)
        visible.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Caption(label, Modifier.width(90.dp))
                Text(value, Modifier.weight(1f))
            }
        }
    }
}

private fun displayValue(value: String) =
    if (Regex("\\d{4}-\\d{2}(-\\d{2})?(T.*)?").matches(value)) UiPolicy.date(value) else value

@Composable
fun RecordDetailBody(
    item: Item,
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onNavigate: (Item) -> Unit,
    onPay: () -> Unit,
    onNextBill: () -> Unit,
    onAdopt: () -> Unit,
    onImage: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var legsOpen by remember(item.id) { mutableStateOf(false) }
    if (item["image"].isNotEmpty()) SecureThumbnail(item, Modifier.fillMaxWidth().height(170.dp))
    Text(
        item.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (item.kind == "ticket") StatusPill(ticketStatus(item))
    if (item.kind == "candidate") StatusPill(item["status"].ifEmpty { "未予約" })
    if (item.kind == "subscription") StatusPill(item["status"])
    if (item["amount"].isNotEmpty())
        MoneyAmount(
            "amount:${item.id}",
            if (item.kind == "adjustment" && item["type"] == "減額") -item.amount else item.amount,
            "${if(item.kind=="adjustment")"調整額"else "金額"}",
            32,
            item.kind == "adjustment",
        )
    val dateFields =
        Catalog.kind(item.kind)
            .fields
            .filter {
                it.key in
                    listOf(
                        "date",
                        "start",
                        "end",
                        "finish",
                        "due",
                        "resultAt",
                        "venue",
                        "from",
                        "to",
                    )
            }
            .map { it.label.substringBefore(" YYYY") to displayValue(item[it.key]) }
    InfoCard(if (item.kind == "candidate") "経路・宿泊・日時" else "日時・会場", dateFields)
    val basicFields =
        Catalog.kind(item.kind)
            .fields
            .filter {
                it.key in
                    listOf(
                        "artist",
                        "tour",
                        "status",
                        "result",
                        "quantity",
                        "cycle",
                        "method",
                        "renew",
                        "attendance",
                        "type",
                        "priceState",
                        "direction",
                        "category",
                        "month",
                    )
            }
            .map {
                (when (it.key) {
                    "cycle" -> "請求周期"
                    "renew" -> "自動更新"
                    "type" -> "種類"
                    "status" -> "状態"
                    "priceState" -> "料金の確定状態"
                    else -> it.label.substringBefore(":").substringBefore("/")
                }) to displayValue(item[it.key])
            }
    InfoCard("登録内容", basicFields)
    if (item["fee"].isNotEmpty())
        Panel(Modifier.fillMaxWidth()) {
            MoneyAmount("fee:${item.id}", item["fee"].toLong(), "手数料", 20)
            MoneyAmount("total:${item.id}", item.amount + item["fee"].toLong(), "支払合計", 24)
        }
    if (item.kind in listOf("artist", "tour")) {
        val shows =
            all.filter {
                it.kind == "live" &&
                    if (item.kind == "artist") it["artist"] == item.title
                    else it["artist"] == item["artist"] && it["tour"] == item.title
            }
        SectionTitle("関連する公演")
        shows
            .sortedBy { it["date"] }
            .forEach { show ->
                Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(show) }) {
                    Text(show.title)
                    Caption("${UiPolicy.date(show["date"])}・${show["venue"]}")
                }
            }
        TextButton(
            onClick = {
                onEdit(
                    Item(
                        kind = "live",
                        data =
                            buildMap {
                                put(
                                    "artist",
                                    if (item.kind == "artist") item.title else item["artist"],
                                )
                                if (item.kind == "tour") put("tour", item.title)
                                if (all.any { it.id == item.id })
                                    put(
                                        if (item.kind == "artist") "artistRef" else "tourRef",
                                        item.id,
                                    )
                            },
                    )
                )
            }
        ) {
            Text("＋ 公演を追加")
        }
    }
    if (item.kind == "live") {
        SectionTitle("チケット・先行受付")
        all.filter { it.kind == "ticket" && it["parent"] == item.id }
            .forEach { ticket ->
                Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(ticket) }) {
                    Text(ticket.title, fontWeight = FontWeight.SemiBold)
                    StatusPill(ticketStatus(ticket))
                    if (ticket["due"].isNotEmpty()) Caption("支払期限 ${UiPolicy.time(ticket["due"])}")
                }
            }
        TextButton(
            onClick = {
                onEdit(Item(kind = "ticket", data = defaults("ticket") + ("parent" to item.id)))
            }
        ) {
            Text("＋ 先行・受付を追加")
        }
        all.filter { it.kind == "plan" && item.id in it["liveRefs"].split(',') }
            .forEach { plan ->
                Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(plan) }) {
                    Text("関連する遠征：${plan.title}")
                }
            }
    }
    if (item.kind == "ticket")
        all.firstOrNull { it.id == item["parent"] }
            ?.let { show ->
                Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(show) }) {
                    Text("公演：${show.title}")
                    Caption(UiPolicy.date(show["date"]))
                }
            }
    if (item.kind == "candidate" && item["segments"].isNotEmpty())
        Panel(Modifier.fillMaxWidth()) {
            TextButton(onClick = { legsOpen = !legsOpen }) {
                Text(if (legsOpen) "区間を閉じる" else "途中駅・乗り換えを表示")
            }
            if (legsOpen) LegTimeline(Itinerary.decode(item["segments"]), money)
        }
    if (item.kind == "plan") {
        val totals = Itinerary.totals(item, all)
        Panel(Modifier.fillMaxWidth()) {
            MoneyAmount(
                "plan:${item.id}",
                totals.total,
                "${if(totals.missingPrices>0)"入力済み料金の合計"else "候補の合計"}",
                30,
            )
            MoneyAmount("traffic:${item.id}", totals.traffic, "交通", 18)
            MoneyAmount("hotel:${item.id}", totals.hotel, "宿泊", 18)
            MoneyAmount("other:${item.id}", totals.other, "その他", 18)
            totals.warnings.forEach { Caption("注意：$it") }
            Button(onClick = onAdopt) {
                Text(if (item["adopted"] == "true") "採用を解除" else "このプランを採用")
            }
        }
        SectionTitle("関連公演")
        Connections.linkedShows(item, all).forEach { show ->
            Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(show) }) {
                Text(show.title)
                Caption(UiPolicy.date(show["date"]))
            }
        }
        TextButton(onClick = { onEdit(item) }) { Text("公演との関連付けを編集") }
        SectionTitle("プランの候補")
        all.filter { it.id in item["members"].split(',') }
            .sortedBy { it["start"] }
            .forEach { c ->
                Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(c) }) {
                    Text(c.title)
                    Caption(UiPolicy.time(c["start"]))
                    MoneyAmount("amount:${c.id}", Itinerary.knownCost(c), size = 18)
                }
            }
    }
    if (item.kind in listOf("ticket", "candidate", "subscription"))
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("実際の支払記録")
            StatusPill(if (item["paid"].isEmpty()) "未払い" else "支払済み")
            Caption("申込・当選・予約だけでは支出に計上しません。換算額と実取引を分けて管理します。")
            if (item["paid"].isEmpty() && (item.kind != "subscription" || item["status"] == "契約中"))
                Button(onClick = onPay) { Text("実際の支払を記録") }
            if (item.kind == "subscription" && item["paid"].isNotEmpty() && item["renew"] == "はい")
                TextButton(onClick = onNextBill) { Text("次の請求日へ進める") }
            all.filter { it.id in item["payments"].split(',') }
                .forEach { tx ->
                    TextButton(onClick = { onNavigate(tx) }) {
                        Text("${UiPolicy.date(tx["date"])}・${money(tx.amount)} の取引を開く")
                    }
                }
        }
    if (item.kind == "transaction")
        all.firstOrNull { item.id in it["payments"].split(',') }
            ?.let { source ->
                Panel(Modifier.fillMaxWidth(), onClick = { onNavigate(source) }) {
                    Text("支払元：${source.title}")
                    Caption("連携済みの実取引です。元データから支払状態を確認できます。")
                }
            }
    if (item["memo"].isNotEmpty())
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("メモ")
            Text(item["memo"])
        }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEdit(item) }) {
            Text(if (all.any { it.id == item.id }) "編集" else "登録・編集")
        }
        if (item.kind in listOf("live", "candidate"))
            OutlinedButton(onClick = onDuplicate) { Text("複製して登録") }
    }
    if (item.kind in listOf("artist", "tour", "live", "ticket", "candidate"))
        TextButton(onClick = onImage) {
            Icon(Icons.Outlined.Photo, null)
            Text("画像を登録・変更")
        }
}

@Composable
internal fun DetailDialog(
    item: Item,
    all: List<Item>,
    model: AppModel,
    money: (Long) -> String,
    onDismiss: () -> Unit,
    onEdit: (Item) -> Unit,
    onNavigate: (Item) -> Unit = {},
) {
    val ctx = LocalContext.current
    val external = LocalExternalActions.current
    var deleting by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    var payDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var notify by remember { mutableStateOf(false) }
    var notificationKey by remember {
        mutableStateOf(
            if (item.kind == "ticket") "remindApply"
            else if (item.kind == "subscription") "remindSubscription" else "remindTravel"
        )
    }
    var notificationTime by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("${Catalog.kind(item.kind).label}の詳細") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.ArrowBack, "戻る") }
                        },
                    )
                }
            ) { padding ->
                Column(
                    Modifier.padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecordDetailBody(
                        item,
                        all,
                        money,
                        onEdit,
                        onNavigate,
                        { pay = true },
                        { model.nextBill(item) },
                        {
                            model.save(
                                item.copy(
                                    data =
                                        item.data +
                                            ("adopted" to (item["adopted"] != "true").toString())
                                )
                            )
                        },
                        { external.image(item) },
                        {
                            model.duplicate(item)
                            onDismiss()
                        },
                    )
                    if (item["url"].isNotEmpty())
                        OutlinedButton(
                            onClick = {
                                if (Rules.url(item["url"]))
                                    try {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(item["url"]))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    } catch (e: Exception) {
                                        model.message.value = "外部ブラウザを開けませんでした"
                                    }
                            }
                        ) {
                            Text("公式情報を外部ブラウザで開く")
                        }
                    if (item.kind in listOf("ticket", "candidate", "subscription", "schedule")) {
                        TextButton(onClick = { notify = !notify }) {
                            Text(if (notify) "通知設定を閉じる" else "通知を設定")
                        }
                        if (notify)
                            Panel(Modifier.fillMaxWidth()) {
                                val keys =
                                    if (item.kind == "ticket")
                                        listOf(
                                            "remindApply" to "申込締切",
                                            "remindResult" to "当落発表",
                                            "remindPay" to "支払期限",
                                        )
                                    else if (item.kind == "subscription")
                                        listOf("remindSubscription" to "更新日")
                                    else listOf("remindTravel" to "予定の開始前")
                                SelectionField(
                                    "通知の種類",
                                    keys
                                        .firstOrNull { it.first == notificationKey }
                                        ?.second
                                        .orEmpty(),
                                    keys.map { it.second },
                                    { label ->
                                        notificationKey = keys.first { it.second == label }.first
                                        notificationTime = item[notificationKey]
                                    },
                                )
                                FieldInput(
                                    "通知日時",
                                    notificationTime,
                                    { notificationTime = it },
                                    dateType = "datetime",
                                )
                                Caption("通知本文に金額や目的地は載せません。省電力設定により遅れる場合があります。")
                                Row {
                                    TextButton(
                                        onClick = {
                                            if (
                                                ctx.checkSelfPermission(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) !=
                                                    android.content.pm.PackageManager
                                                        .PERMISSION_GRANTED
                                            )
                                                external.permission()
                                            else
                                                try {
                                                    val at = LocalDateTime.parse(notificationTime)
                                                    require(at > LocalDateTime.now())
                                                    model.run {
                                                        save(
                                                            item.copy(
                                                                data =
                                                                    item.data +
                                                                        (notificationKey to
                                                                            notificationTime)
                                                            )
                                                        )
                                                        Reminders.set(
                                                            ctx,
                                                            "${item.id}.$notificationKey",
                                                            at,
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    model.message.value = "未来の通知日時を選択してください"
                                                }
                                        }
                                    ) {
                                        Text("通知をON")
                                    }
                                    TextButton(
                                        onClick = {
                                            Reminders.cancel(ctx, "${item.id}.$notificationKey")
                                            model.save(
                                                item.copy(data = item.data - notificationKey)
                                            )
                                        }
                                    ) {
                                        Text("通知をOFF")
                                    }
                                }
                            }
                    }
                    if (all.any { it.id == item.id })
                        TextButton(onClick = { deleting = true }) {
                            Text("この記録を削除", color = MaterialTheme.colorScheme.error)
                        }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("削除しますか？") },
            text = { Text("関連する記録や支払履歴がある場合は削除できません。削除直後は通知の「元に戻す」で復元できます。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.delete(item)
                        deleting = false
                        onDismiss()
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("キャンセル") } },
        )
    if (pay)
        AlertDialog(
            onDismissRequest = { pay = false },
            title = { Text("実際の支払を記録") },
            text = {
                Column {
                    FieldInput("支払日", payDate, { payDate = it }, dateType = "date")
                    Text(
                        "${money(item.amount+(item["fee"].toLongOrNull()?:0))}の支払を取引として登録します。予定や当選だけの場合は記録しないでください。"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.pay(item, LocalDate.parse(payDate))
                        pay = false
                    }
                ) {
                    Text("支払ったことを確認")
                }
            },
            dismissButton = { TextButton(onClick = { pay = false }) { Text("キャンセル") } },
        )
}
