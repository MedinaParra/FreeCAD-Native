#include "StepIo.hpp"

#include <IFSelect_ReturnStatus.hxx>
#include <STEPControl_Reader.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS_Shape.hxx>

#include <sstream>
#include <stdexcept>

namespace fcandroid {

StepImportResult StepIo::importFile(
    const std::string& path,
    const double linearDeflection,
    const double angularDeflection) {
    if (path.empty()) {
        throw std::invalid_argument("STEP path is empty");
    }

    STEPControl_Reader reader;
    const IFSelect_ReturnStatus status = reader.ReadFile(path.c_str());
    if (status != IFSelect_RetDone) {
        throw std::runtime_error("OpenCASCADE could not read the STEP file");
    }

    const Standard_Integer roots = reader.NbRootsForTransfer();
    if (roots <= 0) {
        throw std::runtime_error("The STEP file contains no transferable roots");
    }

    const Standard_Integer transferred = reader.TransferRoots();
    if (transferred <= 0) {
        throw std::runtime_error("STEP entities could not be transferred to BRep");
    }

    const TopoDS_Shape shape = reader.OneShape();
    if (shape.IsNull()) {
        throw std::runtime_error("STEP transfer produced a null shape");
    }

    Standard_Integer solids = 0;
    Standard_Integer shells = 0;
    Standard_Integer faces = 0;
    for (TopExp_Explorer explorer(shape, TopAbs_SOLID); explorer.More(); explorer.Next()) {
        ++solids;
    }
    for (TopExp_Explorer explorer(shape, TopAbs_SHELL); explorer.More(); explorer.Next()) {
        ++shells;
    }
    for (TopExp_Explorer explorer(shape, TopAbs_FACE); explorer.More(); explorer.Next()) {
        ++faces;
    }

    StepImportResult result;
    result.mesh = OcctMesher::triangulate(shape, linearDeflection, angularDeflection);

    std::ostringstream summary;
    summary << "STEP import completed\n"
            << "Roots: " << roots << "\n"
            << "Transferred: " << transferred << "\n"
            << "Solids: " << solids << "\n"
            << "Shells: " << shells << "\n"
            << "Faces: " << faces << "\n"
            << "Vertices: " << result.mesh.vertices.size() / 6U << "\n"
            << "Triangles: " << result.mesh.indices.size() / 3U;
    result.summary = summary.str();
    return result;
}

} // namespace fcandroid
