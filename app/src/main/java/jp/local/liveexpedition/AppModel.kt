package jp.local.liveexpedition

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

class AppModel(app: Application) : AndroidViewModel(app) {
    private val ctx
        get() = getApplication<Application>().applicationContext

    private var database: VaultDatabase? = null
    private var repo: Repository? = null
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items = _items.asStateFlow()
    val message = MutableStateFlow("")
    val editing = MutableStateFlow<Item?>(null)
    val draft = MutableStateFlow<Item?>(null)
    val ocr = MutableStateFlow<OcrReport?>(null)
    val ocrBusy = MutableStateFlow(false)
    val theme =
        ctx.settings.data.map {
            it[stringPreferencesKey("theme")]
                ?: if (it[booleanPreferencesKey("dark")] == false) "light" else "charcoal"
        }
    val accent = ctx.settings.data.map { it[stringPreferencesKey("accent")] ?: "blue" }
    val lockEnabled = ctx.settings.data.map { it[booleanPreferencesKey("lock_enabled")] ?: true }
    val screenSwipe = ctx.settings.data.map { it[booleanPreferencesKey("screen_swipe")] ?: false }
    val tabSlide = ctx.settings.data.map { it[booleanPreferencesKey("tab_slide")] ?: true }
    val homePage =
        ctx.settings.data.map {
            it[stringPreferencesKey("home_page")]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
        }
    val moneyMasks =
        ctx.settings.data.map { p ->
            p.asMap()
                .filterKeys { it.name.startsWith("mask:") }
                .mapKeys { it.key.name.removePrefix("mask:") }
                .mapValues { it.value as? Boolean ?: true }
        }

    fun mask(key: String, value: Boolean) {
        require(key.length <= 100)
        bool("mask:$key", value)
    }

    private var draftJob: Job? = null
    private var undoAction: (suspend () -> Unit)? = null
    private var undoLabel = ""

    fun canUndo(text: String) = undoAction != null && text == undoLabel

    private fun offerUndo(label: String, action: suspend () -> Unit) {
        undoAction = action
        undoLabel = label
        message.value = label
    }

