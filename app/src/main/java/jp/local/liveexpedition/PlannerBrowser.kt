package jp.local.liveexpedition

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlannerBrowser(
    all: List<Item>,
    now: LocalDateTime,
    pin: String,
    onPin: (String) -> Unit,
    onDetail: (Item) -> Unit,
    calendar: Boolean = false,
) {
    val ctx = LocalContext.current
    var dayText by rememberSaveable { mutableStateOf(now.toLocalDate().toString()) }
    val day = LocalDate.parse(dayText)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(day).toString()) }
    val month = YearMonth.parse(monthText)
    var selected by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val events = remember(all) { Connections.events(all) }
    val current =
        Itinerary.priority(
            events.filter { it.type !in listOf("期限", "公演（日付のみ）") },
            now,
            pin,
            selected,
        )
    val active = events.filter { it.end != null && it.start <= now && now < it.end }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (current != null && !calendar)
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Caption(
                        if (current.id == pin) "手動固定中"
                        else if (current.start <= now && current.end != null && now < current.end)
                            "現在の予定"
                        else "次の予定"
                    )
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Caption(
                        "${UiPolicy.time(current.start.toString())} → ${current.end?.let{UiPolicy.time(it.toString())}?:"終了未入力"}"
                    )
                    val target = if (current.start > now) current.start else current.end
                    if (target != null)
                        Text(
                            "${if(current.start>now)"開始予定まで"else "終了予定まで"} ${Duration.between(now,target).toMinutes()}分"
                        )
                    Caption("登録時刻からの推定です。運行状況・遅延は取得していません。")
                    Row {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "区間を閉じる" else "区間を展開")
                        }
                        TextButton(onClick = { onPin(if (pin == current.id) "" else current.id) }) {
                            Text(if (pin == current.id) "固定解除" else "この予定を固定")
                        }
                    }
                    if (expanded) {
                        val legs = Itinerary.decode(current.source["segments"])
                        if (legs.isNotEmpty()) LegTimeline(legs, { "料金は詳細で確認" })
                        TextButton(onClick = { onDetail(current.source) }) { Text("関連する詳細を開く") }
                    }
                    if (active.size > 1)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            active.forEach { event ->
                                AccentChip(
                                    event.id == current.id,
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
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { dayText = day.minusDays(1).toString() }) {
                    Icon(Icons.Outlined.ChevronLeft, "前日")
                }
                TextButton(
                    onClick = {
                        DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    dayText = LocalDate.of(y, m + 1, d).toString()
                                    monthText = YearMonth.of(y, m + 1).toString()
                                },
                                day.year,
                                day.monthValue - 1,
                                day.dayOfMonth,
                            )
                            .show()
                    }
                ) {
                    Text(UiPolicy.date(dayText))
                }
                IconButton(onClick = { dayText = day.plusDays(1).toString() }) {
                    Icon(Icons.Outlined.ChevronRight, "翌日")
                }
            }
        }
        if (calendar)
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                            Text("前月")
                        }
                        Text(UiPolicy.date(monthText))
                        TextButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                            Text("翌月")
                        }
                    }
                    Row {
                        listOf("月", "火", "水", "木", "金", "土", "日").forEach {
                            Text(it, Modifier.weight(1f))
                        }
                    }
                    val first =
                        month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
                    (0..5).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            (0..6).forEach { col ->
                                val d = first.plusDays((week * 7 + col).toLong())
                                val has =
                                    events.any {
                                        it.start.toLocalDate() <= d &&
                                            (it.end?.toLocalDate() ?: it.start.toLocalDate()) >= d
                                    }
                                Surface(
                                    Modifier.weight(1f).height(45.dp).clickable {
                                        dayText = d.toString()
                                    },
                                    color =
                                        if (d == day) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            d.dayOfMonth.toString(),
                                            color =
                                                if (YearMonth.from(d) == month)
                                                    MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (has)
                                            Text("●", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        val visible =
            events.filter {
                if (calendar)
                    it.start.toLocalDate() <= day &&
                        (it.end?.toLocalDate() ?: it.start.toLocalDate()) >= day
                else (it.end?.toLocalDate() ?: it.start.toLocalDate()) >= day
            }
        if (visible.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.EventAvailable,
                    "この日の予定はありません",
                    "遠征プランの採用、公演、申込・支払期限を登録すると表示します。",
                    "今日へ戻る",
                ) {
                    dayText = now.toLocalDate().toString()
                }
            }
        visible
            .groupBy { it.start.toLocalDate() }
            .forEach { (date, list) ->
                item { SectionTitle(UiPolicy.date(date.toString())) }
                items(list, key = { it.id }) { event ->
                    val running = event.end != null && event.start <= now && now < event.end
                    Panel(Modifier.fillMaxWidth(), onClick = { onDetail(event.source) }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                event.title,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (running) StatusPill("登録時刻内")
                            else if (event.type == "期限" && event.start < now) StatusPill("期限超過")
                        }
                        Caption(UiPolicy.time(event.start.toString()))
                        if (event.to.isNotEmpty()) Caption(event.to)
                    }
                }
            }
    }
}

@Composable
fun HomeHub(
    model: AppModel,
    all: List<Item>,
    money: (Long) -> String,
    tiles: List<Tile>,
    initial: Int,
    onOpen: (String, Item?) -> Unit,
    onDetail: (Item) -> Unit,
    onEdit: (Item) -> Unit,
) {
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, 2), pageCount = { 3 })
    LaunchedEffect(initial) { pager.scrollToPage(initial.coerceIn(0, 2)) }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val pin by model.pin.collectAsState(initial = "")
    val hidden by model.hidden.collectAsState(initial = true)
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("今日", "予定一覧", "カレンダー").forEachIndexed { i, label ->
                AccentChip(
                    pager.currentPage == i,
                    { scope.launch { pager.animateScrollToPage(i) } },
                    label = { Text(label) },
                )
            }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
            if (page == 0)
                DashboardScreen(
                    tiles,
                    all,
                    money,
                    model::tiles,
                    onOpen,
                    { model.bool("hidden", !hidden) },
                )
            else PlannerBrowser(all, now, pin, { model.text("pin", it) }, onDetail, page == 2)
        }
    }
}
