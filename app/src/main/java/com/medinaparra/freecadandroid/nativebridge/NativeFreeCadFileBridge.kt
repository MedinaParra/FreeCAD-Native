package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh

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
}
