package com.simpletodo.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val TAG = "WorkManagerKeepAlive"

/**
 * Keeps one job permanently scheduled, so WorkManager never switches its components off.
 *
 * WorkManager disables its RescheduleReceiver whenever it has no pending work and enables it again
 * the moment something is queued. Each toggle is a PACKAGE_CHANGED broadcast, and launchers treat
 * that as the app having changed: they re-inflate every widget from its loading layout and ask for
 * a fresh draw. With the linger clean-up queuing a job on every tick, that meant two such resets
 * per tap — the widget flashed to a spinner. Glance prevents exactly this with a job ten years out;
 * since widgets are drawn without Glance's sessions (see WidgetRenderer), that job is no longer
 * queued for us, so this is the same guard, held here.
 */
object WorkManagerKeepAlive {

    private const val WORK_NAME = "workmanager_keep_alive"

    fun ensure(context: Context) {
        val request = OneTimeWorkRequestBuilder<KeepAliveWorker>()
            .setInitialDelay(3650L, TimeUnit.DAYS)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }.onFailure { Log.w(TAG, "Could not schedule keep-alive", it) }
    }
}

/** Never meant to run: [WorkManagerKeepAlive.ensure] re-arms the job at every process start. */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}
