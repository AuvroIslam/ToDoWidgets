package com.simpletodo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

private const val TAG = "WidgetPinning"

/**
 * "Add to home screen" from inside the app.
 *
 * The framework's pin flow cannot carry the chosen list to the widget, so the selection is left as
 * a hint that the configuration activity picks up and pre-selects. The hint is persisted (not held
 * in memory) because the launcher may start the configuration activity in a fresh process.
 */
object WidgetPinning {

    private const val PREFS = "widget_pin_hint"
    private const val KEY_LIST_ID = "listId"
    private const val KEY_SET_AT = "setAt"

    /**
     * A hint older than this is ignored. Without an expiry, a widget dropped from the picker weeks
     * later could claim a stale choice and show the wrong list.
     */
    private const val HINT_TTL_MS = 2 * 60 * 1000L

    fun isSupported(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true }
            .getOrDefault(false)

    fun setHint(context: Context, listId: String) {
        prefs(context).edit()
            .putString(KEY_LIST_ID, listId)
            .putLong(KEY_SET_AT, System.currentTimeMillis())
            .apply()
    }

    /** Reads and clears the hint. Returns null when there is none, or it has gone stale. */
    fun consumeHint(context: Context): String? {
        val prefs = prefs(context)
        val value = prefs.getString(KEY_LIST_ID, null) ?: return null
        val setAt = prefs.getLong(KEY_SET_AT, 0L)
        prefs.edit().remove(KEY_LIST_ID).remove(KEY_SET_AT).apply()
        val age = System.currentTimeMillis() - setAt
        return if (age in 0..HINT_TTL_MS) value else null
    }

    fun receiverFor(kind: WidgetKind): Class<out GlanceAppWidgetReceiver> = when (kind) {
        WidgetKind.SMALL -> SmallTodoWidgetReceiver::class.java
        WidgetKind.MEDIUM -> MediumTodoWidgetReceiver::class.java
        WidgetKind.LARGE -> LargeTodoWidgetReceiver::class.java
        WidgetKind.XLARGE -> XLargeTodoWidgetReceiver::class.java
    }

    suspend fun requestPin(context: Context, listId: String, kind: WidgetKind): Boolean {
        setHint(context, listId)
        return runCatching {
            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                receiver = receiverFor(kind),
                preview = widgetFor(kind),
                previewState = null,
                successCallback = null,
            )
        }.onFailure { Log.w(TAG, "Pin request failed", it) }.getOrDefault(false)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
