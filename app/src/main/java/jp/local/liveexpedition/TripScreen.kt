@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package jp.local.liveexpedition

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

@Composable
fun TripPlanner(
    model: AppModel,
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onDetail: (Item) -> Unit,
    initialCompare: Boolean = false,
) {
    TripPage(
        all,
        money,
        onEdit,
        onDetail,
        { c, p -> model.add(c, p) },
        { p -> model.save(p) },
        initialCompare,
    )
}

@Composable
fun TripPage(
    all: List<Item>,
    money: (Long) -> String,
    onEdit: (Item) -> Unit,
    onDetail: (Item) -> Unit,
    onAdd: (Item, Item) -> Unit,
    onSave: (Item) -> Unit,
    initialCompare: Boolean = false,
) {
    var comparing by
        androidx.compose.runtime.saveable.rememberSaveable(initialCompare) {
            mutableStateOf(initialCompare)
        }
    var dragging by remember { mutableStateOf<Item?>(null) }
    var addingCandidate by remember { mutableStateOf<Item?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<String?>(null) }
    var root by remember { mutableStateOf(Rect.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var dragWidth by remember { mutableFloatStateOf(0f) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("すべて") }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    val list = rememberLazyListState()
    val horizontal = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val plans = all.filter { it.kind == "plan" }
    val candidates =
        all.filter {
                it.kind == "candidate" &&
                    (query.isBlank() || it.data.values.any { v -> v.contains(query, true) }) &&
                    when (filter) {
                        "交通" -> it["type"] !in listOf("ホテル", "その他")
                        "宿泊" -> it["type"] == "ホテル"
                        "その他" -> it["type"] == "その他"
                        else -> true
                    }
            }
            .sortedBy { it["start"] }
    val latestPlans by rememberUpdatedState(plans)
    val latestAdd by rememberUpdatedState(onAdd)
    val latestSave by rememberUpdatedState(onSave)
    LaunchedEffect(dragging) {
        if (dragging != null)
            while (true) {
                val edge = with(density) { 48.dp.toPx() }
                if (pointer.x > root.right - edge) horizontal.scrollBy(10f)
                else if (pointer.x < root.left + edge) horizontal.scrollBy(-10f)
                if (pointer.y > root.bottom - edge) list.scrollBy(10f)
                else if (pointer.y < root.top + edge * 3) list.scrollBy(-8f)
                hover = targets.entries.firstOrNull { it.value.contains(pointer) }?.key
                delay(16)
            }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentChip(!comparing, { comparing = false }, label = { Text("候補一覧") })
                    AccentChip(comparing, { comparing = true }, label = { Text("プラン比較") })
                }
                IconButton(
                    onClick = {
                        onEdit(
                            Item(kind = if (comparing) "plan" else "candidate", data = emptyMap())
                        )
                    }
                ) {
                    Icon(Icons.Outlined.Add, if (comparing) "プランを追加" else "候補を追加")
                }
            }
            if (!comparing && dragging == null) {
                OutlinedTextField(
                    query,
                    { query = it.take(100) },
                    label = { Text("候補・経路を検索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("すべて", "交通", "宿泊", "その他").forEach { f ->
                        AccentChip(filter == f, { filter = f }, label = { Text(f) })
                    }
                }
            }
            if (dragging != null) {
                Caption("追加するプランへドロップ")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(horizontal),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (plans.map { it.id to it.title } + listOf("new" to "＋ 新規プラン")).forEach {
                        (id, label) ->
                        Surface(
                            Modifier.onGloballyPositioned { targets[id] = it.boundsInRoot() },
                            shape = MaterialTheme.shapes.small,
                            color =
                                if (hover == id) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                label,
                                Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(
                state = list,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (comparing) {
                    if (plans.isEmpty())
                        item {
                            EmptyState(
                                Icons.Outlined.Flight,
                                "プランを比較しましょう",
                                "候補を登録して、複数の組み合わせを検討できます。",
                                "プランを追加",
                            ) {
                                onEdit(Item(kind = "plan", data = emptyMap()))
                            }
                        }
                    items(plans, key = { it.id }) { plan ->
                        PlanComparisonCard(plan, all, money, { onDetail(plan) }) {
                            onSave(
                                plan.copy(
                                    data =
                                        plan.data +
                                            ("adopted" to (plan["adopted"] != "true").toString())
                                )
                            )
                        }
                    }
                    if (candidates.isNotEmpty()) item { SectionTitle("遠征候補") }
                    items(candidates, key = { "compare:${it.id}" }) { candidate ->
                        Panel(Modifier.fillMaxWidth(), onClick = { onDetail(candidate) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.title, fontWeight = FontWeight.SemiBold)
                                    Caption("${candidate["from"]} → ${candidate["to"]}")
                                    if (candidate["amount"].isEmpty()) StatusPill("料金未入力・要確認")
                                    else
                                        MoneyAmount(
                                            "amount:${candidate.id}",
                                            candidate.amount,
                                            size = 18,
                                        )
                                }
                                IconButton(onClick = { addingCandidate = candidate }) {
                                    Icon(Icons.Outlined.Add, "候補をプランに追加")
                                }
                            }
                        }
                    }
                } else {
                    if (candidates.isEmpty())
                        item {
                            EmptyState(
                                Icons.Outlined.Luggage,
                                "遠征の候補を集めましょう",
                                "交通・宿泊を登録し、長押しでプランへ追加します。",
                                "候補を追加",
                            ) {
                                onEdit(Item(kind = "candidate", data = emptyMap()))
                            }
                        }
                    items(candidates, key = { it.id }) { candidate ->
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
                                .pointerInput(candidate) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { p ->
                                            targets.clear()
                                            grab = p
                                            dragWidth = bounds.width
                                            dragging = candidate
                                            pointer = bounds.topLeft + p
                                            hover = null
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            hover = null
                                        },
                                        onDragEnd = {
                                            val target =
                                                targets.entries
                                                    .firstOrNull { it.value.contains(pointer) }
                                                    ?.key
                                            if (target == "new")
                                                latestSave(
                                                    Item(
                                                        kind = "plan",
                                                        data =
                                                            mapOf(
                                                                "title" to
                                                                    "プラン ${latestPlans.size+1}",
                                                                "members" to candidate.id,
                                                            ),
                                                    )
                                                )
                                            else
                                                latestPlans
                                                    .firstOrNull { it.id == target }
                                                    ?.let { latestAdd(candidate, it) }
                                            dragging = null
                                            hover = null
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            pointer += delta
                                            hover =
                                                targets.entries
                                                    .firstOrNull { it.value.contains(pointer) }
                                                    ?.key
                                        },
                                    )
                                }
                        ) {
                            CandidateCard(
                                candidate,
                                money,
                                expanded,
                                { expanded = !expanded },
                                { onDetail(candidate) },
                            )
                        }
                        if (expanded && plans.isNotEmpty())
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                plans.forEach { plan ->
                                    TextButton(onClick = { onAdd(candidate, plan) }) {
                                        Text("${plan.title}に追加")
                                    }
                                }
                            }
                    }
                }
            }
        }
        dragging?.let { item ->
            Surface(
                Modifier.offset {
                        IntOffset(
                            (pointer.x - root.left - grab.x).toInt(),
                            (pointer.y - root.top - grab.y).toInt(),
                        )
                    }
                    .width(with(density) { dragWidth.toDp() })
                    .zIndex(20f),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 16.dp,
            ) {
                CandidateCard(item, money, false, {}, {})
            }
        }
    }
    addingCandidate?.let { candidate ->
        ChoiceSheet(
            "追加先のプラン",
            plans.map { it.id to it.title } + listOf("new" to "新規プラン"),
            emptySet(),
            false,
            { keys ->
                val key = keys.firstOrNull()
                if (key == "new")
                    onSave(
                        Item(
                            kind = "plan",
                            data =
                                mapOf("title" to "プラン ${plans.size+1}", "members" to candidate.id),
                        )
                    )
                else plans.firstOrNull { it.id == key }?.let { onAdd(candidate, it) }
                addingCandidate = null
            },
            { addingCandidate = null },
        )
    }
}

