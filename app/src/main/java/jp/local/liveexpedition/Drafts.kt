package jp.local.liveexpedition

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import org.json.JSONObject

object Drafts {
    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "editor-draft.v1")

    @Synchronized
    fun save(ctx: Context, item: Item) {
        require(item.data.size <= 40)
        Catalog.kind(item.kind)
        require(UUID.fromString(item.id).toString() == item.id)
        val bytes =
            JSONObject()
                .put("id", item.id)
                .put("kind", item.kind)
                .put("data", JSONObject(item.data))
                .toString()
                .toByteArray()
        require(bytes.size <= 128 * 1024)
        val atomic = AtomicFile(file(ctx))
        val out = atomic.startWrite()
        try {
            out.write(Crypto.seal(bytes, "editor-draft-v1"))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun read(ctx: Context): Item? {
        if (!file(ctx).exists()) return null
        require(file(ctx).length() <= 132 * 1024)
        val bytes = Crypto.open(file(ctx).readBytes(), "editor-draft-v1")
        return try {
            val j = JSONObject(JsonSafety.text(bytes))
            val id = j.getString("id")
            require(UUID.fromString(id).toString() == id)
            val kind = j.getString("kind")
            Catalog.kind(kind)
            val data = Rules.decode(j.getJSONObject("data").toString())
            require(data.size <= 40 && data.values.all { it.length <= 20000 })
            Item(id, kind, data)
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        AtomicFile(file(ctx)).delete()
    }
}
