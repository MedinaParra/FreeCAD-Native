package com.medinaparra.freecadandroid.viewer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.medinaparra.freecadandroid.model.SceneMesh

/**
 * Interactive 3D container backed by OpenGL ES 3.
 */
class CadGLSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer = CadRenderer()

    private var currentMesh: SceneMesh? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
        }
    )

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /**
     * Uploads only genuinely new geometry. The camera fit is queued on the GL thread
     * so its matrices and the uploaded mesh are guaranteed to belong to the same frame.
     */
    fun setMesh(mesh: SceneMesh) {
        if (currentMesh === mesh) {
            requestRender()
            return
        }
        currentMesh = mesh
        renderer.setMesh(mesh)
        queueEvent {
            renderer.cameraController.resetToBox(
                mesh.minX,
                mesh.minY,
                mesh.minZ,
                mesh.maxX,
                mesh.maxY,
                mesh.maxZ
            )
        }
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
                if (!isScaling && event.pointerCount == 1) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    renderer.cameraController.handleDrag(dx, dy)
                    requestRender()
                }
                lastTouchX = x
                lastTouchY = y
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount < 2) {
                    isScaling = false
                }
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        queueEvent {
            renderer.releaseBuffers()
        }
        super.onDetachedFromWindow()
    }
}
