#include "PythonRuntime.hpp"

#include "CadCore.hpp"
#include "PyFreeCadModule.hpp"

#include <Python.h>

#include <mutex>
#include <stdexcept>
#include <string>

namespace fcandroid {
namespace {

std::once_flag moduleRegistrationFlag;

std::runtime_error statusError(const PyStatus& status, const char* fallback) {
    return std::runtime_error(status.err_msg != nullptr ? status.err_msg : fallback);
}

void appendPath(PyConfig& config, const std::string& path) {
    wchar_t* wide = Py_DecodeLocale(path.c_str(), nullptr);
    if (wide == nullptr) {
        throw std::runtime_error("Unable to decode a Python search path");
    }
    const PyStatus status = PyWideStringList_Append(&config.module_search_paths, wide);
    PyMem_RawFree(wide);
    if (PyStatus_Exception(status)) {
        throw statusError(status, "Unable to append a Python search path");
    }
}

PyObject* newStringIo() {
    PyObject* ioModule = PyImport_ImportModule("io");
    if (ioModule == nullptr) {
        return nullptr;
    }
    PyObject* stringIoClass = PyObject_GetAttrString(ioModule, "StringIO");
    Py_DECREF(ioModule);
    if (stringIoClass == nullptr) {
        return nullptr;
    }
    PyObject* instance = PyObject_CallNoArgs(stringIoClass);
    Py_DECREF(stringIoClass);
    return instance;
}

std::string objectString(PyObject* object) {
    if (object == nullptr) {
        return {};
    }
    PyObject* value = PyObject_CallMethod(object, "getvalue", nullptr);
    if (value == nullptr) {
        PyErr_Clear();
        return {};
    }
    Py_ssize_t size = 0;
    const char* utf8 = PyUnicode_AsUTF8AndSize(value, &size);
    std::string result;
    if (utf8 != nullptr) {
        result.assign(utf8, static_cast<std::size_t>(size));
    } else {
        PyErr_Clear();
    }
    Py_DECREF(value);
    return result;
}

} // namespace

PythonRuntime& PythonRuntime::instance() {
    static PythonRuntime runtime;
    return runtime;
}

void PythonRuntime::initialize(const std::string& pythonHome) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (initialized_) {
        if (pythonHome_ != pythonHome) {
            throw std::runtime_error("Python was already initialized with a different home directory");
        }
        return;
    }
    if (pythonHome.empty()) {
        throw std::invalid_argument("Python home directory is empty");
    }

    std::call_once(moduleRegistrationFlag, [] {
        if (PyImport_AppendInittab("_freecad_native", &PyInit__freecad_native) != 0) {
            throw std::runtime_error("Unable to register the _freecad_native module");
        }
    });

    PyConfig config;
    PyConfig_InitPythonConfig(&config);
    config.isolated = 1;
    config.use_environment = 0;
    config.user_site_directory = 0;
    config.site_import = 1;
    config.write_bytecode = 0;
    config.module_search_paths_set = 1;

    wchar_t* home = Py_DecodeLocale(pythonHome.c_str(), nullptr);
    if (home == nullptr) {
        PyConfig_Clear(&config);
        throw std::runtime_error("Unable to decode the Python home directory");
    }
    PyStatus status = PyConfig_SetString(&config, &config.home, home);
    PyMem_RawFree(home);
    if (PyStatus_Exception(status)) {
        PyConfig_Clear(&config);
        throw statusError(status, "Unable to configure the Python home directory");
    }

    wchar_t* programName = Py_DecodeLocale("FreeCADAndroid", nullptr);
    if (programName == nullptr) {
        PyConfig_Clear(&config);
        throw std::runtime_error("Unable to configure the embedded Python program name");
    }
    status = PyConfig_SetString(&config, &config.program_name, programName);
    PyMem_RawFree(programName);
    if (PyStatus_Exception(status)) {
        PyConfig_Clear(&config);
        throw statusError(status, "Unable to configure the Python program name");
    }

    try {
        appendPath(config, pythonHome + "/lib/python3.14");
        appendPath(config, pythonHome + "/lib/python3.14/lib-dynload");
        appendPath(config, pythonHome + "/lib/python3.14/site-packages");
    } catch (...) {
        PyConfig_Clear(&config);
        throw;
    }

    status = Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);
    if (PyStatus_Exception(status)) {
        throw statusError(status, "Unable to initialize embedded Python");
    }

    initialized_ = true;
    pythonHome_ = pythonHome;
    PyEval_SaveThread();
}

bool PythonRuntime::isInitialized() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return initialized_;
}

std::string PythonRuntime::version() const {
    return std::string(Py_GetVersion());
}

