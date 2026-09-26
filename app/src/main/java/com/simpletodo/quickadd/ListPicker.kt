package com.simpletodo.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.simpletodo.data.TodoList
import com.simpletodo.ui.theme.TodoAccents

/**
 * The destination list at the top of the quick-add and share sheets: the list's colour and name,
 * opening a menu of the others when there are any to pick from.
 */
@Composable
internal fun ListPicker(
    lists: List<TodoList>,
    activeList: TodoList?,
    loaded: Boolean,
    onSelectList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { if (lists.size > 1) menuOpen = true },
            modifier = modifier,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(TodoAccents.colorAt(activeList?.accent ?: 0)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = activeList?.name ?: if (loaded) "No list" else "…",
                style = MaterialTheme.typography.titleSmall,
            )
            if (lists.size > 1) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = "Change list",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        menuOpen = false
                        onSelectList(list.id)
                    },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(TodoAccents.colorAt(list.accent)),
                        )
                    },
                    trailingIcon = {
                        if (list.id == activeList?.id) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}
