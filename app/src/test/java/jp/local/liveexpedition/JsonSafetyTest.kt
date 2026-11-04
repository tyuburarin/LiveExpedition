package jp.local.liveexpedition

import org.junit.Assert.*
import org.junit.Test

class JsonSafetyTest {
    @Test
    fun deepAndMalformedJsonRejectedBeforeParsing() {
        assertTrue(
            runCatching { JsonSafety.text(("[".repeat(20) + "]".repeat(20)).toByteArray()) }
                .isFailure
        )
        assertTrue(
            runCatching { JsonSafety.text(byteArrayOf(0xC0.toByte(), 0xAF.toByte())) }.isFailure
        )
        JsonSafety.checkDepth("{\"memo\":\"[ ] { } \\\" quoted\"}")
    }
}
