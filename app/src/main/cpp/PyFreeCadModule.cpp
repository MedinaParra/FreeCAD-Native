#include "PyFreeCadModule.hpp"

#include "CadCore.hpp"

#include <Standard_Failure.hxx>
#include <Standard_Version.hxx>

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <sstream>
#include <string>

namespace {

std::atomic<std::uint64_t> gActiveDocumentId {0};

void setPythonErrorFromCurrentException() {
    try {
        throw;
    } catch (const Standard_Failure& failure) {
        const char* message = failure.GetMessageString();
        PyErr_SetString(
            PyExc_RuntimeError,
            message != nullptr ? message : "OpenCASCADE failure");
    } catch (const std::exception& exception) {
        PyErr_SetString(PyExc_RuntimeError, exception.what());
    } catch (...) {
        PyErr_SetString(PyExc_RuntimeError, "Unknown native CAD failure");
    }
}

std::uint64_t asId(const unsigned long long value) {
    return static_cast<std::uint64_t>(value);
}

PyObject* pyReset(PyObject*, PyObject*) {
    try {
        fcandroid::CadCore::instance().reset();
        gActiveDocumentId.store(0U);
        Py_RETURN_NONE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyCreateDocument(PyObject*, PyObject* args) {
    const char* name = nullptr;
    if (!PyArg_ParseTuple(args, "s:create_document", &name)) {
        return nullptr;
    }
    try {
        const std::uint64_t id = fcandroid::CadCore::instance().createDocument(name);
        gActiveDocumentId.store(id);
        return PyLong_FromUnsignedLongLong(id);
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyCloseDocument(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    if (!PyArg_ParseTuple(args, "K:close_document", &documentId)) {
        return nullptr;
    }
    try {
        fcandroid::CadCore::instance().closeDocument(asId(documentId));
        if (gActiveDocumentId.load() == asId(documentId)) {
            gActiveDocumentId.store(0U);
        }
        Py_RETURN_NONE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pySetActiveDocument(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    if (!PyArg_ParseTuple(args, "K:set_active_document", &documentId)) {
        return nullptr;
    }
    gActiveDocumentId.store(asId(documentId));
    Py_RETURN_NONE;
}

PyObject* pyActiveDocument(PyObject*, PyObject*) {
    return PyLong_FromUnsignedLongLong(gActiveDocumentId.load());
}

PyObject* pyAddBox(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    double length = 0.0;
    double width = 0.0;
    double height = 0.0;
    if (!PyArg_ParseTuple(
            args,
            "Ksddd:add_box",
            &documentId,
            &name,
            &length,
            &width,
            &height)) {
        return nullptr;
    }
    try {
        return PyLong_FromUnsignedLongLong(fcandroid::CadCore::instance().addBox(
            asId(documentId), name, length, width, height));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyAddCylinder(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    double radius = 0.0;
    double height = 0.0;
    if (!PyArg_ParseTuple(
            args,
            "Ksdd:add_cylinder",
            &documentId,
            &name,
            &radius,
            &height)) {
        return nullptr;
    }
    try {
        return PyLong_FromUnsignedLongLong(fcandroid::CadCore::instance().addCylinder(
            asId(documentId), name, radius, height));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyAddSphere(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    double radius = 0.0;
    if (!PyArg_ParseTuple(args, "Ksd:add_sphere", &documentId, &name, &radius)) {
        return nullptr;
    }
    try {
        return PyLong_FromUnsignedLongLong(fcandroid::CadCore::instance().addSphere(
            asId(documentId), name, radius));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyAddCone(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    double radius1 = 0.0;
    double radius2 = 0.0;
    double height = 0.0;
    if (!PyArg_ParseTuple(
            args,
            "Ksddd:add_cone",
            &documentId,
            &name,
            &radius1,
            &radius2,
            &height)) {
        return nullptr;
    }
    try {
        return PyLong_FromUnsignedLongLong(fcandroid::CadCore::instance().addCone(
            asId(documentId), name, radius1, radius2, height));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyAddTorus(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    double radius1 = 0.0;
    double radius2 = 0.0;
    if (!PyArg_ParseTuple(
            args,
            "Ksdd:add_torus",
            &documentId,
            &name,
            &radius1,
            &radius2)) {
        return nullptr;
    }
    try {
        return PyLong_FromUnsignedLongLong(fcandroid::CadCore::instance().addTorus(
            asId(documentId), name, radius1, radius2));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* addBoolean(PyObject* args, const fcandroid::ObjectKind kind) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    unsigned long long leftId = 0;
    unsigned long long rightId = 0;
    if (!PyArg_ParseTuple(
            args,
            "KsKK:add_boolean",
            &documentId,
            &name,
            &leftId,
            &rightId)) {
        return nullptr;
    }
    try {
        std::uint64_t result = 0;
        switch (kind) {
            case fcandroid::ObjectKind::Fuse:
                result = fcandroid::CadCore::instance().addFuse(
                    asId(documentId), name, asId(leftId), asId(rightId));
                break;
            case fcandroid::ObjectKind::Cut:
                result = fcandroid::CadCore::instance().addCut(
                    asId(documentId), name, asId(leftId), asId(rightId));
                break;
            case fcandroid::ObjectKind::Common:
                result = fcandroid::CadCore::instance().addCommon(
                    asId(documentId), name, asId(leftId), asId(rightId));
                break;
            default:
                PyErr_SetString(PyExc_RuntimeError, "Invalid boolean object kind");
                return nullptr;
        }
        return PyLong_FromUnsignedLongLong(result);
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyAddFuse(PyObject*, PyObject* args) {
    return addBoolean(args, fcandroid::ObjectKind::Fuse);
}

PyObject* pyAddCut(PyObject*, PyObject* args) {
    return addBoolean(args, fcandroid::ObjectKind::Cut);
}

PyObject* pyAddCommon(PyObject*, PyObject* args) {
    return addBoolean(args, fcandroid::ObjectKind::Common);
}

PyObject* pySetParameter(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    unsigned long long objectId = 0;
    unsigned long parameterIndex = 0;
    double value = 0.0;
    if (!PyArg_ParseTuple(
            args,
            "KKkd:set_parameter",
            &documentId,
            &objectId,
            &parameterIndex,
            &value)) {
        return nullptr;
    }
    try {
        fcandroid::CadCore::instance().setParameter(
            asId(documentId),
            asId(objectId),
            static_cast<std::size_t>(parameterIndex),
            value);
        Py_RETURN_NONE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pySetBooleanOperands(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    unsigned long long objectId = 0;
    unsigned long long leftId = 0;
    unsigned long long rightId = 0;
    if (!PyArg_ParseTuple(
            args,
            "KKKK:set_boolean_operands",
            &documentId,
            &objectId,
            &leftId,
            &rightId)) {
        return nullptr;
    }
    try {
        fcandroid::CadCore::instance().setBooleanOperands(
            asId(documentId), asId(objectId), asId(leftId), asId(rightId));
        Py_RETURN_NONE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pySetPlacement(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    unsigned long long objectId = 0;
    fcandroid::Placement placement;
    if (!PyArg_ParseTuple(
            args,
            "KKddddddd:set_placement",
            &documentId,
            &objectId,
            &placement.x,
            &placement.y,
            &placement.z,
            &placement.qx,
            &placement.qy,
            &placement.qz,
            &placement.qw)) {
        return nullptr;
    }
    try {
        fcandroid::CadCore::instance().setPlacement(
            asId(documentId), asId(objectId), placement);
        Py_RETURN_NONE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pySetVisibility(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    unsigned long long objectId = 0;
    int visible = 0;
    if (!PyArg_ParseTuple(
            args,
            "KKp:set_visibility",
            &documentId,
            &objectId,
            &visible)) {
        return nullptr;
    }
    try {
        fcandroid::CadCore::instance().setVisibility(
            asId(documentId), asId(objectId), visible != 0);
        Py_RETURN_NONE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyRecompute(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    if (!PyArg_ParseTuple(args, "K:recompute", &documentId)) {
        return nullptr;
    }
    try {
        const std::uint64_t id = asId(documentId);
        if (!fcandroid::CadCore::instance().recompute(id)) {
            const std::string error = fcandroid::CadCore::instance().lastError(id);
            PyErr_SetString(PyExc_RuntimeError, error.c_str());
            return nullptr;
        }
        Py_RETURN_TRUE;
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyLastError(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    if (!PyArg_ParseTuple(args, "K:last_error", &documentId)) {
        return nullptr;
    }
    try {
        const std::string value = fcandroid::CadCore::instance().lastError(asId(documentId));
        return PyUnicode_FromStringAndSize(value.data(), static_cast<Py_ssize_t>(value.size()));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pySummary(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    if (!PyArg_ParseTuple(args, "K:summary", &documentId)) {
        return nullptr;
    }
    try {
        const std::string value = fcandroid::CadCore::instance().documentSummary(asId(documentId));
        return PyUnicode_FromStringAndSize(value.data(), static_cast<Py_ssize_t>(value.size()));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyObjectIdByName(PyObject*, PyObject* args) {
    unsigned long long documentId = 0;
    const char* name = nullptr;
    if (!PyArg_ParseTuple(args, "Ks:object_id_by_name", &documentId, &name)) {
        return nullptr;
    }
    try {
        return PyLong_FromUnsignedLongLong(
            fcandroid::CadCore::instance().objectIdByName(asId(documentId), name));
    } catch (...) {
        setPythonErrorFromCurrentException();
        return nullptr;
    }
}

PyObject* pyKernelInfo(PyObject*, PyObject*) {
    std::ostringstream output;
    output << "FreeCAD Android scripting bridge / OCCT "
           << OCC_VERSION_MAJOR << "." << OCC_VERSION_MINOR << "."
           << OCC_VERSION_MAINTENANCE;
    return PyUnicode_FromString(output.str().c_str());
}

PyMethodDef moduleMethods[] = {
    {"reset", pyReset, METH_NOARGS, "Reset all native CAD documents."},
    {"create_document", pyCreateDocument, METH_VARARGS, "Create a native CAD document."},
    {"close_document", pyCloseDocument, METH_VARARGS, "Close a native CAD document."},
    {"set_active_document", pySetActiveDocument, METH_VARARGS, "Set the active document handle."},
    {"active_document", pyActiveDocument, METH_NOARGS, "Return the active document handle."},
    {"add_box", pyAddBox, METH_VARARGS, "Add a parametric box."},
    {"add_cylinder", pyAddCylinder, METH_VARARGS, "Add a parametric cylinder."},
    {"add_sphere", pyAddSphere, METH_VARARGS, "Add a parametric sphere."},
    {"add_cone", pyAddCone, METH_VARARGS, "Add a parametric cone."},
    {"add_torus", pyAddTorus, METH_VARARGS, "Add a parametric torus."},
    {"add_fuse", pyAddFuse, METH_VARARGS, "Add a BRep fuse."},
    {"add_cut", pyAddCut, METH_VARARGS, "Add a BRep cut."},
    {"add_common", pyAddCommon, METH_VARARGS, "Add a BRep common operation."},
    {"set_parameter", pySetParameter, METH_VARARGS, "Set a primitive parameter."},
    {"set_boolean_operands", pySetBooleanOperands, METH_VARARGS, "Set boolean operands."},
    {"set_placement", pySetPlacement, METH_VARARGS, "Set object placement."},
    {"set_visibility", pySetVisibility, METH_VARARGS, "Set object visibility."},
    {"recompute", pyRecompute, METH_VARARGS, "Recompute a document."},
    {"last_error", pyLastError, METH_VARARGS, "Return the last recompute error."},
    {"summary", pySummary, METH_VARARGS, "Return a document summary."},
    {"object_id_by_name", pyObjectIdByName, METH_VARARGS, "Find an object handle by name."},
    {"kernel_info", pyKernelInfo, METH_NOARGS, "Return native kernel information."},
    {nullptr, nullptr, 0, nullptr}
};

PyModuleDef moduleDefinition = {
    PyModuleDef_HEAD_INIT,
    "_freecad_native",
    "Android native document and OpenCASCADE bridge.",
    -1,
    moduleMethods,
    nullptr,
    nullptr,
    nullptr,
    nullptr
};

} // namespace

namespace fcandroid {

std::uint64_t activePythonDocumentId() {
    return gActiveDocumentId.load();
}

void resetPythonDocumentState() {
    gActiveDocumentId.store(0U);
}

} // namespace fcandroid

PyMODINIT_FUNC PyInit__freecad_native() {
    return PyModule_Create(&moduleDefinition);
}
