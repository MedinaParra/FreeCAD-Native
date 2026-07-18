#include "CadCore.hpp"

#include <BRepAlgoAPI_Common.hxx>
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCone.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <BRepPrimAPI_MakeTorus.hxx>
#include <BRep_Builder.hxx>
#include <Standard_Failure.hxx>
#include <TopoDS_Compound.hxx>
#include <gp_Quaternion.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

#include <cmath>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace fcandroid {
namespace {

const char* kindName(const ObjectKind kind) {
    switch (kind) {
        case ObjectKind::Box: return "Part::Box";
        case ObjectKind::Cylinder: return "Part::Cylinder";
        case ObjectKind::Sphere: return "Part::Sphere";
        case ObjectKind::Cone: return "Part::Cone";
        case ObjectKind::Torus: return "Part::Torus";
        case ObjectKind::Fuse: return "Part::Fuse";
        case ObjectKind::Cut: return "Part::Cut";
        case ObjectKind::Common: return "Part::Common";
    }
    return "Unknown";
}

void requirePositive(const double value, const char* label) {
    if (!std::isfinite(value) || value <= 0.0) {
        throw std::invalid_argument(std::string(label) + " must be a finite positive number");
    }
}

std::string failureMessage(const Standard_Failure& failure) {
    const char* message = failure.GetMessageString();
    return message != nullptr ? std::string(message) : std::string("OpenCASCADE failure");
}

} // namespace

CadCore& CadCore::instance() {
    static CadCore core;
    return core;
}

std::uint64_t CadCore::createDocument(const std::string& name) {
    std::lock_guard<std::mutex> lock(mutex_);
    const std::uint64_t id = nextDocumentId_++;
    CadDocument document;
    document.id = id;
    document.name = name.empty() ? "Unnamed" : name;
    documents_.emplace(id, std::move(document));
    return id;
}

void CadCore::closeDocument(const std::uint64_t documentId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (documents_.erase(documentId) == 0U) {
        throw std::invalid_argument("Unknown document handle");
    }
}

std::uint64_t CadCore::addBox(
    const std::uint64_t documentId,
    const std::string& name,
    const double length,
    const double width,
    const double height) {
    requirePositive(length, "Length");
    requirePositive(width, "Width");
    requirePositive(height, "Height");
    std::lock_guard<std::mutex> lock(mutex_);
    return addPrimitiveLocked(
        requireDocumentLocked(documentId),
        name,
        ObjectKind::Box,
        {length, width, height, 0.0});
}

std::uint64_t CadCore::addCylinder(
    const std::uint64_t documentId,
    const std::string& name,
    const double radius,
    const double height) {
    requirePositive(radius, "Radius");
    requirePositive(height, "Height");
    std::lock_guard<std::mutex> lock(mutex_);
    return addPrimitiveLocked(
        requireDocumentLocked(documentId),
        name,
        ObjectKind::Cylinder,
        {radius, height, 0.0, 0.0});
}

std::uint64_t CadCore::addSphere(
    const std::uint64_t documentId,
    const std::string& name,
    const double radius) {
    requirePositive(radius, "Radius");
    std::lock_guard<std::mutex> lock(mutex_);
    return addPrimitiveLocked(
        requireDocumentLocked(documentId),
        name,
        ObjectKind::Sphere,
        {radius, 0.0, 0.0, 0.0});
}

std::uint64_t CadCore::addCone(
    const std::uint64_t documentId,
    const std::string& name,
    const double radius1,
    const double radius2,
    const double height) {
    requirePositive(radius1, "Radius1");
    if (!std::isfinite(radius2) || radius2 < 0.0) {
        throw std::invalid_argument("Radius2 must be finite and non-negative");
    }
    requirePositive(height, "Height");
    std::lock_guard<std::mutex> lock(mutex_);
    return addPrimitiveLocked(
        requireDocumentLocked(documentId),
        name,
        ObjectKind::Cone,
        {radius1, radius2, height, 0.0});
}

std::uint64_t CadCore::addTorus(
    const std::uint64_t documentId,
    const std::string& name,
    const double majorRadius,
    const double minorRadius) {
    requirePositive(majorRadius, "MajorRadius");
    requirePositive(minorRadius, "MinorRadius");
    if (minorRadius >= majorRadius) {
        throw std::invalid_argument("MinorRadius must be smaller than MajorRadius");
    }
    std::lock_guard<std::mutex> lock(mutex_);
    return addPrimitiveLocked(
        requireDocumentLocked(documentId),
        name,
        ObjectKind::Torus,
        {majorRadius, minorRadius, 0.0, 0.0});
}

