package com.simpletodo

import android.app.Application
import android.util.Log
import com.simpletodo.widget.WidgetRenderer
import com.simpletodo.widget.WidgetSync
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

private const val TAG = "TodoApplication"

class TodoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val container = AppGraph.get(this)

        container.appScope.launch {
            runCatching { container.repository.seedIfFirstRun() }
                .onFailure { Log.e(TAG, "Seeding failed", it) }
            // onDeleted can be missed if the app was force-stopped or the launcher replaced, so
            // bindings for widgets the launcher no longer knows about are dropped on start-up.
            WidgetSync.reconcileBindings(this@TodoApplication)
        }

        // Every write redraws the widgets, here and now (see WidgetRenderer for why not through
        // Glance's update). `conflate` plus the short settle delay collapses bursts — adding five
        // tasks in a row — into one redraw, while still guaranteeing the *last* state is drawn.
        container.appScope.launch {
            container.repository.changes
                .conflate()
                .collect {
                    kotlinx.coroutines.delay(WidgetSync.COALESCE_MS)
                    runCatching { WidgetRenderer.renderAll(this@TodoApplication) }
                        .onFailure { Log.w(TAG, "Widget redraw failed", it) }
                }
        }
    }
}
