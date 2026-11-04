package jp.local.liveexpedition

import org.junit.Assert.*
import org.junit.Test

class DraftBookTest {
    @Test
    fun accountAndCardRoutesStaySeparateAndBackgroundImageDoesNotActivate() {
        val first =
            Item(kind = "balance_setting", data = mapOf("accountId" to Ledger.DEFAULT_ACCOUNT))
        val second =
            Item(
                kind = "balance_setting",
                data = mapOf("accountId" to java.util.UUID.randomUUID().toString()),
            )
        val other = Item(kind = "subscription", data = mapOf("title" to "入力中"))
        var book = DraftBook().put(DraftBook().open(first, false))
        assertNotEquals(first.id, book.open(second, false).item.id)
        book = book.put(DraftEntry(other, "record:${other.id}", 3))
        book =
            book.put(
                DraftEntry(
                    first.copy(
                        data = first.data + ("image" to java.util.UUID.randomUUID().toString())
                    ),
                    DraftBook.route(first, false),
                    4,
                ),
                activate = false,
            )
        val restored = DraftBook.decode(DraftBook.encode(book))
        assertEquals(other.id, restored.latest()?.item?.id)
        assertTrue(restored.open(first, false).item["image"].isNotEmpty())
    }

    @Test
    fun multipleRecordsAndScreensSurviveSerializationAndTargetedRemoval() {
        val expense =
            Item(
                kind = "transaction",
                data = mapOf("type" to "支出", "title" to "入力中", "amount" to "3500"),
            )
        val other = Item(kind = "transaction", data = mapOf("type" to "収入", "title" to "別の記録"))
        val billing =
            Item(kind = "subscription", data = mapOf("_section" to "billing", "billDay" to "26"))
        val registration = billing.copy(data = mapOf("_section" to "registration", "title" to "名前"))
        var book = DraftBook()
        for ((n, item) in listOf(expense, other, billing, registration).withIndex()) book =
            book.put(DraftEntry(item, "record:${DraftBook.key(item)}", n.toLong()))
        val restored = DraftBook.decode(DraftBook.encode(book))
        assertEquals(4, restored.entries.size)
        assertEquals("3500", restored.open(expense, true).item["amount"])
        assertEquals("26", restored.open(billing, true).item["billDay"])
        assertEquals("名前", restored.open(registration, true).item.title)
        val removed = restored.remove(billing)
        assertEquals(3, removed.entries.size)
        assertTrue(DraftBook.key(registration) in removed.entries)
    }

    @Test
    fun newFormReopensNaturallyWithoutReplacingAnotherDraft() {
        val request = Item(kind = "transaction", data = mapOf("type" to "支出"))
        val selection = DraftBook().open(request, false)
        val typed =
            selection.copy(
                item = request.copy(data = request.data + ("amount" to "4200")),
                generation = 3,
            )
        val book = DraftBook().put(typed)
        val reopened = book.open(Item(kind = "transaction", data = mapOf("type" to "支出")), false)
        assertEquals(request.id, reopened.item.id)
        assertEquals("4200", reopened.item["amount"])
        assertNotEquals(
            request.id,
            book.open(Item(kind = "transaction", data = mapOf("type" to "収入")), false).item.id,
        )
    }

    @Test
    fun lateOlderWriteCannotOverwritePauseFlush() {
        val item = Item(kind = "account", data = mapOf("title" to "最新"))
        val latest = DraftEntry(item, "record:${item.id}", 12)
        val book =
            DraftBook()
                .put(latest)
                .put(latest.copy(item = item.copy(data = mapOf("title" to "古い")), generation = 10))
        assertEquals("最新", DraftBook.decode(DraftBook.encode(book)).latest()?.item?.title)
    }
}