std::uint64_t CadCore::addFuse(
    const std::uint64_t documentId,
    const std::string& name,
    const std::uint64_t leftId,
    const std::uint64_t rightId) {
    std::lock_guard<std::mutex> lock(mutex_);
    return addBooleanLocked(
        requireDocumentLocked(documentId), name, ObjectKind::Fuse, leftId, rightId);
}

std::uint64_t CadCore::addCut(
    const std::uint64_t documentId,
    const std::string& name,
    const std::uint64_t leftId,
    const std::uint64_t rightId) {
    std::lock_guard<std::mutex> lock(mutex_);
    return addBooleanLocked(
        requireDocumentLocked(documentId), name, ObjectKind::Cut, leftId, rightId);
}

std::uint64_t CadCore::addCommon(
    const std::uint64_t documentId,
    const std::string& name,
    const std::uint64_t leftId,
    const std::uint64_t rightId) {
    std::lock_guard<std::mutex> lock(mutex_);
    return addBooleanLocked(
        requireDocumentLocked(documentId), name, ObjectKind::Common, leftId, rightId);
}

void CadCore::setPlacement(
    const std::uint64_t documentId,
    const std::uint64_t objectId,
    const Placement& placement) {
    const double values[] = {
        placement.x, placement.y, placement.z,
        placement.qx, placement.qy, placement.qz, placement.qw
    };
    for (const double value : values) {
        if (!std::isfinite(value)) {
            throw std::invalid_argument("Placement contains a non-finite number");
        }
    }

    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    requireObjectLocked(document, objectId).placement = placement;
}

void CadCore::setVisibility(
    const std::uint64_t documentId,
    const std::uint64_t objectId,
    const bool visible) {
    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    requireObjectLocked(document, objectId).visible = visible;
}

bool CadCore::recompute(const std::uint64_t documentId) {
    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    document.lastError.clear();

    try {
        for (const std::uint64_t objectId : document.evaluationOrder) {
            CadObject& object = requireObjectLocked(document, objectId);
            TopoDS_Shape shape = buildObjectShape(object, document);
            if (shape.IsNull()) {
                throw std::runtime_error("OpenCASCADE returned a null shape for " + object.name);
            }
            const BRepCheck_Analyzer analyzer(shape, Standard_True);
            if (!analyzer.IsValid()) {
                throw std::runtime_error("BRep validation failed for " + object.name);
            }
            object.shape = std::move(shape);
        }
        return true;
    } catch (const Standard_Failure& failure) {
        document.lastError = failureMessage(failure);
    } catch (const std::exception& exception) {
        document.lastError = exception.what();
    } catch (...) {
        document.lastError = "Unknown native recompute failure";
    }
    return false;
}

TopoDS_Shape CadCore::visibleShape(const std::uint64_t documentId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const CadDocument& document = requireDocumentLocked(documentId);

    BRep_Builder builder;
    TopoDS_Compound compound;
    builder.MakeCompound(compound);
    bool hasShape = false;

    for (const std::uint64_t objectId : document.evaluationOrder) {
        const auto iterator = document.objects.find(objectId);
        if (iterator == document.objects.end()) {
            continue;
        }
        const CadObject& object = iterator->second;
        if (object.visible && !object.shape.IsNull()) {
            builder.Add(compound, object.shape);
            hasShape = true;
        }
    }

    return hasShape ? TopoDS_Shape(compound) : TopoDS_Shape();
}

std::string CadCore::lastError(const std::uint64_t documentId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return requireDocumentLocked(documentId).lastError;
}

std::string CadCore::documentSummary(const std::uint64_t documentId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const CadDocument& document = requireDocumentLocked(documentId);
    std::ostringstream output;
    output << "Document '" << document.name << "'\n";
    output << "Objects: " << document.evaluationOrder.size() << "\n";
    for (const std::uint64_t objectId : document.evaluationOrder) {
        const CadObject& object = document.objects.at(objectId);
        output << "#" << object.id << " " << object.name << " ["
               << kindName(object.kind) << "] visible="
               << (object.visible ? "true" : "false") << "\n";
    }
    return output.str();
}

std::uint64_t CadCore::addPrimitiveLocked(
    CadDocument& document,
    const std::string& name,
    const ObjectKind kind,
    const std::array<double, 4>& parameters) {
    const std::uint64_t id = nextObjectId_++;
    CadObject object;
    object.id = id;
    object.name = name.empty() ? (std::string(kindName(kind)) + std::to_string(id)) : name;
    object.kind = kind;
    object.parameters = parameters;
    document.objects.emplace(id, std::move(object));
    document.evaluationOrder.push_back(id);
    return id;
}

