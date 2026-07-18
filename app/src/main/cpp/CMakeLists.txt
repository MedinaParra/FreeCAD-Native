cmake_minimum_required(VERSION 3.22.1)
project(freecad_android_core LANGUAGES CXX)

if(NOT DEFINED OCCT_INSTALL_ROOT)
    message(FATAL_ERROR "OCCT_INSTALL_ROOT was not provided. Build OCCT first with the repository workflow or scripts.")
endif()
if(NOT DEFINED PYTHON_INSTALL_ROOT)
    message(FATAL_ERROR "PYTHON_INSTALL_ROOT was not provided. Build CPython first with the repository workflow.")
endif()

set(OCCT_ABI_ROOT "${OCCT_INSTALL_ROOT}/${ANDROID_ABI}")
set(OCCT_INCLUDE_DIR "${OCCT_ABI_ROOT}/include/opencascade")
set(OCCT_LIBRARY_DIR "${OCCT_ABI_ROOT}/lib")
set(PYTHON_ABI_ROOT "${PYTHON_INSTALL_ROOT}/${ANDROID_ABI}")
set(PYTHON_INCLUDE_DIR "${PYTHON_ABI_ROOT}/include/python3.14")
set(PYTHON_LIBRARY "${PYTHON_ABI_ROOT}/lib/libpython3.14.so")

if(NOT EXISTS "${OCCT_INCLUDE_DIR}/Standard_Version.hxx")
    message(FATAL_ERROR "OpenCASCADE headers not found for ABI ${ANDROID_ABI}: ${OCCT_INCLUDE_DIR}")
endif()
if(NOT EXISTS "${PYTHON_INCLUDE_DIR}/Python.h" OR NOT EXISTS "${PYTHON_LIBRARY}")
    message(FATAL_ERROR "CPython runtime not found for ABI ${ANDROID_ABI}")
endif()

add_library(Python::Runtime SHARED IMPORTED)
set_target_properties(Python::Runtime PROPERTIES IMPORTED_LOCATION "${PYTHON_LIBRARY}")

set(OCCT_TOOLKITS
    TKernel TKMath TKG2d TKG3d TKGeomBase TKBRep TKGeomAlgo TKTopAlgo
    TKPrim TKBO TKBool TKHLR TKFillet TKOffset TKFeat TKMesh TKXMesh
    TKShHealing TKDE TKXSBase TKDESTEP
)
set(OCCT_IMPORTED_TARGETS)
foreach(TOOLKIT IN LISTS OCCT_TOOLKITS)
    set(TOOLKIT_FILE "${OCCT_LIBRARY_DIR}/lib${TOOLKIT}.so")
    if(NOT EXISTS "${TOOLKIT_FILE}")
        message(FATAL_ERROR "Required OCCT toolkit is missing: ${TOOLKIT_FILE}")
    endif()
    add_library(OCCT::${TOOLKIT} SHARED IMPORTED)
    set_target_properties(OCCT::${TOOLKIT} PROPERTIES IMPORTED_LOCATION "${TOOLKIT_FILE}")
    list(APPEND OCCT_IMPORTED_TARGETS OCCT::${TOOLKIT})
endforeach()

add_library(
    freecad_android_core SHARED
    CadCore.cpp CadCoreScripting.cpp OcctMesher.cpp StepIo.cpp
    PyFreeCadModule.cpp PythonRuntime.cpp
    jni_bridge.cpp jni_python_bridge.cpp jni_step_bridge.cpp
)

target_compile_features(freecad_android_core PRIVATE cxx_std_17)
target_compile_options(freecad_android_core PRIVATE
    -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
target_include_directories(freecad_android_core PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}" "${OCCT_INCLUDE_DIR}" "${PYTHON_INCLUDE_DIR}")

find_library(ANDROID_LOG_LIBRARY log REQUIRED)
find_library(ANDROID_LIBRARY android REQUIRED)
find_library(DL_LIBRARY dl REQUIRED)
target_link_libraries(freecad_android_core PRIVATE
    Python::Runtime ${OCCT_IMPORTED_TARGETS}
    ${ANDROID_LOG_LIBRARY} ${ANDROID_LIBRARY} ${DL_LIBRARY})
