package com.simpletodo.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpletodo.ui.theme.SimpleTodoTheme

class MainActivity : ComponentActivity() {

    private var requestedListId by mutableStateOf<String?>(null)
    private var requestedNewList by mutableStateOf(false)
    private var requestNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntent(intent)

        setContent {
            SimpleTodoTheme {
                val viewModel: TodoViewModel = viewModel(factory = TodoViewModel.factory(this))
                TodoApp(
                    viewModel = viewModel,
                    requestedListId = requestedListId,
                    requestedNewList = requestedNewList,
                    requestNonce = requestNonce,
                    onRequestHandled = {
                        requestedListId = null
                        requestedNewList = false
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        val listId = intent?.getStringExtra(EXTRA_LIST_ID)
        val newList = intent?.getBooleanExtra(EXTRA_NEW_LIST, false) == true
        if (listId != null || newList) {
            requestedListId = listId
            requestedNewList = newList
            requestNonce++
        }
    }

    companion object {
        const val EXTRA_LIST_ID = "com.simpletodo.extra.LIST_ID"
        const val EXTRA_NEW_LIST = "com.simpletodo.extra.NEW_LIST"

        /**
         * Distinct [Intent.setData] values matter here: widgets turn these into PendingIntents,
         * and intents that only differ by extras would be treated as the same pending intent.
         */
        fun listIntent(context: Context, listId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "simpletodo://list/$listId".toUri()
                putExtra(EXTRA_LIST_ID, listId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun newListIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = "simpletodo://lists/new".toUri()
                putExtra(EXTRA_NEW_LIST, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
