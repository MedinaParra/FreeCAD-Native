package com.medinaparra.freecadandroid.io

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
    private const val maxDocumentXmlBytes = 16L * 1024L * 1024L
    private const val maxSingleEntryBytes = 128L * 1024L * 1024L
    private const val maxTotalBytes = 384L * 1024L * 1024L
    private const val maxArchiveBytes = 512L * 1024L * 1024L

    fun extract(
        archiveFile: File,
        outputRoot: File,
        displayName: String
    ): FreeCadArchiveContent {
        require(archiveFile.isFile && archiveFile.length() > 0L) {
            "The selected FCStd document is empty"
        }
        require(archiveFile.length() <= maxArchiveBytes) {
            "The selected FCStd document exceeds the mobile archive limit"
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

            val documentXml = zip.getInputStream(documentEntry).use { input ->
                readBounded(input, maxDocumentXmlBytes, "Document.xml").toString(Charsets.UTF_8)
            }

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
                val actualBytes = zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            require(entryBytes <= maxSingleEntryBytes) {
                                "BREP entry ${entry.name} exceeds the mobile extraction limit"
                            }
                            require(expandedBytes + entryBytes <= maxTotalBytes) {
                                "Expanded FCStd geometry exceeds the mobile memory limit"
                            }
                            output.write(buffer, 0, count)
                        }
                        entryBytes
                    }
                }
                if (entry.size <= 0L) expandedBytes += actualBytes
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

    private fun readBounded(input: InputStream, limit: Long, label: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "$label exceeds the mobile parsing limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun isBrep(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".brp") || lower.endsWith(".brep")
    }
}
