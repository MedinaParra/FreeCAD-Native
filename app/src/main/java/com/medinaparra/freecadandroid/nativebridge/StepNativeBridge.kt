package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh

data class NativeStepPayload(
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray,
    val sourceName: String,
    val summary: String,
    val rootCount: Int,
    val transferredRootCount: Int,
    val shapeCount: Int
) {
    init {
        require(bounds.size == 6) { "Native STEP bounds must contain six values" }
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
    val sourceName: String,
    val summary: String,
    val rootCount: Int,
    val transferredRootCount: Int,
    val shapeCount: Int
)

object StepNativeBridge {
    init {
        System.loadLibrary("freecad_android_core")
    }

    external fun nativeImportStep(
        cachedStepPath: String,
        displayName: String,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeStepPayload

    fun importStep(
        cachedStepPath: String,
        displayName: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeStepScene {
        val payload = nativeImportStep(
            cachedStepPath,
            displayName,
            linearDeflection,
            angularDeflection
        )
        return NativeStepScene(
            mesh = payload.toSceneMesh(),
            sourceName = payload.sourceName,
            summary = payload.summary,
            rootCount = payload.rootCount,
            transferredRootCount = payload.transferredRootCount,
            shapeCount = payload.shapeCount
        )
    }
}
