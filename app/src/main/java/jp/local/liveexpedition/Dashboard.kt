@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package jp.local.liveexpedition

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.time.*
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    tiles: List<Tile>,
    all: List<Item>,
    money: (Long) -> String,
    onTiles: (List<Tile>) -> Unit,
    onOpen: (String, Item?) -> Unit,
    onToggleAmounts: () -> Unit = {},
    scope: String = "home",
    selectedMonth: YearMonth = YearMonth.now(),
    allowedKeys: Set<String> = Tiles.names.keys,
) {
    var draft by remember { mutableStateOf(tiles) }
    var original by remember { mutableStateOf(tiles) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var startScroll by remember { mutableIntStateOf(0) }
    var moved by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val scroll = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestSave by rememberUpdatedState(onTiles)
    val latestOpen by rememberUpdatedState(onOpen)
    val latestAll by rememberUpdatedState(all)
    LaunchedEffect(tiles) { if (dragId == null) draft = tiles }
    LaunchedEffect(dragId) {
        if (dragId != null)
            while (true) {
                val edge = with(density) { 64.dp.toPx() }
                val direction =
                    when {
                        pointer.y < viewport.top + edge -> -1
                        pointer.y > viewport.bottom - edge -> 1
                        else -> 0
                    }
                if (direction != 0 && moved)
                    scroll.scrollBy(direction * with(density) { 10.dp.toPx() })
                delay(16)
            }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { adding = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Add, "タイルを追加", Modifier.size(22.dp))
            }
        }
        BoxWithConstraints(
            Modifier.weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { viewport = it.boundsInRoot() }
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
        ) {
            val gap = 12.dp
            val cell = (maxWidth - gap) / 2
            val rowHeight = (144 * density.fontScale.coerceIn(1f, 1.4f)).dp
            val step = with(density) { (cell + gap).toPx() }
            val rowStep = with(density) { (rowHeight + gap).toPx() }
            val positions = Tiles.pack(draft)
            val rows = positions.values.maxOfOrNull { it.y + it.height } ?: 0
            Box(
                Modifier.fillMaxWidth()
                    .height(if (rows == 0) 120.dp else (rowHeight + gap) * rows - gap + 16.dp)
            ) {
                draft.forEach { tile ->
                    val c = positions.getValue(tile.key)
                    val active = dragId == tile.key
                    val normal = Offset(c.x * step, c.y * rowStep)
                    val raw =
                        if (active)
                            origin + delta + Offset(0f, (scroll.value - startScroll).toFloat())
                        else normal
                    val offset by
                        animateIntOffsetAsState(
                            IntOffset(raw.x.toInt(), raw.y.toInt()),
                            if (active) snap() else spring(stiffness = 400f),
                            label = "tile-position",
                        )
                    val scale by
                        animateFloatAsState(if (active) 1.04f else 1f, label = "tile-scale")
                    Box(
                        Modifier.offset { offset }
                            .size(
                                cell * tile.width + gap * (tile.width - 1),
                                rowHeight * tile.height + gap * (tile.height - 1),
                            )
                            .zIndex(if (active) 10f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (active) 20.dp.toPx() else 0f
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                                clip = false
                            }
                            .semantics {
                                onClick(label = "詳細を開く") {
                                    latestOpen(tile.key, tileItem(tile.key, latestAll))
                                    true
                                }
                                contentDescription =
                                    "${Tiles.names[tile.key]}タイル ${tile.width}×${tile.height}"
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction("設定を開く") {
                                            menu = tile.key
                                            true
                                        }
                                    )
                            }
                            .pointerInput(tile.key, tile.width, tile.height, step, rowStep) {
                                detectDashboardGestures(
                                    onTap = { latestOpen(tile.key, tileItem(tile.key, latestAll)) },
                                    onDragStart = { p ->
                                        original = draft
                                        grab = p
                                        dragId = tile.key
                                        origin =
                                            Tiles.pack(draft).getValue(tile.key).let {
                                                Offset(it.x * step, it.y * rowStep)
                                            }
                                        delta = Offset.Zero
                                        pointer =
                                            Offset(
                                                viewport.left +
                                                    with(density) { 16.dp.toPx() } +
                                                    origin.x +
                                                    p.x,
                                                viewport.top + origin.y - scroll.value + p.y,
                                            )
                                        startScroll = scroll.value
                                        moved = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragCancel = {
                                        draft = original
                                        dragId = null
                                        grab = Offset.Zero
                                    },
                                    onDragEnd = {
                                        if (moved) {
                                            val valid =
                                                pointer.x >= viewport.left &&
                                                    pointer.x <= viewport.right &&
                                                    pointer.y >= viewport.top &&
                                                    pointer.y <= viewport.bottom
                                            if (valid) {
                                                draft =
                                                    Tiles.dropOrder(
                                                        original,
                                                        tile.key,
                                                        (origin.x + delta.x + grab.x) / step,
                                                        (origin.y +
                                                            delta.y +
                                                            grab.y +
                                                            scroll.value - startScroll) / rowStep,
                                                    )
                                                latestSave(draft)
                                            } else draft = original
                                        } else menu = tile.key
                                        dragId = null
                                        grab = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        delta += amount
                                        pointer += amount
                                        if (delta.getDistance() > 8f) moved = true
                                        if (moved)
                                            draft =
                                                Tiles.dropOrder(
                                                    original,
                                                    tile.key,
                                                    (origin.x + delta.x + grab.x) / step,
                                                    (origin.y + delta.y + grab.y + scroll.value -
                                                        startScroll) / rowStep,
                                                )
                                    },
                                )
                            }
                    ) {
                        DashboardTile(
                            tile,
                            all,
                            money,
                            onToggleAmounts,
                            dragId == null,
                            scope,
                            selectedMonth,
                        ) {
                            onOpen(tile.key, tileItem(tile.key, all))
                        }
                    }
                }
            }
            if (draft.isEmpty())
                EmptyState(Icons.Outlined.Dashboard, "ホームを整えましょう", "必要なタイルを選んで追加できます。", "タイルを追加") {
                    adding = true
                }
        }
    }
    if (menu != null) {
        val tile = draft.firstOrNull { it.key == menu }
        if (tile != null)
            ModalBottomSheet(onDismissRequest = { menu = null }) {
                Column(
                    Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        Tiles.names.getValue(tile.key),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Caption("サイズ変更")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2).forEach { (w, h) ->
                            AccentChip(
                                tile.width == w && tile.height == h,
                                {
                                    val changed =
                                        draft.map {
                                            if (it.key == tile.key) it.copy(width = w, height = h)
                                            else it
                                        }
                                    draft = changed
                                    onTiles(changed)
                                },
                                label = { Text("$w×$h") },
                            )
                        }
                    }
                    Caption("表示内容")
                    Row {
                        listOf("default" to "標準", "next" to "次の予定", "total" to "件数・合計").forEach {
                            (v, label) ->
                            AccentChip(
                                tile.variant == v,
                                {
                                    val changed =
                                        draft.map {
                                            if (it.key == tile.key) it.copy(variant = v) else it
                                        }
                                    draft = changed
                                    onTiles(changed)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            draft = draft.filter { it.key != tile.key }
                            onTiles(draft)
                            menu = null
                        }
                    ) {
                        Icon(Icons.Outlined.VisibilityOff, null)
                        Text("この画面から非表示")
                    }
                    Row {
                        TextButton(
                            onClick = {
                                val i = draft.indexOf(tile)
                                if (i > 0) {
                                    draft = Tiles.move(draft, tile.key, draft[i - 1].key)
                                    onTiles(draft)
                                }
                            }
                        ) {
                            Text("前へ")
                        }
                        TextButton(
                            onClick = {
                                val i = draft.indexOf(tile)
                                if (i < draft.lastIndex) {
                                    draft = Tiles.move(draft, tile.key, draft[i + 1].key)
                                    onTiles(draft)
                                }
                            }
                        ) {
                            Text("後ろへ")
                        }
                    }
                }
            }
    }
    if (adding)
        ModalBottomSheet(onDismissRequest = { adding = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 30.dp)
            ) {
                SectionTitle("表示するタイルを選択")
                Tiles.names
                    .filterKeys { it in allowedKeys }
                    .forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val exists = draft.any { it.key == key }
                                    draft =
                                        if (exists) draft.filter { it.key != key }
                                        else draft + Tile(key)
                                    onTiles(draft)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(tileIcon(key), null, Modifier.size(26.dp))
                            Text(label, Modifier.weight(1f).padding(start = 14.dp))
                            Checkbox(draft.any { it.key == key }, null)
                        }
                    }
                TextButton(
                    onClick = {
                        draft = if (scope == "balance") BalanceLayouts.defaults else Tiles.defaults
                        onTiles(draft)
                        adding = false
                    }
                ) {
                    Text("初期配置にリセット")
                }
            }
        }
}

