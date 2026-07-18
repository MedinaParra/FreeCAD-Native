package com.medinaparra.freecadandroid.viewer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.medinaparra.freecadandroid.model.SceneMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CadRenderer implements GLES 3.0 rendering pipeline for CAD meshes and navigation axes.
 */
class CadRenderer : GLSurfaceView.Renderer {

    val cameraController = CameraController()

    // Thread-safe scene mesh propagation from main thread to GL thread
    private var pendingMesh: SceneMesh? = null
    private var activeMesh: SceneMesh? = null
    private var isMeshPendingUpload = false

    // OpenGL State Handles
    private var meshProgram: Int = 0
    private var axesProgram: Int = 0

    // VBO and IBO Handles
    private var meshVboId: Int = 0
    private var meshIboId: Int = 0
    private var axesVboId: Int = 0

    private var indexCount: Int = 0

    // Viewport configuration
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Transform Matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Shaders: Mesh (Phong/Lambertian Lighting)
    private val meshVertexShaderCode = """
        #version 300 es
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        
        out vec3 vNormal;
        out vec3 vFragPos;
        
        void main() {
            vFragPos = vec3(uModelMatrix * vec4(aPosition, 1.0));
            vNormal = normalize(mat3(uModelMatrix) * aNormal);
            gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val meshFragmentShaderCode = """
        #version 300 es
        precision mediump float;
        
        in vec3 vNormal;
        in vec3 vFragPos;
        
        uniform vec4 uColor;
        uniform vec3 uLightDir;
        uniform vec3 uAmbientColor;
        uniform vec3 uDiffuseColor;
        
        out vec4 fragColor;
        
        void main() {
            vec3 norm = normalize(vNormal);
            vec3 lightDir = normalize(uLightDir);
            float diff = max(dot(norm, lightDir), 0.0);
            
            vec3 ambient = uAmbientColor * uColor.rgb;
            vec3 diffuse = diff * uDiffuseColor * uColor.rgb;
            
            fragColor = vec4(ambient + diffuse, uColor.a);
        }
    """.trimIndent()

    // Shaders: Navigation XYZ Axes (Simple colored lines)
    private val axesVertexShaderCode = """
        #version 300 es
        uniform mat4 uMVPMatrix;
        
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aColor;
        
        out vec3 vColor;
        
