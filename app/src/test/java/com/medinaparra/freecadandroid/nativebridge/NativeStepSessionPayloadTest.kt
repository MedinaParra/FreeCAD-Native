package com.medinaparra.freecadandroid.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStepSessionPayloadTest {
    @Test
    fun decodesStableOcctFaceMetadata() {
        val payload = NativeStepSessionPayload(
            sessionHandle = 7L,
            vertices = floatArrayOf(
                0f, 0f, 0f, 0f, 0f, 1f,
                1f, 0f, 0f, 0f, 0f, 1f,
                0f, 1f, 0f, 0f, 0f, 1f
            ),
            indices = intArrayOf(0, 1, 2),
            bounds = floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f),
            triangleFaceIds = intArrayOf(3),
            faceIds = intArrayOf(3),
            faceData = floatArrayOf(0.33f, 0.33f, 0f, 0f, 0f, 1f, 0.5f),
            faceFlags = intArrayOf(NativeStepSessionPayload.FACE_FLAG_PLANAR),
            revision = 2,
            summary = "test"
        )

        val snapshot = payload.toSnapshot("fixture.step")

        assertEquals(7L, snapshot.handle)
        assertEquals(2, snapshot.revision)
        assertEquals(3, snapshot.triangleFaceIds.single())
        assertEquals(3, snapshot.faces.single().faceId)
        assertTrue(snapshot.faces.single().planar)
        assertTrue(snapshot.hasStableFaceMapping)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTriangleMappingWithWrongCardinality() {
        NativeStepSessionPayload(
            sessionHandle = 1L,
            vertices = floatArrayOf(
                0f, 0f, 0f, 0f, 0f, 1f,
                1f, 0f, 0f, 0f, 0f, 1f,
                0f, 1f, 0f, 0f, 0f, 1f
            ),
            indices = intArrayOf(0, 1, 2),
            bounds = floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f),
            triangleFaceIds = intArrayOf(),
            faceIds = intArrayOf(),
            faceData = floatArrayOf(),
            faceFlags = intArrayOf(),
            revision = 0,
            summary = "invalid"
        )
    }
}
