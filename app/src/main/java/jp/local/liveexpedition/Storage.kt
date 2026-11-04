package jp.local.liveexpedition

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

val Context.settings by preferencesDataStore("preferences")

@Entity(tableName = "records")
data class Record(@PrimaryKey val id: String, val kind: String, val payload: String) {
    fun item() = Item(id, kind, Rules.decode(payload))

    companion object {
        fun of(i: Item) = Record(i.id, i.kind, Rules.encode(i.data))
    }
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY id") fun observe(): Flow<List<Record>>

    @Query("SELECT * FROM records ORDER BY id") suspend fun all(): List<Record>

    @Query("SELECT * FROM records WHERE id = :id") suspend fun find(id: String): Record?

    @Upsert suspend fun put(record: Record)

    @Query("DELETE FROM records WHERE id = :id") suspend fun delete(id: String)

    @Query("DELETE FROM records") suspend fun clear()
}

@Database(entities = [Record::class], version = 1, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    private var runtimeKey: ByteArray? = null

    override fun close() {
        try {
            super.close()
        } finally {
            runtimeKey?.fill(0)
            runtimeKey = null
        }
    }

    abstract fun records(): RecordDao

    companion object {
        fun create(context: Context): VaultDatabase {
            System.loadLibrary("sqlcipher")
            val pass = Crypto.databaseKey(context)
            return try {
                Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                    .openHelperFactory(PreservingCipherFactory(pass))
                    .build()
                    .also {
                        it.runtimeKey = pass
                        try {
                            it.openHelper.writableDatabase
                        } catch (e: Exception) {
                            runCatching { it.close() }
                            throw e
                        }
                    }
            } catch (e: Exception) {
                pass.fill(0)
                throw e
            }
        }
    }
}

class Repository(private val db: VaultDatabase) {
    val items = db.records().observe().map { rows -> rows.map { it.item().also(Rules::validate) } }

    suspend fun save(item: Item) =
        db.withTransaction {
            Rules.validate(item)
            if (item["artistRef"].isNotEmpty())
                require(db.records().find(item["artistRef"])?.kind == "artist") {
                    "対象アーティストを確認してください"
                }
            if (item["tourRef"].isNotEmpty())
                require(db.records().find(item["tourRef"])?.kind == "tour") { "対象ツアーを確認してください" }
            require(
                item["liveRefs"].split(',').filter(String::isNotBlank).all {
                    db.records().find(it)?.kind == "live"
                }
            ) {
                "関連公演を確認してください"
            }
            if (item.kind == "ticket")
                require(db.records().find(item["parent"])?.kind == "live") { "公演IDを確認してください" }
            if (item.kind == "plan")
                require(
                    item["members"].split(',').filter(String::isNotBlank).all {
                        db.records().find(it)?.kind == "candidate"
                    }
                ) {
                    "候補を確認してください"
                }
            if (item.kind == "transaction")
                require(
                    db.records().all().none {
                        it.item()["paid"] == item.id || item.id in it.item()["payments"].split(',')
                    }
                ) {
                    "連携した取引は元データから管理してください"
                }
            val old = db.records().find(item.id)?.item()
            require(old != null || db.records().all().size < 10000) { "登録件数の上限は10000件です" }
            require(
                old == null ||
                    listOf("paid", "payments", "billingDates").all { item[it] == old[it] }
            ) {
                "支払記録が更新されています。画面を開き直してください"
            }
            if (old != null && old["paid"].isNotEmpty())
                require(item.amount == old.amount && item["fee"] == old["fee"]) {
                    "支払済み金額の変更はできません"
                }
            db.records().put(Record.of(item))
            if (old != null && old.title != item.title && item.kind in listOf("artist", "tour")) {
                db.records()
                    .all()
                    .map(Record::item)
                    .filter { it.kind == "live" || (item.kind == "artist" && it.kind == "tour") }
                    .forEach { child ->
                        val field = if (item.kind == "artist") "artist" else "tour"
                        val ref = if (item.kind == "artist") "artistRef" else "tourRef"
                        if (child[ref] == item.id) {
                            val updated = child.copy(data = child.data + (field to item.title))
                            Rules.validate(updated)
                            db.records().put(Record.of(updated))
                        }
                    }
            }
        }

    suspend fun delete(item: Item) =
        db.withTransaction {
            val current = requireNotNull(db.records().find(item.id)).item()
            val all = db.records().all().map(Record::item)
            require(
                current["paid"].isEmpty() &&
                    current["payments"].isEmpty() &&
                    all.none {
                        it["paid"] == item.id ||
                            item.id in it["payments"].split(',') ||
                            it["parent"] == item.id ||
                            it["artistRef"] == item.id ||
                            it["tourRef"] == item.id ||
                            item.id in it["liveRefs"].split(',') ||
                            it["members"].split(',').contains(item.id)
                    }
            ) {
                "関連データまたは支払記録があるため削除できません"
            }
            db.records().delete(item.id)
        }

    suspend fun undoDelete(item: Item) {
        require(db.records().find(item.id) == null) { "同じ記録が既に存在します" }
        save(item)
    }

