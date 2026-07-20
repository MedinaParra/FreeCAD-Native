package com.medinaparra.freecadandroid.cadcore;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Deterministic fallback for parametric assembly editing. It never claims STEP geometry import. */
public final class PreviewCadRuntime implements CadCoreRuntime {
    private final Status status = new Status(
            "PARAMETRIC_PREVIEW",
            "1.0",
            false,
            EnumSet.of(Capability.PARAMETRIC_PRIMITIVES,
                    Capability.STEP_HEADER_VALIDATION,
                    Capability.BOUNDING_BOX,
                    Capability.RIGID_TRANSFORM),
            "FreeCAD/OpenCascade runtime not loaded; STEP is validated and retained but not tessellated."
    );

    @Override public Status status() { return status; }

    @Override public Shape createPrimitive(PrimitiveRequest request) throws CadCoreException {
        if (request == null || request.type == null) {
            throw new CadCoreException("INVALID_PRIMITIVE", "Primitive type is required");
        }
        Mesh mesh;
        switch (request.type) {
            case SHELL:
            case SHAFT:
            case LOCKING_SLEEVE:
            case BEARING:
            case HUB:
            case COUPLING:
            case CYLINDER:
                mesh = cylinder(request.length, request.diameter, request.boreDiameter,
                        request.radialSegments);
                break;
            case SUPPORT:
            case BOX:
            default:
                mesh = box(request.width, request.height, request.depth);
                break;
        }
        return new Shape(UUID.randomUUID().toString(), clean(request.name, request.type.name()),
                request.type, null, null, mesh, false, null);
    }

    @Override public Shape importStep(StepRequest request) throws CadCoreException {
        validateStep(request);
        throw new CadCoreException("STEP_RUNTIME_UNAVAILABLE",
                "STEP file is valid, but the FreeCAD/OpenCascade native runtime is not packaged in this build");
    }

