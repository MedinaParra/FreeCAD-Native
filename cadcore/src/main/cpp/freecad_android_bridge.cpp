#include <jni.h>
#include <string>

namespace {
thread_local std::string last_error;
constexpr jlong CAP_STEP_IMPORT = 1LL << 0;
constexpr jlong CAP_TESSELLATION = 1LL << 1;
constexpr jlong CAP_BOUNDING_BOX = 1LL << 2;
constexpr jlong CAP_RIGID_TRANSFORM = 1LL << 3;
constexpr jlong CAP_STEP_EXPORT = 1LL << 4;

jstring text(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_runtimeInfo(
        JNIEnv* env, jclass) {
#ifdef FREECAD_OCC_AVAILABLE
    return text(env, "FreeCAD/OpenCascade Android bridge 1.0");
#else
    return text(env, "FreeCAD Android JNI bridge 1.0; OpenCascade kernel not linked");
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_capabilitiesMask(
        JNIEnv*, jclass) {
#ifdef FREECAD_OCC_AVAILABLE
    return CAP_STEP_IMPORT | CAP_TESSELLATION | CAP_BOUNDING_BOX |
           CAP_RIGID_TRANSFORM | CAP_STEP_EXPORT;
#else
    return 0;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_lastError(
        JNIEnv* env, jclass) {
    return text(env, last_error);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_importStep(
        JNIEnv* env, jclass, jstring path, jdouble, jdouble) {
#ifdef FREECAD_OCC_AVAILABLE
    // Production implementation will call STEPControl_Reader and BRepMesh_IncrementalMesh.
    // The ABI is intentionally stable while the kernel packaging is completed.
    last_error = "OpenCascade integration entry point is compiled but not implemented";
#else
    (void)env;
    (void)path;
    last_error = "OpenCascade/FreeCAD STEP kernel is not linked in this build";
#endif
    return 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_meshVertices(
        JNIEnv* env, jclass, jlong) {
    return env->NewFloatArray(0);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_meshNormals(
        JNIEnv* env, jclass, jlong) {
    return env->NewFloatArray(0);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_meshTriangles(
        JNIEnv* env, jclass, jlong) {
    return env->NewIntArray(0);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_boundingBox(
        JNIEnv* env, jclass, jlong) {
    return env->NewDoubleArray(0);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_transformedCopy(
        JNIEnv*, jclass, jlong, jdoubleArray) {
    last_error = "Native rigid-copy is unavailable without an imported OpenCascade shape";
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_medinaparra_freecadandroid_cadcore_NativeFreeCadApi_release(
        JNIEnv*, jclass, jlong) {
}
