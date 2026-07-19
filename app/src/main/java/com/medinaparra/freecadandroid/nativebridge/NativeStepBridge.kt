package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh

data class NativeStepPayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray,
    val summary: String
) {
    init {
        require(bounds.size == 6) { "STEP bounds must contain six values" }
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

data class NativeStepScene(
    val mesh: SceneMesh,
    val summary: String,
    val displayName: String
)

object NativeStepBridge {
    init {
        System.loadLibrary("freecad_android_core")
    }

    private external fun nativeImportStep(
        localPath: String,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeStepPayload

    fun importStep(
        localPath: String,
        displayName: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeStepScene {
        val payload = nativeImportStep(localPath, linearDeflection, angularDeflection)
        return NativeStepScene(
            mesh = payload.toSceneMesh(),
            summary = payload.summary,
            displayName = displayName
        )
    }
}
