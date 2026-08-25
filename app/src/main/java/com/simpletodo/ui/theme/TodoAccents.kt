package com.simpletodo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simpletodo.data.ACCENT_COUNT

/**
 * The list accent palette. Shared by the app and the widgets so a list looks the same in both.
 *
 * Every accent exists in two tones because a pastel cannot do both jobs. [colors] is the pastel
 * itself and is used wherever the accent is a *fill* — a selected chip, a ticked checkbox, a
 * progress bar, the widget's add button. [deepColors] is the same hue taken far enough down to be
 * read as *text* on a light background, where the pastel would be all but invisible.
 */
object TodoAccents {

    /** The pastel fills. Light enough that [onAccent] sits on all six at better than 8:1. */
    val colors: List<Color> = listOf(
        Color(0xFFFBD87A), // amber — the brand colour, and the default for the first list
        Color(0xFFF7A9A0), // coral
        Color(0xFFC6ABF5), // violet
        Color(0xFFA3C4F7), // blue
        Color(0xFF97DDC0), // green
        Color(0xFFC3C9D4), // slate
    )

    /**
     * The same six hues as ink. Each is deep enough to clear 4.5:1 twice over: on a white surface,
     * and — the harder case — on its *own* pastel, which is what the widget's tonal header band
     * asks for when it puts a green title on a green bar.
     */
    val deepColors: List<Color> = listOf(
        Color(0xFF7A5400), // amber
        Color(0xFF8F2418), // coral
        Color(0xFF58309F), // violet
        Color(0xFF154A96), // blue
        Color(0xFF06553B), // green
        Color(0xFF414B59), // slate
    )

    val names: List<String> = listOf("Amber", "Coral", "Violet", "Blue", "Green", "Slate")

    /** Label colour for anything painted on top of a pastel fill. */
    val onAccent: Color = Color(0xFF2A2418)

    init {
        require(colors.size == ACCENT_COUNT) { "Accent palette must have $ACCENT_COUNT entries" }
        require(deepColors.size == ACCENT_COUNT) { "Deep palette must have $ACCENT_COUNT entries" }
    }

    fun colorAt(index: Int): Color = colors[index.mod(colors.size)]

    fun deepAt(index: Int): Color = deepColors[index.mod(deepColors.size)]

    fun nameAt(index: Int): String = names[index.mod(names.size)]
}

/**
 * The accent as a text colour: the deep tone on a light theme, the pastel on a dark one. Which way
 * round is decided from the surface rather than from a flag, so it stays right inside anything that
 * overrides the scheme locally.
 */
@Composable
fun accentTextColor(index: Int): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        TodoAccents.colorAt(index)
    } else {
        TodoAccents.deepAt(index)
    }
