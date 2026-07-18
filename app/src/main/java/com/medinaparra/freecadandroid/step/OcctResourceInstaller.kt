package com.medinaparra.freecadandroid.step

import android.content.Context
import android.system.Os
import java.io.File
import java.util.zip.ZipInputStream

object OcctResourceInstaller {
    private const val ASSET_NAME = "occt-step-resources.zip"
    private const val INSTALL_VERSION = "occt-step-resources-7.9.2-v1"
    private val lock = Any()

    fun installAndConfigure(context: Context): File = synchronized(lock) {
        val root = File(context.filesDir, INSTALL_VERSION)
        val marker = File(root, ".complete")
        if (!marker.isFile) {
            root.deleteRecursively()
            root.mkdirs()
            extractAsset(context, root)
            marker.writeText("OCCT 7.9.2 STEP resources")
        }
        require(File(root, "SHMessage").isDirectory) { "Missing OCCT SHMessage resources" }
        require(File(root, "XSMessage").isDirectory) { "Missing OCCT XSMessage resources" }
        require(File(root, "StdResource").isDirectory) { "Missing OCCT standard resources" }
        require(File(root, "XSTEPResource").isDirectory) { "Missing OCCT STEP resources" }
        configureEnvironment(root)
        root
    }

    private fun extractAsset(context: Context, root: File) {
        val canonicalRoot = root.canonicalPath + File.separator
        context.assets.open(ASSET_NAME).use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val output = File(root, entry.name)
                    require(output.canonicalPath.startsWith(canonicalRoot)) {
                        "Invalid path in OCCT resource archive"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { stream ->
                            zip.copyTo(stream)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun configureEnvironment(root: File) {
        val standard = File(root, "StdResource").absolutePath
        val exchange = File(root, "XSTEPResource").absolutePath
        set("CSF_LANGUAGE", "us")
        set("CSF_OCCTResourcePath", root.absolutePath)
        set("CSF_SHMessage", File(root, "SHMessage").absolutePath)
        set("CSF_XSMessage", File(root, "XSMessage").absolutePath)
        set("CSF_StandardDefaults", standard)
        set("CSF_PluginDefaults", standard)
        set("CSF_XCAFDefaults", standard)
        set("CSF_StandardLiteDefaults", standard)
        set("CSF_STEPDefaults", exchange)
        set("CSF_IGESDefaults", exchange)
    }

    private fun set(name: String, value: String) {
        Os.setenv(name, value, true)
    }
}
