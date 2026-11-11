package com.simpletodo.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simpletodo.AppGraph
import com.simpletodo.data.DailyReset
import java.util.concurrent.TimeUnit

private const val TAG = "DailyResetRunner"

/**
 * Makes "resets every morning" lists actually reset, whether or not the app is opened.
 *
 * No single trigger can be relied on across phones, so the reset is cheap, idempotent, and asked
 * for from everywhere that already wakes the process: app start, the app coming to the front, the
 * system's periodic widget update, and a job queued for the next reset time. Whichever arrives
 * first after 4 am does the work; the rest find the lists already stamped with today.
 */
object DailyResetRunner {

    private const val WORK_NAME = "daily_list_reset"

    /** Resets any list that is due, then makes sure tomorrow's job is queued. Never throws. */
    suspend fun run(context: Context, fromWorker: Boolean = false) {
        val appContext = context.applicationContext
        val repository = AppGraph.repository(appContext)
        runCatching {
            repository.resetDailyLists(DailyReset.dayOf(System.currentTimeMillis()))
            if (repository.current().lists.any { it.resetsDaily }) schedule(appContext, fromWorker)
        }.onFailure { Log.w(TAG, "Daily reset failed", it) }
    }

    private fun schedule(context: Context, fromWorker: Boolean) {
        // A minute past the hour, so a job that fires a touch early still lands on the new day.
        val delay = DailyReset.millisUntilNext(System.currentTimeMillis()) + 60_000L
        val request = OneTimeWorkRequestBuilder<DailyResetWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        // From inside the running job the next one has to be chained on: replacing a unique job
        // with itself would cancel the very work doing the scheduling.
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}

class DailyResetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DailyResetRunner.run(applicationContext, fromWorker = true)
        runCatching { WidgetRenderer.renderAll(applicationContext) }
        return Result.success()
    }
}
