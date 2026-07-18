#include "CadCore.hpp"
#include "OcctMesher.hpp"
#include "StepImporter.hpp"

#include <android/log.h>
#include <jni.h>
#include <cstdint>
#include <stdexcept>
#include <string>

namespace {
constexpr const char* kLogTag = "FreeCADSTEP";

std::string fromJavaString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) throw std::runtime_error("Unable to decode Java string");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void raiseJavaError(JNIEnv* env, const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

std::string activeError() {
    try {
        throw;
    } catch (const std::exception& error) {
        return error.what();
    } catch (...) {
        return "Unknown STEP import failure";
    }
}

jobject createPayload(
    JNIEnv* env,
    const fcandroid::MeshData& mesh,
    const std::string& sourceName,
    const std::string& summary,
    const fcandroid::StepImportResult& imported) {
    jfloatArray vertices = env->NewFloatArray(static_cast<jsize>(mesh.vertices.size()));
    jintArray indices = env->NewIntArray(static_cast<jsize>(mesh.indices.size()));
    jfloatArray bounds = env->NewFloatArray(6);
    if (vertices == nullptr || indices == nullptr || bounds == nullptr) {
        throw std::runtime_error("Unable to allocate STEP mesh arrays");
    }
    env->SetFloatArrayRegion(vertices, 0, static_cast<jsize>(mesh.vertices.size()), mesh.vertices.data());
    env->SetIntArrayRegion(
        indices,
        0,
        static_cast<jsize>(mesh.indices.size()),
        reinterpret_cast<const jint*>(mesh.indices.data()));
    env->SetFloatArrayRegion(bounds, 0, 6, mesh.bounds.data());

    jstring javaName = env->NewStringUTF(sourceName.c_str());
    jstring javaSummary = env->NewStringUTF(summary.c_str());
    if (javaName == nullptr || javaSummary == nullptr) {
        throw std::runtime_error("Unable to allocate STEP result strings");
    }
    jclass payloadType = env->FindClass(
        "com/medinaparra/freecadandroid/nativebridge/NativeStepPayload");
    if (payloadType == nullptr) throw std::runtime_error("NativeStepPayload class was not found");
    jmethodID constructor = env->GetMethodID(
        payloadType,
        "<init>",
        "([F[I[FLjava/lang/String;Ljava/lang/String;III)V");
    if (constructor == nullptr) throw std::runtime_error("NativeStepPayload constructor was not found");
    return env->NewObject(
        payloadType,
        constructor,
        vertices,
        indices,
        bounds,
        javaName,
        javaSummary,
        static_cast<jint>(imported.rootCount),
        static_cast<jint>(imported.transferredRootCount),
        static_cast<jint>(imported.shapeCount));
}
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_StepNativeBridge_nativeImportStep(
    JNIEnv* env,
    jobject,
    jstring cachedStepPath,
    jstring displayName,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    std::uint64_t documentId = 0U;
    try {
        const std::string path = fromJavaString(env, cachedStepPath);
        const std::string name = fromJavaString(env, displayName);
        fcandroid::StepImportResult imported = fcandroid::StepImporter::importStep(path);
        fcandroid::CadCore& core = fcandroid::CadCore::instance();
        documentId = core.createDocument(name.empty() ? "Imported STEP" : name);
        core.addImportedShape(documentId, name.empty() ? "ImportedShape" : name, imported.shape, path);
        if (!core.recompute(documentId)) throw std::runtime_error(core.lastError(documentId));
        fcandroid::MeshData mesh = fcandroid::OcctMesher::triangulate(
            core.visibleShape(documentId),
            static_cast<double>(linearDeflection),
            static_cast<double>(angularDeflection));
        std::string summary = core.documentSummary(documentId) + imported.diagnostics + "\n";
        jobject payload = createPayload(env, mesh, name, summary, imported);
        core.closeDocument(documentId);
        return payload;
    } catch (...) {
        if (documentId != 0U) {
            try { fcandroid::CadCore::instance().closeDocument(documentId); } catch (...) {}
        }
        raiseJavaError(env, activeError());
        return nullptr;
    }
}
