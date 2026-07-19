#pragma once

#include "OcctMesher.hpp"

#include <string>
#include <vector>

namespace fcandroid {

struct BrepImportResult {
    MeshData mesh;
    std::string summary;
};

class BrepIo final {
public:
    static BrepImportResult importFiles(
        const std::vector<std::string>& paths,
        double linearDeflection,
        double angularDeflection);
};

} // namespace fcandroid