        void main() {
            vColor = aColor;
            gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val axesFragmentShaderCode = """
        #version 300 es
        precision mediump float;
        
        in vec3 vColor;
        out vec4 fragColor;
        
        void main() {
            fragColor = vec4(vColor, 1.0);
        }
    """.trimIndent()

    /**
     * Safely updates the mesh to render.
     */
    fun setMesh(mesh: SceneMesh) {
        synchronized(this) {
            pendingMesh = mesh
            isMeshPendingUpload = true
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set beautiful, clean Elegant Dark background for the 3D viewport
        GLES30.glClearColor(0.07f, 0.07f, 0.08f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        // Compile and link programs
        meshProgram = createProgram(meshVertexShaderCode, meshFragmentShaderCode)
        axesProgram = createProgram(axesVertexShaderCode, axesFragmentShaderCode)

        // Create buffer handles
        val buffers = IntArray(3)
        GLES30.glGenBuffers(3, buffers, 0)
        meshVboId = buffers[0]
        meshIboId = buffers[1]
        axesVboId = buffers[2]

        // Upload constant XYZ axes geometry
        // X: Red (1.2 long), Y: Green (1.2 long), Z: Blue (1.2 long)
        val axesData = floatArrayOf(
            // x, y, z, r, g, b
            0.0f, 0.0f, 0.0f,   1.0f, 0.0f, 0.0f, // X-axis start
            1.2f, 0.0f, 0.0f,   1.0f, 0.0f, 0.0f, // X-axis end

            0.0f, 0.0f, 0.0f,   0.0f, 1.0f, 0.0f, // Y-axis start
            0.0f, 1.2f, 0.0f,   0.0f, 1.0f, 0.0f, // Y-axis end

            0.0f, 0.0f, 0.0f,   0.0f, 0.0f, 1.0f, // Z-axis start
            0.0f, 0.0f, 1.2f,   0.0f, 0.0f, 1.0f  // Z-axis end
        )
        val axesBuffer = ByteBuffer.allocateDirect(axesData.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(axesData)
                position(0)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, axesVboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, axesData.size * 4, axesBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear color and depth buffers
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Check if there is a pending mesh to upload
        var meshToUpload: SceneMesh? = null
        synchronized(this) {
            if (isMeshPendingUpload) {
                meshToUpload = pendingMesh
                isMeshPendingUpload = false
            }
        }

        meshToUpload?.let { uploadMesh(it) }

        // Compute matrices from CameraController
        Matrix.setIdentityM(modelMatrix, 0)
        cameraController.getViewMatrix(viewMatrix)
        cameraController.getProjectionMatrix(projMatrix, viewportWidth, viewportHeight)

        // 1. Render XYZ Axes (rendered at origin without model transforms)
        drawAxes()

        // 2. Render CAD Scene Mesh (if active and uploaded)
        if (indexCount > 0) {
            drawMesh()
        }
    }

    private fun uploadMesh(mesh: SceneMesh) {
        activeMesh = mesh
        indexCount = mesh.indices.size

        // Prepare raw Direct Buffers
        val vBuffer = ByteBuffer.allocateDirect(mesh.vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(mesh.vertices)
                position(0)
            }
        }
        val iBuffer = ByteBuffer.allocateDirect(mesh.indices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(mesh.indices)
                position(0)
            }
        }

        // Upload to GPU buffers
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.vertices.size * 4, vBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, meshIboId)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 2, iBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawAxes() {
        GLES30.glUseProgram(axesProgram)

        // MVP = P * V * I (no model transformation)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

        val mvpHandle = GLES30.glGetUniformLocation(axesProgram, "uMVPMatrix")
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, axesVboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0) // Position

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12) // Color

        // Draw 3 axes segments (6 vertices total)
        GLES30.glLineWidth(3.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, 6)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawMesh() {
        GLES30.glUseProgram(meshProgram)

        // Compute MVP Matrix = Projection * View * Model
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

        // Uniform matrices
        val mvpHandle = GLES30.glGetUniformLocation(meshProgram, "uMVPMatrix")
        val modelHandle = GLES30.glGetUniformLocation(meshProgram, "uModelMatrix")
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)

        // Uniform material properties
        val colorHandle = GLES30.glGetUniformLocation(meshProgram, "uColor")
        val lightDirHandle = GLES30.glGetUniformLocation(meshProgram, "uLightDir")
        val ambientHandle = GLES30.glGetUniformLocation(meshProgram, "uAmbientColor")
        val diffuseHandle = GLES30.glGetUniformLocation(meshProgram, "uDiffuseColor")

        // Set mesh color: Professional CAD Teal (#358CC4)
        GLES30.glUniform4f(colorHandle, 0.207f, 0.549f, 0.768f, 1.0f)
        // Light direction coming from high-front-right
        GLES30.glUniform3f(lightDirHandle, 0.5f, 0.8f, 1.0f)
        // Low ambient, warm diffuse lighting
        GLES30.glUniform3f(ambientHandle, 0.25f, 0.25f, 0.25f)
        GLES30.glUniform3f(diffuseHandle, 0.75f, 0.75f, 0.75f)

        // Bind VBO & Attribute pointers
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVboId)
        GLES30.glEnableVertexAttribArray(0)
        // Stride: 24 bytes (6 Floats * 4 bytes). Position offset: 0 bytes.
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)

        GLES30.glEnableVertexAttribArray(1)
        // Normal offset: 12 bytes (3 Floats * 4 bytes).
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)

        // Bind IBO & Draw elements
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, meshIboId)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        // Unbind buffers and disable attributes
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES30.glCreateProgram()
        if (program != 0) {
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES30.GL_TRUE) {
                val error = GLES30.glGetProgramInfoLog(program)
                Log.e("CadRenderer", "Could not link program: $error")
                GLES30.glDeleteProgram(program)
                return 0
            }
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader != 0) {
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] != GLES30.GL_TRUE) {
                val error = GLES30.glGetShaderInfoLog(shader)
                Log.e("CadRenderer", "Could not compile shader $type: $error")
                GLES30.glDeleteShader(shader)
                return 0
            }
        }
        return shader
    }

    /**
     * Call to clean up OpenGL buffers on destruction.
     */
    fun releaseBuffers() {
        val buffers = intArrayOf(meshVboId, meshIboId, axesVboId)
        GLES30.glDeleteBuffers(3, buffers, 0)
        if (meshProgram != 0) {
            GLES30.glDeleteProgram(meshProgram)
            meshProgram = 0
        }
        if (axesProgram != 0) {
            GLES30.glDeleteProgram(axesProgram)
            axesProgram = 0
        }
    }
}
