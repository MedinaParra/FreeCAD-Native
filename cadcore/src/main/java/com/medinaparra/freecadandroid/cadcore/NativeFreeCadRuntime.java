package com.medinaparra.freecadandroid.cadcore;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native adapter. STEP import is exposed only when kernel and resources are ready. */
public final class NativeFreeCadRuntime implements CadCoreRuntime {
    private static final long CAP_STEP_IMPORT = 1L << 0;
    private static final long CAP_TESSELLATION = 1L << 1;
    private static final long CAP_BOUNDING_BOX = 1L << 2;
    private static final long CAP_RIGID_TRANSFORM = 1L << 3;
    private static final long CAP_STEP_EXPORT = 1L << 4;

    private final Status status;
    private final Map<String,Long> handles = new ConcurrentHashMap<String,Long>();
    private volatile boolean closed;

    public NativeFreeCadRuntime() throws CadCoreException {
        this(null);
    }

    public NativeFreeCadRuntime(File resourceRoot) throws CadCoreException {
        try {
            System.loadLibrary("freecad_android_bridge");
            long mask = NativeFreeCadApi.capabilitiesMask();
            boolean kernelStep = (mask & CAP_STEP_IMPORT) != 0 && (mask & CAP_TESSELLATION) != 0;
            boolean resourcesReady = !kernelStep;
            String diagnostic = NativeFreeCadApi.lastError();
            if (kernelStep) {
                if (resourceRoot != null && resourceRoot.isDirectory()) {
                    resourcesReady = NativeFreeCadApi.initializeResources(resourceRoot.getAbsolutePath());
                    if (!resourcesReady) diagnostic = NativeFreeCadApi.lastError();
                } else {
                    diagnostic = "OCCT kernel linked, but Android resource root is not initialized";
                }
            }
            EnumSet<Capability> capabilities = EnumSet.of(Capability.JNI_BRIDGE,
                    Capability.STEP_HEADER_VALIDATION);
            if (resourcesReady && (mask & CAP_STEP_IMPORT) != 0) capabilities.add(Capability.STEP_IMPORT);
            if (resourcesReady && (mask & CAP_TESSELLATION) != 0) capabilities.add(Capability.TESSELLATION);
            if ((mask & CAP_BOUNDING_BOX) != 0) capabilities.add(Capability.BOUNDING_BOX);
            if ((mask & CAP_RIGID_TRANSFORM) != 0) capabilities.add(Capability.RIGID_TRANSFORM);
            if ((mask & CAP_STEP_EXPORT) != 0) capabilities.add(Capability.STEP_EXPORT);
            status = new Status("OCCT_NATIVE", NativeFreeCadApi.runtimeInfo(), true,
                    capabilities, diagnostic);
        } catch (Throwable error) {
            throw new CadCoreException("NATIVE_RUNTIME_LOAD_FAILED",
                    "Could not load libfreecad_android_bridge.so", error);
        }
    }

    @Override public Status status() { return status; }

    @Override public Shape createPrimitive(PrimitiveRequest request) throws CadCoreException {
        ensureOpen();
        return new PreviewCadRuntime().createPrimitive(request);
    }

    @Override public Shape importStep(StepRequest request) throws CadCoreException {
        ensureOpen();
        String sha = PreviewCadRuntime.validateStep(request);
        if (!status.supports(Capability.STEP_IMPORT)
                || !status.supports(Capability.TESSELLATION)) {
            throw new CadCoreException("STEP_KERNEL_UNAVAILABLE",
                    status.diagnostic.isEmpty()
                            ? "OCCT STEP import or tessellation is unavailable"
                            : status.diagnostic);
        }
        long handle = NativeFreeCadApi.importStep(request.file.getAbsolutePath(),
                request.linearDeflection, request.angularDeflectionDegrees);
        if (handle == 0) {
            throw new CadCoreException("STEP_IMPORT_FAILED", NativeFreeCadApi.lastError());
        }
        try {
            String id = UUID.randomUUID().toString();
            CadCoreRuntime.Mesh mesh = mesh(handle);
            handles.put(id,handle);
            return shape(id, request.file.getName(), PrimitiveType.BOX,
                    request.file.getAbsolutePath(), sha, mesh, handle);
        } catch (Throwable error) {
            NativeFreeCadApi.release(handle);
            if (error instanceof CadCoreException) throw (CadCoreException) error;
            throw new CadCoreException("STEP_TESSELLATION_FAILED",
                    "Could not read tessellation from native shape", error);
        }
    }

    @Override public Shape transformed(Shape source, Transform transform) throws CadCoreException {
        ensureOpen();
        if (source == null || source.mesh == null) {
            throw new CadCoreException("SHAPE_INVALID", "Shape is missing");
        }
        if (transform == null || !transform.isRigid(1e-5)) {
            throw new CadCoreException("NON_RIGID_TRANSFORM", "Only rigid transforms are accepted");
        }
        Long sourceHandle=handles.get(source.id);
        if(source.nativeShape&&sourceHandle!=null&&status.supports(Capability.RIGID_TRANSFORM)){
            long handle=NativeFreeCadApi.transformedCopy(sourceHandle,transform.matrix);
            if(handle==0)throw new CadCoreException("NATIVE_TRANSFORM_FAILED",NativeFreeCadApi.lastError());
            try{
                String id=UUID.randomUUID().toString();
                Mesh mesh=mesh(handle);handles.put(id,handle);
                return shape(id,source.name,source.type,source.sourcePath,
                        source.sourceSha256,mesh,handle);
            }catch(Throwable error){
                NativeFreeCadApi.release(handle);
                if(error instanceof CadCoreException)throw (CadCoreException)error;
                throw new CadCoreException("NATIVE_TRANSFORM_MESH_FAILED",
                        "Could not read transformed native mesh",error);
            }
        }
        return new PreviewCadRuntime().transformed(source, transform);
    }

    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        for(Long handle:new ArrayList<Long>(handles.values()))NativeFreeCadApi.release(handle);
        handles.clear();
    }

    private Shape shape(final String id,String name,PrimitiveType type,String path,
                        String sha,Mesh mesh,long handle){
        return new Shape(id,name,type,path,sha,mesh,true,new Runnable(){
            @Override public void run(){
                Long owned=handles.remove(id);
                if(owned!=null)NativeFreeCadApi.release(owned);
            }
        });
    }

    private void ensureOpen() throws CadCoreException {
        if(closed)throw new CadCoreException("RUNTIME_CLOSED","CAD runtime has been closed");
    }

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
