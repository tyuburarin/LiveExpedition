package jp.local.liveexpedition

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

data class OcrReport(
    val itemId: String,
    val text: String,
    val fields: Map<String, String>,
    val confidence: Int,
)

object OfflineOcr {
    @Synchronized
    fun recognize(ctx: Context, bitmap: Bitmap): Pair<String, Int> {
        val folder = File(ctx.noBackupFilesDir, "ocr/tessdata").apply { mkdirs() }
        for (name in listOf("jpn", "eng")) {
            val target = File(folder, "$name.traineddata")
            if (!target.exists()) {
                val atomic = android.util.AtomicFile(target)
                val out = atomic.startWrite()
                try {
                    ctx.assets.open("ocr/$name.traineddata").use { it.copyTo(out) }
                    atomic.finishWrite(out)
                } catch (e: Exception) {
                    atomic.failWrite(out)
                    throw e
                }
            }
        }
        val tess = TessBaseAPI()
        try {
            check(
                tess.init(
                    folder.parentFile!!.absolutePath + File.separator,
                    "jpn+eng",
                    TessBaseAPI.OEM_LSTM_ONLY,
                )
            ) {
                "OCRの初期化に失敗しました"
            }
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)
            return tess.getUTF8Text().orEmpty().take(8000) to tess.meanConfidence()
        } finally {
            tess.recycle()
        }
    }
}
