package com.medinaparra.freecadandroid.model

/**
 * Render-ready CAD mesh.
 *
 * Vertex layout: x, y, z, nx, ny, nz (6 floats / 24 bytes per vertex).
 * Indices are 32-bit so industrial meshes are not limited to 65,535 vertices.
 */
data class SceneMesh(
    val vertices: FloatArray,
    val indices: IntArray,
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    val vertexCount: Int = vertices.size / FLOATS_PER_VERTEX
    val triangleCount: Int = indices.size / 3

    init {
        require(vertices.isNotEmpty()) { "Mesh vertices cannot be empty" }
        require(vertices.size % FLOATS_PER_VERTEX == 0) {
            "Vertex data must use the x,y,z,nx,ny,nz layout"
        }
        require(indices.isNotEmpty() && indices.size % 3 == 0) {
            "Triangle index data must be non-empty and divisible by three"
        }
        require(indices.all { it in 0 until vertexCount }) {
            "Mesh contains an index outside the vertex range"
        }
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) {
            "Mesh bounding box is invalid"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SceneMesh) return false
        return vertices.contentEquals(other.vertices) &&
            indices.contentEquals(other.indices) &&
            minX == other.minX && minY == other.minY && minZ == other.minZ &&
            maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ
    }

    override fun hashCode(): Int {
        var result = vertices.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + minX.hashCode()
        result = 31 * result + minY.hashCode()
        result = 31 * result + minZ.hashCode()
        result = 31 * result + maxX.hashCode()
        result = 31 * result + maxY.hashCode()
        result = 31 * result + maxZ.hashCode()
        return result
    }

    companion object {
        const val FLOATS_PER_VERTEX = 6
        const val VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES
    }
}
