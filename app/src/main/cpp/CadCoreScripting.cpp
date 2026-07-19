#include "CadCore.hpp"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace fcandroid {
namespace {

bool isBooleanKind(const ObjectKind kind) {
    return kind == ObjectKind::Fuse || kind == ObjectKind::Cut || kind == ObjectKind::Common;
}

void requirePositiveValue(const double value, const char* label) {
    if (!std::isfinite(value) || value <= 0.0) {
        throw std::invalid_argument(std::string(label) + " must be a finite positive number");
    }
}

} // namespace

void CadCore::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    documents_.clear();
    nextDocumentId_ = 1;
    nextObjectId_ = 1;
}

void CadCore::validateName(const std::string& name) {
    if (name.size() > kMaxNameBytes) {
        throw std::length_error("Document or object name exceeds the 1024 byte limit");
    }
}

void CadCore::ensureObjectCapacity(const CadDocument& document) {
    if (document.objects.size() >= kMaxObjectsPerDocument) {
        throw std::length_error("Native object limit reached for this document");
    }
}

void CadCore::removeObject(
    const std::uint64_t documentId,
    const std::uint64_t objectId) {
    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    const CadObject& target = requireObjectLocked(document, objectId);

    for (const auto& [candidateId, candidate] : document.objects) {
        if (candidateId == objectId || !isBooleanKind(candidate.kind)) {
            continue;
        }
        if (candidate.leftId == objectId || candidate.rightId == objectId) {
            throw std::invalid_argument(
                "Cannot remove " + target.name + "; it is referenced by " + candidate.name);
        }
    }

    document.objects.erase(objectId);
    document.evaluationOrder.erase(
        std::remove(
            document.evaluationOrder.begin(),
            document.evaluationOrder.end(),
            objectId),
        document.evaluationOrder.end());
}

void CadCore::setParameter(
    const std::uint64_t documentId,
    const std::uint64_t objectId,
    const std::size_t parameterIndex,
    const double value) {
    if (!std::isfinite(value)) {
        throw std::invalid_argument("Object parameter must be finite");
    }
    if (parameterIndex >= 4U) {
        throw std::out_of_range("Object parameter index is outside the supported range");
    }

    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    CadObject& object = requireObjectLocked(document, objectId);
    if (isBooleanKind(object.kind)) {
        throw std::invalid_argument("Boolean objects do not expose numeric primitive parameters");
    }

    const double previous = object.parameters[parameterIndex];
    object.parameters[parameterIndex] = value;
    try {
        validateParameters(object);
    } catch (...) {
        object.parameters[parameterIndex] = previous;
        throw;
    }
    object.shape.Nullify();
}

void CadCore::setBooleanOperands(
    const std::uint64_t documentId,
    const std::uint64_t objectId,
    const std::uint64_t leftId,
    const std::uint64_t rightId) {
    if (leftId == 0U || rightId == 0U) {
        throw std::invalid_argument("Boolean operands must be valid object handles");
    }
    if (leftId == rightId) {
        throw std::invalid_argument("A boolean operation requires two different objects");
    }

    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    CadObject& object = requireObjectLocked(document, objectId);
    if (!isBooleanKind(object.kind)) {
        throw std::invalid_argument("The selected object is not a boolean operation");
    }

    CadObject& left = requireObjectLocked(document, leftId);
    CadObject& right = requireObjectLocked(document, rightId);
    object.leftId = leftId;
    object.rightId = rightId;
    object.shape.Nullify();
    left.visible = false;
    right.visible = false;
}

std::uint64_t CadCore::objectIdByName(
    const std::uint64_t documentId,
    const std::string& objectName) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const CadDocument& document = requireDocumentLocked(documentId);
    for (const std::uint64_t objectId : document.evaluationOrder) {
        const CadObject& object = requireObjectLocked(document, objectId);
        if (object.name == objectName) {
            return object.id;
        }
    }
    return 0U;
}

void CadCore::validateParameters(const CadObject& object) {
    switch (object.kind) {
        case ObjectKind::Box:
            requirePositiveValue(object.parameters[0], "Length");
            requirePositiveValue(object.parameters[1], "Width");
            requirePositiveValue(object.parameters[2], "Height");
            break;
        case ObjectKind::Cylinder:
            requirePositiveValue(object.parameters[0], "Radius");
            requirePositiveValue(object.parameters[1], "Height");
            break;
        case ObjectKind::Sphere:
            requirePositiveValue(object.parameters[0], "Radius");
            break;
        case ObjectKind::Cone:
            requirePositiveValue(object.parameters[0], "Radius1");
            if (!std::isfinite(object.parameters[1]) || object.parameters[1] < 0.0) {
                throw std::invalid_argument("Radius2 must be finite and non-negative");
            }
            requirePositiveValue(object.parameters[2], "Height");
            break;
        case ObjectKind::Torus:
            requirePositiveValue(object.parameters[0], "Radius1");
            requirePositiveValue(object.parameters[1], "Radius2");
            if (object.parameters[1] >= object.parameters[0]) {
                throw std::invalid_argument("Torus Radius2 must be smaller than Radius1");
            }
            break;
        case ObjectKind::Fuse:
        case ObjectKind::Cut:
        case ObjectKind::Common:
            break;
    }
}

const CadObject& CadCore::requireObjectLocked(
    const CadDocument& document,
    const std::uint64_t objectId) {
    const auto iterator = document.objects.find(objectId);
    if (iterator == document.objects.end()) {
        throw std::invalid_argument("Unknown object handle");
    }
    return iterator->second;
}

} // namespace fcandroid
