package jp.local.liveexpedition

import org.json.JSONArray
import org.json.JSONObject

data class Tile(
    val key: String,
    val width: Int = 1,
    val height: Int = 1,
    val variant: String = "default",
)

data class Cell(val x: Int, val y: Int, val width: Int, val height: Int)

object Tiles {
    val names =
        linkedMapOf(
            "money" to "現在残高",
            "live" to "次のライブ",
            "plan" to "遠征プラン",
            "subscription" to "サブスク",
            "recent" to "直近の予定",
            "movement" to "現在の移動",
            "hotel" to "宿泊",
            "venue" to "目的地・会場",
            "deadlines" to "申込・支払期限",
        )
    val defaults =
        listOf(
            Tile("money", 2, 1),
            Tile("live"),
            Tile("plan"),
            Tile("deadlines"),
            Tile("subscription"),
            Tile("recent", 2, 1),
        )

    fun validate(tiles: List<Tile>) {
        require(tiles.size <= names.size && tiles.map { it.key }.distinct().size == tiles.size)
        tiles.forEach {
            require(
                it.key in names &&
                    it.width in 1..2 &&
                    it.height in 1..2 &&
                    it.variant in listOf("default", "next", "total")
            )
        }
    }

    fun pack(tiles: List<Tile>): Map<String, Cell> {
        validate(tiles)
        val used = mutableSetOf<Pair<Int, Int>>()
        val cells = linkedMapOf<String, Cell>()
        tiles.forEach { t ->
            val position =
                (0..tiles.size * 2)
                    .asSequence()
                    .flatMap { y -> (0..2 - t.width).asSequence().map { x -> x to y } }
                    .first { (x, y) ->
                        (x until x + t.width).all { cx ->
                            (y until y + t.height).all { cy -> cx to cy !in used }
                        }
                    }
            val (x, y) = position
            for (cx in x until x + t.width) for (cy in y until y + t.height) used += cx to cy
            cells[t.key] = Cell(x, y, t.width, t.height)
        }
        return cells
    }

    fun encode(tiles: List<Tile>): String {
        validate(tiles)
        return JSONArray()
            .apply {
                tiles.forEach {
                    put(
                        JSONObject()
                            .put("key", it.key)
                            .put("w", it.width)
                            .put("h", it.height)
                            .put("variant", it.variant)
                    )
                }
            }
            .toString()
    }

    fun decode(raw: String?, legacyOrder: String?, legacyWide: String?): List<Tile> =
        runCatching {
                if (raw != null) {
                    val a = JSONArray(raw)
                    require(a.length() <= names.size)
                    (0 until a.length())
                        .map {
                            val o = a.getJSONObject(it)
                            Tile(
                                o.getString("key"),
                                o.getInt("w"),
                                o.getInt("h"),
                                o.optString("variant", "default"),
                            )
                        }
                        .also(::validate)
                } else if (legacyOrder != null)
                    legacyOrder
                        .split(',')
                        .filter { it in names }
                        .distinct()
                        .map { Tile(it, if (it in legacyWide.orEmpty().split(',')) 2 else 1) }
                else defaults
            }
            .getOrDefault(defaults)

    fun move(tiles: List<Tile>, id: String, to: String): List<Tile> {
        val from = tiles.indexOfFirst { it.key == id }
        val target = tiles.indexOfFirst { it.key == to }
        if (from < 0 || target < 0 || from == target) return tiles
        return tiles.toMutableList().apply { add(target, removeAt(from)) }
    }

    // Hit testing uses the layout at pickup, never the moving preview layout.
    // This prevents alternating swaps when a displaced tile passes under the pointer.
    fun dropOrder(original: List<Tile>, id: String, x: Float, y: Float): List<Tile> {
        if (!x.isFinite() || !y.isFinite() || x < 0 || x >= 2 || y < 0) return original
        val cells = pack(original)
        val hit =
            cells.entries
                .firstOrNull { (_, c) ->
                    x >= c.x && x < c.x + c.width && y >= c.y && y < c.y + c.height
                }
                ?.key
        return if (hit != null) move(original, id, hit)
        else if (y >= (cells.values.maxOfOrNull { it.y + it.height } ?: 0))
            move(original, id, original.lastOrNull()?.key ?: id)
        else original
    }
}