@Composable
internal fun CandidateCard(
    candidate: Item,
    money: (Long) -> String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDetail: () -> Unit,
) {
    val legs = Itinerary.decode(candidate["segments"])
    Panel(Modifier.fillMaxWidth(), onClick = onExpand) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (candidate["type"]) {
                    "ホテル" -> Icons.Outlined.Bed
                    "バス" -> Icons.Outlined.DirectionsBus
                    "飛行機" -> Icons.Outlined.Flight
                    else -> Icons.Outlined.Train
                },
                null,
                Modifier.size(25.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(candidate.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Caption(
                    if (candidate["type"] == "ホテル") candidate["to"]
                    else "${candidate["from"]} → ${candidate["to"]}"
                )
            }
            StatusPill(candidate["status"].ifEmpty { "未予約" })
        }
        Caption("${UiPolicy.time(candidate["start"])} → ${UiPolicy.time(candidate["end"])}")
        if (candidate["amount"].isEmpty()) StatusPill("料金未入力・要確認")
        else MoneyAmount("amount:${candidate.id}", candidate.amount, size = 20)
        if (
            candidate["due"].isNotEmpty() &&
                candidate["paid"].isEmpty() &&
                java.time.LocalDateTime.parse(candidate["due"]) < java.time.LocalDateTime.now()
        )
            StatusPill("期限超過")
        if (expanded) {
            if (legs.isNotEmpty()) LegTimeline(legs, money)
            if (candidate["memo"].isNotEmpty()) Caption(candidate["memo"])
        }
        Row {
            if (legs.isNotEmpty())
                TextButton(onClick = onExpand) {
                    Text(if (expanded) "区間を閉じる" else "${legs.size}区間を表示")
                }
            TextButton(onClick = onDetail) { Text("詳細・編集・支払") }
        }
    }
}

