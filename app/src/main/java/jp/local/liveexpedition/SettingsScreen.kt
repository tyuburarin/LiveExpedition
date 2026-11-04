@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.local.liveexpedition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Caption(description)
        }
        Switch(value, onChange)
    }
}

@Composable
fun SettingsScreen(model: AppModel) {
    var section by rememberSaveable { mutableStateOf("") }
    var confirmOff by remember { mutableStateOf(false) }
    val theme by model.theme.collectAsState(initial = "charcoal")
    val accent by model.accent.collectAsState(initial = "blue")
    val lock by model.lockEnabled.collectAsState(initial = true)
    val secure by model.secure.collectAsState(initial = true)
    val swipe by model.screenSwipe.collectAsState(initial = false)
    val slide by model.tabSlide.collectAsState(initial = true)
    val home by model.homePage.collectAsState(initial = 0)
    BackHandler(section.isNotEmpty()) { section = "" }
    Column(Modifier.fillMaxSize()) {
        if (section.isNotEmpty())
            TextButton(onClick = { section = "" }) {
                Icon(Icons.Outlined.ChevronLeft, null)
                Text("設定一覧へ")
            }
        if (section == "data") {
            BackupSettings(model)
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (section.isEmpty())
                listOf(
                        "theme" to "表示・テーマ",
                        "navigation" to "ナビゲーション",
                        "home" to "ホーム",
                        "privacy" to "認証・プライバシー",
                        "data" to "データ管理",
                    )
                    .forEach { (key, label) ->
                        Panel(Modifier.fillMaxWidth(), onClick = { section = key }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label)
                                Icon(Icons.Outlined.ChevronRight, null)
                            }
                        }
                    }
            when (section) {
                "theme" -> {
                    SectionTitle("表示・テーマ")
                    val options =
                        linkedMapOf(
                            "system" to "システム",
                            "light" to "ライト",
                            "charcoal" to "チャコール",
                            "black" to "ブラック",
                        )
                    SelectionField(
                        "テーマ",
                        options[theme].orEmpty(),
                        options.values.toList(),
                        { label ->
                            model.text("theme", options.entries.first { it.value == label }.key)
                        },
                    )
                    SelectionField(
                        "アクセントカラー",
                        mapOf("blue" to "ブルー", "mint" to "ミント", "violet" to "ラベンダー")[accent]
                            .orEmpty(),
                        listOf("ブルー", "ミント", "ラベンダー"),
                        {
                            model.text(
                                "accent",
                                when (it) {
                                    "ミント" -> "mint"
                                    "ラベンダー" -> "violet"
                                    else -> "blue"
                                },
                            )
                        },
                    )
                    Caption("成功・注意・危険の色は意味に合わせて区別します。")
                }
                "navigation" -> {
                    SectionTitle("ナビゲーション")
                    SettingSwitch(
                        "画面スワイプでタブ切り替え",
                        "初期OFF。子画面の横スクロールやドラッグを優先します。",
                        swipe,
                        { model.bool("screen_swipe", it) },
                    )
                    SettingSwitch(
                        "タブ長押しスライド",
                        "初期ON。指を離した有効なタブに切り替えます。",
                        slide,
                        { model.bool("tab_slide", it) },
                    )
                    Caption("アニメーションはComposeのシステム動作倍率に従います。OSの端からの戻るジェスチャー領域では画面スワイプを開始しません。")
                }
                "home" -> {
                    SectionTitle("ホーム")
                    SelectionField(
                        "ホームの初期表示",
                        listOf("今日", "予定一覧", "カレンダー")[home],
                        listOf("今日", "予定一覧", "カレンダー"),
                        {
                            model.text(
                                "home_page",
                                listOf("今日", "予定一覧", "カレンダー").indexOf(it).toString(),
                            )
                        },
                    )
                    TextButton(onClick = { model.tiles(Tiles.defaults) }) { Text("タイルを初期配置に戻す") }
                    TextButton(onClick = { model.bool("hidden", true) }) { Text("すべての金額を隠す") }
                    Caption("タイルの長押しでサイズ・表示内容・非表示を変更できます。")
                }
                "privacy" -> {
                    SectionTitle("認証・プライバシー")
                    SettingSwitch(
                        "アプリを開くたびにロック",
                        "初期ON。OFFでもDB・画像・バックアップの暗号化は維持します。",
                        lock,
                        { if (it) model.bool("lock_enabled", true) else confirmOff = true },
                    )
                    SettingSwitch(
                        "スクリーンショットを保護",
                        "最近使ったアプリの画面は常に保護します。",
                        secure,
                        { model.bool("secure", it) },
                    )
                    Caption("端末の画面ロック・OSの安全性は別途維持してください。認証情報やパスフレーズを保存しません。")
                }
            }
            Caption("LiveExpedition 0.3 · オフライン版\n詳細なセキュリティ監査・実機受入は別工程です。")
        }
    }
    if (confirmOff)
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("アプリ起動時のロックをOFFにしますか？") },
            text = { Text("次回から端末認証せずアプリを開けます。端末を操作できる人が内容を見られます。暗号化と画面保護は維持します。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.bool("lock_enabled", false)
                        confirmOff = false
                    }
                ) {
                    Text("理解してOFFにする")
                }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("ONを維持") } },
        )
}
