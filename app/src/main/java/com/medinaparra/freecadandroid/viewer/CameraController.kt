package com.medinaparra.freecadandroid.viewer

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * CameraController implements an orbital (spherical coordinates) camera
 * for rotating and zooming around a target 3D coordinate.
 */
class CameraController {
    // Spherical coordinates around target
    var yaw: Float = 45.0f       // Horizontal orbital angle (degrees)
    var pitch: Float = 30.0f     // Vertical orbital angle (degrees)
    var radius: Float = 2.5f     // Zoom distance from target

    // Center pivot point
    var targetX: Float = 0.0f
    var targetY: Float = 0.0f
    var targetZ: Float = 0.0f

    // Camera limits to avoid gimbal locks and extreme zoom
    private val minPitch = -85.0f
    private val maxPitch = 85.0f
    private val minRadius = 0.5f
    private val maxRadius = 15.0f

    /**
     * Rotates the camera based on horizontal and vertical mouse/touch drag offsets.
     */
    fun handleDrag(dx: Float, dy: Float) {
        yaw = (yaw - dx * 0.4f) % 360f
        pitch = (pitch + dy * 0.4f).coerceIn(minPitch, maxPitch)
    }

    /**
     * Changes camera distance based on a scale factor from a pinch gesture.
     */
    fun handleZoom(scaleFactor: Float) {
        radius = (radius / scaleFactor).coerceIn(minRadius, maxRadius)
    }

    /**
     * Resets the camera parameters to look at a box.
     */
    fun resetToBox(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float) {
        targetX = (minX + maxX) / 2.0f
        targetY = (minY + maxY) / 2.0f
        targetZ = (minZ + maxZ) / 2.0f

        val dx = maxX - minX
        val dy = maxY - minY
        val dz = maxZ - minZ
        val maxDim = maxOf(dx, dy, dz)

        radius = maxOf(maxDim * 2.0f, 1.5f)
        yaw = 45.0f
        pitch = 30.0f
    }

    /**
     * Generates look-at View Matrix in output float array.
     */
    fun getViewMatrix(viewMatrix: FloatArray) {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        // Compute camera position on sphere
        val cosPitch = cos(pitchRad)
        val camX = targetX + radius * cosPitch * sin(yawRad)
        val camY = targetY + radius * sin(pitchRad)
        val camZ = targetZ + radius * cosPitch * cos(yawRad)

        // Standard up vector is positive Y (due to our restricted pitch range of -85 to 85)
        val upX = 0.0f
        val upY = 1.0f
        val upZ = 0.0f

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX.toFloat(), camY.toFloat(), camZ.toFloat(),
            targetX, targetY, targetZ,
            upX, upY, upZ
        )
    }

    /**
     * Generates Perspective Projection Matrix in output float array.
     */
    fun getProjectionMatrix(projMatrix: FloatArray, width: Int, height: Int) {
        val aspect = if (height == 0) 1.0f else width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 45.0f, aspect, 0.1f, 100.0f)
    }
}
