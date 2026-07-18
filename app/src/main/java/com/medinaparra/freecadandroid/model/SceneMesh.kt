package com.medinaparra.freecadandroid.model

/**
 * SceneMesh represents a 3D geometry loaded/generated for rendering.
 *
 * Layout details:
 * x, y, z, nx, ny, nz
 * stride = 24 bytes (6 Floats * 4 bytes per Float)
 */
data class SceneMesh(
    val vertices: FloatArray, // x, y, z, nx, ny, nz per vertex
    val indices: ShortArray,  // Triangle index array
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SceneMesh

        if (!vertices.contentEquals(other.vertices)) return false
        if (!indices.contentEquals(other.indices)) return false
        if (minX != other.minX) return false
        if (minY != other.minY) return false
        if (minZ != other.minZ) return false
        if (maxX != other.maxX) return false
        if (maxY != other.maxY) return false
        if (maxZ != other.maxZ) return false

        return true
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
}
