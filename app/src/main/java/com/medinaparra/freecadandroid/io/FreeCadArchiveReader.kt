package com.medinaparra.freecadandroid.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

data class FcStdPlacement(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val qx: Double = 0.0,
    val qy: Double = 0.0,
    val qz: Double = 0.0,
    val qw: Double = 1.0
) {
    init {
        require(listOf(x, y, z, qx, qy, qz, qw).all { it.isFinite() }) {
            "FCStd placement contains a non-finite value"
        }
        require(qx * qx + qy * qy + qz * qz + qw * qw > 1.0e-24) {
            "FCStd placement quaternion is null"
        }
    }
}

data class FcStdObjectRecord(
    val name: String,
    val label: String,
    val typeId: String,
    val shapeEntryName: String?,
    val linkedObjectName: String?,
    val brepFile: File?,
    val visible: Boolean,
    val placement: FcStdPlacement,
    val hasPlacementProperty: Boolean,
    val propertyValues: Map<String, String>,
    val links: List<String>
)

data class FreeCadArchiveContent(
    val displayName: String,
    val documentName: String,
    val sourceArchive: File,
    val documentXml: ByteArray,
    val guiDocumentXml: ByteArray?,
    val objects: List<FcStdObjectRecord>,
    val summary: String
) {
    val shapeObjects: List<FcStdObjectRecord>
        get() = objects.filter { it.brepFile != null }

    val brepFiles: List<File>
        get() = shapeObjects.mapNotNull { it.brepFile }.distinctBy { it.canonicalPath }
}

