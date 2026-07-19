#include "CadCore.hpp"
#include "OcctMesher.hpp"

#include <jni.h>
#include <android/log.h>
#include <Standard_Failure.hxx>
#include <Standard_Version.hxx>

#include <cstdint>
#include <sstream>
#include <stdexcept>
#include <string>

namespace {
constexpr const char* kLogTag = "FreeCADNativeCore";

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
    } catch (const Standard_Failure& failure) {
        const char* message = failure.GetMessageString();
        return message != nullptr ? std::string(message) : std::string("OpenCASCADE failure");
    } catch (const std::exception& exception) {
        return exception.what();
    } catch (...) {
        return "Unknown native exception";
    }
}

jobject makeMeshPayload(JNIEnv* env, const fcandroid::MeshData& mesh) {
    jfloatArray vertices = env->NewFloatArray(static_cast<jsize>(mesh.vertices.size()));
    if (vertices == nullptr) {
        throw std::runtime_error("Unable to allocate vertex array");
    }
    env->SetFloatArrayRegion(
        vertices,
        0,
        static_cast<jsize>(mesh.vertices.size()),
        mesh.vertices.data());

    jintArray indices = env->NewIntArray(static_cast<jsize>(mesh.indices.size()));
    if (indices == nullptr) {
        throw std::runtime_error("Unable to allocate index array");
    }
    env->SetIntArrayRegion(
        indices,
        0,
        static_cast<jsize>(mesh.indices.size()),
        reinterpret_cast<const jint*>(mesh.indices.data()));

    jfloatArray bounds = env->NewFloatArray(6);
    if (bounds == nullptr) {
        throw std::runtime_error("Unable to allocate bounds array");
    }
    env->SetFloatArrayRegion(bounds, 0, 6, mesh.bounds.data());

    jclass payloadClass = env->FindClass(
        "com/medinaparra/freecadandroid/nativebridge/NativeMeshPayload");
    if (payloadClass == nullptr) {
        throw std::runtime_error("NativeMeshPayload class was not found");
    }
    jmethodID constructor = env->GetMethodID(payloadClass, "<init>", "([F[I[F)V");
    if (constructor == nullptr) {
        throw std::runtime_error("NativeMeshPayload constructor was not found");
    }
    return env->NewObject(payloadClass, constructor, vertices, indices, bounds);
}

