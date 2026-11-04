package jp.local.liveexpedition

import androidx.compose.ui.graphics.Color
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object DesignTokens {
    val Background = Color(0xFF1B1D21)
    val Card = Color(0xFF34373D)
    val Input = Color(0xFF292C31)
    val Blue = Color(0xFF79AFFF)
    val Success = Color(0xFF86D4B1)
    val Warning = Color(0xFFF0BE79)
    val Danger = Color(0xFFF29BA7)
    val LightSuccess = Color(0xFF176243)
    val LightWarning = Color(0xFF76500D)
    val LightDanger = Color(0xFF9F263C)
    val Muted = Color(0xFFADB2BC)
    val chart = listOf(Blue, Color(0xFFC4B1A6), Success, Color(0xFFBEC3CE), Warning)

    fun tileAccent(key: String) =
        when (key) {
            "money",
            "accounts" -> Success
            "live",
            "venue" -> Color(0xFFC3A5F7)
            "plan",
            "movement",
            "hotel" -> Color(0xFF83CEDC)
            "subscription" -> Color(0xFFDCA3C6)
            else -> Blue
        }

    fun accent(name: String) =
        when (name) {
            "mint" -> Success
            "violet" -> Color(0xFFC3A5F7)
            else -> Blue
        }
}

enum class Meaning {
    POSITIVE,
    WARNING,
    NEGATIVE,
    PRIMARY,
    NEUTRAL,
}

object UiPolicy {
    fun meaning(text: String) =
        when {
            text.contains("超過") || text.contains("落選") || text.contains("マイナス") -> Meaning.NEGATIVE
            text in listOf("予約済", "支払済み", "支払済", "引落し済み", "当選", "確定", "採用中") -> Meaning.POSITIVE
            text.contains("要確認") ||
                text.contains("未予約") ||
                text.contains("未引落し") ||
                text.contains("未確定") -> Meaning.WARNING
            text in listOf("未予約", "検討中", "未確定", "要確認", "未払い", "期限間近") -> Meaning.WARNING
            text in listOf("申込済み", "申込中", "受付中", "選択中") -> Meaning.PRIMARY
            else -> Meaning.NEUTRAL
        }

    fun date(value: String): String =
        runCatching {
                if (value.length == 7)
                    YearMonth.parse(value)
                        .format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN))
                else if (value.contains('T'))
                    LocalDateTime.parse(value)
                        .format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）HH:mm", Locale.JAPAN))
                else
                    LocalDate.parse(value)
                        .format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPAN))
            }
            .getOrDefault(value)

    fun time(value: String) =
        runCatching {
                LocalDateTime.parse(value)
                    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.JAPAN))
            }
            .getOrDefault(value)

    fun shortDate(value: String) =
        runCatching {
                LocalDate.parse(value.take(10))
                    .format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN))
            }
            .getOrDefault(value)

    fun navTarget(x: Float, y: Float, width: Float, height: Float): Int? =
        if (width <= 0 || x < 0 || x >= width || y < 0 || y >= height) null
        else (x / width * 4).toInt().coerceIn(0, 3)

    fun swipeTarget(current: Int, dx: Float, dy: Float, width: Float, childConsumed: Boolean): Int {
        if (
            childConsumed ||
                width <= 0 ||
                kotlin.math.abs(dx) < width * .2f ||
                kotlin.math.abs(dx) < kotlin.math.abs(dy) * 1.5f
        )
            return current
        return (current + if (dx < 0) 1 else -1).coerceIn(0, 3)
    }
}