internal fun tileItem(key: String, all: List<Item>): Item? =
    when (key) {
        "live",
        "venue" ->
            all.filter { it.kind == "live" && it["date"] >= LocalDate.now().toString() }
                .minByOrNull { it["date"] }
        "plan" -> all.firstOrNull { it.kind == "plan" }
        else ->
            Itinerary.events(all)
                .filter { e ->
                    when (key) {
                        "movement" -> e.type in Itinerary.transport
                        "hotel" -> e.type == "ホテル"
                        else -> true
                    }
                }
                .let { events -> Itinerary.priority(events, LocalDateTime.now(), "")?.source }
    }

@Composable
private fun DashboardTile(
    tile: Tile,
    all: List<Item>,
    money: (Long) -> String,
    onToggleAmounts: () -> Unit,
    enabled: Boolean,
    scope: String,
    selectedMonth: YearMonth,
    onOpen: () -> Unit,
) {
    val roomy = tile.width == 2 || tile.height == 2
    val big = tile.width == 2 && tile.height == 2
    val accent = DesignTokens.tileAccent(tile.key)
    Card(
        modifier =
            Modifier.fillMaxSize()
                .border(1.dp, accent.copy(alpha = .35f), MaterialTheme.shapes.medium),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.surfaceVariant,
                        accent,
                        .07f,
                    ),
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (tile.width == 1) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(tileIcon(tile.key), null, Modifier.size(22.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (scope == "balance" && tile.key == "subscription" && !roomy) "サブスク管理"
                    else Tiles.names.getValue(tile.key),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (tile.key) {
                "money" -> {
                    val totals = Ledger.totals(all)
                    val summary = Finance.month(all, selectedMonth)
                    val value =
                        when (tile.variant) {
                            "next" -> totals.projected
                            "total" -> summary.net
                            else -> totals.current
                        }
                    MoneyAmount(
                        if (tile.variant == "next") "balance:projected"
                        else if (tile.variant == "total") "net:$selectedMonth" else "balance",
                        value,
                        size = if (roomy) 30 else 21,
                        semanticSign = true,
                    )
                    if (tile.variant != "default")
                        Caption(if (tile.variant == "next") "クレジット反映後" else "選択月の収支")
                    if (roomy && tile.variant == "default") {
                        Caption("クレジット反映後残高")
                        MoneyAmount(
                            "balance:projected",
                            totals.projected,
                            size = 20,
                            semanticSign = true,
                        )
                    }
                    if (big) {
                        MoneyAmount("credit:unsettled", totals.outstanding, "未引落し（全期間）", 18)
                        Caption("実残高から未引落しカード利用だけを差し引いた参考残高です。")
                    }
                }
                "accounts" -> {
                    Ledger.totals(all).accounts.forEach { account ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                account.account.title,
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MoneyAmount(
                                "account:${account.account.id}",
                                account.current,
                                size = 18,
                                semanticSign = true,
                                modifier = Modifier.weight(1.5f),
                            )
                        }
                    }
                    if (big) MoneyAmount("balance", Ledger.totals(all).current, "全口座合計", 22, true)
                }
                "credit" -> {
                    MoneyAmount(
                        "credit:unsettled",
                        Ledger.totals(all).outstanding,
                        size = if (roomy) 26 else 20,
                    )
                    if (roomy) Caption("全期間の未引落し利用。予定日だけでは解消しません。")
                }
                "transactions" -> {
                    val summary = Finance.month(all, selectedMonth)
                    MoneyAmount("expense:$selectedMonth", summary.expense, "選択月の支出", 20)
                    if (roomy) MoneyAmount("income:$selectedMonth", summary.income, "選択月の収入", 20)
                }
                "deadlines" -> {
                    val list = Connections.deadlines(all).filterNot { it.complete }
                    Text("${list.size} 件", style = MaterialTheme.typography.titleLarge)
                    list.firstOrNull()?.let {
                        Caption(it.source.title)
                        Caption("${it.label} ${UiPolicy.time(it.at.toString())}")
                        if (it.at < LocalDateTime.now()) StatusPill("期限超過")
                    }
                }
                "subscription" -> {
                    if (scope == "balance" && !roomy) Unit
                    else {
                        MoneyAmount(
                            "subscription:forecast:$selectedMonth",
                            SubscriptionBilling.forecast(all, selectedMonth),
                            size = if (roomy) 26 else 20,
                        )
                        Caption("${selectedMonth.monthValue}月の請求予測")
                        if (roomy)
                            all.filter(SubscriptionBilling::active)
                                .mapNotNull { SubscriptionBilling.next(it) }
                                .minOrNull()
                                ?.let { Caption("次回 ${UiPolicy.shortDate(it.toString())}") }
                    }
                }
                "plan" -> {
                    val plans =
                        all.filter {
                            it.kind == "plan" &&
                                (tile.variant == "total" || it["adopted"] == "true")
                        }
                    TileValue(
                        if (tile.variant == "next")
                            plans.firstOrNull { it["adopted"] == "true" }?.title ?: "採用なし"
                        else if (tile.variant == "total")
                            "${plans.count{it["adopted"]=="true"}} 採用中"
                        else "${plans.size} プラン",
                        if (roomy) 26 else 21,
                    )
                    if (plans.isEmpty() || big || !roomy)
                        Caption(
                            if (plans.any { it["adopted"] == "true" }) "採用中のプランあり" else "候補を比較して計画"
                        )
                    if (roomy)
                        plans.take(if (big) 4 else 1).forEach {
                            Text(
                                it.title,
                                maxLines = if (big) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
                else -> {
                    val item = tileItem(tile.key, all)
                    if (tile.variant == "total")
                        TileValue(
                            "${if(tile.key in listOf("live","venue"))all.count{it.kind=="live"} else Itinerary.events(all).count{when(tile.key){"movement"->it.type in Itinerary.transport
"hotel"->it.type=="ホテル"
else->true}}} 件",
                            22,
                        )
                    else if (item == null) {
                        Caption("予定はまだありません")
                        if (roomy) Caption("タップして追加できます")
                    } else {
                        Text(
                            if (tile.variant == "next")
                                UiPolicy.date(item["date"].ifEmpty { item["start"] })
                            else if (tile.key == "venue") item["venue"].ifEmpty { item.title }
                            else item.title,
                            fontSize = if (big) 24.sp else 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (big) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (tile.variant == "next") Caption(item.title)
                        Caption(UiPolicy.date(item["date"].ifEmpty { item["start"] }))
                        if (roomy)
                            Caption(item["venue"].ifEmpty { "${item["from"]} → ${item["to"]}" })
                        if (big && item["memo"].isNotEmpty()) Caption(item["memo"])
                    }
                }
            }
        }
    }
}

@Composable
internal fun TileValue(text: String, size: Int = 28) {
    BoxWithConstraints {
        val scaled =
            minOf(size.toFloat(), maxWidth.value / (text.length.coerceAtLeast(1) * 0.64f))
                .coerceAtLeast(12f)
        Text(
            text,
            fontSize = scaled.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
