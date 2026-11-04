package jp.local.liveexpedition

import org.junit.Assert.*
import org.junit.Test

class TilesTest {
    @Test
    fun fourSizesPackWithoutOverlapOrLeavingGrid() {
        val sizes = listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)
        for (a in sizes) for (b in sizes) for (c in sizes) {
            val list =
                listOf(
                    Tile("money", a.first, a.second),
                    Tile("live", b.first, b.second),
                    Tile("plan", c.first, c.second),
                    Tile("subscription"),
                )
            val packed = Tiles.pack(list)
            val occupied = mutableSetOf<Pair<Int, Int>>()
            assertEquals(4, packed.size)
            packed.values.forEach { p ->
                assertTrue(p.x >= 0 && p.x + p.width <= 2)
                for (x in p.x until p.x + p.width) for (y in p.y until p.y + p.height) assertTrue(
                    occupied.add(x to y)
                )
            }
        }
    }

    @Test
    fun layoutSurvivesSerializationAndSupportsReorder() {
        val list = listOf(Tile("money", 2, 2), Tile("live", 1, 2), Tile("plan", 1, 1, "total"))
        val moved = Tiles.move(list, "plan", "money")
        assertEquals(listOf("plan", "money", "live"), moved.map { it.key })
        assertEquals(moved, Tiles.decode(Tiles.encode(moved), null, null))
    }

    @Test
    fun oldPreferencesAreImportedWithoutResettingOrderOrHiddenTiles() {
        assertEquals(
            listOf(Tile("plan"), Tile("money", 2, 1)),
            Tiles.decode(null, "plan,money", "money"),
        )
        assertEquals(emptyList<Tile>(), Tiles.decode(null, "", "money"))
        assertEquals(Tiles.defaults, Tiles.decode("broken", null, null))
    }
}
