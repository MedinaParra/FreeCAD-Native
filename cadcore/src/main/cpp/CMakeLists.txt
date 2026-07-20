cmake_minimum_required(VERSION 3.22.1)
project(freecad_android_bridge LANGUAGES CXX)

add_library(freecad_android_bridge SHARED freecad_android_bridge.cpp)
target_compile_features(freecad_android_bridge PRIVATE cxx_std_17)
target_compile_options(freecad_android_bridge PRIVATE -Wall -Wextra -Wpedantic)
target_link_options(freecad_android_bridge PRIVATE -Wl,--no-undefined)

find_library(log-lib log)
target_link_libraries(freecad_android_bridge PRIVATE ${log-lib})

set(OCCT_ANDROID_ROOT "" CACHE PATH "Installed OCCT Android runtime root")
if(OCCT_ANDROID_ROOT AND EXISTS "${OCCT_ANDROID_ROOT}/include/opencascade/Standard.hxx")
    set(OCCT_DIRECT_TOOLKITS
            TKDESTEP
            TKMesh
            TKTopAlgo
            TKBRep
            TKMath
            TKernel)
    set(OCCT_DIRECT_LIBS "")
    foreach(TOOLKIT IN LISTS OCCT_DIRECT_TOOLKITS)
        set(TOOLKIT_PATH "${OCCT_ANDROID_ROOT}/lib/lib${TOOLKIT}.so")
        if(NOT EXISTS "${TOOLKIT_PATH}")
            message(FATAL_ERROR "Required OCCT toolkit is missing: ${TOOLKIT_PATH}")
        endif()
        list(APPEND OCCT_DIRECT_LIBS "${TOOLKIT_PATH}")
    endforeach()

    # OCCT 7.9 exposes opencascade::handle while newer source variants may use
    # a short occ alias. The preprocessor alias keeps one bridge source compatible.
    target_compile_definitions(freecad_android_bridge PRIVATE
            FREECAD_OCC_AVAILABLE=1
            occ=opencascade)
    target_include_directories(freecad_android_bridge PRIVATE
            "${OCCT_ANDROID_ROOT}/include/opencascade")
    target_link_libraries(freecad_android_bridge PRIVATE ${OCCT_DIRECT_LIBS})
    message(STATUS "Building STEP-enabled bridge with minimal OCCT roots: ${OCCT_DIRECT_TOOLKITS}")
else()
    message(STATUS "Building diagnostic bridge without OCCT STEP kernel")
endif()
