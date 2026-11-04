package jp.local.liveexpedition

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class DraftEntry(val item: Item, val route: String, val generation: Long)

data class DraftBook(
    val entries: Map<String, DraftEntry> = emptyMap(),
    val active: String? = null,
) {
    fun latest() = active?.let { entries[it] }

    fun open(item: Item, existing: Boolean): DraftEntry {
        val route = route(item, existing)
        return entries[key(item)]
            ?: entries.values.firstOrNull { it.route == route }
            ?: DraftEntry(item, route, (entries.values.maxOfOrNull { it.generation } ?: 0) + 1)
    }

    fun put(entry: DraftEntry, activate: Boolean = true): DraftBook {
        val key = key(entry.item)
        val old = entries[key]
        if (old != null && old.generation > entry.generation) return this
        require(entries.size < 64 || key in entries) { "下書きが64件あります。不要な下書きを破棄してください" }
        return copy(entries = entries + (key to entry), active = if (activate) key else active)
    }

    fun remove(item: Item): DraftBook {
        val k = key(item)
        return copy(entries = entries - k, active = active.takeUnless { it == k })
    }

    companion object {
        fun route(item: Item, existing: Boolean) =
            if (existing) "record:${key(item)}"
            else
                "new:${item.kind}:${item["type"]}:${item["parent"]}:${item["cardId"]}:${item["accountId"]}:${item["_section"]}"

        fun key(item: Item) = "${item.kind}:${item.id}:${item["_section"].ifEmpty{"edit"}}"

        fun validate(item: Item) {
            Catalog.kind(item.kind)
            require(UUID.fromString(item.id).toString() == item.id)
            require(item.data.size <= 48 && item.data.values.all { it.length <= 20000 })
            require(
                item["_section"].length <= 40 &&
                    item["_section"].all { it.isLetterOrDigit() || it == '_' }
            )
        }

        fun encode(book: DraftBook): ByteArray {
            val entries = JSONArray()
            book.entries.values.forEach { entry ->
                validate(entry.item)
                entries.put(
                    JSONObject()
                        .put("id", entry.item.id)
                        .put("kind", entry.item.kind)
                        .put("data", JSONObject(entry.item.data))
                        .put("route", entry.route)
                        .put("generation", entry.generation)
                )
            }
            val data =
                JSONObject()
                    .put("version", 2)
                    .put("active", book.active ?: JSONObject.NULL)
                    .put("entries", entries)
                    .toString()
                    .toByteArray()
            require(data.size <= 4 * 1024 * 1024)
            return data
        }

        fun decode(bytes: ByteArray): DraftBook {
            require(bytes.size <= 4 * 1024 * 1024)
            val root = JSONObject(JsonSafety.text(bytes))
            require(root.getInt("version") == 2)
            val array = root.getJSONArray("entries")
            require(array.length() <= 64)
            val entries =
                (0 until array.length()).map {
                    val j = array.getJSONObject(it)
                    val item =
                        Item(
                            j.getString("id"),
                            j.getString("kind"),
                            Rules.decode(j.getJSONObject("data").toString()),
                        )
                    validate(item)
                    DraftEntry(
                        item,
                        j.getString("route").also { require(it.length <= 512) },
                        j.getLong("generation"),
                    )
                }
            require(entries.map { key(it.item) }.distinct().size == entries.size)
            val active = if (root.isNull("active")) null else root.getString("active")
            require(active == null || entries.any { key(it.item) == active })
            return DraftBook(entries.associateBy { key(it.item) }, active)
        }
    }
}

object DraftVault {
    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "editor-drafts.v2")

    private const val PURPOSE = "editor-drafts-v2"

    @Synchronized
    private fun readBook(ctx: Context): DraftBook {
        val target = file(ctx)
        if (target.exists()) {
            require(target.length() <= 4 * 1024 * 1024 + 1024)
            val plain = Crypto.open(AtomicFile(target).openRead().use { it.readBytes() }, PURPOSE)
            return try {
                DraftBook.decode(plain)
            } finally {
                plain.fill(0)
            }
        }
        val legacy = File(ctx.noBackupFilesDir, "editor-draft.v1")
        if (!legacy.exists()) return DraftBook()
        require(legacy.length() <= 132 * 1024)
        val bytes =
            Crypto.open(AtomicFile(legacy).openRead().use { it.readBytes() }, "editor-draft-v1")
        try {
            val j = JSONObject(JsonSafety.text(bytes))
            val item =
                Item(
                    j.getString("id"),
                    j.getString("kind"),
                    Rules.decode(j.getJSONObject("data").toString()),
                )
            DraftBook.validate(item)
            val entry = DraftEntry(item, "legacy:${item.id}", 1)
            val book = DraftBook().put(entry)
            write(ctx, book)
            AtomicFile(legacy).delete()
            return book
        } finally {
            bytes.fill(0)
        }
    }

    private fun write(ctx: Context, book: DraftBook) {
        val bytes = DraftBook.encode(book)
        val atomic = AtomicFile(file(ctx))
        val out = atomic.startWrite()
        try {
            out.write(Crypto.seal(bytes, PURPOSE))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized fun active(ctx: Context) = readBook(ctx).latest()

    @Synchronized
    fun list(ctx: Context) = readBook(ctx).entries.values.sortedByDescending { it.generation }

    @Synchronized fun find(ctx: Context, item: Item) = readBook(ctx).entries[DraftBook.key(item)]

    @Synchronized
    fun rebindLegacy(ctx: Context, item: Item, existing: Boolean): DraftEntry {
        val book = readBook(ctx)
        val old = book.entries[DraftBook.key(item)] ?: error("下書きがありません")
        val updated = old.copy(route = DraftBook.route(item, existing))
        write(ctx, book.put(updated))
        return updated
    }

    @Synchronized
    fun resolve(ctx: Context, item: Item, existing: Boolean): DraftEntry {
        val book = readBook(ctx)
        val entry = book.open(item, existing)
        write(ctx, book.put(entry))
        return entry
    }

    @Synchronized
    fun save(ctx: Context, item: Item, route: String, generation: Long, activate: Boolean = true) {
        val book = readBook(ctx)
        write(ctx, book.put(DraftEntry(item, route, generation), activate))
    }

    @Synchronized
    fun finish(ctx: Context, item: Item) {
        write(ctx, readBook(ctx).remove(item))
    }

    @Synchronized
    fun deactivate(ctx: Context) {
        write(ctx, readBook(ctx).copy(active = null))
    }
}
