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
            val step = with(density) { (cell + gap).toPx() }
            val positions = Tiles.pack(draft)
            val rows = positions.values.maxOfOrNull { it.y + it.height } ?: 0
            Box(
                Modifier.fillMaxWidth()
                    .height(if (rows == 0) 120.dp else (cell + gap) * rows - gap + 16.dp)
            ) {
                draft.forEach { tile ->
                    val c = positions.getValue(tile.key)
                    val active = dragId == tile.key
                    val normal = Offset(c.x * step, c.y * step)
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
                                cell * tile.height + gap * (tile.height - 1),
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
                            .pointerInput(tile.key, tile.width, tile.height, step) {
                                detectDashboardGestures(
                                    onTap = { latestOpen(tile.key, tileItem(tile.key, latestAll)) },
                                    onDragStart = { p ->
                                        original = draft
                                        grab = p
                                        dragId = tile.key
                                        origin =
                                            Tiles.pack(draft).getValue(tile.key).let {
                                                Offset(it.x * step, it.y * step)
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
                                                            scroll.value - startScroll) / step,
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
                                                        startScroll) / step,
                                                )
                                    },
                                )
                            }
                    ) {
                        DashboardTile(tile, all, money, onToggleAmounts, dragId == null) {
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
                        Text("ホームから非表示")
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
                Tiles.names.forEach { (key, label) ->
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
                        draft = Tiles.defaults
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
    onOpen: () -> Unit,
) {
    val roomy = tile.width == 2 || tile.height == 2
    val big = tile.width == 2 && tile.height == 2
    Card(
        modifier = Modifier.fillMaxSize(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (tile.width == 1) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(tileIcon(tile.key), null, Modifier.size(if (roomy) 26.dp else 28.dp))
                if (tile.width == 2) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Tiles.names.getValue(tile.key),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            if (tile.width == 1)
                Text(
                    Tiles.names.getValue(tile.key),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            when (tile.key) {
                "money" -> {
                    val month = YearMonth.now().toString()
                    val tx = all.filter { it.kind == "transaction" && it["date"].startsWith(month) }
                    val income = tx.filter { it["type"] == "収入" }.sumOf { it.amount }
                    val expense = tx.filter { it["type"] == "支出" }.sumOf { it.amount }
                    val budget =
                        all.filter {
                                it.kind == "budget" &&
                                    it["month"] == month &&
                                    it["category"].isEmpty()
                            }
                            .sumOf { it.amount }
                    val value =
                        when (tile.variant) {
                            "total" -> income - expense
                            "next" -> budget - expense
                            else -> Rules.balance(all)
                        }
                    MoneyAmount(
                        if (tile.variant == "default") "balance" else "${tile.variant}:$month",
                        value,
                        size = if (big) 32 else if (tile.width == 2) 28 else 20,
                        semanticSign = true,
                    )
                    if (tile.variant != "default")
                        Caption(if (tile.variant == "total") "今月の収支差額" else "予算の残り")
                    if (roomy) {
                        Caption("収入 ${money(income)}　支出 ${money(expense)}")
                        if (tile.height == 2) Caption("現在残高 ${money(Rules.balance(all))}")
                        LinearProgressIndicator(
                            progress = {
                                if (budget > 0) (expense.toFloat() / budget).coerceIn(0f, 1f)
                                else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface,
                            trackColor = MaterialTheme.colorScheme.outline,
                        )
                        Caption(
                            if (budget > 0) "予算 ${money(budget)} / 残り ${money(budget-expense)}"
                            else "予算は残高画面から設定"
                        )
                    }
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
                    val subs = all.filter { it.kind == "subscription" && it["status"] == "契約中" }
                    val monthly =
                        subs.sumOf { if (it["cycle"] == "年額") it.amount / 12 else it.amount }
                    val next = subs.minByOrNull { it["date"] }
                    TileValue(
                        when (tile.variant) {
                            "total" -> money(Finance.annualSubscriptions(all))
                            "next" -> next?.title ?: "更新予定なし"
                            else -> money(monthly)
                        },
                        if (roomy) 24 else 20,
                    )
                    Caption(
                        if (tile.variant == "total") "年間換算"
                        else if (tile.variant == "next") "次の更新" else "月額換算"
                    )
                    if (next != null) Caption("次回 ${UiPolicy.shortDate(next["date"])}")
                    if (big)
                        Caption(
                            "年間換算 ${money(subs.sumOf{if(it["cycle"]=="年額")it.amount else it.amount*12})}"
                        )
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
                    Caption(if (plans.any { it["adopted"] == "true" }) "採用中のプランあり" else "候補を比較して計画")
                    if (roomy) plans.take(if (big) 4 else 2).forEach { Caption(it.title) }
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
                            maxLines = if (roomy) 3 else 1,
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
