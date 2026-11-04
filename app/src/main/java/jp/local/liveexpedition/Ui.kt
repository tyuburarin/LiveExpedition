@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package jp.local.liveexpedition

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.*

internal fun defaults(kind: String): Map<String, String> =
    when (kind) {
        "transaction" -> mapOf("date" to LocalDate.now().toString(), "type" to "支出")
        "subscription" ->
            mapOf(
                "cycle" to "月額",
                "date" to LocalDate.now().toString(),
                "status" to "契約中",
                "renew" to "はい",
            )
        "ticket" -> mapOf("quantity" to "1")
        else -> emptyMap()
    }

@Composable
internal fun BackupSettings(model: AppModel) {
    val external = LocalExternalActions.current
    val dark by model.dark.collectAsState(initial = true)
    val secure by model.secure.collectAsState(initial = true)
    var pass by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("暗号化バックアップ", style = MaterialTheme.typography.titleLarge)
        Text(
            "16文字以上の固有のパスフレーズが必要です。忘れた場合は復号できません。復元は現在のデータを置き換えます。最大24MB・画像100枚の暗号化ファイルです。ファイル選択は2分以内に完了してください。"
        )
        OutlinedTextField(
            pass,
            { pass = it.take(256) },
            label = { Text("パスフレーズ（保存しません）") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = true,
        )
        Button(
            onClick = {
                external.export(pass.toCharArray())
                pass = ""
            },
            enabled = pass.length >= 16,
        ) {
            Text("暗号化して保存先を選ぶ")
        }
        OutlinedButton(onClick = { importing = true }, enabled = pass.length >= 16) {
            Text("バックアップから復元")
        }
        Text(
            "v0.3.0 preview · 通信機能なし\n本番利用前に実機のセキュリティ検証が必要です。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (importing)
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("既存データを置き換えますか？") },
            text = { Text("復元に成功すると、現在のデータはバックアップの内容に置き換わります。必要なら先に現在のデータをバックアップしてください。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        importing = false
                        external.restore(pass.toCharArray())
                        pass = ""
                    }
                ) {
                    Text("ファイルを選択")
                }
            },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("キャンセル") } },
        )
}
