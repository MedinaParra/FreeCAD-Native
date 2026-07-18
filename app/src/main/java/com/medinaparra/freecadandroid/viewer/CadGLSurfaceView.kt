package com.medinaparra.freecadandroid.viewer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.medinaparra.freecadandroid.model.SceneMesh

/**
 * CadGLSurfaceView is the interactive 3D container component.
 * It coordinates with CadRenderer and processes user touch events for camera manipulation.
 */
class CadGLSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer = CadRenderer()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.cameraController.handleZoom(detector.scaleFactor)
            requestRender()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    init {
        // Set up OpenGL ES 3.0 context
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        // Render only when state changes (saves battery and GPU cycles)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /**
     * Propagates a mesh to the renderer and requests a redraw.
     */
    fun setMesh(mesh: SceneMesh) {
        renderer.setMesh(mesh)
        renderer.cameraController.resetToBox(
            mesh.minX, mesh.minY, mesh.minZ,
            mesh.maxX, mesh.maxY, mesh.maxZ
        )
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Pass event to pinch detector
        scaleDetector.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isScaling = true
            }
            MotionEvent.ACTION_MOVE -> {
                // Only perform single-finger orbit rotation if we aren't performing a pinch zoom
                if (!isScaling && event.pointerCount == 1) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY

                    renderer.cameraController.handleDrag(dx, dy)
                    requestRender()
                }
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount < 2) {
                    isScaling = false
                }
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up GL resources when view is detached
        queueEvent {
            renderer.releaseBuffers()
        }
    }
}
