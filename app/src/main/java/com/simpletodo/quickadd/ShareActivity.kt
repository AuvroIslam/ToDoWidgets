package com.simpletodo.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simpletodo.AppGraph
import com.simpletodo.data.TaskLinks
import com.simpletodo.data.TodoJson
import com.simpletodo.data.TodoLimits
import com.simpletodo.data.TodoList
import com.simpletodo.ui.theme.SimpleTodoTheme
import com.simpletodo.ui.theme.TodoAccents
import com.simpletodo.ui.theme.accentTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What another app handed over through the share sheet: a link, usually with some words around it.
 *
 * @param label the best title the share offered, or empty when it was only a link.
 */
internal data class SharedItem(val label: String, val url: String?)

/**
 * Reads an [Intent.ACTION_SEND] of plain text.
 *
 * Apps disagree on where the words go. A browser puts the page title in [Intent.EXTRA_SUBJECT] and
 * only the address in [Intent.EXTRA_TEXT]; LinkedIn and friends put a sentence and the link
 * together in the text. So the link is looked for in both, and the label is the subject when there
 * is one and whatever the text said besides the link otherwise.
 */
internal fun readShare(intent: Intent?): SharedItem? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()

    val url = TaskLinks.find(text) ?: TaskLinks.find(subject)
    val label = listOf(subject, text)
        .map { if (url == null) TodoJson.sanitizeTaskTitle(it) else TaskLinks.labelWithout(it, url) }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    return if (url == null && label.isEmpty()) null else SharedItem(label, url)
}

/**
 * The target for "Share → TodoWidget" from YouTube, Facebook, LinkedIn, a browser, anything that
 * shares text. Like [QuickAddActivity] it is a translucent sheet over the app the user came from,
 * so saving a post for later never takes them out of what they were doing.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = readShare(intent)
        if (shared == null) {
            finish()
            return
        }
        enableEdgeToEdge()

        val repository = AppGraph.repository(this)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        setContent {
            SimpleTodoTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle(initialValue = null)
                val lists = snapshot?.lists.orEmpty()

                // Whoever saves links tends to keep them in one "Watch later" style list, so the
                // sheet opens on the list they used last time rather than always on the first.
                var activeListId by remember { mutableStateOf(prefs.getString(KEY_LAST_LIST, null)) }
                var saved by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(lists) {
                    if (lists.isNotEmpty() && lists.none { it.id == activeListId }) {
                        activeListId = lists.first().id
                    }
                }

                val activeList = lists.firstOrNull { it.id == activeListId }
                val sheet = rememberSheetState()
                val view = LocalView.current

                ShareSheet(
                    sheet = sheet,
                    onDismissed = { finishWithoutTransition() },
                    shared = shared,
                    lists = lists,
                    activeList = activeList,
                    loaded = snapshot != null,
                    saved = saved,
                    error = error,
                    onSelectList = {
                        activeListId = it
                        error = null
                    },
                    onSave = { label ->
                        val list = activeList ?: return@ShareSheet
                        lifecycleScope.launch {
                            val id = repository.addTask(list.id, label, shared.url)
                            if (id == null) {
                                error = "“${list.name}” is full. Pick another list."
                                return@launch
                            }
                            prefs.edit { putString(KEY_LAST_LIST, list.id) }
                            view.confirmHaptic()
                            saved = true
                            delay(750)
                            sheet.dismiss()
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val PREFS = "share_sheet"
        const val KEY_LAST_LIST = "last_list_id"
    }
}

object ShareTags {
    const val SHEET = "share_sheet"
    const val FIELD = "share_field"
    const val SAVE = "share_save"
    const val LIST_CHIP = "share_list_chip"
}

@Composable
private fun ShareSheet(
    sheet: SheetState,
    onDismissed: () -> Unit,
    shared: SharedItem,
    lists: List<TodoList>,
    activeList: TodoList?,
    loaded: Boolean,
    saved: Boolean,
    error: String?,
    onSelectList: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    var label by remember { mutableStateOf(shared.label) }
    val isLink = shared.url != null
    // A link can be saved as it is; plain shared text is the task itself and cannot be blank.
    val canSave = activeList != null && !saved && (isLink || label.isNotBlank())

    fun save() {
        if (canSave) onSave(label.trim())
    }

    SheetHost(
        state = sheet,
        onDismissed = onDismissed,
        modifier = Modifier.testTag(ShareTags.SHEET),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isLink) "Save link to" else "Add task to",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            ListPicker(
                lists = lists,
                activeList = activeList,
                loaded = loaded,
                onSelectList = onSelectList,
                modifier = Modifier.testTag(ShareTags.LIST_CHIP),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = sheet::dismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        val url = shared.url
        if (url != null) {
            Spacer(Modifier.height(6.dp))
            LinkPreview(url = url, accentIndex = activeList?.accent ?: 0)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(TodoLimits.TASK_TITLE_MAX) },
            modifier = Modifier.fillMaxWidth().testTag(ShareTags.FIELD),
            label = { Text(if (isLink) "Label (optional)" else "Task") },
            placeholder = if (isLink) {
                { Text("e.g. Watch this weekend") }
            } else {
                null
            },
            maxLines = 3,
            enabled = !saved,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (loaded && lists.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You don't have any lists yet. Open the app to create one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = saved,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "share_saved",
        ) { isSaved ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                if (isSaved) {
                    PoppingCheck()
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Saved to ${activeList?.name.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = sheet::dismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { save() },
                        enabled = canSave,
                        modifier = Modifier.testTag(ShareTags.SAVE),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TodoAccents.colorAt(activeList?.accent ?: 0),
                            contentColor = TodoAccents.onAccent,
                        ),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
        }
    }
}

/** The site and the full address, so the user can see what they are about to save. */
@Composable
private fun LinkPreview(url: String, accentIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = null,
            tint = accentTextColor(accentIndex),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = TaskLinks.host(url),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
