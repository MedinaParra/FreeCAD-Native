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

data class NativeStepFaceDescriptor(
    val faceId: Int,
    val point: FloatArray,
    val normal: FloatArray,
    val area: Float,
    val planar: Boolean
) {
    init {
        require(faceId > 0) { "OCCT face identifiers are 1-based" }
        require(point.size == 3) { "STEP face point must contain three values" }
        require(normal.size == 3) { "STEP face normal must contain three values" }
        require(area >= 0f) { "STEP face area cannot be negative" }
    }
}

data class NativeStepSessionPayload(
    val sessionHandle: Long,
    val vertices: FloatArray,
    val indices: IntArray,
    val bounds: FloatArray,
    val triangleFaceIds: IntArray,
    val faceIds: IntArray,
    val faceData: FloatArray,
    val faceFlags: IntArray,
    val revision: Int,
    val summary: String
) {
    init {
        require(sessionHandle > 0L) { "STEP session handle must be positive" }
        require(bounds.size == 6) { "STEP bounds must contain six values" }
        require(indices.size % 3 == 0) { "STEP indices must contain complete triangles" }
        require(triangleFaceIds.size == indices.size / 3) {
            "Every STEP triangle must map to one OCCT face"
        }
        require(faceIds.size == faceFlags.size) { "STEP face flags do not match face identifiers" }
        require(faceData.size == faceIds.size * FACE_DATA_STRIDE) {
            "STEP face metadata uses a fixed seven-value stride"
        }
        require(revision >= 0) { "STEP revision cannot be negative" }
    }

    fun toSnapshot(displayName: String): NativeStepSessionSnapshot {
        val faces = faceIds.indices.map { index ->
            val offset = index * FACE_DATA_STRIDE
            NativeStepFaceDescriptor(
                faceId = faceIds[index],
                point = floatArrayOf(faceData[offset], faceData[offset + 1], faceData[offset + 2]),
                normal = floatArrayOf(faceData[offset + 3], faceData[offset + 4], faceData[offset + 5]),
                area = faceData[offset + 6],
                planar = faceFlags[index] and FACE_FLAG_PLANAR != 0
            )
        }
        return NativeStepSessionSnapshot(
            handle = sessionHandle,
            revision = revision,
            mesh = NativeStepPayload(vertices, indices, bounds, summary).toSceneMesh(),
            triangleFaceIds = triangleFaceIds.copyOf(),
            faces = faces,
            summary = summary,
            displayName = displayName
        )
    }

    companion object {
        const val FACE_DATA_STRIDE = 7
        const val FACE_FLAG_PLANAR = 1
    }
}

data class NativeStepSessionSnapshot(
    val handle: Long,
    val revision: Int,
    val mesh: SceneMesh,
    val triangleFaceIds: IntArray,
    val faces: List<NativeStepFaceDescriptor>,
    val summary: String,
    val displayName: String
) {
    val hasStableFaceMapping: Boolean
        get() = triangleFaceIds.size == mesh.indices.size / 3 && faces.isNotEmpty()
}

object NativeStepBridge {
    init {
        System.loadLibrary("freecad_android_core")
    }

    private external fun nativeImportStep(
        localPath: String,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeStepPayload

    private external fun nativeOpenStepSession(
        localPath: String,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeStepSessionPayload

    private external fun nativePreviewPull(
        handle: Long,
        faceId: Int,
        distance: Double,
        linearDeflection: Double,
        angularDeflection: Double
    ): NativeStepSessionPayload

    private external fun nativeCommitStepSession(handle: Long): NativeStepSessionPayload
    private external fun nativeRollbackStepSession(handle: Long): NativeStepSessionPayload
    private external fun nativeSaveStepSession(handle: Long, outputPath: String): String
    private external fun nativeCloseStepSession(handle: Long)

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

    fun openSession(
        localPath: String,
        displayName: String,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeStepSessionSnapshot = nativeOpenStepSession(
        localPath,
        linearDeflection,
        angularDeflection
    ).toSnapshot(displayName)

    fun previewPull(
        snapshot: NativeStepSessionSnapshot,
        faceId: Int,
        distance: Double,
        linearDeflection: Double = 0.35,
        angularDeflection: Double = 0.30
    ): NativeStepSessionSnapshot = nativePreviewPull(
        snapshot.handle,
        faceId,
        distance,
        linearDeflection,
        angularDeflection
    ).toSnapshot(snapshot.displayName)

    fun commit(snapshot: NativeStepSessionSnapshot): NativeStepSessionSnapshot =
        nativeCommitStepSession(snapshot.handle).toSnapshot(snapshot.displayName)

    fun rollback(snapshot: NativeStepSessionSnapshot): NativeStepSessionSnapshot =
        nativeRollbackStepSession(snapshot.handle).toSnapshot(snapshot.displayName)

    fun saveCopy(snapshot: NativeStepSessionSnapshot, outputPath: String): String {
        require(outputPath.isNotBlank()) { "STEP output path cannot be blank" }
        return nativeSaveStepSession(snapshot.handle, outputPath)
    }

    fun close(snapshot: NativeStepSessionSnapshot) {
        nativeCloseStepSession(snapshot.handle)
    }
}
