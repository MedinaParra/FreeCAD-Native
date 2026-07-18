package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh

data class NativeMeshPayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray
) {
    init {
        require(bounds.size == 6) { "Native bounds must contain six values" }
    }

    fun toSceneMesh(): SceneMesh = SceneMesh(
        vertices = vertices,
        indices = indices,
        minX = bounds[0],
        minY = bounds[1],
        minZ = bounds[2],
        maxX = bounds[3],
        maxY = bounds[4],
        maxZ = bounds[5]
    )
}

data class NativeCadScene(
    val mesh: SceneMesh,
    val buildInfo: String,
    val documentSummary: String
)

/** JNI facade for the OCCT-backed document and geometry core. */
object NativeCadBridge {
    init {
        System.loadLibrary("freecad_android_core")
    }

    external fun nativeBuildInfo(): String
    external fun nativeCreateDocument(name: String): Long
    external fun nativeCloseDocument(documentId: Long)
    external fun nativeAddBox(
        documentId: Long,
        name: String,
        length: Double,
        width: Double,
        height: Double
    ): Long
    external fun nativeAddCylinder(
        documentId: Long,
        name: String,
        radius: Double,
        height: Double
    ): Long
    external fun nativeAddSphere(documentId: Long, name: String, radius: Double): Long
    external fun nativeAddCone(
        documentId: Long,
        name: String,
        radius1: Double,
        radius2: Double,
        height: Double
    ): Long
    external fun nativeAddTorus(
        documentId: Long,
        name: String,
        majorRadius: Double,
        minorRadius: Double
    ): Long
    external fun nativeAddFuse(
        documentId: Long,
        name: String,
        leftId: Long,
        rightId: Long
    ): Long
    external fun nativeAddCut(
        documentId: Long,
        name: String,
        leftId: Long,
        rightId: Long
    ): Long
    external fun nativeAddCommon(
        documentId: Long,
        name: String,
        leftId: Long,
        rightId: Long
    ): Long
    external fun nativeSetPlacement(
        documentId: Long,
        objectId: Long,
        x: Double,
        y: Double,
        z: Double,
        qx: Double,
        qy: Double,
        qz: Double,
        qw: Double
    )
    external fun nativeSetVisibility(documentId: Long, objectId: Long, visible: Boolean)
    external fun nativeRecompute(documentId: Long): Boolean
    external fun nativeLastError(documentId: Long): String
    external fun nativeDocumentSummary(documentId: Long): String
    external fun nativeCreateSceneMesh(
        documentId: Long,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeMeshPayload

    /**
     * Creates a parametric document, executes BRep cuts/fusions, validates it and tessellates it.
     * The document is closed after the mesh and diagnostics have been copied to Kotlin.
     */
    fun createOcctDemoScene(): NativeCadScene {
        val documentId = nativeCreateDocument("OCCT Android Document")
        check(documentId != 0L) { "The native core did not create a document" }

        try {
            val base = nativeAddBox(documentId, "Base", 6.0, 4.0, 1.2)

            val holeA = nativeAddCylinder(documentId, "MountingHoleA", 0.35, 1.6)
            nativeSetPlacement(
                documentId, holeA,
                1.0, 1.0, -0.2,
                0.0, 0.0, 0.0, 1.0
            )
            val firstCut = nativeAddCut(documentId, "BaseCutA", base, holeA)

            val holeB = nativeAddCylinder(documentId, "MountingHoleB", 0.35, 1.6)
            nativeSetPlacement(
                documentId, holeB,
                5.0, 1.0, -0.2,
                0.0, 0.0, 0.0, 1.0
            )
            val secondCut = nativeAddCut(documentId, "BaseCutB", firstCut, holeB)

            val tower = nativeAddCylinder(documentId, "Tower", 0.8, 3.5)
            nativeSetPlacement(
                documentId, tower,
                3.0, 2.0, 1.2,
                0.0, 0.0, 0.0, 1.0
            )
            val body = nativeAddFuse(documentId, "BaseAndTower", secondCut, tower)

            val cap = nativeAddSphere(documentId, "SphericalCap", 1.0)
            nativeSetPlacement(
                documentId, cap,
                3.0, 2.0, 4.35,
                0.0, 0.0, 0.0, 1.0
            )
            nativeAddFuse(documentId, "FinalResult", body, cap)

            check(nativeRecompute(documentId)) {
                nativeLastError(documentId).ifBlank { "Unknown OCCT recompute error" }
            }

            val payload = nativeCreateSceneMesh(
                documentId = documentId,
                linearDeflection = 0.05,
                angularDeflection = 0.30
            )
            return NativeCadScene(
                mesh = payload.toSceneMesh(),
                buildInfo = nativeBuildInfo(),
                documentSummary = nativeDocumentSummary(documentId)
            )
        } finally {
            nativeCloseDocument(documentId)
        }
    }
}
