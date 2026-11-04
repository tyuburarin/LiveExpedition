package jp.local.liveexpedition

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

@Composable
fun ExpeditionTabs(selected: Int, onSelect: (Int) -> Unit, longSlide: Boolean = true) {
    var dragging by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableIntStateOf(selected) }
    val latestSelect by rememberUpdatedState(onSelect)
    val haptic = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(70.dp)
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val density = LocalDensity.current
            val tabWidth = maxWidth / 4
            val target =
                if (dragging) (point.x - width / 8).coerceIn(6f, width - width / 4 - 6f)
                else selected * width / 4 + 6
            val left by
                animateFloatAsState(
                    target,
                    if (dragging) snap() else tween(150),
                    label = "tab-capsule",
                )
            Box(
                Modifier.offset { IntOffset(left.toInt(), 6.dp.roundToPx()) }
                    .width(tabWidth - 12.dp)
                    .height(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) .3f else .16f),
                        RoundedCornerShape(22.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = .25f),
                        RoundedCornerShape(22.dp),
                    )
            )
            Row(
                Modifier.fillMaxSize().pointerInput(width, height, longSlide) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var up: PointerInputChange? = null
                        val tap =
                            if (longSlide)
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    up = waitForUpOrCancellation()
                                    true
                                }
                            else {
                                up = waitForUpOrCancellation()
                                true
                            }
                        if (tap == true) {
                            up?.let {
                                UiPolicy.navTarget(it.position.x, it.position.y, width, height)
                                    ?.let(latestSelect)
                                it.consume()
                            }
                        } else {
                            dragging = true
                            point = down.position
                            hover = UiPolicy.navTarget(point.x, point.y, width, height) ?: selected
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                val ended =
                                    drag(down.id) { change ->
                                        point = change.position
                                        change.consume()
                                        val next =
                                            UiPolicy.navTarget(point.x, point.y, width, height)
                                        if (next != null && next != hover) {
                                            hover = next
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                        }
                                    }
                                if (ended)
                                    UiPolicy.navTarget(point.x, point.y, width, height)
                                        ?.let(latestSelect)
                            } finally {
                                dragging = false
                            }
                        }
                    }
                }
            ) {
                listOf(
                        Icons.Outlined.Home,
                        Icons.Outlined.ConfirmationNumber,
                        Icons.Outlined.Flight,
                        Icons.Outlined.AccountBalanceWallet,
                    )
                    .forEachIndexed { i, icon ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().semantics {
                                role = Role.Tab
                                this.selected = selected == i
                                contentDescription = "${listOf("ホーム","ライブ","遠征","残高")[i]}タブ"
                                onClick {
                                    latestSelect(i)
                                    true
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val color =
                                if ((if (dragging) hover else selected) == i)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(icon, null, Modifier.size(25.dp), tint = color)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                listOf("ホーム", "ライブ", "遠征", "残高")[i],
                                fontSize = 11.sp,
                                color = color,
                            )
                        }
                    }
            }
        }
    }
}

fun Modifier.tabSwipe(enabled: Boolean, selected: Int, onSelect: (Int) -> Unit): Modifier =
    if (!enabled) this
    else
        pointerInput(enabled, selected) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                var dx = 0f
                var dy = 0f
                var consumed = false
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val movement = change.positionChangeIgnoreConsumed()
                    dx += movement.x
                    dy += movement.y
                    if (change.isConsumed && movement.getDistance() > 0f) consumed = true
                    pressed = change.pressed
                }
                val edge = 24.dp.toPx()
                if (down.position.x > edge && down.position.x < size.width - edge) {
                    val next =
                        UiPolicy.swipeTarget(selected, dx, dy, size.width.toFloat(), consumed)
                    if (next != selected) onSelect(next)
                }
            }
        }
