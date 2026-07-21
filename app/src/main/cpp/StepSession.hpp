#pragma once

#include "OcctMesher.hpp"

#include <cstdint>
#include <string>
#include <vector>

namespace fcandroid {

struct StepFaceDescriptor {
    std::int32_t id = 0;
    float pointX = 0.0F;
    float pointY = 0.0F;
    float pointZ = 0.0F;
    float normalX = 0.0F;
    float normalY = 0.0F;
    float normalZ = 1.0F;
    float area = 0.0F;
    bool planar = false;
};

struct StepSessionSnapshot {
    std::int64_t handle = 0;
    std::int32_t revision = 0;
    MeshData mesh;
    std::vector<StepFaceDescriptor> faces;
    std::string summary;
};

class StepSessionManager final {
public:
    static StepSessionSnapshot open(
        const std::string& path,
        double linearDeflection,
        double angularDeflection);

    static StepSessionSnapshot previewPull(
        std::int64_t handle,
        std::int32_t faceId,
        double distance,
        double linearDeflection,
        double angularDeflection);

    static StepSessionSnapshot commit(std::int64_t handle);
    static StepSessionSnapshot rollback(std::int64_t handle);
    static std::string save(std::int64_t handle, const std::string& outputPath);
    static void close(std::int64_t handle);
};

} // namespace fcandroid
