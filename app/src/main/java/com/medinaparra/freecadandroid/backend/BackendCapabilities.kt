package com.medinaparra.freecadandroid.backend

/**
 * BackendCapabilities represents the integration capabilities available in the current run.
 */
data class BackendCapabilities(
    val backendName: String,
    val nativeLibraryLoaded: Boolean,
    val occtAvailable: Boolean,
    val freeCadAvailable: Boolean,
    val pythonAvailable: Boolean,
    val stepImportAvailable: Boolean,
    val fcStdImportAvailable: Boolean
)
