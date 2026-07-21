#include "StepSession.hpp"

#include <Standard_Failure.hxx>
#include <android/log.h>
#include <jni.h>

#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr const char* kLogTag = "FreeCADStepSession";

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* characters = env->GetStringUTFChars(value, nullptr);
    if (characters == nullptr) {
        throw std::runtime_error("Unable to read Java string");
    }
    std::string result(characters);
    env->ReleaseStringUTFChars(value, characters);
    return result;
}

std::string exceptionMessage() {
    try {
        throw;
    } catch (const Standard_Failure& failure) {
        const char* message = failure.GetMessageString();
        return message != nullptr ? std::string(message) : std::string("OpenCASCADE failure");
    } catch (const std::exception& exception) {
        return exception.what();
    } catch (...) {
        return "Unknown STEP session error";
    }
}

void throwJava(JNIEnv* env, const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

jfloatArray floatArray(JNIEnv* env, const std::vector<float>& values) {
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (result == nullptr) {
        throw std::runtime_error("Unable to allocate native float array");
    }
    if (!values.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}

jfloatArray boundsArray(JNIEnv* env, const std::array<float, 6>& values) {
    jfloatArray result = env->NewFloatArray(6);
    if (result == nullptr) {
        throw std::runtime_error("Unable to allocate native bounds array");
    }
    env->SetFloatArrayRegion(result, 0, 6, values.data());
    return result;
}

jintArray intArray(JNIEnv* env, const std::vector<std::int32_t>& values) {
    jintArray result = env->NewIntArray(static_cast<jsize>(values.size()));
    if (result == nullptr) {
        throw std::runtime_error("Unable to allocate native integer array");
    }
    if (!values.empty()) {
        env->SetIntArrayRegion(
            result,
            0,
            static_cast<jsize>(values.size()),
            reinterpret_cast<const jint*>(values.data()));
    }
    return result;
}

jobject makePayload(JNIEnv* env, const fcandroid::StepSessionSnapshot& snapshot) {
    std::vector<std::int32_t> faceIds;
    std::vector<float> faceData;
    std::vector<std::int32_t> faceFlags;
    faceIds.reserve(snapshot.faces.size());
    faceData.reserve(snapshot.faces.size() * 7U);
    faceFlags.reserve(snapshot.faces.size());
    for (const fcandroid::StepFaceDescriptor& face : snapshot.faces) {
        faceIds.push_back(face.id);
        faceData.push_back(face.pointX);
        faceData.push_back(face.pointY);
        faceData.push_back(face.pointZ);
        faceData.push_back(face.normalX);
        faceData.push_back(face.normalY);
        faceData.push_back(face.normalZ);
        faceData.push_back(face.area);
        faceFlags.push_back(face.planar ? 1 : 0);
    }

    jclass payloadClass = env->FindClass(
        "com/medinaparra/freecadandroid/nativebridge/NativeStepSessionPayload");
    if (payloadClass == nullptr) {
        throw std::runtime_error("NativeStepSessionPayload class was not found");
    }
    jmethodID constructor = env->GetMethodID(
        payloadClass,
        "<init>",
        "(J[F[I[F[I[I[F[IILjava/lang/String;)V");
    if (constructor == nullptr) {
        throw std::runtime_error("NativeStepSessionPayload constructor was not found");
    }

    const jfloatArray vertices = floatArray(env, snapshot.mesh.vertices);
    const jintArray indices = intArray(env, snapshot.mesh.indices);
    const jfloatArray bounds = boundsArray(env, snapshot.mesh.bounds);
    const jintArray triangleFaceIds = intArray(env, snapshot.mesh.triangleFaceIds);
    const jintArray javaFaceIds = intArray(env, faceIds);
    const jfloatArray javaFaceData = floatArray(env, faceData);
    const jintArray javaFaceFlags = intArray(env, faceFlags);
    const jstring summary = env->NewStringUTF(snapshot.summary.c_str());

    return env->NewObject(
        payloadClass,
        constructor,
        static_cast<jlong>(snapshot.handle),
        vertices,
        indices,
        bounds,
        triangleFaceIds,
        javaFaceIds,
        javaFaceData,
        javaFaceFlags,
        static_cast<jint>(snapshot.revision),
        summary);
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativeOpenStepSession(
    JNIEnv* env,
    jobject,
    jstring localPath,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    try {
        return makePayload(
            env,
            fcandroid::StepSessionManager::open(
                toString(env, localPath),
                linearDeflection,
                angularDeflection));
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativePreviewPull(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint faceId,
    jdouble distance,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    try {
        return makePayload(
            env,
            fcandroid::StepSessionManager::previewPull(
                static_cast<std::int64_t>(handle),
                static_cast<std::int32_t>(faceId),
                distance,
                linearDeflection,
                angularDeflection));
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativePreviewMove(
    JNIEnv* env,
    jobject,
    jlong handle,
    jdouble deltaX,
    jdouble deltaY,
    jdouble deltaZ,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    try {
        return makePayload(
            env,
            fcandroid::StepSessionManager::previewMove(
                static_cast<std::int64_t>(handle),
                deltaX,
                deltaY,
                deltaZ,
                linearDeflection,
                angularDeflection));
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativeCommitStepSession(
    JNIEnv* env,
    jobject,
    jlong handle) {
    try {
        return makePayload(
            env,
            fcandroid::StepSessionManager::commit(static_cast<std::int64_t>(handle)));
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativeRollbackStepSession(
    JNIEnv* env,
    jobject,
    jlong handle) {
    try {
        return makePayload(
            env,
            fcandroid::StepSessionManager::rollback(static_cast<std::int64_t>(handle)));
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativeSaveStepSession(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring outputPath) {
    try {
        const std::string result = fcandroid::StepSessionManager::save(
            static_cast<std::int64_t>(handle),
            toString(env, outputPath));
        return env->NewStringUTF(result.c_str());
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeStepBridge_nativeCloseStepSession(
    JNIEnv* env,
    jobject,
    jlong handle) {
    try {
        fcandroid::StepSessionManager::close(static_cast<std::int64_t>(handle));
    } catch (...) {
        throwJava(env, exceptionMessage());
    }
}
