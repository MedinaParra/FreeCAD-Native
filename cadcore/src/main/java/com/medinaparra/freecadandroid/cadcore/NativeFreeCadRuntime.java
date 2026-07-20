package com.medinaparra.freecadandroid.cadcore;

import java.util.EnumSet;
import java.util.UUID;

/** Native adapter. STEP import is exposed only when the linked kernel reports it. */
public final class NativeFreeCadRuntime implements CadCoreRuntime {
    private static final long CAP_STEP_IMPORT = 1L << 0;
    private static final long CAP_TESSELLATION = 1L << 1;
    private static final long CAP_BOUNDING_BOX = 1L << 2;
    private static final long CAP_RIGID_TRANSFORM = 1L << 3;
    private static final long CAP_STEP_EXPORT = 1L << 4;

    private final Status status;

    public NativeFreeCadRuntime() throws CadCoreException {
        try {
            System.loadLibrary("freecad_android_bridge");
            long mask = NativeFreeCadApi.capabilitiesMask();
            EnumSet<Capability> capabilities = EnumSet.of(Capability.JNI_BRIDGE,
                    Capability.STEP_HEADER_VALIDATION);
            if ((mask & CAP_STEP_IMPORT) != 0) capabilities.add(Capability.STEP_IMPORT);
            if ((mask & CAP_TESSELLATION) != 0) capabilities.add(Capability.TESSELLATION);
            if ((mask & CAP_BOUNDING_BOX) != 0) capabilities.add(Capability.BOUNDING_BOX);
            if ((mask & CAP_RIGID_TRANSFORM) != 0) capabilities.add(Capability.RIGID_TRANSFORM);
            if ((mask & CAP_STEP_EXPORT) != 0) capabilities.add(Capability.STEP_EXPORT);
            status = new Status("FREECAD_NATIVE", NativeFreeCadApi.runtimeInfo(), true,
                    capabilities, NativeFreeCadApi.lastError());
        } catch (Throwable error) {
            throw new CadCoreException("NATIVE_RUNTIME_LOAD_FAILED",
                    "Could not load libfreecad_android_bridge.so", error);
        }
    }

    @Override public Status status() { return status; }

    @Override public Shape createPrimitive(PrimitiveRequest request) throws CadCoreException {
        return new PreviewCadRuntime().createPrimitive(request);
    }

    @Override public Shape importStep(StepRequest request) throws CadCoreException {
        String sha = PreviewCadRuntime.validateStep(request);
        if (!status.supports(Capability.STEP_IMPORT)
                || !status.supports(Capability.TESSELLATION)) {
            throw new CadCoreException("STEP_KERNEL_UNAVAILABLE",
                    "JNI bridge is loaded, but FreeCAD/OpenCascade STEP capability is not linked");
        }
        long handle = NativeFreeCadApi.importStep(request.file.getAbsolutePath(),
                request.linearDeflection, request.angularDeflectionDegrees);
        if (handle == 0) {
            throw new CadCoreException("STEP_IMPORT_FAILED", NativeFreeCadApi.lastError());
        }
        try {
            CadCoreRuntime.Mesh mesh = mesh(handle);
            final long nativeHandle = handle;
            return new Shape(UUID.randomUUID().toString(), request.file.getName(),
                    PrimitiveType.BOX, request.file.getAbsolutePath(), sha, mesh, true,
                    new Runnable() { @Override public void run() { NativeFreeCadApi.release(nativeHandle); }});
        } catch (Throwable error) {
            NativeFreeCadApi.release(handle);
            if (error instanceof CadCoreException) throw (CadCoreException) error;
            throw new CadCoreException("STEP_TESSELLATION_FAILED",
                    "Could not read tessellation from native shape", error);
        }
    }

    @Override public Shape transformed(Shape source, Transform transform) throws CadCoreException {
        if (source == null || source.mesh == null) {
            throw new CadCoreException("SHAPE_INVALID", "Shape is missing");
        }
        if (transform == null || !transform.isRigid(1e-5)) {
            throw new CadCoreException("NON_RIGID_TRANSFORM", "Only rigid transforms are accepted");
        }
        // Imported native handles are deliberately not exposed outside this adapter yet.
        // Use deterministic mesh transformation until the SDK adds handle ownership metadata.
        return new PreviewCadRuntime().transformed(source, transform);
    }

    @Override public void close() { }

    private static Mesh mesh(long handle) throws CadCoreException {
        float[] vertices = NativeFreeCadApi.meshVertices(handle);
        float[] normals = NativeFreeCadApi.meshNormals(handle);
        int[] triangles = NativeFreeCadApi.meshTriangles(handle);
        double[] box = NativeFreeCadApi.boundingBox(handle);
        BoundingBox bounds = box != null && box.length == 6
                ? new BoundingBox(box[0],box[1],box[2],box[3],box[4],box[5]) : null;
        Mesh mesh = new Mesh(vertices,normals,triangles,bounds);
        if (!mesh.valid()) throw new CadCoreException("NATIVE_MESH_INVALID", NativeFreeCadApi.lastError());
        return mesh;
    }
}
