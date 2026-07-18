package com.medinaparra.freecadandroid.nativebridge

import com.medinaparra.freecadandroid.model.SceneMesh

object NativeBoxBridge {
    init {
        System.loadLibrary("freecad_android_core")
    }

    private external fun createBoxVertices(): FloatArray
    private external fun createBoxIndices(): ShortArray
    external fun buildInfo(): String

    fun createScene(): SceneMesh = SceneMesh(
        vertices = createBoxVertices(),
        indices = createBoxIndices(),
        minX = -0.5f,
        minY = -0.5f,
        minZ = -0.5f,
        maxX = 0.5f,
        maxY = 0.5f,
        maxZ = 0.5f
    )
}
