package com.simpletodo.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri

/**
 * Puts [text] on the clipboard.
 *
 * @return true when the app should confirm the copy itself. Android 13 and later show their own
 *   clipboard confirmation, and a snackbar on top of it would only say the same thing twice.
 */
fun copyToClipboard(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Task", text))
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/**
 * Hands [text] to the system share sheet — WhatsApp, Messages, email, whatever the user picks.
 *
 * @return false when nothing on the device can take it.
 */
fun shareText(context: Context, text: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Opens [url] in whatever the user has for it — the YouTube app for a YouTube link, a browser
 * otherwise.
 *
 * @return false when nothing on the device can open it.
 */
fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
