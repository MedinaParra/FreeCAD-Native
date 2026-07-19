#include "BrepIo.hpp"

#include <jni.h>
#include <android/log.h>
#include <Standard_Failure.hxx>

#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr const char* kLogTag = "FreeCADFileIo";

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

std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray values) {
    if (values == nullptr) {
        return {};
    }
    const jsize count = env->GetArrayLength(values);
    std::vector<std::string> result;
    result.reserve(static_cast<std::size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        auto* value = static_cast<jstring>(env->GetObjectArrayElement(values, index));
        if (value == nullptr) {
            result.emplace_back();
            continue;
        }
        result.push_back(toString(env, value));
        env->DeleteLocalRef(value);
    }
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
        return "Unknown FCStd import error";
    }
}

void throwJava(JNIEnv* env, const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

jobject makePayload(
    JNIEnv* env,
    const fcandroid::MeshData& mesh,
    const std::string& summary) {
    jfloatArray vertices = env->NewFloatArray(static_cast<jsize>(mesh.vertices.size()));
    jintArray indices = env->NewIntArray(static_cast<jsize>(mesh.indices.size()));
    jfloatArray bounds = env->NewFloatArray(6);
    if (vertices == nullptr || indices == nullptr || bounds == nullptr) {
        throw std::runtime_error("Unable to allocate FCStd mesh arrays");
    }

    env->SetFloatArrayRegion(
        vertices,
        0,
        static_cast<jsize>(mesh.vertices.size()),
        mesh.vertices.data());
    env->SetIntArrayRegion(
        indices,
        0,
        static_cast<jsize>(mesh.indices.size()),
        reinterpret_cast<const jint*>(mesh.indices.data()));
    env->SetFloatArrayRegion(bounds, 0, 6, mesh.bounds.data());

    jclass payloadClass = env->FindClass(
        "com/medinaparra/freecadandroid/nativebridge/NativeFreeCadFilePayload");
    if (payloadClass == nullptr) {
        throw std::runtime_error("NativeFreeCadFilePayload class was not found");
    }
    jmethodID constructor = env->GetMethodID(
        payloadClass,
        "<init>",
        "([F[I[FLjava/lang/String;)V");
    if (constructor == nullptr) {
        throw std::runtime_error("NativeFreeCadFilePayload constructor was not found");
    }

    jstring javaSummary = env->NewStringUTF(summary.c_str());
    return env->NewObject(
        payloadClass,
        constructor,
        vertices,
        indices,
        bounds,
        javaSummary);
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeFreeCadFileBridge_nativeImportBrepFiles(
    JNIEnv* env,
    jobject,
    jobjectArray localPaths,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    try {
        const fcandroid::BrepImportResult result = fcandroid::BrepIo::importFiles(
            toStringVector(env, localPaths),
            linearDeflection,
            angularDeflection);
        return makePayload(env, result.mesh, result.summary);
    } catch (...) {
        throwJava(env, exceptionMessage());
        return nullptr;
    }
}
