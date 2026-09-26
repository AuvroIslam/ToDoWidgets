package com.simpletodo.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetLinger"

/**
 * Keeps a task ticked on the widget in its row for a few seconds before it is put away.
 *
 * The whole row is the tick target (see TaskRow), which is what makes it easy to hit — and easy to
 * hit by mistake. Without this a mis-tapped task vanished into "completed" at once, and the user
 * could not even tell which one it had been. Lingering in place, struck through, it is plainly
 * visible, and the undo is the same tap in the same spot.
 *
 * The expiry is stored alongside the ids so that the widget never trusts a stale set: if the
 * clean-up job below is ever late or lost, the next render ignores an expired linger on its own.
 */
object WidgetLinger {

    const val DURATION_MS = 4_000L

    private const val KEY_APP_WIDGET_ID = "appWidgetId"
    private const val KEY_KIND = "widgetKind"

    /** The tasks still lingering on this widget at [now]. */
    fun lingering(prefs: Preferences?, now: Long): Set<String> {
        if (prefs == null) return emptySet()
        val until = prefs[WidgetPrefs.JUST_DONE_UNTIL] ?: return emptySet()
        return if (now < until) prefs[WidgetPrefs.JUST_DONE].orEmpty() else emptySet()
    }

    /**
     * Records the outcome of a tap. A task that was just ticked joins the set and restarts the
     * clock for all of them, so ticking through several tasks in a row keeps every one of them in
     * view until the user stops; a task that was just unticked simply leaves it.
     */
    fun record(prefs: MutablePreferences, taskId: String, nowDone: Boolean, now: Long) {
        val current = lingering(prefs, now)
        val next = if (nowDone) current + taskId else current - taskId
        if (next.isEmpty()) {
            clear(prefs)
        } else {
            prefs[WidgetPrefs.JUST_DONE] = next
            if (nowDone) prefs[WidgetPrefs.JUST_DONE_UNTIL] = now + DURATION_MS
        }
    }

    fun clear(prefs: MutablePreferences) {
        prefs.remove(WidgetPrefs.JUST_DONE)
        prefs.remove(WidgetPrefs.JUST_DONE_UNTIL)
    }

    /**
     * Repaints the widget once the linger is over. A widget cannot run a timer of its own, and a
     * delay inside the tap's callback would die with the broadcast that delivered it, so the
     * repaint is handed to WorkManager — the same scheduler Glance itself renders through. One
     * job per widget, replaced on every tick, so only the last tap's deadline counts.
     */
    fun scheduleClear(context: Context, glanceId: GlanceId, kind: WidgetKind) {
        val appWidgetId = TodoWidget.resolveAppWidgetId(context, glanceId)
        if (!TodoWidget.isRealWidget(appWidgetId)) return
        val request = OneTimeWorkRequestBuilder<WidgetLingerWorker>()
            .setInitialDelay(DURATION_MS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_APP_WIDGET_ID to appWidgetId, KEY_KIND to kind.key))
            .build()
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_linger_$appWidgetId", ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.w(TAG, "Could not schedule linger clean-up", it) }
    }

    internal suspend fun finish(context: Context, appWidgetId: Int, kind: WidgetKind) {
        val glanceId = runCatching {
            GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        }.getOrNull() ?: return
        runCatching {
            updateAppWidgetState(context, glanceId) { prefs ->
                // A tick that landed after this job was queued has its own, later deadline.
                if (lingering(prefs, System.currentTimeMillis()).isEmpty()) clear(prefs)
            }
            widgetFor(kind).update(context, glanceId)
        }.onFailure { Log.w(TAG, "Could not put lingering tasks away", it) }
    }

    internal fun appWidgetIdOf(params: WorkerParameters): Int =
        params.inputData.getInt(KEY_APP_WIDGET_ID, TodoWidget.INVALID_WIDGET_ID)

    internal fun kindOf(params: WorkerParameters): WidgetKind =
        WidgetKind.fromKey(params.inputData.getString(KEY_KIND))
}

/** Runs [WidgetLinger.finish] once a linger has run its course. */
class WidgetLingerWorker(
    context: Context,
    private val params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appWidgetId = WidgetLinger.appWidgetIdOf(params)
        if (TodoWidget.isRealWidget(appWidgetId)) {
            WidgetLinger.finish(applicationContext, appWidgetId, WidgetLinger.kindOf(params))
        }
        return Result.success()
    }
}