/** Reads the object manifest and extracts referenced BREP payloads from a FreeCAD FCStd ZIP. */
object FreeCadArchiveReader {
    private const val maxArchiveEntries = 4096
    private const val maxShapeObjects = 512
    private const val maxDocumentXmlBytes = 16L * 1024L * 1024L
    private const val maxGuiDocumentXmlBytes = 16L * 1024L * 1024L
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
            check(mkdirs() || isDirectory) { "Unable to create the FCStd extraction directory" }
        }
        val rootPath = directory.canonicalPath + File.separator

        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries().asSequence().take(maxArchiveEntries + 1).toList()
            require(entries.size <= maxArchiveEntries) { "The FCStd archive contains too many entries" }
            val normalizedEntryNames = entries.map { it.name.lowercase(Locale.ROOT) }
            require(normalizedEntryNames.size == normalizedEntryNames.toSet().size) {
                "The FCStd archive contains duplicate entry names"
            }
            val byLowerName = entries.associateBy { it.name.lowercase(Locale.ROOT) }
            val documentEntry = byLowerName["document.xml"]
                ?: error("Document.xml is missing; this is not a supported FCStd document")
            val documentXml = zip.getInputStream(documentEntry).use { input ->
                readBounded(input, maxDocumentXmlBytes, "Document.xml")
            }
            val guiDocumentXml = byLowerName["guidocument.xml"]?.let { entry ->
                zip.getInputStream(entry).use { input ->
                    readBounded(input, maxGuiDocumentXmlBytes, "GuiDocument.xml")
                }
            }

            val manifest = parseManifest(documentXml, guiDocumentXml, archiveFile.nameWithoutExtension)
            require(manifest.objects.count { it.shapeEntryName != null } <= maxShapeObjects) {
                "The FCStd document contains too many shape objects or linked instances"
            }
            val referencedNames = manifest.objects.mapNotNull { it.shapeEntryName }.distinct()
            val shapeEntries = if (referencedNames.isNotEmpty()) {
                referencedNames.mapNotNull { byLowerName[it.lowercase(Locale.ROOT)] }
            } else {
                entries.filter { !it.isDirectory && isBrep(it.name) }
            }
            require(shapeEntries.size <= maxShapeObjects) {
                "The FCStd document contains too many shape objects"
            }

            var expandedBytes = 0L
            val extractedByEntry = linkedMapOf<String, File>()
            shapeEntries.distinctBy { it.name.lowercase(Locale.ROOT) }.forEachIndexed { index, entry ->
                require(entry.size < 0L || entry.size <= maxSingleEntryBytes) {
                    "BREP entry ${entry.name} is too large for this mobile build"
                }
                val safeName = File(entry.name).name
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "Shape_$index.brp" }
                val target = File(directory, "${index}_$safeName")
                require(target.canonicalPath.startsWith(rootPath)) { "Invalid FCStd archive entry path" }
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
                expandedBytes += actualBytes
                if (actualBytes > 0L) {
                    extractedByEntry[entry.name.lowercase(Locale.ROOT)] = target
                } else {
                    target.delete()
                }
            }
            require(extractedByEntry.isNotEmpty()) {
                "The FCStd document contains no readable BREP shape payloads"
            }

            val objects = if (manifest.objects.any { it.shapeEntryName != null }) {
                manifest.objects.map { record ->
                    record.copy(
                        brepFile = record.shapeEntryName?.let {
                            extractedByEntry[it.lowercase(Locale.ROOT)]
                        }
                    )
                }
            } else {
                extractedByEntry.map { (entryName, file) ->
                    val generatedName = File(entryName).nameWithoutExtension.ifBlank { "Shape" }
                    FcStdObjectRecord(
                        name = generatedName,
                        label = generatedName,
                        typeId = "Part::Feature",
                        shapeEntryName = entryName,
                        linkedObjectName = null,
                        brepFile = file,
                        visible = true,
                        placement = FcStdPlacement(),
                        hasPlacementProperty = false,
                        propertyValues = emptyMap(),
                        links = emptyList()
                    )
                }
            }

            val shapeObjects = objects.count { it.brepFile != null }
            val visibleShapes = objects.count { it.brepFile != null && it.visible }
            val typeCounts = objects.groupingBy { it.typeId }.eachCount().entries
                .sortedByDescending { it.value }.take(8)
                .joinToString { "${it.key}: ${it.value}" }
            val summary = buildString {
                appendLine("FreeCAD object-aware document")
                appendLine("Document: ${manifest.documentName}")
                appendLine("Archive: $displayName")
                if (!manifest.programVersion.isNullOrBlank()) {
                    appendLine("FreeCAD version: ${manifest.programVersion}")
                }
                appendLine("Objects described: ${objects.size}")
                appendLine("Objects with BREP: $shapeObjects")
                appendLine("Visible BREP objects: $visibleShapes")
                if (typeCounts.isNotBlank()) append("Types: $typeCounts")
            }.trimEnd()

            return FreeCadArchiveContent(
                displayName = displayName,
                documentName = manifest.documentName,
                sourceArchive = archiveFile,
                documentXml = documentXml,
                guiDocumentXml = guiDocumentXml,
                objects = objects,
                summary = summary
            )
        }
    }

    private data class ParsedManifest(
        val documentName: String,
        val programVersion: String?,
        val objects: List<FcStdObjectRecord>
    )

    private fun parseManifest(
        documentBytes: ByteArray,
        guiDocumentBytes: ByteArray?,
        fallbackName: String
    ): ParsedManifest {
        val document = parseXml(documentBytes, "Document.xml")
        val root = document.documentElement
        val objectTypes = root.directChild("Objects")?.directChildren("Object")
            ?.associate { it.attribute("name") to it.attribute("type") }.orEmpty()
        val guiVisibility = guiDocumentBytes?.let(::parseGuiVisibility).orEmpty()
        val objectData = root.directChild("ObjectData")?.directChildren("Object").orEmpty()
        require(objectData.size <= maxArchiveEntries) { "Document.xml contains too many objects" }

        val rawRecords = objectData.map { objectElement ->
            val name = objectElement.attribute("name")
            require(name.isNotBlank()) { "FCStd object without a name" }
            require(name.length <= 1024) { "FCStd object name is too long" }
            val properties = objectElement.directChild("Properties")?.directChildren("Property").orEmpty()
            val byName = properties.associateBy { it.attribute("name") }
            val label = byName["Label"]?.firstElementValue() ?: name
            require(label.length <= 4096) { "FCStd object label is too long" }
            val shapeEntry = byName["Shape"]?.firstDescendant("Part")?.attribute("file")
                ?.takeIf { it.isNotBlank() }
            val linkedObjectName = byName["LinkedObject"]?.firstDescendant("XLink")?.let { link ->
                link.attribute("name").takeIf {
                    it.isNotBlank() && link.attribute("file").isBlank()
                }
            }
            val placementElement = byName["Placement"]?.firstDescendant("PropertyPlacement")
            val placement = placementElement?.let(::parsePlacement) ?: FcStdPlacement()
            val documentVisibility = byName["Visibility"]?.firstDescendant("Bool")
                ?.attribute("value")?.toBooleanStrictOrNull()
            val values = properties.mapNotNull { property ->
                val propertyName = property.attribute("name").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                property.firstElementValue()?.let { propertyName to it }
            }.toMap()
            val links = properties.flatMap { property ->
                buildList {
                    property.descendants("Link").mapNotNullTo(this) { link ->
                        link.attribute("value").takeIf(String::isNotBlank)
                    }
                    property.descendants("XLink").mapNotNullTo(this) { link ->
                        link.attribute("name").takeIf {
                            it.isNotBlank() && link.attribute("file").isBlank()
                        }
                    }
                }
            }.distinct()
            FcStdObjectRecord(
                name = name,
                label = label,
                typeId = objectTypes[name] ?: "App::Feature",
                shapeEntryName = shapeEntry,
                linkedObjectName = linkedObjectName,
                brepFile = null,
                visible = guiVisibility[name] ?: documentVisibility ?: true,
                placement = placement,
                hasPlacementProperty = placementElement != null,
                propertyValues = values,
                links = links
            )
        }

        val byObjectName = rawRecords.associateBy { it.name }
        fun resolveShapeEntry(record: FcStdObjectRecord, visited: MutableSet<String>): String? {
            record.shapeEntryName?.let { return it }
            if (!visited.add(record.name)) return null
            val target = record.linkedObjectName?.let(byObjectName::get) ?: return null
            return resolveShapeEntry(target, visited)
        }
        val records = rawRecords.map { record ->
            if (record.shapeEntryName != null || record.linkedObjectName == null) record
            else record.copy(shapeEntryName = resolveShapeEntry(record, linkedSetOf()))
        }

        val documentLabel = root.directChild("Properties")?.directChildren("Property")
            ?.firstOrNull { it.attribute("name") == "Label" }?.firstElementValue()
        return ParsedManifest(
            documentName = documentLabel?.takeIf(String::isNotBlank) ?: fallbackName,
            programVersion = root.attribute("ProgramVersion").takeIf(String::isNotBlank),
            objects = records
        )
    }

    private fun parseGuiVisibility(bytes: ByteArray): Map<String, Boolean> {
        val root = parseXml(bytes, "GuiDocument.xml").documentElement
        return root.directChild("ViewProviderData")?.directChildren("ViewProvider")
            ?.mapNotNull { provider ->
                val name = provider.attribute("name").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val property = provider.directChild("Properties")?.directChildren("Property")
                    ?.firstOrNull { it.attribute("name") == "Visibility" }
                val value = property?.firstDescendant("Bool")?.attribute("value")
                    ?.toBooleanStrictOrNull() ?: return@mapNotNull null
                name to value
            }?.toMap().orEmpty()
    }

    private fun parsePlacement(element: Element): FcStdPlacement = FcStdPlacement(
        x = element.doubleAttribute("Px", 0.0),
        y = element.doubleAttribute("Py", 0.0),
        z = element.doubleAttribute("Pz", 0.0),
        qx = element.doubleAttribute("Q0", 0.0),
        qy = element.doubleAttribute("Q1", 0.0),
        qz = element.doubleAttribute("Q2", 0.0),
        qw = element.doubleAttribute("Q3", 1.0)
    )

    private fun parseXml(bytes: ByteArray, label: String): Document {
        val textPrefix = bytes.take(4096).toByteArray().toString(Charsets.UTF_8).uppercase(Locale.ROOT)
        require("<!DOCTYPE" !in textPrefix && "<!ENTITY" !in textPrefix) {
            "$label contains a forbidden XML declaration"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        return ByteArrayInputStream(bytes).use { factory.newDocumentBuilder().parse(it) }
    }

    private fun Element.attribute(name: String): String = getAttribute(name).orEmpty()

    private fun Element.doubleAttribute(name: String, default: Double): Double {
        val value = attribute(name).takeIf(String::isNotBlank)?.toDoubleOrNull() ?: default
        require(value.isFinite()) { "FCStd attribute $name is non-finite" }
        return value
    }

    private fun Element.directChildren(tag: String): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tag) add(node as Element)
        }
    }

    private fun Element.directChild(tag: String): Element? = directChildren(tag).firstOrNull()

    private fun Element.firstDescendant(tag: String): Element? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }

    private fun Element.descendants(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.firstElementValue(): String? {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val child = nodes.item(index) as? Element ?: continue
            child.attribute("value").takeIf(String::isNotBlank)?.let { return it }
        }
        return null
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
