package com.medinaparra.freecadandroid.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

object AndroidDocumentLoader {
    fun readMacro(context: Context, uri: Uri): Pair<String, String> {
        val name = displayName(context, uri) ?: "Imported.FCMacro"
        val lower = name.lowercase(Locale.ROOT)
        require(lower.endsWith(".fcmacro") || lower.endsWith(".py")) {
            "Select a .FCMacro or .py file"
        }
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Android could not open the selected macro" }
            input.readBytes()
        }
        require(bytes.isNotEmpty()) { "The selected macro is empty" }
        require(bytes.size <= 4 * 1024 * 1024) { "The selected macro is larger than 4 MB" }
        val source = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        require(source.isNotBlank()) { "The selected macro has no Python source" }
        return name to source
    }

    fun stage(
        context: Context,
        uri: Uri,
        directoryName: String,
        fallbackName: String
    ): Pair<File, String> {
        val name = displayName(context, uri) ?: fallbackName
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, directoryName).apply { mkdirs() }
        val target = File(directory, safeName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Android could not open the selected document" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        require(target.length() > 0L) { "The selected document is empty" }
        return target to name
    }

    fun friendlyMacroError(error: Throwable, sourceName: String): String {
        val raw = readableFailure(error)
        val hint = when {
            "No module named" in raw ->
                "The macro imports a workbench or Python module that is not embedded yet."
            "has no property" in raw || "AttributeError" in raw ->
                "The macro uses a FreeCAD property or method that this Android core does not expose yet."
            "NotImplementedError" in raw || "not supported yet" in raw ->
                "The macro reached a FreeCAD operation that is not implemented yet."
            "did not create or activate" in raw ->
                "The macro must create a document with App.newDocument()."
            else -> "The Python traceback below identifies the failing line."
        }
        return "$sourceName\n$hint\n\n$raw"
    }

    fun readableFailure(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return root.message?.takeIf { it.isNotBlank() }
            ?: error.message?.takeIf { it.isNotBlank() }
            ?: error::class.java.simpleName
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
