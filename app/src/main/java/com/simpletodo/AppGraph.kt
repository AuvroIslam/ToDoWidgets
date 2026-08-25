package com.simpletodo

import android.content.Context
import androidx.datastore.core.DataStore
import com.simpletodo.data.TodoRepository
import com.simpletodo.data.TodoSnapshot
import com.simpletodo.data.TodoStore
import com.simpletodo.data.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide singletons.
 *
 * Widget broadcasts can start the process without [TodoApplication.onCreate] having reached the
 * point where a field would be assigned, so the graph is built lazily from whatever [Context] is
 * at hand rather than depending on Application init order.
 */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store: DataStore<TodoSnapshot> = TodoStore.create(applicationContext, appScope)
    val repository: TodoRepository = TodoRepository(store)
    val theme: ThemePreference = ThemePreference(applicationContext)
}

object AppGraph {

    @Volatile
    private var container: AppContainer? = null

    fun get(context: Context): AppContainer {
        container?.let { return it }
        return synchronized(this) {
            container ?: AppContainer(context).also { container = it }
        }
    }

    fun repository(context: Context): TodoRepository = get(context).repository

    /** Test seam: lets instrumented tests swap in a container backed by a temporary file. */
    fun setForTest(replacement: AppContainer?) {
        synchronized(this) { container = replacement }
    }
}
