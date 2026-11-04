package jp.local.liveexpedition

import org.junit.Assert.*
import org.junit.Test

class AnnouncementParserTest {
    @Test
    fun extractsExplicitJapaneseLabelsAndTimesWithoutInventingFields() {
        val result =
            AnnouncementParser.extract(
                "アーティスト：テスト\nツアー：春ツアー\n公演名：福岡公演\n会場：ホール\n2027年3月22日 開場16:00 / 開演17:00\nhttps://example.org/live"
            )
        assertEquals("2027-03-22T16:00", result["start"])
        assertEquals("ホール", result["venue"])
        assertEquals("テスト", result["artist"])
        assertFalse(result.containsKey("finish"))
    }

    @Test
    fun missingYearAndInvalidDateAreNotGuessed() {
        assertFalse(AnnouncementParser.extract("3月22日 開場16:00").containsKey("date"))
        assertFalse(AnnouncementParser.extract("2026/02/30 OPEN 16:00").containsKey("start"))
        assertTrue(AnnouncementParser.extract("何も確定していません").isEmpty())
    }
}
