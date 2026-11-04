package jp.local.liveexpedition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExpeditionTheme(
    dark: Boolean = true,
    mode: String = if (dark) "charcoal" else "light",
    accent: String = "blue",
    content: @Composable () -> Unit,
) {
    val primary = DesignTokens.accent(accent)
    val background = if (mode == "black") Color.Black else DesignTokens.Background
    val scheme =
        if (dark)
            darkColorScheme(
                primary = primary,
                onPrimary = DesignTokens.Background,
                primaryContainer = primary.copy(alpha = .24f),
                onPrimaryContainer = Color.White,
                secondary = primary,
                secondaryContainer = DesignTokens.Input,
                onSecondaryContainer = Color.White,
                background = background,
                surface = background,
                surfaceVariant = DesignTokens.Card,
                onSurface = Color(0xFFF2F3F7),
                onSurfaceVariant = DesignTokens.Muted,
                outline = Color(0xFF68707C),
                error = DesignTokens.Danger,
                surfaceContainerLowest = background,
                surfaceContainerLow = DesignTokens.Input,
                surfaceContainer = DesignTokens.Card,
                surfaceContainerHigh = DesignTokens.Card,
                surfaceContainerHighest = DesignTokens.Card,
                surfaceTint = primary,
            )
        else
            lightColorScheme(
                primary =
                    when (accent) {
                        "mint" -> DesignTokens.LightSuccess
                        "violet" -> Color(0xFF694294)
                        else -> Color(0xFF215C9E)
                    },
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCEAFF),
                onPrimaryContainer = Color(0xFF193B64),
                secondary = Color(0xFF315B88),
                secondaryContainer = Color(0xFFE5EAF2),
                background = Color(0xFFF4F6FA),
                surface = Color.White,
                surfaceVariant = Color(0xFFE7EBF1),
                onSurface = Color(0xFF1B2533),
                onSurfaceVariant = Color(0xFF525C6B),
                outline = Color(0xFF727B89),
                error = DesignTokens.LightDanger,
                surfaceContainerLow = Color.White,
                surfaceContainer = Color(0xFFE7EBF1),
                surfaceContainerHigh = Color(0xFFE7EBF1),
                surfaceContainerHighest = Color(0xFFDCE1E9),
            )
    MaterialTheme(
        colorScheme = scheme,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(22.dp),
            ),
        content = content,
    )
}

@Composable
fun semanticColor(meaning: Meaning): Color {
    val light = MaterialTheme.colorScheme.onSurface.luminance() < .5f
    return when (meaning) {
        Meaning.POSITIVE -> if (light) DesignTokens.LightSuccess else DesignTokens.Success
        Meaning.WARNING -> if (light) DesignTokens.LightWarning else DesignTokens.Warning
        Meaning.NEGATIVE -> MaterialTheme.colorScheme.error
        Meaning.PRIMARY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    if (onClick != null)
        Card(onClick = onClick, modifier = modifier, colors = colors) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    else
        Card(modifier = modifier, colors = colors) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) =
    Text(
        text,
        modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

@Composable
fun SectionTitle(text: String) =
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )

@Composable
fun StatusPill(text: String) {
    val color = semanticColor(UiPolicy.meaning(text))
    Surface(color = color.copy(alpha = .15f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = color,
        )
    }
}

@Composable
fun AccentChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected,
        onClick,
        label = label,
        shape = RoundedCornerShape(24.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
    )
}

fun tileIcon(key: String): ImageVector =
    when (key) {
        "money",
        "accounts" -> Icons.Outlined.AccountBalanceWallet
        "credit" -> Icons.Outlined.CreditCard
        "transactions" -> Icons.Outlined.ReceiptLong
        "live" -> Icons.Outlined.ConfirmationNumber
        "plan" -> Icons.Outlined.Flight
        "subscription" -> Icons.Outlined.CloudQueue
        "movement" -> Icons.Outlined.Train
        "hotel" -> Icons.Outlined.Bed
        "venue" -> Icons.Outlined.Place
        else -> Icons.Outlined.CalendarMonth
    }

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(icon, null, Modifier.padding(22.dp).size(38.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Caption(body)
        FilledTonalButton(onClick = onAction) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(action)
        }
    }
}