    suspend fun pay(id: String, date: LocalDate) =
        db.withTransaction {
            val source = requireNotNull(db.records().find(id)).item()
            require(source.kind in listOf("ticket", "candidate", "subscription"))
            require(source["paid"].isEmpty()) { "支払済みです" }
            require(source["amount"].isNotEmpty()) { "金額を入力してください" }
            require(date.year in 2000..2100 && date <= LocalDate.now()) { "実際の支払日を指定してください" }
            if (source.kind == "subscription")
                require(source["status"] == "契約中" && date >= LocalDate.parse(source["date"])) {
                    "対象の請求日と支払日を確認してください"
                }
            val billingKey = if (source.kind == "subscription") source["date"] else date.toString()
            require(billingKey !in source["billingDates"].split(',')) { "この請求日の支払は記録済みです" }
            val txn =
                Item(
                    kind = "transaction",
                    data =
                        mapOf(
                            "title" to source.title,
                            "type" to "支出",
                            "date" to date.toString(),
                            "amount" to
                                (source.amount + (source["fee"].toLongOrNull() ?: 0L)).toString(),
                            "category" to
                                if (source.kind == "ticket") "ライブ"
                                else if (source.kind == "subscription") "サブスク" else "遠征",
                            "method" to source["method"],
                        ),
                )
            Rules.validate(txn)
            db.records().put(Record.of(txn))
            val updated =
                source.copy(
                    data =
                        source.data +
                            mapOf(
                                "paid" to txn.id,
                                "payments" to
                                    (source["payments"].split(',').filter(String::isNotBlank) +
                                            txn.id)
                                        .joinToString(","),
                                "billingDates" to
                                    (source["billingDates"].split(',').filter(String::isNotBlank) +
                                            billingKey)
                                        .joinToString(","),
                            )
                )
            Rules.validate(updated)
            db.records().put(Record.of(updated))
        }

    suspend fun nextBill(id: String) =
        db.withTransaction {
            val s = requireNotNull(db.records().find(id)).item()
            require(
                s.kind == "subscription" &&
                    s["paid"].isNotEmpty() &&
                    s["renew"] == "はい" &&
                    s["status"] == "契約中"
            )
            val d = LocalDate.parse(s["date"])
            val next = if (s["cycle"] == "年額") d.plusYears(1) else d.plusMonths(1)
            val updated = s.copy(data = s.data + ("date" to next.toString()) - "paid")
            Rules.validate(updated)
            db.records().put(Record.of(updated))
        }

    suspend fun addToPlan(candidate: Item, plan: Item) =
        db.withTransaction {
            val latest = requireNotNull(db.records().find(plan.id)).item()
            require(db.records().find(candidate.id)?.kind == "candidate") {
                "候補が更新されています。一覧を開き直してください"
            }
            require(candidate.kind == "candidate" && latest.kind == "plan")
            val ids =
                (latest["members"].split(',').filter(String::isNotBlank) + candidate.id).distinct()
            val updated = latest.copy(data = latest.data + ("members" to ids.joinToString(",")))
            Rules.validate(updated)
            db.records().put(Record.of(updated))
        }

    suspend fun snapshot(): ByteArray =
        db.withTransaction {
            val a = JSONArray()
            db.records().all().forEach { r ->
                a.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("kind", r.kind)
                        .put("data", JSONObject(r.payload))
                )
            }
            JSONObject().put("version", 1).put("records", a).toString().toByteArray(Charsets.UTF_8)
        }

    suspend fun restore(bytes: ByteArray) {
        require(bytes.size <= Crypto.MAX_BACKUP)
        val j = JSONObject(JsonSafety.text(bytes))
        require(j.getInt("version") == 1)
        val array = j.getJSONArray("records")
        require(array.length() <= 10000)
        val items =
            (0 until array.length()).map { n ->
                val r = array.getJSONObject(n)
                Item(
                        r.getString("id"),
                        r.getString("kind"),
                        Rules.decode(r.getJSONObject("data").toString()),
                    )
                    .also(Rules::validate)
            }
        require(items.map { it.id }.distinct().size == items.size)
        items.forEach { i ->
            if (i["artistRef"].isNotEmpty())
                require(items.any { it.id == i["artistRef"] && it.kind == "artist" })
            if (i["tourRef"].isNotEmpty())
                require(items.any { it.id == i["tourRef"] && it.kind == "tour" })
            require(
                i["liveRefs"].split(',').filter(String::isNotBlank).all { id ->
                    items.any { it.id == id && it.kind == "live" }
                }
            )
            if (i.kind == "ticket") require(items.any { it.id == i["parent"] && it.kind == "live" })
            if (i["paid"].isNotEmpty())
                require(
                    items.any {
                        it.id == i["paid"] && it.kind == "transaction" && it["type"] == "支出"
                    }
                )
            require(
                i["payments"].split(',').filter(String::isNotBlank).all { id ->
                    items.any { it.id == id && it.kind == "transaction" && it["type"] == "支出" }
                }
            )
            require(
                i["payments"].split(',').filter(String::isNotBlank).size ==
                    i["billingDates"].split(',').filter(String::isNotBlank).size
            )
            if (i["paid"].isNotEmpty()) require(i["paid"] in i["payments"].split(','))
            if (i.kind in listOf("ticket", "candidate") && i["paid"].isNotEmpty())
                require(
                    items.first { it.id == i["paid"] }.amount ==
                        i.amount + (i["fee"].toLongOrNull() ?: 0L)
                )
            if (i.kind == "plan")
                require(
                    i["members"].split(',').filter(String::isNotBlank).all { id ->
                        items.any { it.id == id && it.kind == "candidate" }
                    }
                )
        }
        val paymentIds = items.flatMap { it["payments"].split(',').filter(String::isNotBlank) }
        require(paymentIds.distinct().size == paymentIds.size)
        db.withTransaction {
            db.records().clear()
            items.forEach { db.records().put(Record.of(it)) }
        }
    }
}
