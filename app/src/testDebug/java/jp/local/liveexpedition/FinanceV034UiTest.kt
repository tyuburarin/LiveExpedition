package jp.local.liveexpedition

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinanceV034UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetOnlyTestPreferences() {
        runBlocking {
            androidx.test.core.app.ApplicationProvider.getApplicationContext<
                    android.app.Application
                >()
                .settings
                .edit { it.clear() }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap =
            compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                android.graphics.Bitmap.createBitmap(
                        view.width,
                        view.height,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    .also { view.draw(android.graphics.Canvas(it)) }
            }
        val file = File("build/ui-previews-v034/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val account = Ledger.defaultAccount()
    private val second = Item(kind = "account", data = mapOf("title" to "サブ口座"))
    private val sub =
        Item(
            kind = "subscription",
            data =
                mapOf(
                    "title" to "テストサービス",
                    "cycle" to "月額",
                    "amount" to "900",
                    "date" to "2026-11-26",
                    "status" to "契約中",
                ),
        )

    @Test
    fun expenseOpensDirectlyAndTwoAccountChoiceChangesDraft() {
        var draft: Item? = null
        val expense = Item(kind = "transaction", data = mapOf("type" to "支出"))
        compose.setContent {
            ExpeditionTheme {
                FinanceEditor(expense, listOf(account, second), { draft = it }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("支出を登録").assertExists()
        compose.onNodeWithText("収入").assertDoesNotExist()
        compose.onNodeWithText("振替").assertDoesNotExist()
        compose.onNodeWithText("口座：メイン口座").performClick()
        compose.onNodeWithText("サブ口座").performClick()
        compose.onNodeWithText("金額 円").performTextInput("3500")
        compose.onNodeWithText("内容").performTextInput("昼食")
        assertEquals(second.id, draft?.get("accountId"))
        assertEquals("3500", draft?.get("amount"))
        capture("expense-editor")
    }

    @Test
    fun oneAccountHasNoAccountPicker() {
        compose.setContent {
            ExpeditionTheme {
                FinanceEditor(
                    Item(kind = "transaction", data = mapOf("type" to "収入")),
                    listOf(account),
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("口座：メイン口座").assertHasNoClickAction()
    }

    @Test
    fun twoFormDraftsRestoreAndSavingRemovesOnlyOne() {
        var opened by
            mutableStateOf<Item?>(Item(kind = "transaction", data = mapOf("type" to "支出")))
        var book = DraftBook()
        compose.setContent {
            ExpeditionTheme {
                opened?.let { item ->
                    FinanceEditor(
                        item,
                        listOf(account),
                        { updated ->
                            book = book.put(DraftEntry(updated, DraftBook.route(updated, false), 1))
                        },
                        { opened = null },
                        {
                            book = book.remove(item)
                            opened = null
                        },
                        {},
                        { saved ->
                            book = book.remove(saved)
                            opened = null
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("金額 円").performTextInput("3500")
        compose.onNodeWithText("内容").performTextInput("昼食")
        compose.onNodeWithContentDescription("閉じる（自動保存）").performClick()
        compose.runOnIdle { opened = Item(kind = "subscription", data = defaults("subscription")) }
        compose.onNodeWithText("サービス名").performTextInput("別の下書き")
        compose.onNodeWithContentDescription("閉じる（自動保存）").performClick()
        compose.runOnIdle {
            book = DraftBook.decode(DraftBook.encode(book))
            opened = book.entries.values.first { it.item.kind == "transaction" }.item
        }
        compose.onNodeWithText("3500").assertExists()
        compose.onNodeWithText("入力途中のデータがあります").assertDoesNotExist()
        compose.onNodeWithText("保存").performClick()
        assertEquals(1, book.entries.size)
        assertEquals("別の下書き", book.entries.values.single().item.title)
    }

    @Test
    fun subscriptionDetailUsesBillingCardsAndNeverShowsPaymentTracking() {
        var edited: Item? = null
        compose.setContent {
            PreviewFrame("サブスク詳細", 3) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubscriptionDetail(sub, { edited = it })
                }
            }
        }
        compose.onNodeWithText("日時・会場").assertDoesNotExist()
        compose.onNodeWithText("実際の支払記録").assertDoesNotExist()
        compose.onNodeWithText("編集").assertDoesNotExist()
        compose.onNodeWithText("請求情報").performClick()
        assertEquals("billing", edited?.get("_section"))
        capture("subscription-detail")
    }

    @Test
    fun compactBalanceSubscriptionHasNoAmountAndExpandedMasksMatchHome() {
        var expanded by mutableStateOf(false)
        val month = YearMonth.of(2026, 11)
        compose.setContent {
            ExpeditionTheme {
                CompositionLocalProvider(
                    LocalMoneyVisibility provides
                        MoneyVisibility(true, mapOf("subscription:forecast:$month" to false))
                ) {
                    Column {
                        Box(Modifier.height(220.dp)) {
                            DashboardScreen(
                                listOf(Tile("subscription", if (expanded) 2 else 1, 1)),
                                listOf(sub),
                                PreviewData.money,
                                {},
                                { _, _ -> },
                                scope = "balance",
                                selectedMonth = month,
                            )
                        }
                        Box(Modifier.height(220.dp)) {
                            DashboardScreen(
                                listOf(Tile("subscription", 2, 1)),
                                listOf(sub),
                                PreviewData.money,
                                {},
                                { _, _ -> },
                                selectedMonth = month,
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("サブスク管理").assertExists()
        compose.onAllNodesWithText("¥900").assertCountEquals(1)
        compose.runOnIdle { expanded = true }
        compose.onAllNodesWithText("¥900").assertCountEquals(2)
    }

    @Test
    fun balanceTilesDragAndStoredOrderAreIndependentOfHome() {
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val layout =
            listOf(
                Tile("money", 2, 1),
                Tile("credit"),
                Tile("subscription"),
                Tile("accounts", 2, 1),
            )
        runBlocking {
            androidx.test.core.app.ApplicationProvider.getApplicationContext<
                    android.app.Application
                >()
                .settings
                .edit {
                    it[
                        androidx.datastore.preferences.core.stringPreferencesKey(
                            "balance_tiles_v034"
                        )] = Tiles.encode(layout)
                }
        }
        compose.setContent {
            ExpeditionTheme {
                val tiles by model.balanceTiles.collectAsState(initial = layout)
                DashboardScreen(
                    tiles,
                    listOf(account, sub),
                    PreviewData.money,
                    model::balanceTiles,
                    { _, _ -> },
                    scope = "balance",
                )
            }
        }
        compose.onNodeWithContentDescription("未引落しのカード利用タイル 1×1").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(190f, 0f), 300)
            up()
        }
        compose.waitUntil(5000) {
            compose.onNodeWithContentDescription("サブスクタイル 1×1").fetchSemanticsNode()
            runBlocking { model.balanceTiles.first() }[1].key == "subscription"
        }
        assertEquals(Tiles.defaults, runBlocking { model.tiles.first() })
        capture("balance-tiles-after-drag")
    }

    @Test
    fun screenshotsUseOnlyTestFixtures() {
        var screen by mutableStateOf("balance")
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[AppModel::class.java]
        val all = listOf(account, second, sub) + PreviewData.items
        compose.setContent {
            PreviewFrame(
                if (screen == "balance") "残高" else if (screen == "accounts") "口座管理" else "ホーム",
                if (screen == "home") 0 else 3,
            ) {
                when (screen) {
                    "balance" -> BalanceDashboard(model, all, PreviewData.money, {}, {})
                    "accounts" -> AccountManager(all, {})
                    else -> DashboardScreen(Tiles.defaults, all, PreviewData.money, {}, { _, _ -> })
                }
            }
        }
        capture("balance-dashboard")
        compose.runOnIdle { screen = "accounts" }
        capture("account-manager")
        compose.runOnIdle { screen = "home" }
        capture("home")
    }
}
