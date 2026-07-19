#include "BrepIo.hpp"

#include <BRepBuilderAPI_Transform.hxx>
#include <BRepTools.hxx>
#include <BRep_Builder.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Shape.hxx>
#include <TopLoc_Location.hxx>
#include <gp_Quaternion.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

#include <cmath>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace fcandroid {
namespace {

TopoDS_Shape applyPlacement(
    const TopoDS_Shape& shape,
    const std::array<double, 7>& placement) {
    for (const double value : placement) {
        if (!std::isfinite(value)) {
            throw std::invalid_argument("FCStd placement contains a non-finite value");
        }
    }
    const double norm = std::sqrt(
        placement[3] * placement[3] + placement[4] * placement[4] +
        placement[5] * placement[5] + placement[6] * placement[6]);
    if (norm <= 1.0e-12) {
        throw std::invalid_argument("FCStd placement quaternion is null");
    }

    // PropertyPartShape may serialize the object's old top-level location in
    // the BREP. Reset only that location so the editable Placement is applied
    // exactly once while all internal sub-shape locations remain intact.
    TopoDS_Shape localShape = shape;
    localShape.Location(TopLoc_Location());

    gp_Trsf transform;
    transform.SetRotation(gp_Quaternion(
        placement[3] / norm,
        placement[4] / norm,
        placement[5] / norm,
        placement[6] / norm));
    transform.SetTranslationPart(gp_Vec(placement[0], placement[1], placement[2]));
    BRepBuilderAPI_Transform transformer(localShape, transform, Standard_True);
    transformer.Build();
    if (!transformer.IsDone() || transformer.Shape().IsNull()) {
        throw std::runtime_error("Unable to apply FCStd object placement");
    }
    return transformer.Shape();
}

} // namespace

BrepImportResult BrepIo::importFiles(
    const std::vector<std::string>& paths,
    const double linearDeflection,
    const double angularDeflection) {
    std::vector<BrepObjectInput> objects;
    objects.reserve(paths.size());
    for (std::size_t index = 0; index < paths.size(); ++index) {
        BrepObjectInput object;
        object.path = paths[index];
        object.name = "Shape" + std::to_string(index + 1U);
        objects.push_back(std::move(object));
    }
    return importObjects(objects, linearDeflection, angularDeflection);
}

BrepImportResult BrepIo::importObjects(
    const std::vector<BrepObjectInput>& objects,
    const double linearDeflection,
    const double angularDeflection) {
    if (objects.empty()) {
        throw std::invalid_argument("The FreeCAD archive contains no BREP shape objects");
    }
    if (objects.size() > 512U) {
        throw std::length_error("The FreeCAD archive exceeds the 512 shape object limit");
    }

    const bool hasVisibleObject = [&] {
        for (const BrepObjectInput& object : objects) {
            if (object.visible) return true;
        }
        return false;
    }();

    BRep_Builder compoundBuilder;
    TopoDS_Compound compound;
    compoundBuilder.MakeCompound(compound);

    std::size_t loaded = 0U;
    std::size_t failed = 0U;
    std::size_t hidden = 0U;
    for (const BrepObjectInput& object : objects) {
        if (hasVisibleObject && !object.visible) {
            ++hidden;
            continue;
        }
        if (object.path.empty()) {
            ++failed;
            continue;
        }

        BRep_Builder shapeBuilder;
        TopoDS_Shape shape;
        const Standard_Boolean ok = BRepTools::Read(shape, object.path.c_str(), shapeBuilder);
        if (ok == Standard_False || shape.IsNull()) {
            ++failed;
            continue;
        }
        try {
            compoundBuilder.Add(compound, applyPlacement(shape, object.placement));
            ++loaded;
        } catch (...) {
            ++failed;
        }
    }

    if (loaded == 0U) {
        throw std::runtime_error(
            "OpenCASCADE could not restore any visible BREP shape from the FCStd archive");
    }

    Standard_Integer solids = 0;
    Standard_Integer shells = 0;
    Standard_Integer faces = 0;
    for (TopExp_Explorer explorer(compound, TopAbs_SOLID); explorer.More(); explorer.Next()) ++solids;
    for (TopExp_Explorer explorer(compound, TopAbs_SHELL); explorer.More(); explorer.Next()) ++shells;
    for (TopExp_Explorer explorer(compound, TopAbs_FACE); explorer.More(); explorer.Next()) ++faces;

    BrepImportResult result;
    result.mesh = OcctMesher::triangulate(compound, linearDeflection, angularDeflection);

    std::ostringstream summary;
    summary << "FreeCAD object-aware BREP import completed\n"
            << "Shape objects: " << objects.size() << "\n"
            << "Visible shapes loaded: " << loaded << "\n"
            << "Hidden shapes skipped: " << hidden << "\n"
            << "Shapes rejected: " << failed << "\n";
    if (!hasVisibleObject) {
        summary << "Visibility recovery: all shapes previewed because the document hid every object\n";
    }
    summary << "Solids: " << solids << "\n"
            << "Shells: " << shells << "\n"
            << "Faces: " << faces << "\n"
            << "Vertices: " << result.mesh.vertices.size() / 6U << "\n"
            << "Triangles: " << result.mesh.indices.size() / 3U;
    result.summary = summary.str();
    return result;
}

} // namespace fcandroid
