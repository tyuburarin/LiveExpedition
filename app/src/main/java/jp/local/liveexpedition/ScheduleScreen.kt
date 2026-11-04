@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package jp.local.liveexpedition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SchedulePage(
    events: List<ScheduleEvent>,
    now: LocalDateTime,
    pin: String,
    onPin: (String) -> Unit,
    onDetail: (Item) -> Unit,
    onAdd: () -> Unit,
) {
    var selected by remember { mutableStateOf("") }
    var day by remember(now.toLocalDate()) { mutableStateOf(now.toLocalDate()) }
    var expanded by remember { mutableStateOf(false) }
    val current = Itinerary.priority(events, now, pin, selected)
    val overlapping = events.filter { it.start <= now && it.end != null && now < it.end }
    val next = events.firstOrNull { it.start > now && it.id != current?.id }
    val daily =
        events.filter {
            it.start.toLocalDate() <= day &&
                (it.end?.toLocalDate() ?: it.start.toLocalDate()) >= day
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Caption("登録時刻表による予定表示・運行状況は未取得")
                }
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "予定を追加") }
            }
        }
        if (current != null)
            item {
                val running = current.start <= now && current.end != null && now < current.end
                val remaining =
                    Duration.between(now, if (running) current.end else current.start).toMinutes()
                Panel(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Caption(
                            when {
                                current.id == pin -> "手動固定した予定"
                                running -> "現在の予定"
                                current.start > now -> "次の予定"
                                else -> "終了時刻が未入力の予定"
                            }
                        )
                        Caption(now.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            when (current.type) {
                                "電車" -> Icons.Outlined.Train
                                "バス" -> Icons.Outlined.DirectionsBus
                                "飛行機" -> Icons.Outlined.Flight
                                "ホテル" -> Icons.Outlined.Bed
                                "ライブ" -> Icons.Outlined.ConfirmationNumber
                                else -> Icons.Outlined.Event
                            },
                            null,
                            Modifier.size(36.dp),
                        )
                        Column {
                            Text(
                                current.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (current.from.isNotBlank() || current.to.isNotBlank())
                                Caption("${current.from} → ${current.to}")
                            Caption(
                                "${current.start.format(DateTimeFormatter.ofPattern("M/d HH:mm"))} → ${current.end?.format(DateTimeFormatter.ofPattern("M/d HH:mm"))?:"終了未入力"}"
                            )
                        }
                    }
                    if (current.end != null) {
                        val duration = Duration.between(current.start, current.end).toMillis()
                        LinearProgressIndicator(
                            progress = {
                                if (duration > 0)
                                    (Duration.between(current.start, now).toMillis().toFloat() /
                                            duration)
                                        .coerceIn(0f, 1f)
                                else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        if (remaining >= 0) "${if(running)"終了予定まで" else "開始まで"} ${remaining}分"
                        else "予定時刻を${-remaining}分経過（遅延状況は未取得）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (next != null && current.type in Itinerary.transport)
                        Caption(
                            "次の行動：${next.from.ifEmpty{next.to}} · ${next.start.format(DateTimeFormatter.ofPattern("HH:mm"))} ${next.title}"
                        )
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "区間情報を閉じる" else "途中駅・乗り換えを展開")
                    }
                    if (expanded) {
                        val legs = Itinerary.decode(current.source["segments"])
                        if (legs.isEmpty())
                            Caption(current.source["memo"].ifEmpty { "途中駅の登録はありません" })
                        else LegTimeline(legs, { "料金は候補詳細で確認" })
                        TextButton(onClick = { onDetail(current.source) }) { Text("詳細・編集を開く") }
                    }
                    TextButton(onClick = { onPin(if (pin == current.id) "" else current.id) }) {
                        Icon(Icons.Outlined.PushPin, null)
                        Text(if (pin == current.id) "固定を解除" else "遅延時などにこの予定を固定")
                    }
                }
            }
        if (overlapping.size > 1)
            item {
                Column {
                    Caption("重複する予定 ${overlapping.size}件 · 表示を切り替えられます")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        overlapping.forEach { event ->
                            AccentChip(
                                current?.id == event.id,
                                {
                                    selected = event.id
                                    if (pin.isNotEmpty()) onPin("")
                                },
                                label = { Text(event.title) },
                            )
                        }
                    }
                }
            }
        if (next != null)
            item {
                SectionTitle("次の行動")
                Panel(Modifier.fillMaxWidth(), onClick = { onDetail(next.source) }) {
                    Text(next.title, fontWeight = FontWeight.SemiBold)
                    Caption(
                        "${next.start.format(DateTimeFormatter.ofPattern("M/d HH:mm"))} · ${next.to}"
                    )
                }
            }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { day = day.minusDays(1) }) {
                    Icon(Icons.Outlined.ChevronLeft, "前日")
                }
                Text(
                    day.format(DateTimeFormatter.ofPattern("M月d日（E）の行程", Locale.JAPAN)),
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { day = day.plusDays(1) }) {
                    Icon(Icons.Outlined.ChevronRight, "翌日")
                }
            }
        }
        if (daily.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.CalendarMonth,
                    "この日の予定はありません",
                    "遠征プランを採用するか、予定を追加しましょう。",
                    "予定を追加",
                    onAdd,
                )
            }
        items(daily, key = { it.id }) { e ->
            Panel(Modifier.fillMaxWidth(), onClick = { onDetail(e.source) }) {
                Row {
                    Text(
                        e.start.format(DateTimeFormatter.ofPattern("HH:mm")),
                        Modifier.width(58.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column {
                        Text(e.title, fontWeight = FontWeight.Medium)
                        Caption("${e.from} → ${e.to}")
                        Caption(
                            e.end?.let {
                                "終了 ${it.format(DateTimeFormatter.ofPattern("M/d HH:mm"))}"
                            } ?: "終了時刻未入力"
                        )
                    }
                }
                TextButton(onClick = { onPin(e.id) }) { Text("この予定を固定") }
            }
        }
    }
}

@Composable
fun ScheduleScreen(model: AppModel, all: List<Item>, onDetail: (Item) -> Unit, onAdd: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val pin by model.pin.collectAsState(initial = "")
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }
    SchedulePage(Itinerary.events(all), now, pin, { model.text("pin", it) }, onDetail, onAdd)
}

@Composable
fun LegTimeline(legs: List<Leg>, money: (Long) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        legs.forEachIndexed { n, l ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${n+1}", fontWeight = FontWeight.Bold)
                Column {
                    Text("${l.mode}｜${l.from} → ${l.to}", fontWeight = FontWeight.SemiBold)
                    Caption("${UiPolicy.time(l.start)} → ${UiPolicy.time(l.end)}")
                    if (l.stops.isNotEmpty()) Caption("途中駅：${l.stops}")
                    Caption(if (l.fare.isEmpty()) "料金未入力" else money(l.fare.toLong()))
                    if (n < legs.lastIndex)
                        Caption(
                            "乗り換え ${Duration.between(LocalDateTime.parse(l.end),LocalDateTime.parse(legs[n+1].start)).toMinutes()}分 · 次の出発 ${legs[n+1].start.substringAfter('T')}"
                        )
                }
            }
        }
        Itinerary.connectionWarnings(legs).forEach { Caption("注意：$it") }
    }
}
