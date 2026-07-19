package com.medinaparra.freecadandroid.backend

import com.medinaparra.freecadandroid.model.SceneMesh

/** Lightweight fallback used only when the native build is unavailable. */
class SimulatorCadBackend : CadBackend {
    override val capabilities: BackendCapabilities = BackendCapabilities(
        backendName = "SIMULATOR",
        nativeLibraryLoaded = false,
        occtAvailable = false,
        freeCadAvailable = false,
        pythonAvailable = false,
        stepImportAvailable = false,
        fcStdImportAvailable = false
    )

    override fun createDemoScene(): SceneMesh {
        val vertices = floatArrayOf(
            -0.5f, -0.5f,  0.5f,  0f,  0f,  1f,
             0.5f, -0.5f,  0.5f,  0f,  0f,  1f,
             0.5f,  0.5f,  0.5f,  0f,  0f,  1f,
            -0.5f,  0.5f,  0.5f,  0f,  0f,  1f,

            -0.5f, -0.5f, -0.5f,  0f,  0f, -1f,
            -0.5f,  0.5f, -0.5f,  0f,  0f, -1f,
             0.5f,  0.5f, -0.5f,  0f,  0f, -1f,
             0.5f, -0.5f, -0.5f,  0f,  0f, -1f,

            -0.5f,  0.5f, -0.5f,  0f,  1f,  0f,
            -0.5f,  0.5f,  0.5f,  0f,  1f,  0f,
             0.5f,  0.5f,  0.5f,  0f,  1f,  0f,
             0.5f,  0.5f, -0.5f,  0f,  1f,  0f,

            -0.5f, -0.5f, -0.5f,  0f, -1f,  0f,
             0.5f, -0.5f, -0.5f,  0f, -1f,  0f,
             0.5f, -0.5f,  0.5f,  0f, -1f,  0f,
            -0.5f, -0.5f,  0.5f,  0f, -1f,  0f,

             0.5f, -0.5f, -0.5f,  1f,  0f,  0f,
             0.5f,  0.5f, -0.5f,  1f,  0f,  0f,
             0.5f,  0.5f,  0.5f,  1f,  0f,  0f,
             0.5f, -0.5f,  0.5f,  1f,  0f,  0f,

            -0.5f, -0.5f, -0.5f, -1f,  0f,  0f,
            -0.5f, -0.5f,  0.5f, -1f,  0f,  0f,
            -0.5f,  0.5f,  0.5f, -1f,  0f,  0f,
            -0.5f,  0.5f, -0.5f, -1f,  0f,  0f
        )
        val indices = intArrayOf(
             0,  1,  2,  0,  2,  3,
             4,  5,  6,  4,  6,  7,
             8,  9, 10,  8, 10, 11,
            12, 13, 14, 12, 14, 15,
            16, 17, 18, 16, 18, 19,
            20, 21, 22, 20, 22, 23
        )
        return SceneMesh(
            vertices = vertices,
            indices = indices,
            minX = -0.5f,
            minY = -0.5f,
            minZ = -0.5f,
            maxX = 0.5f,
            maxY = 0.5f,
            maxZ = 0.5f
        )
    }
}
