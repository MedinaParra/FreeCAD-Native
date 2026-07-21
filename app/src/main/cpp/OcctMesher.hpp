#pragma once

#include <TopoDS_Shape.hxx>

#include <array>
#include <cstdint>
#include <vector>

namespace fcandroid {

struct MeshData {
    std::vector<float> vertices;
    std::vector<std::int32_t> indices;
    // One 1-based OCCT face identifier per rendered triangle.
    // The identifier is session-local and regenerated after every committed BRep change.
    std::vector<std::int32_t> triangleFaceIds;
    std::array<float, 6> bounds {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
};

class OcctMesher final {
public:
    static MeshData triangulate(
        const TopoDS_Shape& shape,
        double linearDeflection = 0.05,
        double angularDeflection = 0.35);
};

} // namespace fcandroid
