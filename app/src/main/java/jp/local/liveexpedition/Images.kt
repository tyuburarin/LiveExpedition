package jp.local.liveexpedition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object Images {
    const val MAX_INPUT = 10 * 1024 * 1024
    const val MAX_STORED = 4 * 1024 * 1024

    private fun file(context: Context, id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(File(context.filesDir, "images").apply { mkdirs() }, "$id.enc")
    }

    fun read(context: Context, id: String): ByteArray =
        Crypto.open(file(context, id).readBytes(), "image:$id")

    fun write(context: Context, id: String, bytes: ByteArray) {
        require(bytes.size <= MAX_STORED)
        val atomic = android.util.AtomicFile(file(context, id))
        val out = atomic.startWrite()
        try {
            out.write(Crypto.seal(bytes, "image:$id"))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    fun remove(context: Context, id: String) {
        file(context, id).delete()
    }

    fun import(context: Context, uri: Uri): String {
        require(uri.scheme == "content")
        require(
            context.contentResolver.getType(uri) in listOf("image/jpeg", "image/png", "image/webp")
        ) {
            "JPEG・PNG・WebPを選択してください"
        }
        val bytes =
            requireNotNull(context.contentResolver.openInputStream(uri)).use {
                it.readNBytes(MAX_INPUT + 1)
            }
        require(bytes.size in 1..MAX_INPUT)
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            require(
                opts.outWidth in 1..16000 &&
                    opts.outHeight in 1..16000 &&
                    opts.outWidth.toLong() * opts.outHeight <= 40_000_000
            )
            require(opts.outMimeType in listOf("image/jpeg", "image/png", "image/webp"))
            val decodedOpts =
                BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (
                        opts.outWidth / inSampleSize > 2048 || opts.outHeight / inSampleSize > 2048
                    ) inSampleSize *= 2
                }
            val bitmap =
                requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodedOpts))
            val clean =
                try {
                    ByteArrayOutputStream().use { out ->
                        require(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out))
                        out.toByteArray()
                    }
                } finally {
                    bitmap.recycle()
                }
            return try {
                val id = UUID.randomUUID().toString()
                write(context, id, clean)
                id
            } finally {
                clean.fill(0)
            }
        } finally {
            bytes.fill(0)
        }
    }
}
