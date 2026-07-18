#pragma once
#include <TopoDS_Shape.hxx>
#include <string>
namespace fcandroid {
struct StepImportResult {
    TopoDS_Shape shape;
    int rootCount {0};
    int transferredRootCount {0};
    int shapeCount {0};
    std::string diagnostics;
};
class StepImporter final {
public:
    static StepImportResult importStep(const std::string& cachedStepPath);
};
}
