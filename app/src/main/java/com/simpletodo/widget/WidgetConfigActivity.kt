package com.simpletodo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simpletodo.AppGraph
import com.simpletodo.data.TodoList
import com.simpletodo.ui.NameListDialog
import com.simpletodo.ui.theme.SimpleTodoTheme
import com.simpletodo.ui.theme.TodoAccents
import kotlinx.coroutines.launch

/**
 * Chooses which list a widget instance shows.
 *
 * Doubles as the reconfiguration screen: the same activity is launched from the widget's own
 * "change list" control, which is why it never assumes it was started by the launcher.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set first: if the user backs out of first-time configuration the launcher must be told
        // the widget was not configured, so it can remove the placeholder it created.
        setResult(RESULT_CANCELED, resultIntent())

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        val repository = AppGraph.repository(this)

        setContent {
            SimpleTodoTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle(initialValue = null)
                val lists = snapshot?.lists
                var selectedId by remember { mutableStateOf<String?>(null) }
                var showCreate by remember { mutableStateOf(false) }

                // Pre-select: the widget's current list, then the "add to home screen" hint from
                // the app, then simply the first list.
                LaunchedEffect(lists) {
                    if (selectedId == null && lists != null && lists.isNotEmpty()) {
                        val bound = snapshot?.widgetBindings?.get(appWidgetId)
                        val hint = WidgetPinning.consumeHint(this@WidgetConfigActivity)
                        selectedId = listOfNotNull(bound, hint)
                            .firstOrNull { candidate -> lists.any { it.id == candidate } }
                            ?: lists.first().id
                    }
                }

                WidgetConfigScreen(
                    lists = lists,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onCreateList = { showCreate = true },
                    onCancel = { finish() },
                    onConfirm = { listId -> confirm(listId) },
                )

                if (showCreate) {
                    NameListDialog(
                        title = "New list",
                        confirmLabel = "Create",
                        initialValue = "",
                        initialAccent = lists?.size ?: 0,
                        onDismiss = { showCreate = false },
                        onConfirm = { name, accent ->
                            showCreate = false
                            lifecycleScope.launch {
                                val id = repository.createList(name, accent)
                                if (id != null) selectedId = id
                            }
                        },
                    )
                }
            }
        }
    }

    private fun confirm(listId: String) {
        lifecycleScope.launch {
            AppGraph.repository(this@WidgetConfigActivity).bindWidget(appWidgetId, listId)
            WidgetSync.updateAllWidgets(this@WidgetConfigActivity)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java).apply {
                // Unique per widget so widgets do not share one cached PendingIntent.
                data = "simpletodo://widget/$appWidgetId/configure".toUri()
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    lists: List<TodoList>?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreateList: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Choose a list") })
        },
        bottomBar = {
            // Scaffold hands window insets to its content, not to a custom bottom bar, so the
            // buttons have to step around the navigation bar themselves.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { selectedId?.let(onConfirm) },
                    enabled = selectedId != null,
                ) { Text("Use this list") }
            }
        },
    ) { padding ->
        when {
            lists == null -> Box(Modifier.fillMaxSize().padding(padding))

            lists.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No lists yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Create a list and this widget will show it on your home screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCreateList) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create a list")
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    val selected = list.id == selectedId
                    ListItem(
                        modifier = Modifier
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(list.id) },
                            )
                            .semantics {
                                contentDescription =
                                    "${list.name}, ${list.activeCount} tasks left" +
                                    if (selected) ", selected" else ""
                            },
                        headlineContent = { Text(list.name) },
                        supportingContent = {
                            Text(
                                if (list.tasks.isEmpty()) "Empty list"
                                else "${list.activeCount} left · ${list.doneCount} done",
                            )
                        },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(TodoAccents.colorAt(list.accent)),
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    )
                }
                item {
                    TextButton(
                        onClick = onCreateList,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New list")
                    }
                }
            }
        }
    }
}