MacroExecutionResult PythonRuntime::execute(const std::string& sourceCode) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) {
        throw std::runtime_error("Embedded Python has not been initialized");
    }
    if (sourceCode.empty()) {
        throw std::invalid_argument("Macro source code is empty");
    }

    MacroExecutionResult execution;
    const PyGILState_STATE gilState = PyGILState_Ensure();

    PyObject* sysModule = nullptr;
    PyObject* oldStdout = nullptr;
    PyObject* oldStderr = nullptr;
    PyObject* stdoutCapture = nullptr;
    PyObject* stderrCapture = nullptr;
    PyObject* globals = nullptr;

    auto cleanup = [&] {
        if (sysModule != nullptr) {
            if (oldStdout != nullptr) {
                PyObject_SetAttrString(sysModule, "stdout", oldStdout);
            }
            if (oldStderr != nullptr) {
                PyObject_SetAttrString(sysModule, "stderr", oldStderr);
            }
        }
        Py_XDECREF(globals);
        Py_XDECREF(stderrCapture);
        Py_XDECREF(stdoutCapture);
        Py_XDECREF(oldStderr);
        Py_XDECREF(oldStdout);
        Py_XDECREF(sysModule);
        PyGILState_Release(gilState);
    };

    try {
        sysModule = PyImport_ImportModule("sys");
        if (sysModule == nullptr) {
            throw std::runtime_error("Unable to import Python sys module");
        }
        oldStdout = PyObject_GetAttrString(sysModule, "stdout");
        oldStderr = PyObject_GetAttrString(sysModule, "stderr");
        stdoutCapture = newStringIo();
        stderrCapture = newStringIo();
        if (oldStdout == nullptr || oldStderr == nullptr ||
            stdoutCapture == nullptr || stderrCapture == nullptr) {
            throw std::runtime_error("Unable to create Python output capture streams");
        }
        if (PyObject_SetAttrString(sysModule, "stdout", stdoutCapture) != 0 ||
            PyObject_SetAttrString(sysModule, "stderr", stderrCapture) != 0) {
            throw std::runtime_error("Unable to redirect Python output streams");
        }

        globals = PyDict_New();
        if (globals == nullptr) {
            throw std::runtime_error("Unable to create Python macro globals");
        }
        if (PyDict_SetItemString(globals, "__builtins__", PyEval_GetBuiltins()) != 0) {
            throw std::runtime_error("Unable to configure Python builtins");
        }
        PyObject* macroName = PyUnicode_FromString("<freecad-android-macro>");
        if (macroName == nullptr || PyDict_SetItemString(globals, "__name__", macroName) != 0) {
            Py_XDECREF(macroName);
            throw std::runtime_error("Unable to configure Python macro metadata");
        }
        Py_DECREF(macroName);

        CadCore::instance().reset();
        resetPythonDocumentState();

        const char* resetCode =
            "import FreeCAD\n"
            "FreeCAD._reset()\n";
        PyObject* resetResult = PyRun_StringFlags(
            resetCode, Py_file_input, globals, globals, nullptr);
        if (resetResult == nullptr) {
            PyErr_Print();
            execution.errorText = objectString(stderrCapture);
            cleanup();
            return execution;
        }
        Py_DECREF(resetResult);

        PyObject* result = PyRun_StringFlags(
            sourceCode.c_str(), Py_file_input, globals, globals, nullptr);
        if (result == nullptr) {
            PyErr_Print();
            execution.errorText = objectString(stderrCapture);
            execution.stdoutText = objectString(stdoutCapture);
            cleanup();
            return execution;
        }
        Py_DECREF(result);

        execution.documentId = activePythonDocumentId();
        if (execution.documentId == 0U) {
            execution.errorText = "The macro did not create or activate a FreeCAD document.";
            execution.stdoutText = objectString(stdoutCapture);
            cleanup();
            return execution;
        }

        if (!CadCore::instance().recompute(execution.documentId)) {
            execution.errorText = CadCore::instance().lastError(execution.documentId);
            execution.stdoutText = objectString(stdoutCapture);
            cleanup();
            return execution;
        }

        execution.documentSummary = CadCore::instance().documentSummary(execution.documentId);
        execution.stdoutText = objectString(stdoutCapture);
        const std::string stderrText = objectString(stderrCapture);
        if (!stderrText.empty()) {
            if (!execution.stdoutText.empty()) {
                execution.stdoutText += "\n";
            }
            execution.stdoutText += stderrText;
        }
        execution.success = true;
        cleanup();
        return execution;
    } catch (const std::exception& exception) {
        if (PyErr_Occurred()) {
            PyErr_Print();
        }
        execution.stdoutText = objectString(stdoutCapture);
        execution.errorText = objectString(stderrCapture);
        if (execution.errorText.empty()) {
            execution.errorText = exception.what();
        }
        cleanup();
        return execution;
    }
}

std::string PythonRuntime::pythonString(void* object) {
    return objectString(static_cast<PyObject*>(object));
}

} // namespace fcandroid
