package com.medinaparra.freecadandroid.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Non-destructive FCStd writer for universal object metadata supported by the mobile editor. */
object FreeCadArchiveWriter {
    private const val maxEntries = 4096
    private const val maxExpandedBytes = 512L * 1024L * 1024L

    fun write(
        content: FreeCadArchiveContent,
        updatedObjects: List<FcStdObjectRecord>,
        destination: File
    ): File {
        require(content.sourceArchive.isFile) { "The source FCStd archive is unavailable" }
        require(destination.canonicalFile != content.sourceArchive.canonicalFile) {
            "FCStd export requires a different destination file"
        }
        val byName = updatedObjects.associateBy { it.name }
        require(byName.size == updatedObjects.size) { "FCStd object names must remain unique" }
        require(content.objects.all { it.name in byName }) {
            "FCStd export cannot omit source document objects"
        }
        require(updatedObjects.all { it.label.length <= 4096 }) {
            "FCStd object labels cannot exceed 4096 characters"
        }

        val documentXml = updateDocumentXml(content.documentXml, byName)
        val guiDocumentXml = content.guiDocumentXml?.let { updateGuiDocumentXml(it, byName) }
        destination.parentFile?.mkdirs()
        if (destination.exists()) check(destination.delete()) { "Unable to replace the FCStd export" }

        try {
            ZipFile(content.sourceArchive).use { source ->
                val entries = source.entries().asSequence().take(maxEntries + 1).toList()
                require(entries.size <= maxEntries) { "The FCStd archive contains too many entries" }
                val normalizedEntryNames = entries.map { it.name.lowercase(Locale.ROOT) }
                require(normalizedEntryNames.size == normalizedEntryNames.toSet().size) {
                    "The FCStd archive contains duplicate entry names"
                }
                ZipOutputStream(destination.outputStream().buffered()).use { output ->
                    source.comment?.let(output::setComment)
                    var expandedBytes = 0L
                    entries.forEach { entry ->
                        val replacement = when (entry.name.lowercase(Locale.ROOT)) {
                            "document.xml" -> documentXml
                            "guidocument.xml" -> guiDocumentXml
                            else -> null
                        }
                        val copy = ZipEntry(entry.name).apply {
                            if (entry.time >= 0L) time = entry.time
                            comment = entry.comment
                            extra = entry.extra
                        }
                        output.putNextEntry(copy)
                        if (!entry.isDirectory) {
                            if (replacement != null) {
                                expandedBytes += replacement.size
                                require(expandedBytes <= maxExpandedBytes) {
                                    "FCStd export exceeds the mobile size limit"
                                }
                                output.write(replacement)
                            } else {
                                source.getInputStream(entry).use { input ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        expandedBytes += count
                                        require(expandedBytes <= maxExpandedBytes) {
                                            "FCStd export exceeds the mobile size limit"
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        output.closeEntry()
                    }
                }
            }
            require(destination.isFile && destination.length() > 0L) { "FCStd export is empty" }
            return destination
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    private fun updateDocumentXml(
        bytes: ByteArray,
        objects: Map<String, FcStdObjectRecord>
    ): ByteArray {
        val document = parseXml(bytes, "Document.xml")
        document.documentElement.directChild("ObjectData")?.directChildren("Object")?.forEach { objectNode ->
            val record = objects[objectNode.attribute("name")] ?: return@forEach
            val properties = objectNode.directChild("Properties")?.directChildren("Property").orEmpty()
            properties.firstOrNull { it.attribute("name") == "Label" }
                ?.firstDescendant("String")?.setAttribute("value", record.label)
            properties.firstOrNull { it.attribute("name") == "Visibility" }
                ?.firstDescendant("Bool")?.setAttribute("value", record.visible.toString())
            properties.firstOrNull { it.attribute("name") == "Placement" }
                ?.firstDescendant("PropertyPlacement")?.applyPlacement(record.placement)
        }
        document.documentElement.directChild("Properties")?.directChildren("Property")
            ?.firstOrNull { it.attribute("name") == "LastModifiedDate" }
            ?.firstDescendant("String")
            ?.setAttribute("value", OffsetDateTime.now().toString())
        return serialize(document)
    }

    private fun updateGuiDocumentXml(
        bytes: ByteArray,
        objects: Map<String, FcStdObjectRecord>
    ): ByteArray {
        val document = parseXml(bytes, "GuiDocument.xml")
        document.documentElement.directChild("ViewProviderData")
            ?.directChildren("ViewProvider")?.forEach { provider ->
                val record = objects[provider.attribute("name")] ?: return@forEach
                provider.directChild("Properties")?.directChildren("Property")
                    ?.firstOrNull { it.attribute("name") == "Visibility" }
                    ?.firstDescendant("Bool")
                    ?.setAttribute("value", record.visible.toString())
            }
        return serialize(document)
    }

    private fun Element.applyPlacement(value: FcStdPlacement) {
        setAttribute("Px", value.x.toFreeCadNumber())
        setAttribute("Py", value.y.toFreeCadNumber())
        setAttribute("Pz", value.z.toFreeCadNumber())
        setAttribute("Q0", value.qx.toFreeCadNumber())
        setAttribute("Q1", value.qy.toFreeCadNumber())
        setAttribute("Q2", value.qz.toFreeCadNumber())
        setAttribute("Q3", value.qw.toFreeCadNumber())
    }

    private fun Double.toFreeCadNumber(): String {
        require(isFinite()) { "FCStd edit contains a non-finite value" }
        return String.format(Locale.ROOT, "%.16f", this)
    }

    private fun parseXml(bytes: ByteArray, label: String): Document {
        val prefix = bytes.take(4096).toByteArray().toString(Charsets.UTF_8).uppercase(Locale.ROOT)
        require("<!DOCTYPE" !in prefix && "<!ENTITY" !in prefix) {
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

    private fun serialize(document: Document): ByteArray {
        val output = ByteArrayOutputStream()
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        transformer.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun Element.attribute(name: String): String = getAttribute(name).orEmpty()

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
}
