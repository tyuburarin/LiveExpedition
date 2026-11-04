package jp.local.liveexpedition

import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDateTime
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun performanceListSwitchKeepsTicketNavigation() {
        var selected: Item? = null
        compose.setContent {
            PreviewFrame("ライブ", 1) {
                LiveScreen(PreviewData.items, PreviewData.money, {}, { selected = it })
            }
        }
        compose.onNodeWithText("公演一覧").performClick()
        compose.onNodeWithText("オフィシャル先行").performClick()
        Assert.assertEquals(PreviewData.ticket.id, selected?.id)
        capture("performance-list")
    }

    @Test
    fun compactPlanRetainsDetailsAndCandidateAdd() {
        var added: Pair<Item, Item>? = null
        compose.setContent {
            PreviewFrame("遠征", 2) {
                TripPage(
                    PreviewData.items,
                    PreviewData.money,
                    {},
                    {},
                    { c, p -> added = c to p },
                    {},
                    true,
                )
            }
        }
        compose.onNodeWithText("内訳・注意を表示").assertExists()
        compose.onNodeWithText("遠征候補").assertExists()
        compose.onAllNodesWithContentDescription("候補をプランに追加").onFirst().performClick()
        compose.onAllNodesWithText(PreviewData.plan.title).onLast().performClick()
        Assert.assertEquals(PreviewData.candidate.id, added?.first?.id)
        Assert.assertEquals(PreviewData.plan.id, added?.second?.id)
    }

    @Test
    fun screenSwipeSettingAndChildScrollPriority() {
        var enabled by mutableStateOf(false)
        var child by mutableStateOf(false)
        var target = 1
        compose.setContent {
            Box(Modifier.fillMaxSize().tabSwipe(enabled, 1, { target = it })) {
                if (child)
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Box(Modifier.width(1200.dp).fillMaxSize())
                    }
            }
        }
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY))
        }
        Assert.assertEquals(1, target)
        compose.runOnIdle { enabled = true }
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY))
        }
        Assert.assertEquals(2, target)
        compose.runOnIdle {
            child = true
            target = 1
        }
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY))
        }
        Assert.assertEquals(1, target)
    }

    @Test
    fun tabHoldSlideCommitsAndOutsideReleaseCancels() {
        var selected = 0
        compose.setContent {
            var tab by remember { mutableIntStateOf(0) }
            ExpeditionTheme {
                Surface {
                    ExpeditionTabs(
                        tab,
                        {
                            selected = it
                            tab = it
                        },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("ホームタブ").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(195f, 0f), 300)
            up()
        }
        Assert.assertEquals(2, selected)
        compose.onNodeWithContentDescription("遠征タブ").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(-95f, 0f), 200)
            moveBy(Offset(0f, -100f), 200)
            up()
        }
        Assert.assertEquals(2, selected)
    }

    @Test
    fun calendarAndCardDetailsRender() {
        var calendar by mutableStateOf(true)
        compose.setContent {
            PreviewFrame("ホーム") {
                if (calendar)
                    PlannerBrowser(
                        PreviewData.items,
                        LocalDateTime.parse("2026-11-04T08:24"),
                        "",
                        {},
                        {},
                        true,
                    )
                else
                    androidx.compose.foundation.lazy.LazyColumn {
                        item {
                            Column(
                                Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RecordDetailBody(
                                    PreviewData.live,
                                    PreviewData.items,
                                    PreviewData.money,
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                )
                            }
                        }
                    }
            }
        }
        compose.onNodeWithText("2026年11月").assertExists()
        capture("calendar")
        compose.runOnIdle { calendar = false }
        compose.onNodeWithText("福岡公演").assertExists()
        capture("live-detail")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image =
            compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                android.graphics.Bitmap.createBitmap(
                        view.width,
                        view.height,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    .also { bitmap -> view.draw(android.graphics.Canvas(bitmap)) }
            }
        val dir = File("build/ui-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun populatedDashboardRendersAndTapNavigates() {
        var opened = ""
        compose.setContent {
            PreviewFrame("ホーム") {
                DashboardScreen(
                    Tiles.defaults,
                    PreviewData.items,
                    PreviewData.money,
                    {},
                    { key, _ -> opened = key },
                )
            }
        }
        compose.onNodeWithContentDescription("現在残高タイル 2×1").performTouchInput { click() }
        Assert.assertEquals("money", opened)
        capture("home")
    }

    @Test
    fun longPressWithoutMovementOpensSizeMenuAndPersistsFourSizes() {
        var layout = Tiles.defaults
        compose.setContent {
            var state by remember { mutableStateOf(layout) }
            PreviewFrame("ホーム") {
                DashboardScreen(
                    state,
                    PreviewData.items,
                    PreviewData.money,
                    {
                        layout = it
                        state = it
                    },
                    { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("次のライブタイル 1×1").performTouchInput { longClick(center) }
        listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2).forEach { (w, h) ->
            compose.onNodeWithText("$w×$h").performClick()
            Assert.assertEquals(Tile("live", w, h), layout.first { it.key == "live" })
        }
        Assert.assertEquals(layout, Tiles.decode(Tiles.encode(layout), null, null))
        capture("tile-menu")
    }

    @Test
    fun dragFollowsPointerAndCommitsReorderedLayout() {
        var saved = Tiles.defaults
        compose.setContent {
            var state by remember { mutableStateOf(saved) }
            PreviewFrame("ホーム") {
                DashboardScreen(
                    state,
                    PreviewData.items,
                    PreviewData.money,
                    {
                        saved = it
                        state = it
                    },
                    { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("次のライブタイル 1×1").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(190f, 0f), 300)
            up()
        }
        Assert.assertNotEquals(Tiles.defaults.map { it.key }, saved.map { it.key })
        Assert.assertEquals("subscription", saved[1].key)
        capture("home-after-drag")
    }

    @Test
    fun screenshotSchedule() {
        compose.setContent { SchedulePreview() }
        compose.onNodeWithText("現在の予定").assertExists()
        capture("schedule")
    }

    @Test
    fun screenshotLive() {
        compose.setContent { LivePreview() }
        compose.onNodeWithText("○○").assertExists()
        capture("live")
    }

    @Test
    fun screenshotFinance() {
        compose.setContent { FinancePreview() }
        compose.onNodeWithText("今月の収支差額").assertExists()
        capture("finance")
    }

    @Test
    fun screenshotSubscriptions() {
        compose.setContent { SubscriptionsPreview() }
        compose.onNodeWithText("年間換算の合計").assertExists()
        capture("subscriptions")
    }

    @Test
    fun screenshotPlan() {
        compose.setContent { TripPreview() }
        compose.onNodeWithText("福岡遠征").assertExists()
        capture("trip-plan")
    }

    @Test
    fun tourCardOpensPerformancesAndTicketDetails() {
        var selected: Item? = null
        compose.setContent {
            PreviewFrame("ライブ", 1) {
                LiveScreen(PreviewData.items, PreviewData.money, {}, { selected = it })
            }
        }
        compose.onNodeWithText("○○").performClick()
        compose.onNodeWithText("○○ツアー").performClick()
        compose.onNodeWithText("オフィシャル先行").performClick()
        Assert.assertEquals(PreviewData.ticket.id, selected?.id)
    }

    @Test
    fun homePagerSwipesToScheduleWithoutNavigatingTile() {
        compose.setContent {
            PreviewFrame("ホーム") {
                val pager = rememberPagerState(pageCount = { 2 })
                HorizontalPager(pager) { page ->
                    if (page == 0)
                        DashboardScreen(
                            Tiles.defaults,
                            PreviewData.items,
                            PreviewData.money,
                            {},
                            { _, _ -> },
                        )
                    else
                        SchedulePage(
                            Itinerary.events(PreviewData.items),
                            LocalDateTime.parse("2026-11-04T08:24"),
                            "",
                            {},
                            {},
                            {},
                        )
                }
            }
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithText("現在の予定").assertIsDisplayed()
    }

    @Test
    fun cancelDuringDragDoesNotPersistOrOpenMenu() {
        var writes = 0
        compose.setContent {
            PreviewFrame("ホーム") {
                DashboardScreen(
                    Tiles.defaults,
                    PreviewData.items,
                    PreviewData.money,
                    { writes++ },
                    { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("次のライブタイル 1×1").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(180f, 0f), 300)
            cancel()
        }
        compose.waitForIdle()
        Assert.assertEquals(0, writes)
        compose.onNodeWithText("サイズ変更").assertDoesNotExist()
    }

    @Test
    fun fourMixedSizesRenderInPackedGrid() {
        val tiles =
            listOf(
                Tile("money", 2, 1),
                Tile("live", 1, 2),
                Tile("plan", 1, 1),
                Tile("recent", 1, 1),
                Tile("subscription", 2, 2),
            )
        compose.setContent {
            PreviewFrame("ホーム") {
                DashboardScreen(tiles, PreviewData.items, PreviewData.money, {}, { _, _ -> })
            }
        }
        compose.onNodeWithContentDescription("次のライブタイル 1×2").assertIsDisplayed()
        capture("home-four-sizes")
    }

    @Test
    fun candidateDropAddsToSelectedPlanAndRetainsCandidate() {
        var added: Pair<Item, Item>? = null
        compose.setContent {
            PreviewFrame("遠征", 2) {
                TripPage(
                    PreviewData.items,
                    PreviewData.money,
                    {},
                    {},
                    { candidate, plan -> added = candidate to plan },
                    {},
                )
            }
        }
        compose.onNodeWithText(PreviewData.candidate.title).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(1f, 8f), 150)
        }
        val target =
            compose.onNodeWithText(PreviewData.plan.title).fetchSemanticsNode().boundsInRoot.center
        val original = compose.onAllNodesWithText(PreviewData.candidate.title)[0]
        val bounds = original.fetchSemanticsNode().boundsInRoot
        original.performTouchInput {
            moveTo(target - bounds.topLeft, 300)
            up()
        }
        Assert.assertEquals(PreviewData.candidate.id, added?.first?.id)
        Assert.assertEquals(PreviewData.plan.id, added?.second?.id)
        compose.onNodeWithText(PreviewData.candidate.title).assertExists()
    }

    @Test
    fun candidateDropCanCreateNewPlan() {
        var created: Item? = null
        compose.setContent {
            PreviewFrame("遠征", 2) {
                TripPage(
                    PreviewData.items.filter { it.kind != "plan" },
                    PreviewData.money,
                    {},
                    {},
                    { _, _ -> },
                    { created = it },
                )
            }
        }
        compose.onNodeWithText(PreviewData.candidate.title).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(1f, 8f), 150)
        }
        val target = compose.onNodeWithText("＋ 新規プラン").fetchSemanticsNode().boundsInRoot.center
        val original = compose.onAllNodesWithText(PreviewData.candidate.title)[0]
        val bounds = original.fetchSemanticsNode().boundsInRoot
        original.performTouchInput {
            moveTo(target - bounds.topLeft, 300)
            up()
        }
        Assert.assertEquals(PreviewData.candidate.id, created?.get("members"))
        Assert.assertEquals("plan", created?.kind)
    }

    @Test
    fun lightThemeRendersFinanceWithReadableSurfaceColors() {
        compose.setContent {
            ExpeditionTheme(false) {
                Surface(Modifier.fillMaxSize()) {
                    FinanceDashboard(PreviewData.items, PreviewData.money, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("今月の収支差額").assertIsDisplayed()
        capture("finance-light")
    }
}
