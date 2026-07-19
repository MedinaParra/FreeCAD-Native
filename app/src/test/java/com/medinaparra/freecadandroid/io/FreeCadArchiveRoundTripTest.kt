package com.medinaparra.freecadandroid.io

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeCadArchiveRoundTripTest {
    @Test
    fun parsesObjectManifestAndPreservesUnknownArchiveEntriesOnSave() {
        val root = Files.createTempDirectory("fcstd-roundtrip").toFile()
        try {
            val source = File(root, "source.FCStd")
            writeFixture(source)
            val content = FreeCadArchiveReader.extract(source, File(root, "extract"), source.name)

            assertEquals("Fixture", content.documentName)
            assertEquals(3, content.objects.size)
            assertEquals(3, content.shapeObjects.size)
            val body = content.objects.single { it.name == "Body" }
            val pad = content.objects.single { it.name == "Pad" }
            val bodyLink = content.objects.single { it.name == "BodyLink" }
            assertEquals("PartDesign::Body", body.typeId)
            assertEquals("Main body", body.label)
            assertTrue(body.visible)
            assertFalse(pad.visible)
            assertTrue(body.hasPlacementProperty)
            assertEquals(1.0, body.placement.x, 0.0)
            assertEquals(listOf("Pad"), body.links)
            assertNotNull(body.brepFile)
            assertEquals("Body", bodyLink.linkedObjectName)
            assertEquals("Body.Shape.brp", bodyLink.shapeEntryName)
            assertEquals(body.brepFile?.canonicalPath, bodyLink.brepFile?.canonicalPath)
            assertEquals(42.0, bodyLink.placement.x, 0.0)

            val updatedBody = body.copy(
                label = "Edited <body>",
                visible = false,
                placement = body.placement.copy(x = 12.5, y = -3.0, z = 8.25)
            )
            val destination = File(root, "edited.FCStd")
            FreeCadArchiveWriter.write(
                content,
                content.objects.map { if (it.name == body.name) updatedBody else it },
                destination
            )

            ZipFile(destination).use { zip ->
                assertArrayEquals(
                    byteArrayOf(1, 2, 3, 4),
                    zip.getInputStream(zip.getEntry("Extra.bin")).readBytes()
                )
            }
            val reopened = FreeCadArchiveReader.extract(
                destination,
                File(root, "reopened"),
                destination.name
            )
            val edited = reopened.objects.single { it.name == "Body" }
            assertEquals("Edited <body>", edited.label)
            assertEquals(12.5, edited.placement.x, 0.0)
            assertEquals(-3.0, edited.placement.y, 0.0)
            assertEquals(8.25, edited.placement.z, 0.0)
            assertFalse(edited.visible)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeFixture(file: File) {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document SchemaVersion="4" ProgramVersion="1.1.1">
              <Properties Count="2">
                <Property name="Label" type="App::PropertyString"><String value="Fixture"/></Property>
                <Property name="LastModifiedDate" type="App::PropertyString"><String value="old"/></Property>
              </Properties>
              <Objects Count="3">
                <Object type="PartDesign::Body" name="Body" id="1"/>
                <Object type="PartDesign::Pad" name="Pad" id="2"/>
                <Object type="App::Link" name="BodyLink" id="3"/>
              </Objects>
              <ObjectData Count="3">
                <Object name="Body">
                  <Properties Count="5">
                    <Property name="Group" type="App::PropertyLinkList"><LinkList count="1"><Link value="Pad"/></LinkList></Property>
                    <Property name="Label" type="App::PropertyString"><String value="Main body"/></Property>
                    <Property name="Placement" type="App::PropertyPlacement"><PropertyPlacement Px="1" Py="2" Pz="3" Q0="0" Q1="0" Q2="0" Q3="1"/></Property>
                    <Property name="Shape" type="Part::PropertyPartShape"><Part file="Body.Shape.brp"/></Property>
                    <Property name="Visibility" type="App::PropertyBool"><Bool value="false"/></Property>
                  </Properties>
                </Object>
                <Object name="Pad">
                  <Properties Count="3">
                    <Property name="Label" type="App::PropertyString"><String value="Pad"/></Property>
                    <Property name="Shape" type="Part::PropertyPartShape"><Part file="Pad.Shape.brp"/></Property>
                    <Property name="Visibility" type="App::PropertyBool"><Bool value="true"/></Property>
                  </Properties>
                </Object>
                <Object name="BodyLink">
                  <Properties Count="4">
                    <Property name="Label" type="App::PropertyString"><String value="Body instance"/></Property>
                    <Property name="LinkedObject" type="App::PropertyXLink"><XLink file="" stamp="" name="Body"/></Property>
                    <Property name="Placement" type="App::PropertyPlacement"><PropertyPlacement Px="42" Py="0" Pz="0" Q0="0" Q1="0" Q2="0" Q3="1"/></Property>
                    <Property name="Visibility" type="App::PropertyBool"><Bool value="true"/></Property>
                  </Properties>
                </Object>
              </ObjectData>
            </Document>
        """.trimIndent()
        val guiXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document SchemaVersion="1">
              <ViewProviderData Count="3">
                <ViewProvider name="Body"><Properties Count="1"><Property name="Visibility" type="App::PropertyBool"><Bool value="true"/></Property></Properties></ViewProvider>
                <ViewProvider name="Pad"><Properties Count="1"><Property name="Visibility" type="App::PropertyBool"><Bool value="false"/></Property></Properties></ViewProvider>
                <ViewProvider name="BodyLink"><Properties Count="1"><Property name="Visibility" type="App::PropertyBool"><Bool value="true"/></Property></Properties></ViewProvider>
              </ViewProviderData>
            </Document>
        """.trimIndent()
        ZipOutputStream(file.outputStream()).use { zip ->
            mapOf(
                "Document.xml" to documentXml.toByteArray(),
                "GuiDocument.xml" to guiXml.toByteArray(),
                "Body.Shape.brp" to "body-brep".toByteArray(),
                "Pad.Shape.brp" to "pad-brep".toByteArray(),
                "Extra.bin" to byteArrayOf(1, 2, 3, 4)
            ).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
