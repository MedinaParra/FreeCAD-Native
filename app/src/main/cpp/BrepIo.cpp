#include "BrepIo.hpp"

#include <BRepTools.hxx>
#include <BRep_Builder.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Shape.hxx>

#include <sstream>
#include <stdexcept>

namespace fcandroid {

BrepImportResult BrepIo::importFiles(
    const std::vector<std::string>& paths,
    const double linearDeflection,
    const double angularDeflection) {
    if (paths.empty()) {
        throw std::invalid_argument("The FreeCAD archive contains no BREP shape files");
    }

    BRep_Builder compoundBuilder;
    TopoDS_Compound compound;
    compoundBuilder.MakeCompound(compound);

    std::size_t loaded = 0U;
    std::size_t failed = 0U;
    for (const std::string& path : paths) {
        if (path.empty()) {
            ++failed;
            continue;
        }

        BRep_Builder shapeBuilder;
        TopoDS_Shape shape;
        const Standard_Boolean ok = BRepTools::Read(shape, path.c_str(), shapeBuilder);
        if (ok == Standard_False || shape.IsNull()) {
            ++failed;
            continue;
        }
        compoundBuilder.Add(compound, shape);
        ++loaded;
    }

    if (loaded == 0U) {
        throw std::runtime_error(
            "OpenCASCADE could not restore any BREP shape from the FCStd archive");
    }

    Standard_Integer solids = 0;
    Standard_Integer shells = 0;
    Standard_Integer faces = 0;
    for (TopExp_Explorer explorer(compound, TopAbs_SOLID); explorer.More(); explorer.Next()) {
        ++solids;
    }
    for (TopExp_Explorer explorer(compound, TopAbs_SHELL); explorer.More(); explorer.Next()) {
        ++shells;
    }
    for (TopExp_Explorer explorer(compound, TopAbs_FACE); explorer.More(); explorer.Next()) {
        ++faces;
    }

    BrepImportResult result;
    result.mesh = OcctMesher::triangulate(
        compound,
        linearDeflection,
        angularDeflection);

    std::ostringstream summary;
    summary << "FreeCAD FCStd BREP import completed\n"
            << "BREP files discovered: " << paths.size() << "\n"
            << "BREP files loaded: " << loaded << "\n"
            << "BREP files rejected: " << failed << "\n"
            << "Solids: " << solids << "\n"
            << "Shells: " << shells << "\n"
            << "Faces: " << faces << "\n"
            << "Vertices: " << result.mesh.vertices.size() / 6U << "\n"
            << "Triangles: " << result.mesh.indices.size() / 3U;
    result.summary = summary.str();
    return result;
}

} // namespace fcandroid
