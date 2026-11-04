@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.local.liveexpedition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDateTime
import java.util.UUID

// Debug/preview only. Nothing here inserts data into Room or bypasses authentication.
object PreviewData {
    private fun id(name: String) = UUID.nameUUIDFromBytes(name.toByteArray()).toString()

    private fun item(name: String, kind: String, vararg pairs: Pair<String, String>) =
        Item(id(name), kind, mapOf(*pairs))

    val live =
        item(
            "live",
            "live",
            "title" to "福岡公演",
            "artist" to "○○",
            "tour" to "○○ツアー",
            "date" to "2026-11-22",
            "venue" to "マリンメッセ福岡",
            "start" to "2026-11-22T16:00",
            "end" to "2026-11-22T17:00",
            "finish" to "2026-11-22T20:00",
        )
    val ticket =
        item(
            "ticket",
            "ticket",
            "title" to "オフィシャル先行",
            "parent" to live.id,
            "quantity" to "1",
            "amount" to "8800",
            "fee" to "660",
            "status" to "申込済み",
            "result" to "当選",
            "due" to "2026-11-10T23:59",
        )
    val candidate =
        Itinerary.normalize(
            item(
                "candidate",
                "candidate",
                "title" to "福岡空港から会場へ",
                "segments" to
                    Itinerary.encode(
                        listOf(
                            Leg(
                                "電車",
                                "博多",
                                "福岡空港",
                                "2026-11-04T08:20",
                                "2026-11-04T08:34",
                                "280",
                                "祇園・東比恵",
                            ),
                            Leg("バス", "福岡空港", "会場", "2026-11-04T09:00", "2026-11-04T09:25", "500"),
                        )
                    ),
                "status" to "予約済",
                "priceState" to "確定",
                "direction" to "往路",
            )
        )
    val hotel =
        item(
            "hotel",
            "candidate",
            "title" to "福岡の宿泊",
            "type" to "ホテル",
            "start" to "2026-11-04T15:00",
            "end" to "2026-11-05T10:00",
            "from" to "博多",
            "to" to "ホテル",
            "amount" to "9800",
            "status" to "予約済",
            "priceState" to "確定",
        )
    val plan =
        item(
            "plan",
            "plan",
            "title" to "福岡遠征",
            "members" to "${candidate.id},${hotel.id}",
            "adopted" to "true",
        )
    val items =
        listOf(
            live,
            ticket,
            candidate,
            hotel,
            plan,
            item(
                "live2",
                "live",
                "title" to "東京公演",
                "artist" to "△△",
                "tour" to "△△ライブ",
                "date" to "2026-12-27",
                "venue" to "東京ドーム",
            ),
            item(
                "salary",
                "transaction",
                "title" to "給与",
                "date" to "2026-11-01",
                "type" to "収入",
                "amount" to "250000",
                "category" to "給与",
                "method" to "銀行振込",
            ),
            item(
                "expense1",
                "transaction",
                "title" to "生活費",
                "date" to "2026-11-02",
                "type" to "支出",
                "amount" to "104420",
                "category" to "生活費",
                "method" to "クレジットカード",
            ),
            item(
                "expense2",
                "transaction",
                "title" to "交通費",
                "date" to "2026-11-03",
                "type" to "支出",
                "amount" to "3280",
                "category" to "遠征",
                "method" to "電子マネー",
            ),
            item(
                "expense3",
                "transaction",
                "title" to "宿泊費",
                "date" to "2026-11-03",
                "type" to "支出",
                "amount" to "9800",
                "category" to "遠征",
                "method" to "現金",
            ),
            item(
                "budget",
                "budget",
                "title" to "今月の予算",
                "month" to "2026-11",
                "amount" to "180000",
            ),
            item(
                "sub1",
                "subscription",
                "title" to "音楽ストリーミング",
                "cycle" to "月額",
                "amount" to "1080",
                "date" to "2026-12-01",
                "status" to "契約中",
                "method" to "クレジットカード",
            ),
            item(
                "sub2",
                "subscription",
                "title" to "動画配信",
                "cycle" to "月額",
                "amount" to "1490",
                "date" to "2026-12-10",
                "status" to "契約中",
                "method" to "クレジットカード",
            ),
            item(
                "sub3",
                "subscription",
                "title" to "クラウドストレージ",
                "cycle" to "年額",
                "amount" to "7080",
                "date" to "2027-04-10",
                "status" to "契約中",
                "method" to "クレジットカード",
            ),
        )
    val money: (Long) -> String = { "¥%,d".format(it) }
}

@Composable
fun PreviewFrame(title: String, tab: Int = 0, content: @Composable () -> Unit) {
    ExpeditionTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                if (tab == 0) Icons.Outlined.Settings else Icons.Outlined.Settings,
                                null,
                            )
                        }
                    },
                )
            },
            bottomBar = { ExpeditionTabs(tab, {}) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}

@Preview(name = "普段のホーム", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun DashboardPreview() {
    PreviewFrame("ホーム") {
        DashboardScreen(Tiles.defaults, PreviewData.items, PreviewData.money, {}, { _, _ -> })
    }
}

@Preview(name = "予定ホーム", widthDp = 390, heightDp = 844)
@Composable
fun SchedulePreview() {
    PreviewFrame("ホーム") {
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

@Preview(name = "ライブ一覧", widthDp = 390, heightDp = 844)
@Composable
fun LivePreview() {
    PreviewFrame("ライブ", 1) { LiveScreen(PreviewData.items, PreviewData.money, {}, {}) }
}

@Preview(name = "遠征プラン比較", widthDp = 390, heightDp = 844)
@Composable
fun TripPreview() {
    PreviewFrame("遠征", 2) {
        TripPage(
            PreviewData.items,
            PreviewData.money,
            {},
            {},
            { _, _ -> },
            {},
            initialCompare = true,
        )
    }
}

@Preview(name = "収支", widthDp = 390, heightDp = 844)
@Composable
fun FinancePreview() {
    PreviewFrame("残高", 3) { FinanceDashboard(PreviewData.items, PreviewData.money, {}, {}, {}) }
}

@Preview(name = "サブスク", widthDp = 390, heightDp = 844)
@Composable
fun SubscriptionsPreview() {
    PreviewFrame("サブスク管理", 3) { SubscriptionScreen(PreviewData.items, PreviewData.money, {}, {}) }
}
