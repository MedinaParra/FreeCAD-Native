// SPDX-License-Identifier: LGPL-2.1-or-later

#include <jni.h>

#include <Matrix.h>
#include <Vector3D.h>

#include <cmath>
#include <sstream>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_medinaparra_freecadandroid_nativebridge_FreeCadBaseBridge_nativeSelfTest(
    JNIEnv* environment,
    jobject) {
    const Base::Vector3d vector(3.0, 4.0, 12.0);
    const Base::Vector3d axis(1.0, 0.0, 0.0);
    const Base::Vector3d cross = vector.Cross(axis);

    Base::Matrix4D transform;
    transform.move(10.0, 20.0, 30.0);
    const Base::Vector3d moved = transform * Base::Vector3d(1.0, 2.0, 3.0);

    const bool valid =
        std::abs(vector.Length() - 13.0) < 1.0e-12 &&
        std::abs(vector.Dot(axis) - 3.0) < 1.0e-12 &&
        std::abs(cross.y - 12.0) < 1.0e-12 &&
        std::abs(cross.z + 4.0) < 1.0e-12 &&
        std::abs(moved.x - 11.0) < 1.0e-12 &&
        std::abs(moved.y - 22.0) < 1.0e-12 &&
        std::abs(moved.z - 33.0) < 1.0e-12;

    std::ostringstream output;
    output << (valid ? "FREECAD BASE NATIVO ACTIVO" : "FREECAD BASE TEST FALLIDO")
           << "\nFuente: FreeCAD 1.1.1 src/Base"
           << "\nBase::Vector3d Length=" << vector.Length()
           << "\nBase::Matrix4D determinant=" << transform.determinant()
           << "\nTransform result=(" << moved.x << ", " << moved.y << ", " << moved.z << ")";

    const std::string result = output.str();
    return environment->NewStringUTF(result.c_str());
}
