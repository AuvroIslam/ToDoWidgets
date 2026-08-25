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
    val scrollable: Boolean,
    val rowHeight: Dp,
    val controlSize: Dp,
    val outerPadding: Dp,
    val titleSize: TextUnit,
    val taskSize: TextUnit,
    val taskMaxLines: Int,
) {
    companion object {
        /** Below this width there is no room for a delete control next to the text. */
        private val WIDE = 240.dp

        /** Below this height a scrolling list with a footer would leave nothing to scroll. */
        private val TALL_ENOUGH = 240.dp

        private val PANEL_WIDTH = 300.dp
        private val PANEL_HEIGHT = 460.dp

        fun forSize(size: DpSize): WidgetSpec {
            val w = size.width
            val h = size.height
            return when {
                w >= PANEL_WIDTH && h >= PANEL_HEIGHT -> panel()
                w >= WIDE && h >= TALL_ENOUGH -> tall()
                w >= WIDE -> standard(h)
                else -> compact(h)
            }
        }

        /** 2x2. Just enough to see what is next and tick it off. */
        private fun compact(height: Dp) = WidgetSpec(
            layout = WidgetLayout.COMPACT,
            maxTasks = rowsThatFit(height, padding = 10, header = 46, row = 36, footer = 0, cap = 6),
            showDeleteButton = false,
            showCompletedSection = false,
            showFooter = false,
            showListControls = false,
            scrollable = false,
            rowHeight = 36.dp,
            controlSize = 36.dp,
            outerPadding = 10.dp,
            titleSize = 14.sp,
            taskSize = 13.sp,
            taskMaxLines = 1,
        )

        /** 4x2. Wide enough for a real title bar and a delete affordance per row. */
        private fun standard(height: Dp) = WidgetSpec(
            layout = WidgetLayout.STANDARD,
            maxTasks = rowsThatFit(height, padding = 12, header = 48, row = 44, footer = 0, cap = 8),
            showDeleteButton = true,
            showCompletedSection = false,
            showFooter = false,
            showListControls = false,
            scrollable = false,
            rowHeight = 44.dp,
            controlSize = 44.dp,
            outerPadding = 12.dp,
            titleSize = 16.sp,
            taskSize = 14.sp,
            taskMaxLines = 1,
        )

        /**
         * 4x4. A list you actually work from: scrolls, shows completed, has list controls.
         *
         * No row budget to compute — from here up the list scrolls, so the cap only has to be
         * past anything a person will realistically put in one list.
         */
        private fun tall() = WidgetSpec(
            layout = WidgetLayout.TALL,
            maxTasks = 60,
            showDeleteButton = true,
            showCompletedSection = true,
            showFooter = true,
            showListControls = true,
            scrollable = true,
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
            maxTasks = 120,
            showDeleteButton = true,
            showCompletedSection = true,
            showFooter = true,
            showListControls = true,
            scrollable = true,
            rowHeight = 52.dp,
            controlSize = 50.dp,
            outerPadding = 14.dp,
            titleSize = 19.sp,
            taskSize = 15.sp,
            taskMaxLines = 2,
        )

        /**
         * How many fixed-height rows fit in the space left over after the widget's own padding,
         * the header and the footer. Getting the padding into this sum matters: without it the
         * last row is laid out past the bottom edge and the launcher clips it in half.
         *
         * Always at least one, so a squashed widget still shows the next task rather than nothing.
         */
        private fun rowsThatFit(
            height: Dp,
            padding: Int,
            header: Int,
            row: Int,
            footer: Int,
            cap: Int,
        ): Int {
            val usable = height.value - (padding * 2) - header - footer
            return (usable / row).toInt().coerceIn(1, cap)
        }
    }
}
