#pragma once

#include "OcctMesher.hpp"

#include <string>

namespace fcandroid {

struct StepImportResult {
    MeshData mesh;
    std::string summary;
};

class StepIo final {
public:
    static StepImportResult importFile(
        const std::string& path,
        double linearDeflection,
        double angularDeflection);
};

} // namespace fcandroid
