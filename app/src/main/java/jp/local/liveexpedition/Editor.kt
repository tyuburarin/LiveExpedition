@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package jp.local.liveexpedition

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.time.*
import kotlinx.coroutines.launch

@Composable
internal fun FieldInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    options: List<String> = emptyList(),
    dateType: String = "",
    numeric: Boolean = false,
    enabled: Boolean = true,
) {
    val ctx = LocalContext.current
    if (options.isNotEmpty()) {
        SelectionField(label.substringBefore(" YYYY"), value, options, onChange)
        return
    }
    val pick = {
        val d = runCatching { LocalDate.parse(value.take(10)) }.getOrDefault(LocalDate.now())
        DatePickerDialog(
                ctx,
                { _, y, m, day ->
                    val date = LocalDate.of(y, m + 1, day)
                    if (dateType == "datetime") {
                        val t =
                            runCatching { LocalDateTime.parse(value).toLocalTime() }
                                .getOrDefault(LocalTime.of(12, 0))
                        TimePickerDialog(
                                ctx,
                                { _, h, min -> onChange(date.atTime(h, min).toString()) },
                                t.hour,
                                t.minute,
                                true,
                            )
                            .apply { setTitle("時刻を選択") }
                            .show()
                    } else
                        onChange(
                            if (dateType == "month") YearMonth.from(date).toString()
                            else date.toString()
                        )
                },
                d.year,
                d.monthValue - 1,
                d.dayOfMonth,
            )
            .apply { setTitle("日付を選択") }
            .show()
    }
    OutlinedTextField(
        if (dateType.isEmpty()) value else if (value.isEmpty()) "" else UiPolicy.date(value),
        onChange,
        label = { Text(label.substringBefore(" YYYY")) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = dateType.isNotEmpty(),
        enabled = enabled,
        keyboardOptions =
            KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        minLines = if (label == "メモ") 3 else 1,
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        trailingIcon = {
            if (dateType.isNotEmpty())
                IconButton(onClick = pick, enabled = enabled) {
                    Icon(Icons.Outlined.CalendarMonth, "日時を選択")
                }
        },
    )
}

internal fun draftLegs(raw: String): List<Leg> =
    runCatching {
            if (raw.isEmpty()) emptyList()
            else {
                val a = org.json.JSONArray(raw)
                require(a.length() <= 20)
                (0 until a.length()).map {
                    val j = a.getJSONObject(it)
                    Leg(
                        j.optString("mode", "電車"),
                        j.optString("from"),
                        j.optString("to"),
                        j.optString("start"),
                        j.optString("end"),
                        j.optString("fare"),
                        j.optString("stops"),
                    )
                }
            }
        }
        .getOrDefault(emptyList())

@Composable
internal fun EditDialog(
    item: Item,
    onDismiss: () -> Unit,
    all: List<Item> = emptyList(),
    onDraft: ((Item) -> Unit)? = null,
    onOcr: ((Item) -> Unit)? = null,
    ocrReport: OcrReport? = null,
    ocrBusy: Boolean = false,
    onConsumeOcr: () -> Unit = {},
    onSaveExtra: (suspend (Item) -> Unit)? = null,
    onDeleteExtra: ((Item) -> Unit)? = null,
    onSave: suspend (Item) -> Unit,
) {
    val values =
        remember(item.id) { mutableStateMapOf<String, String>().apply { putAll(item.data) } }
    var legs by remember(item.id) { mutableStateOf(draftLegs(item["segments"])) }
    var page by remember(item.id) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var categorySheet by remember { mutableStateOf(false) }
    var categoryEdit by remember { mutableStateOf<Item?>(null) }
    var categoryDelete by remember { mutableStateOf<Item?>(null) }
    var showLinks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun snapshot(): Item {
        val data = values.toMutableMap()
        if (item.kind == "candidate") {
            if (legs.isEmpty()) data.remove("segments")
            else data["segments"] = Itinerary.encode(legs)
        }
        return item.copy(data = data)
    }
    SideEffect { if (!busy) onDraft?.invoke(snapshot()) }
    LaunchedEffect(item["image"]) {
        if (item["image"].isNotEmpty()) values["image"] = item["image"]
    }
    val basic =
        when (item.kind) {
            "live" -> setOf("title", "artist", "tour")
            "ticket" -> setOf("title", "parent", "quantity", "amount", "fee")
            "candidate" ->
                setOf("title", "from", "to", "start", "end", "amount", "status", "priceState")
            else ->
                Catalog.kind(item.kind)
                    .fields
                    .map { it.key }
                    .filterNot { it in listOf("memo", "url", "method", "renew", "due") }
                    .toSet()
        }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "${Catalog.kind(item.kind).label}の${if(all.any{it.id==item.id})"編集"else "登録"}"
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !busy) {
                                Icon(Icons.Outlined.Close, "閉じる（下書き保存）")
                            }
                        },
                        actions = {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        try {
                                            busy = true
                                            val updated = Itinerary.normalize(snapshot())
                                            Rules.validate(updated)
                                            onSave(updated)
                                        } catch (e: Exception) {
                                            error =
                                                if (e is IllegalArgumentException)
                                                    e.message ?: "必須項目を確認してください"
                                                else "日時・金額・関連項目を確認してください"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) {
                                Text("保存")
                            }
                        },
                    )
                }
            ) { padding ->
                Column(
                    Modifier.padding(padding)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (item.kind in listOf("live", "ticket", "artist", "tour")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentChip(page == 0, { page = 0 }, label = { Text("基本情報") })
                            AccentChip(
                                page == 1,
                                { page = 1 },
                                label = { Text(if (item.kind == "live") "公演日時" else "日時・詳細") },
                            )
                            AccentChip(page == 2, { page = 2 }, label = { Text("画像・メモ") })
                        }
                        if (page == 0) {
                            if (onOcr != null)
                                OutlinedButton(
                                    enabled = !ocrBusy,
                                    onClick = {
                                        onDraft?.invoke(snapshot())
                                        onOcr(snapshot())
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.DocumentScanner, null)
                                    Text(if (ocrBusy) "端末内で読み取り中" else "画像を選んでオフラインOCR")
                                }
                            if (ocrBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            var pasteOpen by remember { mutableStateOf(false) }
                            TextButton(onClick = { pasteOpen = !pasteOpen }) { Text("告知文を貼り付けて入力") }
                            if (pasteOpen) {
                                OutlinedTextField(
                                    pasted,
                                    { pasted = it.take(8000) },
                                    label = { Text("告知文を貼り付け") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                )
                                TextButton(
                                    onClick = {
                                        val found = OcrFields.extract(pasted, item.kind)
                                        values.putAll(found)
                                        error = "${found.size}項目を反映しました。不明な値は補完していません。"
                                    }
                                ) {
                                    Text("抽出して入力欄に反映")
                                }
                            }
                        }
                    }
                    if (item.kind == "candidate") {
                        SelectionField(
                            "候補の種類",
                            values["type"].orEmpty(),
                            listOf("電車", "バス", "飛行機", "ホテル", "その他"),
                            { next ->
                                if (next != values["type"] && legs.isNotEmpty()) {
                                    error = "区間を削除してから種類を変更してください。"
                                } else values["type"] = next
                            },
                        )
                        if (legs.isNotEmpty()) Caption("複数区間の時刻・料金は自動集計します")
                    }
                    val schema =
                        Catalog.kind(item.kind)
                            .fields
                            .filterNot { it.key in listOf("artistRef", "tourRef") }
                            .filter { f ->
                                when (item.kind) {
                                    "live" ->
                                        when (page) {
                                            0 -> f.key in basic
                                            1 ->
                                                f.key in
                                                    listOf(
                                                        "date",
                                                        "venue",
                                                        "start",
                                                        "end",
                                                        "finish",
                                                    )
                                            else -> f.key in listOf("attendance", "url", "memo")
                                        }
                                    "ticket" ->
                                        when (page) {
                                            0 -> f.key in basic
                                            1 -> f.key !in basic && f.key !in listOf("memo", "url")
                                            else -> f.key in listOf("memo", "url")
                                        }
                                    "artist",
                                    "tour" -> if (page == 0) f.key in basic else f.key !in basic
                                    "candidate" ->
                                        f.key != "type" &&
                                            values["type"].orEmpty().isNotEmpty() &&
                                            (advanced || f.key in basic) &&
                                            !(values["type"] == "ホテル" &&
                                                f.key in listOf("from", "transfers", "direction"))
                                    else -> advanced || f.key in basic
                                }
                            }
                            .filterNot {
                                item.kind == "candidate" &&
                                    legs.isNotEmpty() &&
                                    it.key in
                                        listOf("from", "to", "start", "end", "amount", "transfers")
                            }
                    schema.forEach { f ->
                        when {
                            f.key == "parent" -> {
                                val parent = all.firstOrNull { it.id == values["parent"] }
                                var picker by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { picker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        parent?.let {
                                            "${it.title}・${UiPolicy.shortDate(it["date"])}"
                                        } ?: "公演を選択"
                                    )
                                }
                                if (picker)
                                    ChoiceSheet(
                                        "対象の公演",
                                        all.filter { it.kind == "live" }
                                            .map {
                                                it.id to
                                                    "${it.title}・${UiPolicy.shortDate(it["date"])}"
                                            },
                                        setOf(values["parent"].orEmpty()),
                                        false,
                                        { keys ->
                                            values["parent"] = keys.firstOrNull().orEmpty()
                                            picker = false
                                        },
                                        { picker = false },
                                    )
                            }
                            f.key == "category" -> {
                                OutlinedButton(
                                    onClick = { categorySheet = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(values[f.key].orEmpty().ifEmpty { "カテゴリを選択" })
                                }
                            }
                            f.key == "artist" -> {
                                SelectionField(
                                    "アーティスト",
                                    values[f.key].orEmpty(),
                                    Connections.artistNames(all),
                                    { name ->
                                        if (name != values[f.key]) {
                                            values.remove("tourRef")
                                            values.remove("tour")
                                        }
                                        values[f.key] = name
                                        val master =
                                            all.firstOrNull {
                                                it.kind == "artist" && it.title == name
                                            }
                                        if (master != null) values["artistRef"] = master.id
                                        else values.remove("artistRef")
                                    },
                                    true,
                                )
                            }
                            f.key == "tour" -> {
                                SelectionField(
                                    "ツアー・イベント（単発なら空欄）",
                                    values[f.key].orEmpty(),
                                    Connections.tours(values["artist"].orEmpty(), all).filter {
                                        it != "単発公演"
                                    },
                                    { name ->
                                        values[f.key] = name
                                        val master =
                                            all.firstOrNull {
                                                it.kind == "tour" &&
                                                    it.title == name &&
                                                    it["artist"] == values["artist"]
                                            }
                                        if (master != null) values["tourRef"] = master.id
                                        else values.remove("tourRef")
                                    },
                                    true,
                                )
                            }
                            else -> {
                                val options =
                                    when (f.key) {
                                        "type" ->
                                            when (item.kind) {
                                                "transaction" -> listOf("支出", "収入", "振替", "予定")
                                                "adjustment" -> listOf("増額", "減額")
                                                else -> emptyList()
                                            }
                                        "cycle" -> listOf("月額", "年額")
                                        "status" ->
                                            when (item.kind) {
                                                "ticket" -> listOf("未申込", "申込済み", "辞退")
                                                "subscription" -> listOf("契約中", "解約済")
                                                else -> listOf("検討中", "未予約", "予約済")
                                            }
                                        "result" -> listOf("未発表", "当選", "落選")
                                        "renew" -> listOf("はい", "いいえ")
                                        "attendance" -> listOf("参加予定", "参加済み", "不参加")
                                        "direction" -> listOf("往路", "復路", "片道")
                                        "priceState" -> listOf("確定", "概算", "未確定")
                                        "method" -> listOf("現金", "クレジットカード", "電子マネー", "銀行振込")
                                        else -> emptyList()
                                    }
                                val label =
                                    if (item.kind == "candidate" && values["type"] == "ホテル")
                                        when (f.key) {
                                            "start" -> "チェックイン"
                                            "end" -> "チェックアウト"
                                            "to" -> "宿泊先"
                                            else -> f.label
                                        }
                                    else f.label
                                FieldInput(
                                    label + if (f.required) " *" else "",
                                    values[f.key].orEmpty(),
                                    {
                                        values[f.key] = it.take(if (f.key == "memo") 8000 else 2048)
                                    },
                                    options,
                                    dateType =
                                        when (f.key) {
                                            "start",
                                            "end",
                                            "finish",
                                            "resultAt",
                                            "due" -> "datetime"
                                            "date" -> "date"
                                            "month" -> "month"
                                            else -> ""
                                        },
                                    numeric =
                                        f.key in listOf("amount", "fee", "quantity", "transfers"),
                                    enabled =
                                        !(item["paid"].isNotEmpty() &&
                                            f.key in listOf("amount", "fee")),
                                )
                            }
                        }
                    }
                    if (item.kind == "candidate" && values["type"] in Itinerary.transport + "複合") {
                        legs.forEachIndexed { n, l ->
                            Panel {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("区間${n+1}")
                                    TextButton(
                                        onClick = { legs = legs.filterIndexed { i, _ -> i != n } }
                                    ) {
                                        Text("削除")
                                    }
                                }
                                fun update(next: Leg) {
                                    legs = legs.mapIndexed { i, old -> if (i == n) next else old }
                                }
                                FieldInput(
                                    "交通手段",
                                    l.mode,
                                    { update(l.copy(mode = it)) },
                                    Itinerary.transport,
                                )
                                FieldInput("出発地点", l.from, { update(l.copy(from = it.take(256))) })
                                FieldInput("到着地点", l.to, { update(l.copy(to = it.take(256))) })
                                FieldInput(
                                    "出発日時",
                                    l.start,
                                    { update(l.copy(start = it)) },
                                    dateType = "datetime",
                                )
                                FieldInput(
                                    "到着日時",
                                    l.end,
                                    { update(l.copy(end = it)) },
                                    dateType = "datetime",
                                )
                                FieldInput(
                                    "区間料金（不明は空欄）",
                                    l.fare,
                                    { update(l.copy(fare = it.take(9))) },
                                    numeric = true,
                                )
                                FieldInput(
                                    "途中駅",
                                    l.stops,
                                    { update(l.copy(stops = it.take(1000))) },
                                )
                            }
                        }
                        OutlinedButton(
                            enabled = legs.size < 20,
                            onClick = {
                                if (legs.isEmpty() && values["start"].orEmpty().isNotEmpty())
                                    legs =
                                        listOf(
                                            Leg(
                                                values["type"].orEmpty().ifEmpty { "電車" },
                                                values["from"].orEmpty(),
                                                values["to"].orEmpty(),
                                                values["start"].orEmpty(),
                                                values["end"].orEmpty(),
                                                values["amount"].orEmpty(),
                                            )
                                        )
                                legs = legs + Leg()
                                values["type"] = "複合"
                            },
                        ) {
                            Text("＋ 乗り換え区間を追加")
                        }
                    }
                    if (item.kind == "plan") {
                        TextButton(onClick = { showLinks = true }) {
                            Text(
                                "関連する公演を選択（${values["liveRefs"].orEmpty().split(',').count{it.isNotEmpty()}}件）"
                            )
                        }
                        if (showLinks)
                            ChoiceSheet(
                                "関連公演（複数選択）",
                                all.filter { it.kind == "live" }
                                    .map {
                                        it.id to "${it.title}・${UiPolicy.shortDate(it["date"])}"
                                    },
                                values["liveRefs"]
                                    .orEmpty()
                                    .split(',')
                                    .filter(String::isNotEmpty)
                                    .toSet(),
                                true,
                                {
                                    values["liveRefs"] = it.joinToString(",")
                                    showLinks = false
                                },
                                { showLinks = false },
                            )
                    }
                    if (item.kind !in listOf("live", "ticket", "artist", "tour"))
                        TextButton(onClick = { advanced = !advanced }) {
                            Text(if (advanced) "補助項目を閉じる" else "メモ・支払方法などを追加")
                        }
                    if (page < 2 && item.kind in listOf("live", "ticket", "artist", "tour"))
                        Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                            Text("次へ")
                        }
                    if (values["image"].orEmpty().isNotEmpty())
                        SecureThumbnail(
                            item.copy(data = values.toMap()),
                            Modifier.fillMaxWidth().height(140.dp),
                        )
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                    Caption("入力途中の内容は端末内に暗号化して一時保存します。保存ボタンを押すまでは登録データに反映しません。")
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (ocrReport?.itemId == item.id) {
        var raw by remember(ocrReport) { mutableStateOf(ocrReport.text) }
        var extracted by
            remember(ocrReport) { mutableStateOf(OcrFields.extract(ocrReport.text, item.kind)) }
        AlertDialog(
            onDismissRequest = onConsumeOcr,
            title = { Text("読み取り結果を確認") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Caption("端末内OCR・認識の目安 ${ocrReport.confidence}%。誤読があります。確認してから反映してください。")
                    OutlinedTextField(
                        raw,
                        {
                            raw = it.take(8000)
                            extracted = OcrFields.extract(it, item.kind)
                        },
                        label = { Text("読み取ったテキストを修正") },
                        minLines = 4,
                    )
                    extracted.forEach { (key, v) ->
                        Caption(
                            "${Catalog.kind(item.kind).fields.firstOrNull{it.key==key}?.label?.substringBefore(" YYYY")?:key}：${if(key in listOf("date","start","end","due","resultAt"))UiPolicy.date(v)else v}"
                        )
                    }
                    if (extracted.isEmpty()) Caption("確実に抽出できる項目がありません。テキストを参照して手入力してください。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        values.putAll(extracted)
                        values["memo"] = raw
                        item["image"].takeIf { it.isNotEmpty() }?.let { values["image"] = it }
                        onConsumeOcr()
                    }
                ) {
                    Text("確認して入力欄へ反映")
                }
            },
            dismissButton = { TextButton(onClick = onConsumeOcr) { Text("反映しない") } },
        )
    }
    if (categorySheet)
        ModalBottomSheet(onDismissRequest = { categorySheet = false }) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("カテゴリを選択・管理")
                (all.filter { it.kind == "category" }.map { it.title } +
                        listOf("ライブ", "遠征", "サブスク", "生活費", "給与"))
                    .distinct()
                    .chunked(2)
                    .forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { name ->
                                AccentChip(
                                    values["category"] == name,
                                    {
                                        values["category"] = name
                                        categorySheet = false
                                    },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                if (onSaveExtra != null) {
                    TextButton(
                        onClick = {
                            categoryEdit = Item(kind = "category", data = emptyMap())
                            categorySheet = false
                        }
                    ) {
                        Text("＋ カテゴリを追加")
                    }
                    all.filter { it.kind == "category" }
                        .forEach { c ->
                            Row {
                                Text(c.title, Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        categoryEdit = c
                                        categorySheet = false
                                    }
                                ) {
                                    Text("編集")
                                }
                                if (onDeleteExtra != null)
                                    TextButton(onClick = { categoryDelete = c }) { Text("削除") }
                            }
                        }
                }
            }
        }
    categoryEdit?.let { c ->
        EditDialog(c, { categoryEdit = null }, all) { updated ->
            onSaveExtra?.invoke(updated)
            values["category"] = updated.title
            categoryEdit = null
        }
    }
    categoryDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { categoryDelete = null },
            title = { Text("カテゴリを削除しますか？") },
            text = { Text("過去の取引に記録されたカテゴリ名は残します。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExtra?.invoke(c)
                        categoryDelete = null
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = { TextButton(onClick = { categoryDelete = null }) { Text("キャンセル") } },
        )
    }
}
