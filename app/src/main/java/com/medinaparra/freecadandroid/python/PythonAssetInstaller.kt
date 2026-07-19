package com.medinaparra.freecadandroid.python

import android.content.Context
import android.os.Build
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object PythonAssetInstaller {
    private const val runtimeVersion = "3.14.6-v1"
    private val lock = Any()

    fun install(context: Context): String = synchronized(lock) {
        val abi = selectAbi()
        val target = File(context.filesDir, "python-runtime-$runtimeVersion-$abi")
        val marker = File(target, ".installation-complete")
        if (marker.isFile) {
            return@synchronized target.absolutePath
        }

        val temporary = File(context.filesDir, "${target.name}.installing")
        temporary.deleteRecursively()
        check(temporary.mkdirs()) { "Unable to create Python installation directory" }

        try {
            context.assets.open("python-runtime/$abi.zip").use { rawInput ->
                ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                    val rootPath = temporary.canonicalPath + File.separator
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val output = File(temporary, entry.name)
                        val outputPath = output.canonicalPath
                        check(outputPath == temporary.canonicalPath || outputPath.startsWith(rootPath)) {
                            "Unsafe path in embedded Python archive: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            check(output.isDirectory || output.mkdirs()) {
                                "Unable to create Python directory: ${entry.name}"
                            }
                        } else {
                            output.parentFile?.let { parent ->
                                check(parent.isDirectory || parent.mkdirs()) {
                                    "Unable to create Python parent directory"
                                }
                            }
                            FileOutputStream(output).use { destination ->
                                zip.copyTo(destination, bufferSize = 64 * 1024)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            val standardLibrary = File(temporary, "lib/python3.14")
            check(standardLibrary.isDirectory) {
                "Embedded Python archive does not contain lib/python3.14"
            }
            val freeCadModule = File(standardLibrary, "site-packages/FreeCAD.py")
            val partModule = File(standardLibrary, "site-packages/Part.py")
            check(freeCadModule.isFile && partModule.isFile) {
                "FreeCAD Python compatibility modules are missing"
            }

            target.deleteRecursively()
            check(temporary.renameTo(target)) {
                "Unable to activate the embedded Python runtime"
            }
            check(marker.createNewFile() || marker.isFile) {
                "Unable to mark the Python runtime as installed"
            }
            target.absolutePath
        } catch (error: Throwable) {
            temporary.deleteRecursively()
            throw error
        }
    }

    fun selectedAbi(): String = selectAbi()

    private fun selectAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull { abi ->
            abi == "arm64-v8a" || abi == "armeabi-v7a"
        } ?: error(
            "This build requires an ARM Android ABI. Device ABIs: " +
                Build.SUPPORTED_ABIS.joinToString()
        )
    }
}
