@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.local.liveexpedition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppScreen(model: AppModel, onSecure: (Boolean) -> Unit) {
    val all by model.items.collectAsState()
    val hidden by model.hidden.collectAsState(initial = true)
    val masks by model.moneyMasks.collectAsState(initial = emptyMap())
    val secure by model.secure.collectAsState(initial = true)
    val tiles by model.tiles.collectAsState(initial = Tiles.defaults)
    val message by model.message.collectAsState()
    val editing by model.editing.collectAsState()
    val draft by model.draft.collectAsState()
    val ocr by model.ocr.collectAsState()
    val ocrBusy by model.ocrBusy.collectAsState()
    val swipe by model.screenSwipe.collectAsState(initial = false)
    val slide by model.tabSlide.collectAsState(initial = true)
    val initialPage by model.homePage.collectAsState(initial = 0)
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Item?>(null) }
    var discard by remember { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val external = LocalExternalActions.current
    val money: (Long) -> String = { if (hidden) "••••" else "¥%,d".format(it) }
    val open: (String, Item?) -> Unit = { key, item ->
        when (key) {
            "money" -> {
                tab = 3
                route = ""
            }
            "subscription" -> {
                tab = 3
                route = "subscription"
            }
            "plan" -> {
                tab = 2
                route = "plans"
            }
            "live",
            "venue" ->
                if (item != null) detail = item
                else {
                    tab = 1
                    route = ""
                }
            "deadlines" -> route = "deadlines"
            else ->
                if (item != null) detail = item
                else model.beginEdit(Item(kind = "schedule", data = emptyMap()))
        }
    }
    BackHandler(route.isNotEmpty()) { route = "" }
    LaunchedEffect(secure) { onSecure(secure) }
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            val result =
                snack.showSnackbar(
                    message,
                    actionLabel = if (model.canUndo(message)) "元に戻す" else null,
                )
            if (result == SnackbarResult.ActionPerformed) model.undo()
            if (model.message.value == message) model.message.value = ""
        }
    }
    CompositionLocalProvider(
        LocalMoneyVisibility provides MoneyVisibility(hidden, masks, model::mask)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (route) {
                                "settings" -> "設定"
                                "subscription" -> "サブスク管理"
                                "deadlines" -> "申込・支払期限"
                                else -> listOf("ホーム", "ライブ", "遠征", "残高")[tab]
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        if (route.isNotEmpty())
                            IconButton(onClick = { route = "" }) {
                                Icon(Icons.Outlined.ArrowBack, "戻る")
                            }
                    },
                    actions = {
                        if (route != "settings")
                            IconButton(onClick = { route = "settings" }) {
                                Icon(Icons.Outlined.Settings, "設定")
                            }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                )
            },
            bottomBar = {
                ExpeditionTabs(
                    tab,
                    {
                        tab = it
                        route = ""
                    },
                    slide,
                )
            },
            snackbarHost = { SnackbarHost(snack) },
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .padding(padding)
                    .tabSwipe(swipe && route.isEmpty() && editing == null, tab, { tab = it })
            ) {
                if (draft != null && editing == null && route.isEmpty()) {
                    Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row {
                            Text("入力途中の記録があります", Modifier.weight(1f))
                            TextButton(onClick = model::resumeDraft) { Text("再開") }
                            TextButton(onClick = { discard = true }) { Text("破棄") }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    holder.SaveableStateProvider(tab) {
                        when {
                            route == "settings" -> SettingsScreen(model)
                            route == "subscription" ->
                                SubscriptionScreen(all, money, model::beginEdit, { detail = it })
                            route == "deadlines" -> DeadlineScreen(all, { detail = it })
                            tab == 0 ->
                                HomeHub(
                                    model,
                                    all,
                                    money,
                                    tiles,
                                    initialPage,
                                    open,
                                    { detail = it },
                                    model::beginEdit,
                                )
                            tab == 1 -> LiveScreen(all, money, model::beginEdit, { detail = it })
                            tab == 2 ->
                                TripPlanner(
                                    model,
                                    all,
                                    money,
                                    model::beginEdit,
                                    { detail = it },
                                    route == "plans",
                                )
                            else ->
                                FinanceDashboard(
                                    all,
                                    money,
                                    model::beginEdit,
                                    { detail = it },
                                    { route = "subscription" },
                                )
                        }
                    }
                }
            }
        }
        editing?.let { item ->
            EditDialog(
                item,
                { model.editing.value = null },
                all,
                onDraft = model::saveDraft,
                onOcr = external.ocr,
                ocrReport = ocr,
                ocrBusy = ocrBusy,
                onConsumeOcr = { model.ocr.value = null },
                onSaveExtra = model::saveChecked,
                onDeleteExtra = model::delete,
            ) { updated ->
                model.saveChecked(updated)
                model.finishDraft()
            }
        }
        detail?.let { original ->
            val item = all.firstOrNull { it.id == original.id } ?: original
            DetailDialog(
                item,
                all,
                model,
                money,
                { detail = null },
                {
                    model.beginEdit(it)
                    detail = null
                },
                { detail = it },
            )
        }
        if (discard)
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text("下書きを破棄しますか？") },
                text = { Text("登録済みのデータは変更せず、一時保存した入力だけを削除します。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.discardDraft()
                            discard = false
                        }
                    ) {
                        Text("破棄")
                    }
                },
                dismissButton = { TextButton(onClick = { discard = false }) { Text("残す") } },
            )
    }
}

@Composable
fun DeadlineScreen(all: List<Item>, onDetail: (Item) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        val deadlines = Connections.deadlines(all).filterNot { it.complete }
        if (deadlines.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.EventAvailable,
                    "確認が必要な期限はありません",
                    "チケット・遠征候補・サブスクの期限を登録できます。",
                    "閉じる",
                ) {}
            }
        deadlines.forEach { d ->
            item(key = d.id) {
                Panel(Modifier.fillMaxWidth(), onClick = { onDetail(d.source) }) {
                    Text(d.source.title, fontWeight = FontWeight.SemiBold)
                    Caption("${d.label}・${UiPolicy.date(d.at.toString())}")
                    StatusPill(
                        if (d.at < java.time.LocalDateTime.now()) "期限超過"
                        else if (d.at < java.time.LocalDateTime.now().plusDays(3)) "期限間近" else "予定"
                    )
                }
            }
        }
    }
}
