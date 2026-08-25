package com.simpletodo.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The four widget flavours offered in the picker. Each has its own provider (and therefore its own
 * default placement size and resize bounds), but they all share one composition, so a widget
 * dropped as "Small" and stretched to full width becomes the extra-large layout rather than a
 * scaled-up small one.
 */
enum class WidgetKind(val key: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    XLARGE("xlarge"),
    ;

    companion object {
        fun fromKey(key: String?): WidgetKind = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/**
 * Representative sizes used only for the widget-picker preview, which cannot ask the launcher how
 * big it will be. The live widget uses `SizeMode.Exact` and its real measured size instead.
 */
object WidgetPreviewSizes {
    fun forKind(kind: WidgetKind): DpSize = when (kind) {
        WidgetKind.SMALL -> DpSize(180.dp, 200.dp)
        WidgetKind.MEDIUM -> DpSize(360.dp, 200.dp)
        WidgetKind.LARGE -> DpSize(360.dp, 400.dp)
        WidgetKind.XLARGE -> DpSize(360.dp, 500.dp)
    }
}

enum class WidgetLayout { COMPACT, STANDARD, TALL, PANEL }

/**
 * Everything the composition needs to know about how much room it has. Derived from the *measured*
 * size, never from the provider, so resizing in any direction picks the right layout.
 */
data class WidgetSpec(
    val layout: WidgetLayout,
    val maxTasks: Int,
    val showDeleteButton: Boolean,
    val showCompletedSection: Boolean,
    val showFooter: Boolean,
    val showListControls: Boolean,
    val rowHeight: Dp,
    val controlSize: Dp,
    val outerPadding: Dp,
    val titleSize: TextUnit,
    val taskSize: TextUnit,
    val taskMaxLines: Int,
) {
    companion object {
        /**
         * Upper bound on the rows handed to the launcher, the same at every size.
         *
         * Not a layout budget: every size scrolls, so a widget's height no longer decides how much
         * of a list it can show. This is a guard on the RemoteViews payload, which crosses a Binder
         * transaction to the launcher — a list long enough to blow that limit would take the whole
         * update with it. Well past what anyone keeps in one list, and far under the limit.
         */
        private const val MAX_ROWS = 120

        /** Below this width there is no room for a delete control next to the text. */
        private val WIDE = 240.dp

        /** Below this height there is no room for a footer on top of a usable list. */
        private val TALL_ENOUGH = 240.dp

        private val PANEL_WIDTH = 300.dp
        private val PANEL_HEIGHT = 460.dp

        fun forSize(size: DpSize): WidgetSpec {
            val w = size.width
            val h = size.height
            return when {
                w >= PANEL_WIDTH && h >= PANEL_HEIGHT -> panel()
                w >= WIDE && h >= TALL_ENOUGH -> tall()
                w >= WIDE -> standard()
                else -> compact()
            }
        }

        /** 2x2. Just enough to see what is next and tick it off — then scroll for the rest. */
        private fun compact() = WidgetSpec(
            layout = WidgetLayout.COMPACT,
            maxTasks = MAX_ROWS,
            showDeleteButton = false,
            showCompletedSection = false,
            showFooter = false,
            showListControls = false,
            rowHeight = 36.dp,
            controlSize = 36.dp,
            outerPadding = 10.dp,
            titleSize = 14.sp,
            taskSize = 13.sp,
            taskMaxLines = 1,
        )

        /** 4x2. Wide enough for a real title bar and a delete affordance per row. */
        private fun standard() = WidgetSpec(
            layout = WidgetLayout.STANDARD,
            maxTasks = MAX_ROWS,
            showDeleteButton = true,
            showCompletedSection = false,
            showFooter = false,
            showListControls = false,
            rowHeight = 44.dp,
            controlSize = 44.dp,
            outerPadding = 12.dp,
            titleSize = 16.sp,
            taskSize = 14.sp,
            taskMaxLines = 1,
        )

        /** 4x4. A list you actually work from: shows completed, and has the list controls. */
        private fun tall() = WidgetSpec(
            layout = WidgetLayout.TALL,
            maxTasks = MAX_ROWS,
            showDeleteButton = true,
            showCompletedSection = true,
            showFooter = true,
            showListControls = true,
            rowHeight = 46.dp,
            controlSize = 46.dp,
            outerPadding = 12.dp,
            titleSize = 17.sp,
            taskSize = 14.sp,
            taskMaxLines = 1,
        )

        /** Full width and tall. Same tools, but roomy: two-line titles and a progress bar. */
        private fun panel() = WidgetSpec(
            layout = WidgetLayout.PANEL,
            maxTasks = MAX_ROWS,
            showDeleteButton = true,
            showCompletedSection = true,
            showFooter = true,
            showListControls = true,
            rowHeight = 52.dp,
            controlSize = 50.dp,
            outerPadding = 14.dp,
            titleSize = 19.sp,
            taskSize = 15.sp,
            taskMaxLines = 2,
        )
    }
}