std::uint64_t CadCore::addBooleanLocked(
    CadDocument& document,
    const std::string& name,
    const ObjectKind kind,
    const std::uint64_t leftId,
    const std::uint64_t rightId) {
    if (leftId == rightId) {
        throw std::invalid_argument("A boolean operation requires two different objects");
    }
    CadObject& left = requireObjectLocked(document, leftId);
    CadObject& right = requireObjectLocked(document, rightId);

    const std::uint64_t id = nextObjectId_++;
    CadObject object;
    object.id = id;
    object.name = name.empty() ? (std::string(kindName(kind)) + std::to_string(id)) : name;
    object.kind = kind;
    object.leftId = leftId;
    object.rightId = rightId;

    // Match FreeCAD's usual boolean behavior: operands remain in the document but are hidden.
    left.visible = false;
    right.visible = false;
    document.objects.emplace(id, std::move(object));
    document.evaluationOrder.push_back(id);
    return id;
}

TopoDS_Shape CadCore::buildObjectShape(
    const CadObject& object,
    const CadDocument& document) {
    TopoDS_Shape result;

    switch (object.kind) {
        case ObjectKind::Box:
            result = BRepPrimAPI_MakeBox(
                object.parameters[0], object.parameters[1], object.parameters[2]).Shape();
            break;
        case ObjectKind::Cylinder:
            result = BRepPrimAPI_MakeCylinder(
                object.parameters[0], object.parameters[1]).Shape();
            break;
        case ObjectKind::Sphere:
            result = BRepPrimAPI_MakeSphere(object.parameters[0]).Shape();
            break;
        case ObjectKind::Cone:
            result = BRepPrimAPI_MakeCone(
                object.parameters[0], object.parameters[1], object.parameters[2]).Shape();
            break;
        case ObjectKind::Torus:
            result = BRepPrimAPI_MakeTorus(
                object.parameters[0], object.parameters[1]).Shape();
            break;
        case ObjectKind::Fuse:
        case ObjectKind::Cut:
        case ObjectKind::Common: {
            const CadObject& left = document.objects.at(object.leftId);
            const CadObject& right = document.objects.at(object.rightId);
            if (left.shape.IsNull() || right.shape.IsNull()) {
                throw std::runtime_error("Boolean dependency has not been recomputed");
            }

            if (object.kind == ObjectKind::Fuse) {
                BRepAlgoAPI_Fuse operation(left.shape, right.shape);
                operation.SetRunParallel(Standard_False);
                operation.Build();
                if (!operation.IsDone()) {
                    throw std::runtime_error("BRep fuse failed");
                }
                result = operation.Shape();
            } else if (object.kind == ObjectKind::Cut) {
                BRepAlgoAPI_Cut operation(left.shape, right.shape);
                operation.SetRunParallel(Standard_False);
                operation.Build();
                if (!operation.IsDone()) {
                    throw std::runtime_error("BRep cut failed");
                }
                result = operation.Shape();
            } else {
                BRepAlgoAPI_Common operation(left.shape, right.shape);
                operation.SetRunParallel(Standard_False);
                operation.Build();
                if (!operation.IsDone()) {
                    throw std::runtime_error("BRep common failed");
                }
                result = operation.Shape();
            }
            break;
        }
    }

    return applyPlacement(result, object.placement);
}

TopoDS_Shape CadCore::applyPlacement(
    const TopoDS_Shape& shape,
    const Placement& placement) {
    const double norm = std::sqrt(
        placement.qx * placement.qx +
        placement.qy * placement.qy +
        placement.qz * placement.qz +
        placement.qw * placement.qw);

    gp_Quaternion rotation;
    if (norm > 1.0e-12) {
        rotation = gp_Quaternion(
            placement.qx / norm,
            placement.qy / norm,
            placement.qz / norm,
            placement.qw / norm);
    }

    gp_Trsf transform;
    transform.SetRotation(rotation);
    transform.SetTranslationPart(gp_Vec(placement.x, placement.y, placement.z));

    BRepBuilderAPI_Transform transformer(shape, transform, Standard_True);
    transformer.Build();
    if (!transformer.IsDone()) {
        throw std::runtime_error("Failed to apply object placement");
    }
    return transformer.Shape();
}

CadDocument& CadCore::requireDocumentLocked(const std::uint64_t documentId) {
    const auto iterator = documents_.find(documentId);
    if (iterator == documents_.end()) {
        throw std::invalid_argument("Unknown document handle");
    }
    return iterator->second;
}

const CadDocument& CadCore::requireDocumentLocked(const std::uint64_t documentId) const {
    const auto iterator = documents_.find(documentId);
    if (iterator == documents_.end()) {
        throw std::invalid_argument("Unknown document handle");
    }
    return iterator->second;
}

CadObject& CadCore::requireObjectLocked(
    CadDocument& document,
    const std::uint64_t objectId) {
    const auto iterator = document.objects.find(objectId);
    if (iterator == document.objects.end()) {
        throw std::invalid_argument("Unknown object handle");
    }
    return iterator->second;
}

} // namespace fcandroid
