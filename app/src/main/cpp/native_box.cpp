#include <jni.h>
#include <android/log.h>

namespace {
constexpr const char* TAG = "FreeCADNative";

constexpr float kVertices[] = {
    -0.5f,-0.5f, 0.5f, 0.f, 0.f, 1.f,  0.5f,-0.5f, 0.5f, 0.f, 0.f, 1.f,
     0.5f, 0.5f, 0.5f, 0.f, 0.f, 1.f, -0.5f, 0.5f, 0.5f, 0.f, 0.f, 1.f,
     0.5f,-0.5f,-0.5f, 0.f, 0.f,-1.f, -0.5f,-0.5f,-0.5f, 0.f, 0.f,-1.f,
    -0.5f, 0.5f,-0.5f, 0.f, 0.f,-1.f,  0.5f, 0.5f,-0.5f, 0.f, 0.f,-1.f,
    -0.5f, 0.5f, 0.5f, 0.f, 1.f, 0.f,  0.5f, 0.5f, 0.5f, 0.f, 1.f, 0.f,
     0.5f, 0.5f,-0.5f, 0.f, 1.f, 0.f, -0.5f, 0.5f,-0.5f, 0.f, 1.f, 0.f,
    -0.5f,-0.5f,-0.5f, 0.f,-1.f, 0.f,  0.5f,-0.5f,-0.5f, 0.f,-1.f, 0.f,
     0.5f,-0.5f, 0.5f, 0.f,-1.f, 0.f, -0.5f,-0.5f, 0.5f, 0.f,-1.f, 0.f,
     0.5f,-0.5f, 0.5f, 1.f, 0.f, 0.f,  0.5f,-0.5f,-0.5f, 1.f, 0.f, 0.f,
     0.5f, 0.5f,-0.5f, 1.f, 0.f, 0.f,  0.5f, 0.5f, 0.5f, 1.f, 0.f, 0.f,
    -0.5f,-0.5f,-0.5f,-1.f, 0.f, 0.f, -0.5f,-0.5f, 0.5f,-1.f, 0.f, 0.f,
    -0.5f, 0.5f, 0.5f,-1.f, 0.f, 0.f, -0.5f, 0.5f,-0.5f,-1.f, 0.f, 0.f
};

constexpr jshort kIndices[] = {
     0, 1, 2,  0, 2, 3,
     4, 5, 6,  4, 6, 7,
     8, 9,10,  8,10,11,
    12,13,14, 12,14,15,
    16,17,18, 16,18,19,
    20,21,22, 20,22,23
};
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeBoxBridge_createBoxVertices(
    JNIEnv* env, jobject) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Creating box vertices in C++");
    const jsize count = static_cast<jsize>(sizeof(kVertices) / sizeof(kVertices[0]));
    jfloatArray result = env->NewFloatArray(count);
    if (result != nullptr) env->SetFloatArrayRegion(result, 0, count, kVertices);
    return result;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeBoxBridge_createBoxIndices(
    JNIEnv* env, jobject) {
    const jsize count = static_cast<jsize>(sizeof(kIndices) / sizeof(kIndices[0]));
    jshortArray result = env->NewShortArray(count);
    if (result != nullptr) env->SetShortArrayRegion(result, 0, count, kIndices);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeBoxBridge_buildInfo(
    JNIEnv* env, jobject) {
    return env->NewStringUTF("C++17 / JNI / Android NDK / ARM64");
}