@Composable
fun PlanComparisonCard(
    plan: Item,
    all: List<Item>,
    money: (Long) -> String,
    onDetail: () -> Unit,
    onAdopt: () -> Unit,
) {
    val t = Itinerary.totals(plan, all)
    val adopted = plan["adopted"] == "true"
    var expanded by
        androidx.compose.runtime.saveable.rememberSaveable(plan.id) { mutableStateOf(false) }
    val members = all.filter { it.id in plan["members"].split(',') }
    val unreserved = members.count { it["status"] != "予約済" }
    Panel(
        Modifier.fillMaxWidth()
            .then(
                if (adopted)
                    Modifier.border(
                        1.dp,
                        semanticColor(Meaning.POSITIVE),
                        MaterialTheme.shapes.medium,
                    )
                else Modifier
            ),
        onClick = onDetail,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(plan.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            StatusPill(if (adopted) "採用中" else "未採用")
            Icon(Icons.Outlined.ChevronRight, null)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (members.any { it["type"] !in listOf("ホテル", "その他") })
                Icon(
                    Icons.Outlined.Train,
                    null,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            if (members.any { it["type"] == "ホテル" })
                Icon(
                    Icons.Outlined.Bed,
                    null,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            MoneyAmount("plan:${plan.id}", t.total, size = 26, modifier = Modifier.weight(1f))
        }
        Caption("移動 ${t.travelMinutes/60}時間${t.travelMinutes%60}分 ・ 乗換 ${t.transfers}回")
        if (t.missingPrices > 0) StatusPill("料金未入力 ${t.missingPrices}件・要確認")
        if (unreserved > 0) StatusPill("未予約 $unreserved 件・要確認")
        Row {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "内訳を閉じる" else "内訳・注意を表示")
            }
            TextButton(onClick = onAdopt) { Text(if (adopted) "採用を解除" else "このプランを採用") }
        }
        if (expanded) {
            MoneyAmount("traffic:${plan.id}", t.traffic, "交通", 18)
            MoneyAmount("hotel:${plan.id}", t.hotel, "宿泊", 18)
            MoneyAmount("other:${plan.id}", t.other, "その他", 18)
            Caption("出発 ${t.start?.let(UiPolicy::time)?:"未登録"}")
            Caption("到着 ${t.end?.let(UiPolicy::time)?:"未登録"}")
            Caption("未入力項目 ${t.missingFields} / 料金未確定 ${t.tentativePrices}")
            Connections.linkedShows(plan, all).forEach {
                Caption("関連公演：${it.title}・${UiPolicy.shortDate(it["date"])}")
            }
            t.warnings.forEach { Caption("注意：$it") }
        }
    }
}
