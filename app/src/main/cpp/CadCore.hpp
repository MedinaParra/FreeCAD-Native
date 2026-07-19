#pragma once

#include <TopoDS_Shape.hxx>

#include <array>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace fcandroid {

enum class ObjectKind {
    Box,
    Cylinder,
    Sphere,
    Cone,
    Torus,
    Fuse,
    Cut,
    Common
};

struct Placement {
    double x {0.0};
    double y {0.0};
    double z {0.0};
    double qx {0.0};
    double qy {0.0};
    double qz {0.0};
    double qw {1.0};
};

struct CadObject {
    std::uint64_t id {0};
    std::string name;
    ObjectKind kind {ObjectKind::Box};
    std::array<double, 4> parameters {0.0, 0.0, 0.0, 0.0};
    std::uint64_t leftId {0};
    std::uint64_t rightId {0};
    Placement placement;
    bool visible {true};
    TopoDS_Shape shape;
};

struct CadDocument {
    std::uint64_t id {0};
    std::string name;
    std::unordered_map<std::uint64_t, CadObject> objects;
    std::vector<std::uint64_t> evaluationOrder;
    std::string lastError;
};

class CadCore final {
public:
    static CadCore& instance();

    void reset();
    std::uint64_t createDocument(const std::string& name);
    void closeDocument(std::uint64_t documentId);
    void removeObject(std::uint64_t documentId, std::uint64_t objectId);

    std::uint64_t addBox(
        std::uint64_t documentId,
        const std::string& name,
        double length,
        double width,
        double height);

    std::uint64_t addCylinder(
        std::uint64_t documentId,
        const std::string& name,
        double radius,
        double height);

    std::uint64_t addSphere(
        std::uint64_t documentId,
        const std::string& name,
        double radius);

    std::uint64_t addCone(
        std::uint64_t documentId,
        const std::string& name,
        double radius1,
        double radius2,
        double height);

    std::uint64_t addTorus(
        std::uint64_t documentId,
        const std::string& name,
        double majorRadius,
        double minorRadius);

    std::uint64_t addFuse(
        std::uint64_t documentId,
        const std::string& name,
        std::uint64_t leftId,
        std::uint64_t rightId);

    std::uint64_t addCut(
        std::uint64_t documentId,
        const std::string& name,
        std::uint64_t leftId,
        std::uint64_t rightId);

    std::uint64_t addCommon(
        std::uint64_t documentId,
        const std::string& name,
        std::uint64_t leftId,
        std::uint64_t rightId);

    void setParameter(
        std::uint64_t documentId,
        std::uint64_t objectId,
        std::size_t parameterIndex,
        double value);

    void setBooleanOperands(
        std::uint64_t documentId,
        std::uint64_t objectId,
        std::uint64_t leftId,
        std::uint64_t rightId);

    void setPlacement(
        std::uint64_t documentId,
        std::uint64_t objectId,
        const Placement& placement);

    void setVisibility(
        std::uint64_t documentId,
        std::uint64_t objectId,
        bool visible);

    std::uint64_t objectIdByName(
        std::uint64_t documentId,
        const std::string& objectName) const;

    bool recompute(std::uint64_t documentId);
    TopoDS_Shape visibleShape(std::uint64_t documentId) const;
    std::string lastError(std::uint64_t documentId) const;
    std::string documentSummary(std::uint64_t documentId) const;

private:
    CadCore() = default;

    static constexpr std::size_t kMaxDocuments = 32U;
    static constexpr std::size_t kMaxObjectsPerDocument = 10000U;
    static constexpr std::size_t kMaxNameBytes = 1024U;
    static constexpr std::size_t kMaxPrismCoordinateValues = 30000U;

    std::uint64_t addPrimitiveLocked(
        CadDocument& document,
        const std::string& name,
        ObjectKind kind,
        const std::array<double, 4>& parameters);

    std::uint64_t addBooleanLocked(
        CadDocument& document,
        const std::string& name,
        ObjectKind kind,
        std::uint64_t leftId,
        std::uint64_t rightId);

    static void validateParameters(const CadObject& object);
    static void validateName(const std::string& name);
    static void ensureObjectCapacity(const CadDocument& document);

    static TopoDS_Shape buildObjectShape(
        const CadObject& object,
        const CadDocument& document);

    static TopoDS_Shape applyPlacement(
        const TopoDS_Shape& shape,
        const Placement& placement);

    CadDocument& requireDocumentLocked(std::uint64_t documentId);
    const CadDocument& requireDocumentLocked(std::uint64_t documentId) const;
    static CadObject& requireObjectLocked(CadDocument& document, std::uint64_t objectId);
    static const CadObject& requireObjectLocked(
        const CadDocument& document,
        std::uint64_t objectId);

    mutable std::mutex mutex_;
    std::unordered_map<std::uint64_t, CadDocument> documents_;
    std::uint64_t nextDocumentId_ {1};
    std::uint64_t nextObjectId_ {1};
};

} // namespace fcandroid