    fun undo() {
        val action = undoAction ?: return
        undoAction = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                message.value = "操作を元に戻しました"
            } catch (e: Exception) {
                message.value = e.message?.take(120) ?: "元に戻せませんでした。記録を確認してください"
            }
        }
    }

    fun saveDraft(item: Item) {
        if (draft.value == item) return
        editing.value = item
        draft.value = item
        draftJob?.cancel()
        draftJob =
            viewModelScope.launch {
                delay(300)
                try {
                    withContext(Dispatchers.IO) { Drafts.save(ctx, item) }
                } catch (e: Exception) {
                    message.value = "一時保存できませんでした。空き容量を確認してください"
                }
            }
    }

    suspend fun finishDraft() {
        draftJob?.cancel()
        withContext(Dispatchers.IO) { Drafts.clear(ctx) }
        draft.value = null
        editing.value = null
        ocr.value = null
    }

    fun discardDraft() {
        viewModelScope.launch { finishDraft() }
    }

    fun beginEdit(item: Item) {
        editing.value = if (draft.value?.id == item.id) draft.value else item
    }

    fun resumeDraft() {
        editing.value = draft.value
    }

    fun duplicate(item: Item) {
        val copied =
            item.copy(
                id = java.util.UUID.randomUUID().toString(),
                data =
                    item.data.filterKeys {
                        it !in listOf("paid", "payments", "billingDates") + Catalog.reminderKeys
                    } + mapOf("title" to "${item.title}（コピー）"),
            )
        beginEdit(copied)
    }

    val hidden = ctx.settings.data.map { it[booleanPreferencesKey("hidden")] ?: true }
    val dark = ctx.settings.data.map { it[booleanPreferencesKey("dark")] ?: true }
    val secure = ctx.settings.data.map { it[booleanPreferencesKey("secure")] ?: true }
    val layout =
        ctx.settings.data.map {
            it[stringPreferencesKey("tiles")] ?: "money,live,plan,subscription,recent"
        }
    val wide = ctx.settings.data.map { it[stringPreferencesKey("wide")] ?: "money" }
    val pin = ctx.settings.data.map { it[stringPreferencesKey("pin")] ?: "" }
    val tiles =
        ctx.settings.data.map {
            Tiles.decode(
                it[stringPreferencesKey("dashboard_v2")],
                it[stringPreferencesKey("tiles")],
                it[stringPreferencesKey("wide")],
            )
        }

    fun tiles(value: List<Tile>) {
        val encoded = Tiles.encode(value)
        viewModelScope.launch {
            val prefs = ctx.settings.data.first()
            val previous =
                Tiles.decode(
                    prefs[stringPreferencesKey("dashboard_v2")],
                    prefs[stringPreferencesKey("tiles")],
                    prefs[stringPreferencesKey("wide")],
                )
            ctx.settings.edit { it[stringPreferencesKey("dashboard_v2")] = encoded }
            offerUndo("ホーム配置を保存しました") {
                ctx.settings.edit {
                    it[stringPreferencesKey("dashboard_v2")] = Tiles.encode(previous)
                }
            }
        }
    }

    suspend fun open(): Boolean =
        withContext(Dispatchers.IO) {
            if (repo != null) return@withContext true
            try {
                val db = VaultDatabase.create(ctx)
                database = db
                repo = Repository(db)
                try {
                    draft.value = Drafts.read(ctx)
                } catch (e: Exception) {
                    message.value = "一時保存を読み込めませんでした。元データは変更していません"
                }
                viewModelScope.launch {
                    repo!!
                        .items
                        .catch { message.value = "データの読み取りに失敗しました。" }
                        .collect { rows ->
                            _items.value = rows
                            withContext(Dispatchers.IO) {
                                rows.forEach { item ->
                                    Catalog.reminderKeys.forEach { key ->
                                        if (item[key].isNotEmpty())
                                            runCatching {
                                                Reminders.set(
                                                    ctx,
                                                    "${item.id}.$key",
                                                    java.time.LocalDateTime.parse(item[key]),
                                                )
                                            }
                                    }
                                }
                            }
                        }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    fun bool(key: String, value: Boolean) {
        viewModelScope.launch {
            ctx.settings.edit { prefs ->
                prefs[booleanPreferencesKey(key)] = value
                if (key == "hidden")
                    prefs
                        .asMap()
                        .keys
                        .filter { it.name.startsWith("mask:") }
                        .forEach { prefs.remove(it) }
            }
        }
    }

    fun text(key: String, value: String) {
        viewModelScope.launch { ctx.settings.edit { it[stringPreferencesKey(key)] = value } }
    }

    fun run(action: suspend Repository.() -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { requireNotNull(repo).action() }
            } catch (e: Exception) {
                message.value =
                    if (e is IllegalArgumentException) (e.message ?: "入力を確認してください").take(150)
                    else "処理に失敗しました。データは削除していません。"
            }
        }
    }

    fun save(item: Item) = run { save(item) }

    suspend fun saveChecked(item: Item) =
        withContext(Dispatchers.IO) { requireNotNull(repo).save(item) }

    fun delete(item: Item) = run {
        delete(item)
        Catalog.reminderKeys.forEach { Reminders.cancel(ctx, "${item.id}.$it") }
        offerUndo("記録を削除しました") { requireNotNull(repo).undoDelete(item) }
    }

    fun recognize(item: Item, uri: Uri) {
        if (ocrBusy.value) return
        ocrBusy.value = true
        viewModelScope.launch {
            try {
                val report =
                    withContext(Dispatchers.IO) {
                        val id = Images.import(ctx, uri)
                        val updated =
                            (editing.value?.takeIf { it.id == item.id } ?: item).let {
                                it.copy(data = it.data + ("image" to id))
                            }
                        editing.value = updated
                        draft.value = updated
                        Drafts.save(ctx, updated)
                        val bytes = Images.read(ctx, id)
                        val bitmap =
                            try {
                                requireNotNull(
                                    android.graphics.BitmapFactory.decodeByteArray(
                                        bytes,
                                        0,
                                        bytes.size,
                                    )
                                )
                            } finally {
                                bytes.fill(0)
                            }
                        try {
                            val (text, confidence) = OfflineOcr.recognize(ctx, bitmap)
                            OcrReport(item.id, text, OcrFields.extract(text, item.kind), confidence)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                ocr.value = report
            } catch (e: LinkageError) {
                message.value = "この端末ではOCRエンジンを起動できません。画像は一時保存されています"
            } catch (e: Exception) {
                message.value = "画像を読み取れませんでした。形式・空き容量を確認し、手入力してください"
            } finally {
                ocrBusy.value = false
            }
        }
    }

    fun pay(item: Item, date: LocalDate) = run { pay(item.id, date) }

    fun nextBill(item: Item) = run { nextBill(item.id) }

    fun add(candidate: Item, plan: Item) = run {
        addToPlan(candidate, plan)
        message.value = "プランに候補を追加しました"
    }

    suspend fun export(pass: CharArray): ByteArray =
        withContext(Dispatchers.IO) {
            val r = requireNotNull(repo)
            val plainData = r.snapshot()
            val images = JSONObject()
            try {
                val rows = JSONObject(plainData.toString(Charsets.UTF_8)).getJSONArray("records")
                require(rows.length() <= 10000)
                val ids =
                    (0 until rows.length())
                        .map { rows.getJSONObject(it).getJSONObject("data").optString("image") }
                        .filter(String::isNotBlank)
                        .distinct()
                require(ids.size <= 100) { "バックアップの画像上限は100枚です" }
                var total = plainData.size.toLong()
                ids.forEach { id ->
                    val b = Images.read(ctx, id)
                    try {
                        total += b.size * 4L / 3L + 100
                        require(total < Crypto.MAX_BACKUP - 128)
                        images.put(id, Base64.encodeToString(b, Base64.NO_WRAP))
                    } finally {
                        b.fill(0)
                    }
                }
                val plain =
                    JSONObject()
                        .put("format", 1)
                        .put("database", JSONObject(plainData.toString(Charsets.UTF_8)))
                        .put("images", images)
                        .toString()
                        .toByteArray()
                try {
                    Crypto.backup(plain, pass)
                } finally {
                    plain.fill(0)
                }
            } finally {
                plainData.fill(0)
                pass.fill('\u0000')
            }
        }

    suspend fun import(bytes: ByteArray, pass: CharArray) =
        withContext(Dispatchers.IO) {
            val plain =
                try {
                    Crypto.restore(bytes, pass)
                } finally {
                    pass.fill('\u0000')
                    bytes.fill(0)
                }
            val created = mutableListOf<String>()
            try {
                val root = JSONObject(JsonSafety.text(plain))
                require(root.getInt("format") == 1)
                val db = root.getJSONObject("database")
                val images = root.getJSONObject("images")
                require(images.length() <= 100)
                val remap = mutableMapOf<String, String>()
                images.keys().forEach { id ->
                    require(java.util.UUID.fromString(id).toString() == id)
                    val b = Base64.decode(images.getString(id), Base64.NO_WRAP)
                    require(b.size <= Images.MAX_STORED)
                    try {
                        val o =
                            android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
                        require(
                            o.outMimeType == "image/jpeg" &&
                                o.outWidth in 1..2048 &&
                                o.outHeight in 1..2048
                        )
                        val newid = java.util.UUID.randomUUID().toString()
                        val bitmap =
                            requireNotNull(
                                android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
                            )
                        val clean =
                            try {
                                java.io.ByteArrayOutputStream().use { out ->
                                    require(
                                        bitmap.compress(
                                            android.graphics.Bitmap.CompressFormat.JPEG,
                                            85,
                                            out,
                                        )
                                    )
                                    out.toByteArray()
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        try {
                            Images.write(ctx, newid, clean)
                        } finally {
                            clean.fill(0)
                        }
                        created += newid
                        remap[id] = newid
                    } finally {
                        b.fill(0)
                    }
                }
                val rows = db.getJSONArray("records")
                for (n in 0 until rows.length()) {
                    val data = rows.getJSONObject(n).getJSONObject("data")
                    if (data.optString("image").isNotEmpty())
                        data.put("image", requireNotNull(remap[data.getString("image")]))
                }
                requireNotNull(repo).restore(db.toString().toByteArray())
                created.clear()
            } finally {
                created.forEach { Images.remove(ctx, it) }
                plain.fill(0)
            }
        }

    fun importImage(item: Item, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val id = Images.import(ctx, uri)
                    try {
                        requireNotNull(repo).save(item.copy(data = item.data + ("image" to id)))
                    } catch (e: Exception) {
                        Images.remove(ctx, id)
                        throw e
                    }
                }
            } catch (e: Exception) {
                message.value = "画像を登録できませんでした。形式・サイズを確認してください。"
            }
        }
    }

    override fun onCleared() {
        database?.close()
        super.onCleared()
    }
}
