package com.medinaparra.freecadandroid.viewer

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orbital camera for CAD geometry expressed in real model units.
 *
 * The clipping planes and zoom limits are derived from the current bounding box.
 * This is important because OCCT models can range from millimetres to metres and a
 * fixed far plane can silently clip an otherwise valid mesh.
 */
class CameraController {
    @Volatile
    var yaw: Float = 45.0f

    @Volatile
    var pitch: Float = 30.0f

    @Volatile
    var radius: Float = 2.5f

    @Volatile
    var targetX: Float = 0.0f

    @Volatile
    var targetY: Float = 0.0f

    @Volatile
    var targetZ: Float = 0.0f

    private val minPitch = -85.0f
    private val maxPitch = 85.0f

    @Volatile
    private var sceneRadius = 1.0f

    @Volatile
    private var minRadius = 0.05f

    @Volatile
    private var maxRadius = 1000.0f

    fun handleDrag(dx: Float, dy: Float) {
        yaw = (yaw - dx * 0.4f) % 360f
        pitch = (pitch + dy * 0.4f).coerceIn(minPitch, maxPitch)
    }

    fun handleZoom(scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0.0f) return
        radius = (radius / scaleFactor).coerceIn(minRadius, maxRadius)
    }

    /** Fits the complete OCCT bounding box and updates scale-dependent clipping. */
    fun resetToBox(
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float
    ) {
        val bounds = floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
        if (bounds.any { !it.isFinite() } || minX > maxX || minY > maxY || minZ > maxZ) {
            resetFallback()
            return
        }

        targetX = (minX + maxX) * 0.5f
        targetY = (minY + maxY) * 0.5f
        targetZ = (minZ + maxZ) * 0.5f

        val dx = (maxX - minX).coerceAtLeast(0.0f)
        val dy = (maxY - minY).coerceAtLeast(0.0f)
        val dz = (maxZ - minZ).coerceAtLeast(0.0f)
        val maxDimension = maxOf(dx, dy, dz)
        val diagonal = sqrt(dx * dx + dy * dy + dz * dz)

        sceneRadius = maxOf(diagonal * 0.5f, maxDimension * 0.5f, 0.5f)
        minRadius = maxOf(sceneRadius * 0.03f, 0.01f)
        maxRadius = maxOf(sceneRadius * 100.0f, 100.0f)

        // A generous fit also works in a tall portrait viewport.
        radius = maxOf(sceneRadius * 3.5f, maxDimension * 2.2f, 1.5f)
            .coerceIn(minRadius, maxRadius)
        yaw = 45.0f
        pitch = 30.0f
    }

    fun getViewMatrix(viewMatrix: FloatArray) {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val cosPitch = cos(pitchRad)
        val camX = targetX + radius * cosPitch * sin(yawRad)
        val camY = targetY + radius * sin(pitchRad)
        val camZ = targetZ + radius * cosPitch * cos(yawRad)

        Matrix.setLookAtM(
            viewMatrix,
            0,
            camX.toFloat(),
            camY.toFloat(),
            camZ.toFloat(),
            targetX,
            targetY,
            targetZ,
            0.0f,
            1.0f,
            0.0f
        )
    }

    /**
     * Uses adaptive near/far planes. For the 80 mm validation macro this moves the
     * far plane beyond the roughly 170 mm camera distance instead of clipping at 100.
     */
    fun getProjectionMatrix(projectionMatrix: FloatArray, width: Int, height: Int) {
        val aspect = if (height <= 0) 1.0f else width.coerceAtLeast(1).toFloat() / height.toFloat()
        val nearPlane = maxOf(sceneRadius * 0.002f, radius * 0.0005f, 0.01f)
        val farPlane = maxOf(
            radius + sceneRadius * 4.0f,
            sceneRadius * 12.0f,
            nearPlane * 1000.0f
        )
        Matrix.perspectiveM(
            projectionMatrix,
            0,
            45.0f,
            aspect,
            nearPlane,
            farPlane
        )
    }

    private fun resetFallback() {
        targetX = 0.0f
        targetY = 0.0f
        targetZ = 0.0f
        sceneRadius = 1.0f
        minRadius = 0.05f
        maxRadius = 1000.0f
        radius = 3.5f
        yaw = 45.0f
        pitch = 30.0f
    }
}
