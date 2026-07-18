#include "StepImporter.hpp"

#include <IFSelect_ReturnStatus.hxx>
#include <STEPControl_Reader.hxx>
#include <Standard_Failure.hxx>

#include <fstream>
#include <mutex>
#include <sstream>
#include <stdexcept>

namespace fcandroid {
namespace {

std::mutex& stepMutex() {
    static std::mutex mutex;
    return mutex;
}

const char* statusName(const IFSelect_ReturnStatus status) {
    switch (status) {
        case IFSelect_RetVoid: return "void";
        case IFSelect_RetDone: return "done";
        case IFSelect_RetError: return "error";
        case IFSelect_RetFail: return "fail";
        case IFSelect_RetStop: return "stop";
    }
    return "unknown";
}

} // namespace

StepImportResult StepImporter::importStep(const std::string& cachedStepPath) {
    if (cachedStepPath.empty()) {
        throw std::invalid_argument("STEP path is empty");
    }
    std::ifstream probe(cachedStepPath, std::ios::binary);
    if (!probe.good()) {
        throw std::runtime_error("The selected STEP copy cannot be opened");
    }
    probe.close();

    std::lock_guard<std::mutex> lock(stepMutex());
    try {
        STEPControl_Reader reader;
        const IFSelect_ReturnStatus status = reader.ReadFile(cachedStepPath.c_str());
        if (status != IFSelect_RetDone) {
            throw std::runtime_error(
                std::string("OCCT STEP reader returned ") + statusName(status));
        }

        StepImportResult result;
        result.rootCount = static_cast<int>(reader.NbRootsForTransfer());
        result.transferredRootCount = static_cast<int>(reader.TransferRoots());
        result.shapeCount = static_cast<int>(reader.NbShapes());
        result.shape = reader.OneShape();

        if (result.transferredRootCount <= 0 || result.shapeCount <= 0 || result.shape.IsNull()) {
            throw std::runtime_error("The STEP file contains no transferable BRep shapes");
        }

        std::ostringstream diagnostics;
        diagnostics << "STEP roots: " << result.rootCount << "\n"
                    << "Transferred roots: " << result.transferredRootCount << "\n"
                    << "OCCT result shapes: " << result.shapeCount;
        result.diagnostics = diagnostics.str();
        return result;
    } catch (const Standard_Failure& failure) {
        const char* message = failure.GetMessageString();
        throw std::runtime_error(
            message != nullptr ? message : "OpenCASCADE STEP import failure");
    }
}

} // namespace fcandroid
