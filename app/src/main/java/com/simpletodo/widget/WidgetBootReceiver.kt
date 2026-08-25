package com.simpletodo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simpletodo.AppGraph
import kotlinx.coroutines.launch

private const val TAG = "WidgetBootReceiver"

/**
 * Re-pushes widget content after a device restart or an app update.
 *
 * The launcher restores widget instances itself, but their sessions do not survive a reboot, so
 * without this the first render would wait for whatever happens to poke the widget next.
 * `BOOT_COMPLETED` (not `LOCKED_BOOT_COMPLETED`) is used deliberately: task data lives in
 * credential-encrypted storage and is unreadable before the user unlocks.
 */
class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        AppGraph.get(appContext).appScope.launch {
            try {
                WidgetSync.reconcileBindings(appContext)
                WidgetSync.updateAllWidgets(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Post-boot widget refresh failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
