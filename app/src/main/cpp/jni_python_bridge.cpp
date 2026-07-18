#include "CadCore.hpp"
#include "OcctMesher.hpp"
#include "PythonRuntime.hpp"

#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <stdexcept>
#include <string>

namespace {

constexpr const char* kLogTag = "FreeCADPython";

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

void throwJava(JNIEnv* env, const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

std::string currentExceptionMessage() {
    try {
        throw;
    } catch (const std::exception& exception) {
        return exception.what();
    } catch (...) {
        return "Unknown embedded Python failure";
    }
}

jobject makeMacroPayload(
    JNIEnv* env,
    const fcandroid::MacroExecutionResult& execution,
    const fcandroid::MeshData* mesh) {
    const std::vector<float> emptyVertices;
    const std::vector<std::uint32_t> emptyIndices;
    const std::array<float, 6> emptyBounds {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};

    const std::vector<float>& verticesData = mesh != nullptr ? mesh->vertices : emptyVertices;
    const std::vector<std::uint32_t>& indicesData = mesh != nullptr ? mesh->indices : emptyIndices;
    const std::array<float, 6>& boundsData = mesh != nullptr ? mesh->bounds : emptyBounds;

    jfloatArray vertices = env->NewFloatArray(static_cast<jsize>(verticesData.size()));
    jintArray indices = env->NewIntArray(static_cast<jsize>(indicesData.size()));
    jfloatArray bounds = env->NewFloatArray(6);
    if (vertices == nullptr || indices == nullptr || bounds == nullptr) {
        throw std::runtime_error("Unable to allocate macro mesh arrays");
    }
    if (!verticesData.empty()) {
        env->SetFloatArrayRegion(
            vertices,
            0,
            static_cast<jsize>(verticesData.size()),
            verticesData.data());
    }
    if (!indicesData.empty()) {
        env->SetIntArrayRegion(
            indices,
            0,
            static_cast<jsize>(indicesData.size()),
            reinterpret_cast<const jint*>(indicesData.data()));
    }
    env->SetFloatArrayRegion(bounds, 0, 6, boundsData.data());

    jstring output = env->NewStringUTF(execution.stdoutText.c_str());
    jstring error = env->NewStringUTF(execution.errorText.c_str());
    jstring summary = env->NewStringUTF(execution.documentSummary.c_str());
    if (output == nullptr || error == nullptr || summary == nullptr) {
        throw std::runtime_error("Unable to allocate macro result strings");
    }

    jclass payloadClass = env->FindClass(
        "com/medinaparra/freecadandroid/nativebridge/NativeMacroPayload");
    if (payloadClass == nullptr) {
        throw std::runtime_error("NativeMacroPayload class was not found");
    }
    jmethodID constructor = env->GetMethodID(
        payloadClass,
        "<init>",
        "([F[I[FZJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (constructor == nullptr) {
        throw std::runtime_error("NativeMacroPayload constructor was not found");
    }

    return env->NewObject(
        payloadClass,
        constructor,
        vertices,
        indices,
        bounds,
        execution.success ? JNI_TRUE : JNI_FALSE,
        static_cast<jlong>(execution.documentId),
        output,
        error,
        summary);
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeInitializePython(
    JNIEnv* env,
    jobject,
    jstring pythonHome) {
    try {
        fcandroid::PythonRuntime& runtime = fcandroid::PythonRuntime::instance();
        runtime.initialize(toString(env, pythonHome));
        const std::string version = runtime.version();
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "Embedded Python initialized: %s", version.c_str());
        return env->NewStringUTF(version.c_str());
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeRunMacro(
    JNIEnv* env,
    jobject,
    jstring sourceCode,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    try {
        fcandroid::MacroExecutionResult execution =
            fcandroid::PythonRuntime::instance().execute(toString(env, sourceCode));

        fcandroid::MeshData mesh;
        const fcandroid::MeshData* meshPointer = nullptr;
        if (execution.success && execution.documentId != 0U) {
            const TopoDS_Shape shape = fcandroid::CadCore::instance().visibleShape(
                execution.documentId);
            mesh = fcandroid::OcctMesher::triangulate(
                shape,
                linearDeflection,
                angularDeflection);
            meshPointer = &mesh;
        }
        return makeMacroPayload(env, execution, meshPointer);
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return nullptr;
    }
}
