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
import com.simpletodo.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
     * Puts the lingering tasks away once their time is up, in two ways at once.
     *
     * The timer in this process is the one that normally fires: it is on time to the frame, and
     * a process that just handled a tap is not frozen for several seconds more. WorkManager is the
     * backstop for a process killed in between — it cannot be the main path, because on phones
     * that throttle background work its "4 seconds" can turn into minutes, leaving a ticked task
     * sitting in its row. Whichever runs second finds nothing left to do. The job is unique per
     * widget and replaced on every tick, so only the last tap's deadline counts.
     */
    fun scheduleClear(context: Context, glanceId: GlanceId) {
        val appWidgetId = TodoWidget.resolveAppWidgetId(context, glanceId)
        if (!TodoWidget.isRealWidget(appWidgetId)) return
        val appContext = context.applicationContext
        AppGraph.get(appContext).appScope.launch {
            delay(DURATION_MS)
            finish(appContext, appWidgetId)
        }
        val request = OneTimeWorkRequestBuilder<WidgetLingerWorker>()
            .setInitialDelay(DURATION_MS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_APP_WIDGET_ID to appWidgetId))
            .build()
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_linger_$appWidgetId", ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.w(TAG, "Could not schedule linger clean-up", it) }
    }

    internal suspend fun finish(context: Context, appWidgetId: Int) {
        val glanceId = runCatching {
            GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        }.getOrNull() ?: return
        runCatching {
            updateAppWidgetState(context, glanceId) { prefs ->
                // A tick that landed after this job was queued has its own, later deadline.
                if (lingering(prefs, System.currentTimeMillis()).isEmpty()) clear(prefs)
            }
            WidgetRenderer.render(context, listOf(appWidgetId))
        }.onFailure { Log.w(TAG, "Could not put lingering tasks away", it) }
    }

    internal fun appWidgetIdOf(params: WorkerParameters): Int =
        params.inputData.getInt(KEY_APP_WIDGET_ID, TodoWidget.INVALID_WIDGET_ID)
}

/** Runs [WidgetLinger.finish] once a linger has run its course. */
class WidgetLingerWorker(
    context: Context,
    private val params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appWidgetId = WidgetLinger.appWidgetIdOf(params)
        if (TodoWidget.isRealWidget(appWidgetId)) {
            WidgetLinger.finish(applicationContext, appWidgetId)
        }
        return Result.success()
    }
}
