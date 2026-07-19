#include "CadCore.hpp"

#include <cmath>
#include <stdexcept>
#include <utility>

namespace fcandroid {

std::uint64_t CadCore::addPrism(const std::uint64_t documentId,
                                const std::string& name,
                                const std::vector<double>& profileCoordinates,
                                const double dx,
                                const double dy,
                                const double dz) {
    if (profileCoordinates.size() < 9U || profileCoordinates.size() % 3U != 0U) {
        throw std::invalid_argument("A prism profile requires at least three XYZ points");
    }
    if (profileCoordinates.size() > kMaxPrismCoordinateValues) {
        throw std::length_error("Prism profile exceeds the 10000 point limit");
    }
    for (const double value : profileCoordinates) {
        if (!std::isfinite(value)) {
            throw std::invalid_argument("Prism profile contains a non-finite coordinate");
        }
    }
    if (!std::isfinite(dx) || !std::isfinite(dy) || !std::isfinite(dz)) {
        throw std::invalid_argument("Prism extrusion contains a non-finite coordinate");
    }
    if (std::sqrt(dx * dx + dy * dy + dz * dz) <= 1.0e-12) {
        throw std::invalid_argument("Prism extrusion vector must not be null");
    }

    std::lock_guard<std::mutex> lock(mutex_);
    CadDocument& document = requireDocumentLocked(documentId);
    ensureObjectCapacity(document);
    validateName(name);
    const std::uint64_t id = nextObjectId_++;
    CadObject object;
    object.id = id;
    object.name = name.empty() ? (std::string("Part::Prism") + std::to_string(id)) : name;
    object.kind = ObjectKind::Prism;
    object.parameters = {dx, dy, dz, 0.0};
    object.profileData = profileCoordinates;
    document.objects.emplace(id, std::move(object));
    document.evaluationOrder.push_back(id);
    return id;
}

} // namespace fcandroid
