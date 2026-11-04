package jp.local.liveexpedition

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.CancellationException

// One recognizer owns tap, stationary long-press and drag. A nested clickable
// Card would otherwise consume the stationary release before the drag recognizer.
internal suspend fun PointerInputScope.detectDashboardGestures(
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown()
    var up: PointerInputChange? = null
    val completed =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            up = waitForUpOrCancellation()
            true
        }
    if (completed == true) {
        up?.let {
            it.consume()
            onTap()
        }
    } else {
        val held = currentEvent.changes.firstOrNull { it.id == down.id && it.pressed }
        if (held != null) {
            onDragStart(held.position)
            try {
                if (
                    drag(down.id) { change ->
                        onDrag(change, change.positionChange())
                        change.consume()
                    }
                ) {
                    currentEvent.changes.forEach { if (it.changedToUp()) it.consume() }
                    onDragEnd()
                } else onDragCancel()
            } catch (e: CancellationException) {
                onDragCancel()
                throw e
            }
        }
    }
}
