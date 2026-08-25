package com.simpletodo.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.min

/**
 * Long-press drag reordering for a [androidx.compose.foundation.lazy.LazyColumn].
 *
 * Only the [reorderableCount] items starting at [firstIndex] participate, which lets the caller
 * keep section headers and the completed section in the same list without them ever becoming drop
 * targets. Indices reported to [onMove] are relative to [firstIndex], so callers work in terms of
 * their own data, while [draggingItemIndex] stays absolute because that is what a lazy item knows
 * about itself.
 */
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val firstIndex: () -> Int,
    private val reorderableCount: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onSettle: () -> Unit,
) {
    private val reorderableRange: IntRange
        get() = firstIndex() until (firstIndex() + reorderableCount())

    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemInitialOffset = 0
    private var dragDelta by mutableFloatStateOf(0f)

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    /** Pixel offset to apply to the item currently under the finger. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo
            ?.let { draggingItemInitialOffset + dragDelta - it.offset }
            ?: 0f

    fun onDragStart(offsetY: Float) {
        val y = offsetY.toInt()
        val visible = listState.layoutInfo.visibleItemsInfo
        // The list is laid out with gaps between the cards. A press that lands in a gap belongs to
        // the nearest card rather than to nothing, otherwise the top and bottom few dp of every
        // row silently refuse to start a drag.
        val candidate = visible.firstOrNull { y in it.offset..(it.offset + it.size) }
            ?: visible.minByOrNull { min(abs(y - it.offset), abs(y - (it.offset + it.size))) }
            ?: return
        if (candidate.index !in reorderableRange) return
        draggingItemIndex = candidate.index
        draggingItemInitialOffset = candidate.offset
        dragDelta = 0f
    }

    fun onDragInterrupted() {
        val wasDragging = draggingItemIndex != null
        draggingItemIndex = null
        dragDelta = 0f
        if (wasDragging) onSettle()
    }

    fun onDrag(deltaY: Float) {
        dragDelta += deltaY
        val dragging = draggingItemIndex ?: return
        val info = draggingItemLayoutInfo ?: return
        val start = info.offset + draggingItemOffset
        val middle = start + info.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != dragging &&
                item.index in reorderableRange &&
                middle.toInt() in item.offset..(item.offset + item.size)
        } ?: return

        val base = firstIndex()
        onMove(dragging - base, target.index - base)
        draggingItemIndex = target.index
    }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState,
    firstIndex: Int,
    reorderableCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onSettle: () -> Unit,
): DragDropState {
    val firstState = rememberUpdatedState(firstIndex)
    val countState = rememberUpdatedState(reorderableCount)
    val moveState = rememberUpdatedState(onMove)
    val settleState = rememberUpdatedState(onSettle)
    return remember(listState) {
        DragDropState(
            listState = listState,
            firstIndex = { firstState.value },
            reorderableCount = { countState.value },
            onMove = { from, to -> moveState.value(from, to) },
            onSettle = { settleState.value() },
        )
    }
}

fun Modifier.reorderable(state: DragDropState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset.y) },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount.y)
        },
        onDragEnd = { state.onDragInterrupted() },
        onDragCancel = { state.onDragInterrupted() },
    )
}
