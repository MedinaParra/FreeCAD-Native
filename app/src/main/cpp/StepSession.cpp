#include "StepSession.hpp"

#include <BRepAdaptor_Surface.hxx>
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <BRepBuilderAPI_Copy.hxx>
#include <BRepGProp.hxx>
#include <BRepPrimAPI_MakePrism.hxx>
#include <GProp_GProps.hxx>
#include <GeomAbs_SurfaceType.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <STEPControl_Reader.hxx>
#include <STEPControl_Writer.hxx>
#include <TopAbs_Orientation.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp.hxx>
#include <TopTools_IndexedMapOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Shape.hxx>
#include <gp_Dir.hxx>
#include <gp_Pln.hxx>
#include <gp_Pnt.hxx>
#include <gp_Vec.hxx>

#include <atomic>
#include <cmath>
#include <limits>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <unordered_map>

namespace fcandroid {
namespace {

struct Session {
    std::string sourcePath;
    TopoDS_Shape current;
    TopoDS_Shape preview;
    bool hasPreview = false;
    std::int32_t revision = 0;
    double linearDeflection = 0.35;
    double angularDeflection = 0.30;
};

std::mutex gMutex;
std::unordered_map<std::int64_t, std::shared_ptr<Session>> gSessions;
std::atomic<std::int64_t> gNextHandle {1};

std::shared_ptr<Session> requireSession(const std::int64_t handle) {
    const auto iterator = gSessions.find(handle);
    if (iterator == gSessions.end() || !iterator->second) {
        throw std::invalid_argument("STEP session handle is invalid or already closed");
    }
    return iterator->second;
}

TopoDS_Shape readStep(const std::string& path) {
    if (path.empty()) {
        throw std::invalid_argument("STEP path is empty");
    }
    STEPControl_Reader reader;
    const IFSelect_ReturnStatus readStatus = reader.ReadFile(path.c_str());
    if (readStatus != IFSelect_RetDone) {
        throw std::runtime_error("OpenCASCADE could not read the STEP file");
    }
    if (reader.NbRootsForTransfer() <= 0 || reader.TransferRoots() <= 0) {
        throw std::runtime_error("STEP entities could not be transferred to BRep");
    }
    TopoDS_Shape shape = reader.OneShape();
    if (shape.IsNull()) {
        throw std::runtime_error("STEP transfer produced a null shape");
    }
    return shape;
}

TopTools_IndexedMapOfShape mapFaces(const TopoDS_Shape& shape) {
    TopTools_IndexedMapOfShape map;
    TopExp::MapShapes(shape, TopAbs_FACE, map);
    return map;
}

std::vector<StepFaceDescriptor> describeFaces(const TopoDS_Shape& shape) {
    const TopTools_IndexedMapOfShape faces = mapFaces(shape);
    std::vector<StepFaceDescriptor> result;
    result.reserve(static_cast<std::size_t>(faces.Extent()));
    for (Standard_Integer index = 1; index <= faces.Extent(); ++index) {
        const TopoDS_Face face = TopoDS::Face(faces(index));
        GProp_GProps properties;
        BRepGProp::SurfaceProperties(face, properties);
        const gp_Pnt center = properties.CentreOfMass();
        const double area = properties.Mass();

        StepFaceDescriptor descriptor;
        descriptor.id = static_cast<std::int32_t>(index);
        descriptor.pointX = static_cast<float>(center.X());
        descriptor.pointY = static_cast<float>(center.Y());
        descriptor.pointZ = static_cast<float>(center.Z());
        descriptor.area = static_cast<float>(area);

        BRepAdaptor_Surface adapter(face, Standard_True);
        descriptor.planar = adapter.GetType() == GeomAbs_Plane;
        if (descriptor.planar) {
            gp_Dir direction = adapter.Plane().Axis().Direction();
            if (face.Orientation() == TopAbs_REVERSED) {
                direction.Reverse();
            }
            descriptor.normalX = static_cast<float>(direction.X());
            descriptor.normalY = static_cast<float>(direction.Y());
            descriptor.normalZ = static_cast<float>(direction.Z());
        }
        result.push_back(descriptor);
    }
    return result;
}

StepSessionSnapshot snapshot(
    const std::int64_t handle,
    const Session& session,
    const bool usePreview,
    const std::string& operation) {
    const TopoDS_Shape& shape = usePreview && session.hasPreview ? session.preview : session.current;
    if (shape.IsNull()) {
        throw std::runtime_error("STEP session contains a null shape");
    }

    StepSessionSnapshot result;
    result.handle = handle;
    result.revision = session.revision;
    result.mesh = OcctMesher::triangulate(
        shape,
        session.linearDeflection,
        session.angularDeflection);
    result.faces = describeFaces(shape);

    std::ostringstream summary;
    summary << operation << "\n"
            << "Session: " << handle << "\n"
            << "Revision: " << session.revision << "\n"
            << "Preview: " << ((usePreview && session.hasPreview) ? "yes" : "no") << "\n"
            << "Faces: " << result.faces.size() << "\n"
            << "Vertices: " << result.mesh.vertices.size() / 6U << "\n"
            << "Triangles: " << result.mesh.indices.size() / 3U;
    result.summary = summary.str();
    return result;
}

TopoDS_Face requireFace(const TopoDS_Shape& shape, const std::int32_t faceId) {
    const TopTools_IndexedMapOfShape faces = mapFaces(shape);
    const Standard_Integer index = static_cast<Standard_Integer>(faceId);
    if (index < 1 || index > faces.Extent()) {
        throw std::out_of_range("Selected OCCT face is no longer available in the current revision");
    }
    return TopoDS::Face(faces(index));
}

TopoDS_Shape buildPull(
    const TopoDS_Shape& current,
    const TopoDS_Face& face,
    const double distance) {
    if (!std::isfinite(distance) || std::abs(distance) < 1.0e-6) {
        throw std::invalid_argument("Pull distance must be finite and non-zero");
    }

    BRepAdaptor_Surface adapter(face, Standard_True);
    if (adapter.GetType() != GeomAbs_Plane) {
        throw std::invalid_argument("The first native Pull milestone supports planar faces only");
    }

    gp_Dir direction = adapter.Plane().Axis().Direction();
    if (face.Orientation() == TopAbs_REVERSED) {
        direction.Reverse();
    }
    const gp_Vec vector(direction.X() * distance, direction.Y() * distance, direction.Z() * distance);
    const TopoDS_Shape prism = BRepPrimAPI_MakePrism(face, vector, Standard_True, Standard_True).Shape();
    if (prism.IsNull()) {
        throw std::runtime_error("OpenCASCADE could not build the Pull prism");
    }

    TopoDS_Shape output;
    if (distance > 0.0) {
        BRepAlgoAPI_Fuse operation(current, prism);
        operation.SetRunParallel(Standard_False);
        operation.Build();
        if (!operation.IsDone()) {
            throw std::runtime_error("OpenCASCADE fuse failed during Pull preview");
        }
        output = operation.Shape();
    } else {
        BRepAlgoAPI_Cut operation(current, prism);
        operation.SetRunParallel(Standard_False);
        operation.Build();
        if (!operation.IsDone()) {
            throw std::runtime_error("OpenCASCADE cut failed during Pull preview");
        }
        output = operation.Shape();
    }

    if (output.IsNull()) {
        throw std::runtime_error("Pull preview produced a null BRep");
    }
    return output;
}

} // namespace

StepSessionSnapshot StepSessionManager::open(
    const std::string& path,
    const double linearDeflection,
    const double angularDeflection) {
    if (!std::isfinite(linearDeflection) || linearDeflection <= 0.0 ||
        !std::isfinite(angularDeflection) || angularDeflection <= 0.0) {
        throw std::invalid_argument("STEP tessellation tolerances must be positive");
    }

    auto session = std::make_shared<Session>();
    session->sourcePath = path;
    session->current = readStep(path);
    session->linearDeflection = linearDeflection;
    session->angularDeflection = angularDeflection;

    const std::int64_t handle = gNextHandle.fetch_add(1);
    {
        std::lock_guard<std::mutex> guard(gMutex);
        gSessions.emplace(handle, session);
    }
    return snapshot(handle, *session, false, "STEP session opened");
}

StepSessionSnapshot StepSessionManager::previewPull(
    const std::int64_t handle,
    const std::int32_t faceId,
    const double distance,
    const double linearDeflection,
    const double angularDeflection) {
    std::lock_guard<std::mutex> guard(gMutex);
    const std::shared_ptr<Session> session = requireSession(handle);
    if (std::isfinite(linearDeflection) && linearDeflection > 0.0) {
        session->linearDeflection = linearDeflection;
    }
    if (std::isfinite(angularDeflection) && angularDeflection > 0.0) {
        session->angularDeflection = angularDeflection;
    }
    const TopoDS_Face face = requireFace(session->current, faceId);
    session->preview = buildPull(session->current, face, distance);
    session->hasPreview = true;
    return snapshot(handle, *session, true, "Native Pull preview");
}

StepSessionSnapshot StepSessionManager::commit(const std::int64_t handle) {
    std::lock_guard<std::mutex> guard(gMutex);
    const std::shared_ptr<Session> session = requireSession(handle);
    if (!session->hasPreview || session->preview.IsNull()) {
        throw std::logic_error("There is no STEP preview to commit");
    }
    session->current = session->preview;
    session->preview.Nullify();
    session->hasPreview = false;
    if (session->revision < std::numeric_limits<std::int32_t>::max()) {
        ++session->revision;
    }
    return snapshot(handle, *session, false, "STEP preview committed");
}

StepSessionSnapshot StepSessionManager::rollback(const std::int64_t handle) {
    std::lock_guard<std::mutex> guard(gMutex);
    const std::shared_ptr<Session> session = requireSession(handle);
    session->preview.Nullify();
    session->hasPreview = false;
    return snapshot(handle, *session, false, "STEP preview rolled back");
}

std::string StepSessionManager::save(const std::int64_t handle, const std::string& outputPath) {
    if (outputPath.empty()) {
        throw std::invalid_argument("STEP output path is empty");
    }
    std::lock_guard<std::mutex> guard(gMutex);
    const std::shared_ptr<Session> session = requireSession(handle);
    STEPControl_Writer writer;
    const IFSelect_ReturnStatus transferStatus = writer.Transfer(session->current, STEPControl_AsIs);
    if (transferStatus != IFSelect_RetDone) {
        throw std::runtime_error("OpenCASCADE could not transfer the edited BRep for STEP export");
    }
    const IFSelect_ReturnStatus writeStatus = writer.Write(outputPath.c_str());
    if (writeStatus != IFSelect_RetDone) {
        throw std::runtime_error("OpenCASCADE could not write the edited STEP copy");
    }
    std::ostringstream summary;
    summary << "STEP copy saved\nPath: " << outputPath << "\nRevision: " << session->revision;
    return summary.str();
}

void StepSessionManager::close(const std::int64_t handle) {
    std::lock_guard<std::mutex> guard(gMutex);
    const auto erased = gSessions.erase(handle);
    if (erased == 0U) {
        throw std::invalid_argument("STEP session handle is invalid or already closed");
    }
}

} // namespace fcandroid
