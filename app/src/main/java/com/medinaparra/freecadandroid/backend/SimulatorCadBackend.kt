package com.medinaparra.freecadandroid.backend

import com.medinaparra.freecadandroid.model.SceneMesh

/**
 * SimulatorCadBackend is a lightweight simulation backend for testing the 3D pipeline
 * when native libraries (C++, OpenCASCADE, FreeCAD) are not compiled/loaded.
 */
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
        // Generate a 3D cube/box mesh.
        // Vertex Layout: x, y, z, nx, ny, nz (stride = 24 bytes)
        // Bounding box spans from -0.5 to 0.5 in all axes.
        val vertices = floatArrayOf(
            // Front face (nz = 1.0f)
            -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
             0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
             0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
            -0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,

            // Back face (nz = -1.0f)
            -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
            -0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
             0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
             0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,

            // Top face (ny = 1.0f)
            -0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,
            -0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
             0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
             0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,

            // Bottom face (ny = -1.0f)
            -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
             0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
             0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
            -0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,

            // Right face (nx = 1.0f)
            0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
            0.5f,  0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
            0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
            0.5f, -0.5f,  0.5f,  1.0f,  0.0f,  0.0f,

            // Left face (nx = -1.0f)
           -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
           -0.5f, -0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
           -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
           -0.5f,  0.5f, -0.5f, -1.0f,  0.0f,  0.0f
        )

        // Triangle Indices (6 faces * 2 triangles * 3 vertices = 36 indices)
        val indices = shortArrayOf(
            0, 1, 2,     0, 2, 3,     // Front
            4, 5, 6,     4, 6, 7,     // Back
            8, 9, 10,    8, 10, 11,   // Top
            12, 13, 14,  12, 14, 15,  // Bottom
            16, 17, 18,  16, 18, 19,  // Right
            20, 21, 22,  20, 22, 23   // Left
        )

        return SceneMesh(
            vertices = vertices,
            indices = indices,
            minX = -0.5f, minY = -0.5f, minZ = -0.5f,
            maxX = 0.5f, maxY = 0.5f, maxZ = 0.5f
        )
    }
}
