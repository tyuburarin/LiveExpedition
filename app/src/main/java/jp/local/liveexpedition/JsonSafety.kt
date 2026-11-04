package jp.local.liveexpedition

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object JsonSafety {
    fun text(bytes: ByteArray): String {
        require(bytes.size <= Crypto.MAX_BACKUP)
        val text =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        checkDepth(text)
        return text
    }

    fun checkDepth(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { c ->
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else
                when (c) {
                    '"' -> quoted = true
                    '[',
                    '{' -> {
                        depth++
                        require(depth <= 16) { "JSON nesting exceeds limit" }
                    }
                    ']',
                    '}' -> {
                        depth--
                        require(depth >= 0)
                    }
                }
        }
        require(depth == 0 && !quoted && !escaped)
    }
}
