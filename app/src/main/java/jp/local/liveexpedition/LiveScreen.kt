@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package jp.local.liveexpedition

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import kotlinx.coroutines.*

internal fun tourKey(item: Item) =
    listOf(item["artist"], item["tour"].ifBlank { item.title }).joinToString("\u001f")

internal fun ticketStatus(ticket: Item, now: LocalDateTime = LocalDateTime.now()): String =
    when {
        ticket["paid"].isNotEmpty() -> "支払済み"
        ticket["result"] in listOf("当選", "落選") -> ticket["result"]
        ticket["status"].contains("申込") && ticket["status"] != "未申込" -> "申込済み"
        ticket["start"].isNotEmpty() &&
            ticket["end"].isNotEmpty() &&
            now >= LocalDateTime.parse(ticket["start"]) &&
            now <= LocalDateTime.parse(ticket["end"]) -> "受付中"
        else -> ticket["status"].ifEmpty { "予定" }
    }

@Composable
internal fun SecureThumbnail(item: Item?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val image by
        produceState<android.graphics.Bitmap?>(initialValue = null, item?.get("image")) {
            if (item?.get("image").isNullOrEmpty()) value = null
            else
                value =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                val b = Images.read(ctx, item!!["image"])
                                try {
                                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                    BitmapFactory.decodeByteArray(b, 0, b.size, opts)
                                } finally {
                                    b.fill(0)
                                }
                            }
                            .getOrNull()
                    }
        }
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (image != null)
            Image(
                image!!.asImageBitmap(),
                "公演画像",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        else
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ConfirmationNumber, null, Modifier.size(32.dp))
            }
    }
}

