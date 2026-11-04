package jp.local.liveexpedition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

class ExternalActions(
    val export: (CharArray) -> Unit,
    val restore: (CharArray) -> Unit,
    val image: (Item) -> Unit,
    val permission: () -> Unit,
    val ocr: (Item) -> Unit = {},
)

val LocalExternalActions =
    staticCompositionLocalOf<ExternalActions> { error("ExternalActions missing") }

// Launchers live outside the locked/unlocked screen tree. Leaving for a system
// picker locks the app without unregistering the pending result callback.
@Composable
fun ExternalHost(model: AppModel, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf<CharArray?>(null) }
    var pendingImage by remember { mutableStateOf<Item?>(null) }
    var pendingOcr by remember { mutableStateOf(false) }
    LaunchedEffect(password) {
        if (password != null) {
            delay(120_000)
            password?.fill('\u0000')
            password = null
        }
    }
    DisposableEffect(Unit) { onDispose { password?.fill('\u0000') } }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val p = password
            password = null
            if (uri == null || p == null) p?.fill('\u0000')
            else
                scope.launch {
                    try {
                        val bytes = model.export(p)
                        try {
                            withContext(Dispatchers.IO) {
                                requireNotNull(ctx.contentResolver.openOutputStream(uri, "wt"))
                                    .use { it.write(bytes) }
                            }
                            model.message.value = "暗号化バックアップを保存しました"
                        } finally {
                            bytes.fill(0)
                        }
                    } catch (e: Exception) {
                        model.message.value = "保存できませんでした。パスフレーズ・画像サイズ・保存先を確認してください。"
                    } finally {
                        p.fill('\u0000')
                    }
                }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val p = password
            password = null
            if (uri == null || p == null) p?.fill('\u0000')
            else
                scope.launch {
                    try {
                        val bytes =
                            withContext(Dispatchers.IO) {
                                requireNotNull(ctx.contentResolver.openInputStream(uri)).use {
                                    val b = it.readNBytes(Crypto.MAX_BACKUP + 1)
                                    require(b.size <= Crypto.MAX_BACKUP)
                                    b
                                }
                            }
                        model.import(bytes, p)
                        model.message.value = "検証したバックアップを復元しました"
                    } catch (e: Exception) {
                        model.message.value = "復元できませんでした。形式・完全性・パスフレーズを確認してください。既存DBは保持されています。"
                    } finally {
                        p.fill('\u0000')
                    }
                }
        }
    val image =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val item = pendingImage
            pendingImage = null
            if (uri != null && item != null) {
                if (pendingOcr) model.recognize(item, uri) else model.importImage(item, uri)
            }
            pendingOcr = false
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            model.message.value = if (allowed) "通知を許可しました。通知をもう一度設定してください。" else "通知は許可されていません"
        }
    CompositionLocalProvider(
        LocalExternalActions provides
            ExternalActions(
                export = { p ->
                    password?.fill('\u0000')
                    password = p
                    export.launch("live-backup.enc")
                },
                restore = { p ->
                    password?.fill('\u0000')
                    password = p
                    restore.launch(arrayOf("application/octet-stream", "*/*"))
                },
                image = { item ->
                    pendingOcr = false
                    pendingImage = item
                    image.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                permission = { permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                ocr = { item ->
                    pendingOcr = true
                    pendingImage = item
                    image.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
    ) {
        content()
    }
}