    public static String validateStep(StepRequest request) throws CadCoreException {
        if (request == null || request.file == null || !request.file.isFile()) {
            throw new CadCoreException("STEP_FILE_MISSING", "STEP file does not exist");
        }
        String name = request.file.getName().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".step") || name.endsWith(".stp"))) {
            throw new CadCoreException("STEP_EXTENSION_INVALID", "Expected .step or .stp file");
        }
        byte[] head = new byte[512];
        int count;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(request.file))) {
            count = input.read(head);
        } catch (Exception error) {
            throw new CadCoreException("STEP_READ_FAILED", "Could not read STEP file", error);
        }
        String text = new String(head, 0, Math.max(0, count), StandardCharsets.US_ASCII);
        if (!text.contains("ISO-10303-21") || !text.contains("HEADER")) {
            throw new CadCoreException("STEP_HEADER_INVALID", "ISO-10303-21 header not found");
        }
        return sha256(request.file);
    }

    @Override public Shape transformed(Shape source, Transform transform) throws CadCoreException {
        if (source == null || source.mesh == null || !source.mesh.valid()) {
            throw new CadCoreException("SHAPE_INVALID", "Source shape has no valid mesh");
        }
        if (transform == null || !transform.isRigid(1e-5)) {
            throw new CadCoreException("NON_RIGID_TRANSFORM",
                    "Only rigid transforms are allowed; STEP geometry is never scaled to force alignment");
        }
        float[] vertices = source.mesh.vertices.clone();
        double[] m = transform.matrix;
        for (int i=0;i<vertices.length;i+=3) {
            double x=vertices[i], y=vertices[i+1], z=vertices[i+2];
            vertices[i]=(float)(m[0]*x+m[1]*y+m[2]*z+m[3]);
            vertices[i+1]=(float)(m[4]*x+m[5]*y+m[6]*z+m[7]);
            vertices[i+2]=(float)(m[8]*x+m[9]*y+m[10]*z+m[11]);
        }
        float[] normals = source.mesh.normals.clone();
        for (int i=0;i<normals.length;i+=3) {
            double x=normals[i], y=normals[i+1], z=normals[i+2];
            normals[i]=(float)(m[0]*x+m[1]*y+m[2]*z);
            normals[i+1]=(float)(m[4]*x+m[5]*y+m[6]*z);
            normals[i+2]=(float)(m[8]*x+m[9]*y+m[10]*z);
        }
        Mesh mesh = new Mesh(vertices, normals, source.mesh.triangles, bounds(vertices));
        return new Shape(UUID.randomUUID().toString(), source.name, source.type,
                source.sourcePath, source.sourceSha256, mesh, source.nativeShape, null);
    }

    @Override public void close() { }

    private static Mesh box(double width, double height, double depth) throws CadCoreException {
        if (!(width > 0 && height > 0 && depth > 0)) {
            throw new CadCoreException("INVALID_BOX", "Width, height and depth must be positive");
        }
        float x=(float)(width/2), y=(float)(height/2), z=(float)(depth/2);
        float[] v={-x,-y,-z, x,-y,-z, x,y,-z, -x,y,-z,
                -x,-y,z, x,-y,z, x,y,z, -x,y,z};
        int[] t={0,2,1,0,3,2,4,5,6,4,6,7,0,1,5,0,5,4,
                1,2,6,1,6,5,2,3,7,2,7,6,3,0,4,3,4,7};
        float[] n = vertexNormals(v,t);
        return new Mesh(v,n,t,bounds(v));
    }

    private static Mesh cylinder(double length, double diameter, double boreDiameter,
                                 int segments) throws CadCoreException {
        if (!(length > 0 && diameter > 0) || boreDiameter < 0 || boreDiameter >= diameter) {
            throw new CadCoreException("INVALID_CYLINDER",
                    "Length and diameter must be positive; bore must be smaller than diameter");
        }
        int rings = boreDiameter > 0 ? 4 : 2;
        List<Float> vertices = new ArrayList<>();
        double half=length/2, outer=diameter/2, inner=boreDiameter/2;
        for (int ring=0;ring<rings;ring++) {
            boolean innerRing = ring >= 2;
            double radius = innerRing ? inner : outer;
            double x = (ring % 2 == 0) ? -half : half;
            for (int i=0;i<segments;i++) {
                double angle=2*Math.PI*i/segments;
                vertices.add((float)x);
                vertices.add((float)(radius*Math.cos(angle)));
                vertices.add((float)(radius*Math.sin(angle)));
            }
        }
        List<Integer> triangles = new ArrayList<>();
        for (int i=0;i<segments;i++) {
            int next=(i+1)%segments;
            addQuad(triangles,i,next,segments+next,segments+i,false);
        }
        if (boreDiameter > 0) {
            for (int i=0;i<segments;i++) {
                int next=(i+1)%segments;
                addQuad(triangles,2*segments+i,3*segments+i,3*segments+next,2*segments+next,true);
                addQuad(triangles,i,2*segments+i,2*segments+next,next,true);
                addQuad(triangles,segments+i,segments+next,3*segments+next,3*segments+i,true);
            }
        } else {
            int leftCenter=vertices.size()/3;
            vertices.add((float)-half);vertices.add(0f);vertices.add(0f);
            int rightCenter=vertices.size()/3;
            vertices.add((float)half);vertices.add(0f);vertices.add(0f);
            for (int i=0;i<segments;i++) {
                int next=(i+1)%segments;
                triangles.add(leftCenter);triangles.add(next);triangles.add(i);
                triangles.add(rightCenter);triangles.add(segments+i);triangles.add(segments+next);
            }
        }
        float[] v=new float[vertices.size()]; for(int i=0;i<v.length;i++)v[i]=vertices.get(i);
        int[] t=new int[triangles.size()]; for(int i=0;i<t.length;i++)t[i]=triangles.get(i);
        return new Mesh(v,vertexNormals(v,t),t,bounds(v));
    }

    private static void addQuad(List<Integer> t,int a,int b,int c,int d,boolean reverse) {
        if(reverse){t.add(a);t.add(c);t.add(b);t.add(a);t.add(d);t.add(c);}
        else{t.add(a);t.add(b);t.add(c);t.add(a);t.add(c);t.add(d);}
    }

    private static float[] vertexNormals(float[] vertices,int[] triangles) {
        float[] normals=new float[vertices.length];
        for(int i=0;i<triangles.length;i+=3){int ia=triangles[i]*3,ib=triangles[i+1]*3,ic=triangles[i+2]*3;
            float ax=vertices[ib]-vertices[ia],ay=vertices[ib+1]-vertices[ia+1],az=vertices[ib+2]-vertices[ia+2];
            float bx=vertices[ic]-vertices[ia],by=vertices[ic+1]-vertices[ia+1],bz=vertices[ic+2]-vertices[ia+2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            for(int index:new int[]{ia,ib,ic}){normals[index]+=nx;normals[index+1]+=ny;normals[index+2]+=nz;}}
        for(int i=0;i<normals.length;i+=3){double norm=Math.sqrt(normals[i]*normals[i]+normals[i+1]*normals[i+1]+normals[i+2]*normals[i+2]);
            if(norm>1e-9){normals[i]/=norm;normals[i+1]/=norm;normals[i+2]/=norm;}}
        return normals;
    }

    private static BoundingBox bounds(float[] vertices) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        for(int i=0;i<vertices.length;i+=3){minX=Math.min(minX,vertices[i]);maxX=Math.max(maxX,vertices[i]);
            minY=Math.min(minY,vertices[i+1]);maxY=Math.max(maxY,vertices[i+1]);minZ=Math.min(minZ,vertices[i+2]);maxZ=Math.max(maxZ,vertices[i+2]);}
        return new BoundingBox(minX,minY,minZ,maxX,maxY,maxZ);
    }

    private static String sha256(java.io.File file) throws CadCoreException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[65536];int count;
            try(java.io.FileInputStream input=new java.io.FileInputStream(file)){
                while((count=input.read(buffer))>=0)if(count>0)digest.update(buffer,0,count);
            }
            StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value));
            return hex.toString();
        } catch(Exception error){throw new CadCoreException("STEP_HASH_FAILED","Could not hash STEP file",error);}
    }

    private static String clean(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value.trim();}
}
