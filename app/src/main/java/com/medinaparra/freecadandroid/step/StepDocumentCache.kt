package com.medinaparra.freecadandroid.step

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class CachedStepDocument(
    val file: File,
    val displayName: String,
    val byteCount: Long
)

object StepDocumentCache {
    fun copyIntoPrivateStorage(context: Context, uri: Uri): CachedStepDocument {
        val displayName = queryDisplayName(context, uri).ifBlank { "imported-model.step" }
        val extension = displayName.substringAfterLast('.', "step").lowercase()
        require(extension in setOf("step", "stp", "p21")) {
            "El archivo seleccionado no parece ser STEP (.step, .stp o .p21)"
        }

        val directory = File(context.cacheDir, "step-imports").apply { mkdirs() }
        directory.listFiles()?.forEach { old ->
            if (old.isFile && System.currentTimeMillis() - old.lastModified() > 24L * 60L * 60L * 1000L) {
                old.delete()
            }
        }

        val safeBase = displayName
            .substringBeforeLast('.', displayName)
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .take(80)
            .ifBlank { "model" }
        val target = File(directory, "${safeBase}_${System.nanoTime()}.$extension")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        } ?: error("No fue posible abrir el documento seleccionado")
        require(bytes > 0L && target.isFile) { "El archivo STEP está vacío" }
        return CachedStepDocument(target, displayName, bytes)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }
}
