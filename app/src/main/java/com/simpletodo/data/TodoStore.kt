package com.simpletodo.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import org.json.JSONException
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "TodoStore"

/**
 * DataStore serializer. DataStore gives us atomic write-to-temp-then-rename semantics and
 * serialises concurrent writers inside the process, which is what keeps the app and the widgets
 * from ever seeing a half-written file.
 */
object TodoSerializer : Serializer<TodoSnapshot> {

    override val defaultValue: TodoSnapshot = TodoSnapshot.EMPTY

    override suspend fun readFrom(input: InputStream): TodoSnapshot {
        val text = input.readBytes().toString(Charsets.UTF_8)
        return try {
            TodoJson.decode(text)
        } catch (e: JSONException) {
            throw CorruptionException("Todo data could not be parsed", e)
        }
    }

    override suspend fun writeTo(t: TodoSnapshot, output: OutputStream) {
        output.write(TodoJson.encode(t).toByteArray(Charsets.UTF_8))
    }
}

object TodoStore {

    const val FILE_NAME = "todo_store_v1.json"
    private const val DIR_NAME = "datastore"

    fun fileIn(context: Context): File = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    fun create(context: Context, scope: CoroutineScope): DataStore<TodoSnapshot> {
        val file = fileIn(context)
        file.parentFile?.mkdirs()
        return DataStoreFactory.create(
            serializer = TodoSerializer,
            // A corrupt file is quarantined rather than deleted, so nothing is silently
            // destroyed and support can still recover it, while the user gets a working app.
            corruptionHandler = ReplaceFileCorruptionHandler { corruption ->
                quarantine(file, corruption)
                TodoSnapshot.EMPTY
            },
            scope = scope,
            produceFile = { file },
        )
    }

    private fun quarantine(file: File, corruption: CorruptionException) {
        runCatching {
            if (file.exists()) {
                val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
                file.copyTo(backup, overwrite = true)
                Log.w(TAG, "Quarantined corrupt store to ${backup.name}", corruption)
            }
        }.onFailure { Log.e(TAG, "Could not quarantine corrupt store", it) }
    }
}