@Composable
fun LiveScreen(
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onDetail: (Item) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf<String?>(null) }
    var tour by rememberSaveable { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
    var allShows by rememberSaveable { mutableStateOf(false) }
    val lives = all.filter { it.kind == "live" }
    val tickets = all.filter { it.kind == "ticket" }
    fun showDraft(): Item {
        val artistMaster = all.firstOrNull { it.kind == "artist" && it.title == artist }
        val tourMaster =
            all.firstOrNull { it.kind == "tour" && it.title == tour && it["artist"] == artist }
        return Item(
            kind = "live",
            data =
                buildMap {
                    artist?.let { put("artist", it) }
                    if (tour != null && tour != "単発公演") put("tour", tour!!)
                    artistMaster?.let { put("artistRef", it.id) }
                    tourMaster?.let { put("tourRef", it.id) }
                },
        )
    }
    BackHandler(artist != null) { if (tour != null) tour = null else artist = null }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (artist != null)
                IconButton(onClick = { if (tour != null) tour = null else artist = null }) {
                    Icon(Icons.Outlined.ChevronLeft, "上の階層へ戻る")
                }
            OutlinedTextField(
                query,
                { query = it.take(100) },
                label = { Text("アーティスト・公演・会場を検索") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = { add = true }) { Icon(Icons.Outlined.Add, "追加") }
        }
        if (artist == null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentChip(!allShows, { allShows = false }, label = { Text("アーティスト") })
                AccentChip(allShows, { allShows = true }, label = { Text("公演一覧") })
            }
        if (artist != null) SectionTitle(if (tour != null) "$artist / $tour" else artist!!)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            if (artist == null && !allShows) {
                val names =
                    Connections.artistNames(all).filter { name ->
                        name.contains(query, true) ||
                            lives.any {
                                it["artist"] == name &&
                                    it.data.values.any { v -> v.contains(query, true) }
                            }
                    }
                if (names.isEmpty())
                    item {
                        EmptyState(
                            Icons.Outlined.Person,
                            "アーティストを登録しましょう",
                            "アーティストからツアー・公演・受付へ進めます。",
                            "アーティストを追加",
                        ) {
                            onEdit(Item(kind = "artist", data = emptyMap()))
                        }
                    }
                items(names, key = { it }) { name ->
                    val master = all.firstOrNull { it.kind == "artist" && it.title == name }
                    val shows = lives.filter { it["artist"] == name }
                    val next =
                        shows
                            .filter { it["date"] >= LocalDate.now().toString() }
                            .minByOrNull { it["date"] }
                    Panel(
                        Modifier.fillMaxWidth(),
                        onClick = {
                            artist = name
                            query = ""
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SecureThumbnail(
                                master?.takeIf { it["image"].isNotEmpty() }
                                    ?: shows.firstOrNull { it["image"].isNotEmpty() },
                                Modifier.size(58.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Caption(
                                    next?.let {
                                        "次の公演：${UiPolicy.shortDate(it["date"])}・${it["venue"]}"
                                    } ?: "公演を追加して予定を管理"
                                )
                            }
                            IconButton(
                                onClick = {
                                    onDetail(
                                        master
                                            ?: Item(kind = "artist", data = mapOf("title" to name))
                                    )
                                }
                            ) {
                                Icon(Icons.Outlined.MoreHoriz, "アーティストの詳細・編集")
                            }
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                }
                val upcoming =
                    lives
                        .filter {
                            it["date"] >= LocalDate.now().toString() &&
                                (query.isBlank() ||
                                    it.data.values.any { v -> v.contains(query, true) })
                        }
                        .sortedBy { it["date"] }
                        .take(5)
                if (upcoming.isNotEmpty()) item { SectionTitle("近日の公演") }
                items(upcoming, key = { it.id }) { show ->
                    Panel(Modifier.fillMaxWidth(), onClick = { onDetail(show) }) {
                        Text("${show["artist"]}｜${show.title}", fontWeight = FontWeight.SemiBold)
                        Caption("${UiPolicy.shortDate(show["date"])}・${show["venue"]}")
                        tickets
                            .firstOrNull { it["parent"] == show.id }
                            ?.let { StatusPill(ticketStatus(it)) }
                    }
                }
                val applications =
                    tickets
                        .filter {
                            ticketStatus(it) == "申込済み" &&
                                it["paid"].isEmpty() &&
                                (query.isBlank() ||
                                    it.data.values.any { v -> v.contains(query, true) })
                        }
                        .sortedBy { it["end"].ifEmpty { it["due"] } }
                if (applications.isNotEmpty()) item { SectionTitle("申込中の受付") }
                items(applications, key = { "application:${it.id}" }) { ticket ->
                    Panel(Modifier.fillMaxWidth(), onClick = { onDetail(ticket) }) {
                        Text(ticket.title, fontWeight = FontWeight.SemiBold)
                        all.firstOrNull { it.id == ticket["parent"] }
                            ?.let { Caption("${it["artist"]}・${it.title}") }
                        StatusPill(ticketStatus(ticket))
                        if (ticket["end"].isNotEmpty())
                            Caption("申込締切 ${UiPolicy.date(ticket["end"])}")
                    }
                }
            } else if (artist != null && tour == null) {
                val tours = Connections.tours(artist!!, all).filter { it.contains(query, true) }
                if (tours.isEmpty())
                    item {
                        EmptyState(
                            Icons.Outlined.ConfirmationNumber,
                            "ツアー・イベントを追加",
                            "単発公演はツアー名を空欄で登録できます。",
                            "公演を追加",
                        ) {
                            onEdit(showDraft())
                        }
                    }
                items(tours, key = { it }) { name ->
                    val shows =
                        lives.filter {
                            it["artist"] == artist && it["tour"].ifEmpty { "単発公演" } == name
                        }
                    val next =
                        shows
                            .filter { it["date"] >= LocalDate.now().toString() }
                            .minByOrNull { it["date"] } ?: shows.maxByOrNull { it["date"] }
                    Panel(
                        Modifier.fillMaxWidth(),
                        onClick = {
                            tour = name
                            query = ""
                        },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SecureThumbnail(
                                shows.firstOrNull { it["image"].isNotEmpty() },
                                Modifier.size(64.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Caption("${shows.size}公演")
                                next?.let {
                                    Caption("${UiPolicy.shortDate(it["date"])}・${it["venue"]}")
                                }
                            }
                            all.firstOrNull {
                                    it.kind == "tour" && it.title == name && it["artist"] == artist
                                }
                                ?.let { master ->
                                    IconButton(onClick = { onDetail(master) }) {
                                        Icon(Icons.Outlined.MoreHoriz, "ツアーの詳細・編集")
                                    }
                                }
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                }
            } else {
                val shows =
                    lives
                        .filter {
                            (artist == null ||
                                (it["artist"] == artist &&
                                    it["tour"].ifEmpty { "単発公演" } == tour)) &&
                                (query.isBlank() ||
                                    it.data.values.any { v -> v.contains(query, true) })
                        }
                        .sortedBy { it["date"] }
                if (shows.isEmpty())
                    item {
                        EmptyState(Icons.Outlined.Event, "公演を追加", "同じ公演に複数の先行受付を登録できます。", "公演を追加") {
                            onEdit(showDraft())
                        }
                    }
                items(shows, key = { it.id }) { show ->
                    Panel(Modifier.fillMaxWidth(), onClick = { onDetail(show) }) {
                        Text(show.title, fontWeight = FontWeight.SemiBold)
                        Caption("${UiPolicy.date(show["date"])}・${show["venue"]}")
                        Caption(
                            "開場 ${show["start"].takeIf{it.isNotEmpty()}?.let(UiPolicy::time)?:"未入力"} / 開演 ${show["end"].takeIf{it.isNotEmpty()}?.let(UiPolicy::time)?:"未入力"}"
                        )
                        tickets
                            .filter { it["parent"] == show.id }
                            .forEach { ticket ->
                                HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { onDetail(ticket) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ticket.title)
                                        if (ticket["due"].isNotEmpty())
                                            Caption("支払期限 ${UiPolicy.time(ticket["due"])}")
                                    }
                                    StatusPill(ticketStatus(ticket))
                                    Icon(Icons.Outlined.ChevronRight, null)
                                }
                            }
                        TextButton(
                            onClick = {
                                onEdit(
                                    Item(
                                        kind = "ticket",
                                        data = defaults("ticket") + ("parent" to show.id),
                                    )
                                )
                            }
                        ) {
                            Text("＋ 先行・受付を追加")
                        }
                    }
                }
            }
        }
    }
    if (add)
        ChoiceSheet(
            "追加する内容",
            listOf("artist" to "アーティスト", "tour" to "ツアー・イベント", "live" to "公演・単発イベント"),
            emptySet(),
            false,
            { keys ->
                val kind = keys.first()
                add = false
                onEdit(
                    if (kind == "live") showDraft()
                    else
                        Item(
                            kind = kind,
                            data =
                                if (kind == "tour" && artist != null) mapOf("artist" to artist!!)
                                else emptyMap(),
                        )
                )
            },
            { add = false },
        )
}