fcandroid::Placement placementFrom(
    const jdouble x,
    const jdouble y,
    const jdouble z,
    const jdouble qx,
    const jdouble qy,
    const jdouble qz,
    const jdouble qw) {
    fcandroid::Placement placement;
    placement.x = x;
    placement.y = y;
    placement.z = z;
    placement.qx = qx;
    placement.qy = qy;
    placement.qz = qz;
    placement.qw = qw;
    return placement;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeBuildInfo(
    JNIEnv* env,
    jobject) {
    std::ostringstream output;
    output << "FreeCAD Android Core 0.4.0\n";
    output << "OCCT " << OCC_VERSION_MAJOR << "." << OCC_VERSION_MINOR << "."
           << OCC_VERSION_MAINTENANCE << "\n";
#if defined(__aarch64__)
    output << "ABI: arm64-v8a";
#elif defined(__arm__)
    output << "ABI: armeabi-v7a";
#elif defined(__x86_64__)
    output << "ABI: x86_64";
#else
    output << "ABI: unknown";
#endif
    return env->NewStringUTF(output.str().c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeCreateDocument(
    JNIEnv* env,
    jobject,
    jstring name) {
    try {
        return static_cast<jlong>(
            fcandroid::CadCore::instance().createDocument(toString(env, name)));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeCloseDocument(
    JNIEnv* env,
    jobject,
    jlong documentId) {
    try {
        fcandroid::CadCore::instance().closeDocument(
            static_cast<std::uint64_t>(documentId));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddBox(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jdouble length,
    jdouble width,
    jdouble height) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addBox(
            static_cast<std::uint64_t>(documentId),
            toString(env, name), length, width, height));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddCylinder(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jdouble radius,
    jdouble height) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addCylinder(
            static_cast<std::uint64_t>(documentId),
            toString(env, name), radius, height));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddSphere(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jdouble radius) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addSphere(
            static_cast<std::uint64_t>(documentId), toString(env, name), radius));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddCone(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jdouble radius1,
    jdouble radius2,
    jdouble height) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addCone(
            static_cast<std::uint64_t>(documentId),
            toString(env, name), radius1, radius2, height));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddTorus(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jdouble majorRadius,
    jdouble minorRadius) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addTorus(
            static_cast<std::uint64_t>(documentId),
            toString(env, name), majorRadius, minorRadius));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddFuse(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jlong leftId,
    jlong rightId) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addFuse(
            static_cast<std::uint64_t>(documentId),
            toString(env, name),
            static_cast<std::uint64_t>(leftId),
            static_cast<std::uint64_t>(rightId)));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddCut(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jlong leftId,
    jlong rightId) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addCut(
            static_cast<std::uint64_t>(documentId),
            toString(env, name),
            static_cast<std::uint64_t>(leftId),
            static_cast<std::uint64_t>(rightId)));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeAddCommon(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jstring name,
    jlong leftId,
    jlong rightId) {
    try {
        return static_cast<jlong>(fcandroid::CadCore::instance().addCommon(
            static_cast<std::uint64_t>(documentId),
            toString(env, name),
            static_cast<std::uint64_t>(leftId),
            static_cast<std::uint64_t>(rightId)));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeSetPlacement(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jlong objectId,
    jdouble x,
    jdouble y,
    jdouble z,
    jdouble qx,
    jdouble qy,
    jdouble qz,
    jdouble qw) {
    try {
        fcandroid::CadCore::instance().setPlacement(
            static_cast<std::uint64_t>(documentId),
            static_cast<std::uint64_t>(objectId),
            placementFrom(x, y, z, qx, qy, qz, qw));
    } catch (...) {
        throwJava(env, currentExceptionMessage());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeSetVisibility(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jlong objectId,
    jboolean visible) {
    try {
        fcandroid::CadCore::instance().setVisibility(
            static_cast<std::uint64_t>(documentId),
            static_cast<std::uint64_t>(objectId),
            visible == JNI_TRUE);
    } catch (...) {
        throwJava(env, currentExceptionMessage());
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeRecompute(
    JNIEnv* env,
    jobject,
    jlong documentId) {
    try {
        return fcandroid::CadCore::instance().recompute(
                   static_cast<std::uint64_t>(documentId))
            ? JNI_TRUE
            : JNI_FALSE;
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeLastError(
    JNIEnv* env,
    jobject,
    jlong documentId) {
    try {
        const std::string error = fcandroid::CadCore::instance().lastError(
            static_cast<std::uint64_t>(documentId));
        return env->NewStringUTF(error.c_str());
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeDocumentSummary(
    JNIEnv* env,
    jobject,
    jlong documentId) {
    try {
        const std::string summary = fcandroid::CadCore::instance().documentSummary(
            static_cast<std::uint64_t>(documentId));
        return env->NewStringUTF(summary.c_str());
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_NativeCadBridge_nativeCreateSceneMesh(
    JNIEnv* env,
    jobject,
    jlong documentId,
    jdouble linearDeflection,
    jdouble angularDeflection) {
    try {
        const TopoDS_Shape shape = fcandroid::CadCore::instance().visibleShape(
            static_cast<std::uint64_t>(documentId));
        const fcandroid::MeshData mesh = fcandroid::OcctMesher::triangulate(
            shape, linearDeflection, angularDeflection);
        return makeMeshPayload(env, mesh);
    } catch (...) {
        throwJava(env, currentExceptionMessage());
        return nullptr;
    }
}
