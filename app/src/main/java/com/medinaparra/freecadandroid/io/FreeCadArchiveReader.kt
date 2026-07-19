package com.medinaparra.freecadandroid.io

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class FreeCadArchiveContent(
    val displayName: String,
    val documentName: String,
    val brepFiles: List<File>,
    val summary: String
)

/** Extracts the OpenCASCADE BREP payloads stored inside a FreeCAD FCStd ZIP. */
object FreeCadArchiveReader {
    private const val maxEntries = 512
    private const val maxSingleEntryBytes = 256L * 1024L * 1024L
    private const val maxTotalBytes = 768L * 1024L * 1024L

    fun extract(
        archiveFile: File,
        outputRoot: File,
        displayName: String
    ): FreeCadArchiveContent {
        require(archiveFile.isFile && archiveFile.length() > 0L) {
            "The selected FCStd document is empty"
        }

        val directory = File(outputRoot, archiveFile.nameWithoutExtension).apply {
            deleteRecursively()
            mkdirs()
        }
        val rootPath = directory.canonicalPath + File.separator

        ZipFile(archiveFile).use { zip ->
            val documentEntry = zip.entries().asSequence().firstOrNull {
                it.name.equals("Document.xml", ignoreCase = true)
            } ?: error("Document.xml is missing; this is not a supported FCStd document")

            val documentXml = zip.getInputStream(documentEntry)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val documentName = Regex(
                "<Document\\b[^>]*?\\bName=\"([^\"]+)\"",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(documentXml)?.groupValues?.getOrNull(1)
                ?: archiveFile.nameWithoutExtension

            val objectMatches = Regex(
                "<Object\\b[^>]*?type=\"([^\"]+)\"[^>]*?name=\"([^\"]+)\"",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(documentXml).toList()

            val brepEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && isBrep(it.name) }
                .take(maxEntries + 1)
                .toList()
            require(brepEntries.isNotEmpty()) {
                "The FCStd document contains no .brp or .brep geometry payloads"
            }
            require(brepEntries.size <= maxEntries) {
                "The FCStd document contains too many BREP payloads"
            }

            var expandedBytes = 0L
            val extracted = brepEntries.mapIndexed { index, entry ->
                require(entry.size < 0L || entry.size <= maxSingleEntryBytes) {
                    "BREP entry ${entry.name} is too large for this mobile build"
                }
                if (entry.size > 0L) {
                    expandedBytes += entry.size
                    require(expandedBytes <= maxTotalBytes) {
                        "Expanded FCStd geometry exceeds the mobile memory limit"
                    }
                }

                val safeName = File(entry.name).name
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "Shape_$index.brp" }
                val target = File(directory, "${index}_$safeName")
                require(target.canonicalPath.startsWith(rootPath)) {
                    "Invalid FCStd archive entry path"
                }
                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                require(target.length() > 0L) { "BREP entry ${entry.name} is empty" }
                target
            }

            val typeCounts = objectMatches
                .map { it.groupValues[1] }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .joinToString { "${it.key}: ${it.value}" }

            val version = Regex(
                "ProgramVersion\\s*=\\s*\"([^\"]+)\"",
                RegexOption.IGNORE_CASE
            ).find(documentXml)?.groupValues?.getOrNull(1)

            val summary = buildString {
                appendLine("FreeCAD native document")
                appendLine("Document: $documentName")
                appendLine("Archive: $displayName")
                if (!version.isNullOrBlank()) appendLine("FreeCAD version: $version")
                appendLine("Objects described: ${objectMatches.size}")
                appendLine("BREP payloads: ${extracted.size}")
                if (typeCounts.isNotBlank()) append("Types: $typeCounts")
            }.trimEnd()

            return FreeCadArchiveContent(
                displayName = displayName,
                documentName = documentName,
                brepFiles = extracted,
                summary = summary
            )
        }
    }

    private fun isBrep(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".brp") || lower.endsWith(".brep")
    }
}
