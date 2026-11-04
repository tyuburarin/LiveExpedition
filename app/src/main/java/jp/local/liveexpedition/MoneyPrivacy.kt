package jp.local.liveexpedition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

data class MoneyVisibility(
    val hidden: Boolean = false,
    val overrides: Map<String, Boolean> = emptyMap(),
    val set: (String, Boolean) -> Unit = { _, _ -> },
) {
    fun isHidden(key: String) = overrides[key] ?: hidden
}

val LocalMoneyVisibility = staticCompositionLocalOf { MoneyVisibility() }

@Composable
fun MoneyAmount(
    key: String,
    value: Long,
    label: String = "",
    size: Int = 26,
    semanticSign: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val privacy = LocalMoneyVisibility.current
    val masked = privacy.isHidden(key)
    var fitted by remember(key, value, size, masked) { mutableIntStateOf(size) }
    Column(modifier) {
        if (label.isNotEmpty()) Caption(label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (masked) "••••" else "¥%,d".format(value),
                fontSize = fitted.sp,
                softWrap = false,
                onTextLayout = { if (it.didOverflowWidth && fitted > 11) fitted-- },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color =
                    if (semanticSign && !masked)
                        semanticColor(
                            if (value < 0) Meaning.NEGATIVE
                            else if (value > 0) Meaning.POSITIVE else Meaning.NEUTRAL
                        )
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { privacy.set(key, !masked) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (masked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    "${label.ifEmpty{ "金額" }}の表示切替",
                    Modifier.size(18.dp),
                )
            }
        }
    }
}
