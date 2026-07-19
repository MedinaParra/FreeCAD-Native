#pragma once

#include "OcctMesher.hpp"

#include <array>
#include <string>
#include <vector>

namespace fcandroid {

struct BrepImportResult {
    MeshData mesh;
    std::string summary;
};

struct BrepObjectInput {
    std::string path;
    std::string name;
    std::array<double, 7> placement {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0};
    bool visible {true};
};

class BrepIo final {
public:
    static BrepImportResult importFiles(
        const std::vector<std::string>& paths,
        double linearDeflection,
        double angularDeflection);

    static BrepImportResult importObjects(
        const std::vector<BrepObjectInput>& objects,
        double linearDeflection,
        double angularDeflection);
};

} // namespace fcandroid
