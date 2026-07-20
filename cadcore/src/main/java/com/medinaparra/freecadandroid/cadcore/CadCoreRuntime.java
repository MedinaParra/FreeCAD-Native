package com.medinaparra.freecadandroid.cadcore;

import java.io.File;
import java.util.EnumSet;

/** Stable contract consumed by Android engineering applications. */
public interface CadCoreRuntime extends AutoCloseable {
    enum Capability {
        JNI_BRIDGE,
        PARAMETRIC_PRIMITIVES,
        STEP_HEADER_VALIDATION,
        STEP_IMPORT,
        TESSELLATION,
        BOUNDING_BOX,
        RIGID_TRANSFORM,
        STEP_EXPORT
    }

    enum PrimitiveType {
        SHELL,
        SHAFT,
        SUPPORT,
        LOCKING_SLEEVE,
        BEARING,
        HUB,
        COUPLING,
        BOX,
        CYLINDER
    }

    final class Status {
        public final String backend;
        public final String version;
        public final boolean nativeLoaded;
        public final EnumSet<Capability> capabilities;
        public final String diagnostic;

        public Status(String backend, String version, boolean nativeLoaded,
                      EnumSet<Capability> capabilities, String diagnostic) {
            this.backend = backend;
            this.version = version;
            this.nativeLoaded = nativeLoaded;
            this.capabilities = capabilities == null
                    ? EnumSet.noneOf(Capability.class)
                    : EnumSet.copyOf(capabilities);
            this.diagnostic = diagnostic == null ? "" : diagnostic;
        }

        public boolean supports(Capability capability) {
            return capabilities.contains(capability);
        }
    }

    final class BoundingBox {
        public final double minX, minY, minZ, maxX, maxY, maxZ;

        public BoundingBox(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public double sizeX() { return maxX - minX; }
        public double sizeY() { return maxY - minY; }
        public double sizeZ() { return maxZ - minZ; }
    }

    final class Mesh {
        public final float[] vertices;
        public final float[] normals;
        public final int[] triangles;
        public final BoundingBox bounds;

        public Mesh(float[] vertices, float[] normals, int[] triangles, BoundingBox bounds) {
            this.vertices = vertices == null ? new float[0] : vertices.clone();
            this.normals = normals == null ? new float[0] : normals.clone();
            this.triangles = triangles == null ? new int[0] : triangles.clone();
            this.bounds = bounds;
        }

        public boolean valid() {
            return vertices.length >= 9 && vertices.length % 3 == 0
                    && triangles.length >= 3 && triangles.length % 3 == 0;
        }
    }

    final class Transform {
        /** Row-major rigid 4x4 transform. */
        public final double[] matrix;

        public Transform(double[] matrix) {
            if (matrix == null || matrix.length != 16) {
                throw new IllegalArgumentException("Rigid transform must contain 16 values");
            }
            this.matrix = matrix.clone();
        }

        public static Transform identity() {
            return new Transform(new double[]{
                    1,0,0,0,
                    0,1,0,0,
                    0,0,1,0,
                    0,0,0,1
            });
        }

        public boolean isRigid(double tolerance) {
            double tx = matrix[0]*matrix[1] + matrix[4]*matrix[5] + matrix[8]*matrix[9];
            double ty = matrix[0]*matrix[2] + matrix[4]*matrix[6] + matrix[8]*matrix[10];
            double tz = matrix[1]*matrix[2] + matrix[5]*matrix[6] + matrix[9]*matrix[10];
            double nx = matrix[0]*matrix[0] + matrix[4]*matrix[4] + matrix[8]*matrix[8];
            double ny = matrix[1]*matrix[1] + matrix[5]*matrix[5] + matrix[9]*matrix[9];
            double nz = matrix[2]*matrix[2] + matrix[6]*matrix[6] + matrix[10]*matrix[10];
            return Math.abs(tx) <= tolerance && Math.abs(ty) <= tolerance
                    && Math.abs(tz) <= tolerance
                    && Math.abs(nx-1) <= tolerance
                    && Math.abs(ny-1) <= tolerance
                    && Math.abs(nz-1) <= tolerance
                    && Math.abs(matrix[12]) <= tolerance
                    && Math.abs(matrix[13]) <= tolerance
                    && Math.abs(matrix[14]) <= tolerance
                    && Math.abs(matrix[15]-1) <= tolerance;
        }
    }

    final class Shape implements AutoCloseable {
        public final String id;
        public final String name;
        public final PrimitiveType type;
        public final String sourcePath;
        public final String sourceSha256;
        public final Mesh mesh;
        public final boolean nativeShape;
        private final Runnable releaser;
        private boolean closed;

        public Shape(String id, String name, PrimitiveType type, String sourcePath,
                     String sourceSha256, Mesh mesh, boolean nativeShape, Runnable releaser) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.sourcePath = sourcePath;
            this.sourceSha256 = sourceSha256;
            this.mesh = mesh;
            this.nativeShape = nativeShape;
            this.releaser = releaser;
        }

        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            if (releaser != null) releaser.run();
        }
    }

    final class PrimitiveRequest {
        public final PrimitiveType type;
        public final String name;
        public final double length;
        public final double diameter;
        public final double width;
        public final double height;
        public final double depth;
        public final double boreDiameter;
        public final int radialSegments;

        public PrimitiveRequest(PrimitiveType type, String name, double length,
                                double diameter, double width, double height,
                                double depth, double boreDiameter, int radialSegments) {
            this.type = type;
            this.name = name;
            this.length = length;
            this.diameter = diameter;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.boreDiameter = boreDiameter;
            this.radialSegments = Math.max(12, Math.min(256, radialSegments));
        }
    }

    final class StepRequest {
        public final File file;
        public final double linearDeflection;
        public final double angularDeflectionDegrees;

        public StepRequest(File file, double linearDeflection, double angularDeflectionDegrees) {
            this.file = file;
            this.linearDeflection = linearDeflection;
            this.angularDeflectionDegrees = angularDeflectionDegrees;
        }
    }

    Status status();
    Shape createPrimitive(PrimitiveRequest request) throws CadCoreException;
    Shape importStep(StepRequest request) throws CadCoreException;
    Shape transformed(Shape source, Transform transform) throws CadCoreException;
    @Override void close();
}
