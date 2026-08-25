package com.simpletodo.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simpletodo.AppGraph
import com.simpletodo.data.Task
import com.simpletodo.data.TodoList
import com.simpletodo.data.TodoRepository
import com.simpletodo.data.TodoSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A deletion that can still be undone from the snackbar. */
sealed interface PendingUndo {
    data class DeletedTask(val listId: String, val task: Task, val index: Int) : PendingUndo
    data class DeletedList(val list: TodoList, val index: Int) : PendingUndo
}

class TodoViewModel(private val repository: TodoRepository) : ViewModel() {

    /** null means "not loaded yet", which is distinct from "loaded and empty". */
    val snapshot: StateFlow<TodoSnapshot?> =
        repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var selectedListId by mutableStateOf<String?>(null)
        private set

    var pendingUndo by mutableStateOf<PendingUndo?>(null)
        private set

    fun openList(listId: String?) {
        selectedListId = listId
    }

    fun consumeUndo(): PendingUndo? = pendingUndo.also { pendingUndo = null }

    fun clearUndo() {
        pendingUndo = null
    }

    // ---------------------------------------------------------------- lists

    fun createList(
        name: String,
        accent: Int? = null,
        onCreated: (String) -> Unit = {},
    ) = viewModelScope.launch {
        repository.createList(name, accent)?.let(onCreated)
    }

    fun renameList(listId: String, name: String) = viewModelScope.launch {
        repository.renameList(listId, name)
    }

    fun setAccent(listId: String, accent: Int) = viewModelScope.launch {
        repository.setListAccent(listId, accent)
    }

    fun deleteList(listId: String) = viewModelScope.launch {
        val current = repository.current()
        val index = current.lists.indexOfFirst { it.id == listId }
        val list = current.lists.getOrNull(index) ?: return@launch
        repository.deleteList(listId)
        if (selectedListId == listId) selectedListId = null
        pendingUndo = PendingUndo.DeletedList(list, index)
    }

    fun moveList(from: Int, to: Int) = viewModelScope.launch {
        repository.moveList(from, to)
    }

    // ---------------------------------------------------------------- tasks

    fun addTask(listId: String, title: String) = viewModelScope.launch {
        repository.addTask(listId, title)
    }

    fun toggleTask(listId: String, taskId: String) = viewModelScope.launch {
        repository.toggleTask(listId, taskId)
    }

    fun editTask(listId: String, taskId: String, title: String) = viewModelScope.launch {
        repository.editTask(listId, taskId, title)
    }

    fun deleteTask(listId: String, taskId: String) = viewModelScope.launch {
        val list = repository.current().listById(listId) ?: return@launch
        val index = list.tasks.indexOfFirst { it.id == taskId }
        val task = list.tasks.getOrNull(index) ?: return@launch
        repository.deleteTask(listId, taskId)
        pendingUndo = PendingUndo.DeletedTask(listId, task, index)
    }

    fun clearCompleted(listId: String) = viewModelScope.launch {
        repository.clearCompleted(listId)
    }

    fun moveActiveTask(listId: String, from: Int, to: Int) = viewModelScope.launch {
        repository.moveActiveTask(listId, from, to)
    }

    /** Commits a drag once, instead of writing to disk on every hovered row. */
    fun commitActiveOrder(listId: String, orderedActiveIds: List<String>) = viewModelScope.launch {
        repository.reorderActiveTasks(listId, orderedActiveIds)
    }

    fun undo(undo: PendingUndo) = viewModelScope.launch {
        when (undo) {
            is PendingUndo.DeletedTask -> repository.restoreTask(undo.listId, undo.task, undo.index)
            is PendingUndo.DeletedList -> repository.restoreList(undo.list, undo.index)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer { TodoViewModel(AppGraph.repository(appContext)) }
            }
        }

        /** Used by the config and quick-add screens, which need the same data but no navigation. */
        fun of(extras: CreationExtras, context: Context): TodoViewModel =
            TodoViewModel(AppGraph.repository(context.applicationContext))
    }
}
