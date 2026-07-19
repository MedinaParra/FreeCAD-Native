#pragma once

#include <cstdint>
#include <mutex>
#include <string>

namespace fcandroid {

struct MacroExecutionResult {
    bool success {false};
    std::uint64_t documentId {0};
    std::string stdoutText;
    std::string errorText;
    std::string documentSummary;
};

class PythonRuntime final {
public:
    static PythonRuntime& instance();

    void initialize(const std::string& pythonHome);
    bool isInitialized() const;
    std::string version() const;
    MacroExecutionResult execute(const std::string& sourceCode);

private:
    PythonRuntime() = default;

    static std::string pythonString(void* object);

    mutable std::mutex mutex_;
    bool initialized_ {false};
    std::string pythonHome_;
};

} // namespace fcandroid
