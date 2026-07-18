cmake_minimum_required(VERSION 3.22.1)
project(freecad_android_core LANGUAGES CXX)

add_library(freecad_android_core SHARED native_box.cpp)

target_compile_features(freecad_android_core PRIVATE cxx_std_17)
target_compile_options(freecad_android_core PRIVATE -Wall -Wextra -Wpedantic)

find_library(log-lib log)
target_link_libraries(freecad_android_core PRIVATE ${log-lib})
