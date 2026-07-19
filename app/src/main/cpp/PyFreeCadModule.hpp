#pragma once

#include <Python.h>

#include <cstdint>

namespace fcandroid {

std::uint64_t activePythonDocumentId();
void resetPythonDocumentState();

} // namespace fcandroid

PyMODINIT_FUNC PyInit__freecad_native();
