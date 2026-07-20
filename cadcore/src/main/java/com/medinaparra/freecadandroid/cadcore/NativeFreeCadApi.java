package com.medinaparra.freecadandroid.cadcore;

/** JNI ABI implemented by libfreecad_android_bridge.so. */
final class NativeFreeCadApi {
    private NativeFreeCadApi() { }

    static native String runtimeInfo();
    static native long capabilitiesMask();
    static native String lastError();
    static native long importStep(String absolutePath, double linearDeflection,
                                  double angularDeflectionDegrees);
    static native float[] meshVertices(long handle);
    static native float[] meshNormals(long handle);
    static native int[] meshTriangles(long handle);
    static native double[] boundingBox(long handle);
    static native long transformedCopy(long handle, double[] rigidMatrix4x4);
    static native void release(long handle);
}
