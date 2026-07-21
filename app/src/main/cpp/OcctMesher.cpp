#include "OcctMesher.hpp"

#include <BRepMesh_IncrementalMesh.hxx>
#include <BRep_Tool.hxx>
#include <Poly_Triangle.hxx>
#include <Poly_Triangulation.hxx>
#include <TopAbs_Orientation.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp.hxx>
#include <TopLoc_Location.hxx>
#include <TopTools_IndexedMapOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace fcandroid {
namespace {

void appendVertex(
    MeshData& mesh,
    const gp_Pnt& point,
    const gp_Vec& normal) {
    mesh.vertices.push_back(static_cast<float>(point.X()));
    mesh.vertices.push_back(static_cast<float>(point.Y()));
    mesh.vertices.push_back(static_cast<float>(point.Z()));
    mesh.vertices.push_back(static_cast<float>(normal.X()));
    mesh.vertices.push_back(static_cast<float>(normal.Y()));
    mesh.vertices.push_back(static_cast<float>(normal.Z()));

    mesh.bounds[0] = std::min(mesh.bounds[0], static_cast<float>(point.X()));
    mesh.bounds[1] = std::min(mesh.bounds[1], static_cast<float>(point.Y()));
    mesh.bounds[2] = std::min(mesh.bounds[2], static_cast<float>(point.Z()));
    mesh.bounds[3] = std::max(mesh.bounds[3], static_cast<float>(point.X()));
    mesh.bounds[4] = std::max(mesh.bounds[4], static_cast<float>(point.Y()));
    mesh.bounds[5] = std::max(mesh.bounds[5], static_cast<float>(point.Z()));
}

} // namespace

MeshData OcctMesher::triangulate(
    const TopoDS_Shape& shape,
    const double linearDeflection,
    const double angularDeflection) {
    if (shape.IsNull()) {
        throw std::invalid_argument("Cannot triangulate a null shape");
    }
    if (!std::isfinite(linearDeflection) || linearDeflection <= 0.0) {
        throw std::invalid_argument("Linear deflection must be positive");
    }
    if (!std::isfinite(angularDeflection) || angularDeflection <= 0.0) {
        throw std::invalid_argument("Angular deflection must be positive");
    }

    BRepMesh_IncrementalMesh mesher(
        shape,
        linearDeflection,
        Standard_False,
        angularDeflection,
        Standard_True);
    mesher.Perform();
    if (!mesher.IsDone()) {
        throw std::runtime_error("OpenCASCADE tessellation failed");
    }

    MeshData mesh;
    const float positiveInfinity = std::numeric_limits<float>::infinity();
    mesh.bounds = {
        positiveInfinity,
        positiveInfinity,
        positiveInfinity,
        -positiveInfinity,
        -positiveInfinity,
        -positiveInfinity
    };

    TopTools_IndexedMapOfShape faceMap;
    TopExp::MapShapes(shape, TopAbs_FACE, faceMap);
    for (Standard_Integer faceIndex = 1; faceIndex <= faceMap.Extent(); ++faceIndex) {
        const TopoDS_Face face = TopoDS::Face(faceMap(faceIndex));
        TopLoc_Location location;
        const Handle(Poly_Triangulation) triangulation =
            BRep_Tool::Triangulation(face, location);
        if (triangulation.IsNull()) {
            continue;
        }

        const gp_Trsf locationTransform = location.Transformation();
        const bool reversed = face.Orientation() == TopAbs_REVERSED;

        for (Standard_Integer triangleIndex = 1;
             triangleIndex <= triangulation->NbTriangles();
             ++triangleIndex) {
            Standard_Integer node1 = 0;
            Standard_Integer node2 = 0;
            Standard_Integer node3 = 0;
            triangulation->Triangle(triangleIndex).Get(node1, node2, node3);
            if (reversed) {
                std::swap(node2, node3);
            }

            gp_Pnt point1 = triangulation->Node(node1).Transformed(locationTransform);
            gp_Pnt point2 = triangulation->Node(node2).Transformed(locationTransform);
            gp_Pnt point3 = triangulation->Node(node3).Transformed(locationTransform);

            const gp_Vec edge1(point1, point2);
            const gp_Vec edge2(point1, point3);
            gp_Vec normal = edge1.Crossed(edge2);
            if (normal.SquareMagnitude() <= 1.0e-20) {
                continue;
            }
            normal.Normalize();

            const std::int32_t firstIndex =
                static_cast<std::int32_t>(mesh.vertices.size() / 6U);
            appendVertex(mesh, point1, normal);
            appendVertex(mesh, point2, normal);
            appendVertex(mesh, point3, normal);
            mesh.indices.push_back(firstIndex);
            mesh.indices.push_back(firstIndex + 1);
            mesh.indices.push_back(firstIndex + 2);
            mesh.triangleFaceIds.push_back(static_cast<std::int32_t>(faceIndex));
        }
    }

    if (mesh.indices.empty()) {
        throw std::runtime_error("The shape produced no renderable triangles");
    }
    if (mesh.triangleFaceIds.size() * 3U != mesh.indices.size()) {
        throw std::runtime_error("OCCT face mapping does not match the tessellated triangle count");
    }

    return mesh;
}

} // namespace fcandroid
