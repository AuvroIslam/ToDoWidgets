package com.simpletodo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The three appearance states an Android app is expected to offer. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        /** Unknown keys fall back to following the system, which is the safe default. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Where the user's appearance choice lives.
 *
 * Deliberately SharedPreferences rather than the DataStore the tasks use. This value is needed
 * *synchronously* by the first composition of every activity; reading it from a suspending source
 * would paint one frame in the system theme before correcting itself, which on a device set to
 * dark is a white flash on every single launch.
 *
 * It is also kept out of [TodoSnapshot] on purpose: that snapshot is the widgets' shared state, and
 * every write to it bumps a revision and pushes an update to all four widget sizes. Changing the
 * app's theme has nothing to say to a widget.
 */
class ThemePreference(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_MODE, null)))

    val mode: StateFlow<ThemeMode> = _mode

    fun set(mode: ThemeMode) {
        if (_mode.value == mode) return
        prefs.edit().putString(KEY_MODE, mode.key).apply()
        _mode.value = mode
    }

    private companion object {
        const val PREFS = "app_settings"
        const val KEY_MODE = "theme_mode"
    }
}
