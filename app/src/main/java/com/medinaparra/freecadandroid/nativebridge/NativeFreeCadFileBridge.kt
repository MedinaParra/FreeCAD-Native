package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.io.FcStdObjectRecord

data class NativeFreeCadFilePayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray,
    val summary: String
) {
    init {
        require(bounds.size == 6) { "FCStd bounds must contain six values" }
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

data class NativeFreeCadFileScene(
    val mesh: SceneMesh,
    val summary: String,
    val displayName: String
)

object NativeFreeCadFileBridge {
    init {
        System.loadLibrary("freecad_android_core")
    }

    private external fun nativeImportBrepFiles(
        localPaths: Array<String>,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeFreeCadFilePayload

    private external fun nativeImportBrepObjects(
        localPaths: Array<String>,
        objectNames: Array<String>,
        placementValues: DoubleArray,
        visibilityValues: BooleanArray,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeFreeCadFilePayload

    fun importFcStdBreps(
        localPaths: List<String>,
        displayName: String,
        archiveSummary: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeFreeCadFileScene {
        require(localPaths.isNotEmpty()) { "The FCStd archive contains no supported BREP payload" }
        val payload = nativeImportBrepFiles(
            localPaths.toTypedArray(),
            linearDeflection,
            angularDeflection
        )
        return NativeFreeCadFileScene(
            mesh = payload.toSceneMesh(),
            summary = archiveSummary + "\n" + payload.summary,
            displayName = displayName
        )
    }

    fun importFcStdObjects(
        objects: List<FcStdObjectRecord>,
        displayName: String,
        archiveSummary: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeFreeCadFileScene {
        val shapeObjects = objects.filter { it.brepFile != null }
        require(shapeObjects.isNotEmpty()) { "The FCStd archive contains no supported shape objects" }
        val placements = DoubleArray(shapeObjects.size * 7)
        shapeObjects.forEachIndexed { index, record ->
            val value = record.placement
            val offset = index * 7
            placements[offset] = value.x
            placements[offset + 1] = value.y
            placements[offset + 2] = value.z
            placements[offset + 3] = value.qx
            placements[offset + 4] = value.qy
            placements[offset + 5] = value.qz
            placements[offset + 6] = value.qw
        }
        val payload = nativeImportBrepObjects(
            shapeObjects.map { requireNotNull(it.brepFile).absolutePath }.toTypedArray(),
            shapeObjects.map { it.name }.toTypedArray(),
            placements,
            BooleanArray(shapeObjects.size) { shapeObjects[it].visible },
            linearDeflection,
            angularDeflection
        )
        return NativeFreeCadFileScene(
            mesh = payload.toSceneMesh(),
            summary = archiveSummary + "\n" + payload.summary,
            displayName = displayName
        )
    }
}
