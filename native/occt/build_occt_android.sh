#!/usr/bin/env bash
set -euo pipefail

OCCT_REF="${OCCT_REF:-V8_0_0}"
OCCT_EXPECTED_PREFIX="${OCCT_EXPECTED_PREFIX:-d3056ef}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-24}"
JOBS="${JOBS:-2}"
ROOT="${ROOT:-$(pwd)/build/occt-android}"
SOURCE="$ROOT/source"
BUILD="$ROOT/build-$ANDROID_ABI"
INSTALL="$ROOT/install-$ANDROID_ABI"

if [[ -z "${ANDROID_NDK_HOME:-}" || ! -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]]; then
  echo "ANDROID_NDK_HOME must point to an installed Android NDK" >&2
  exit 2
fi

mkdir -p "$ROOT"
if [[ ! -d "$SOURCE/.git" ]]; then
  rm -rf "$SOURCE"
  git clone --depth 1 --branch "$OCCT_REF" https://github.com/Open-Cascade-SAS/OCCT.git "$SOURCE"
fi

ACTUAL_COMMIT=$(git -C "$SOURCE" rev-parse HEAD)
if [[ "$ACTUAL_COMMIT" != "$OCCT_EXPECTED_PREFIX"* ]]; then
  echo "Unexpected OCCT commit: $ACTUAL_COMMIT (expected prefix $OCCT_EXPECTED_PREFIX)" >&2
  exit 3
fi

rm -rf "$BUILD" "$INSTALL"
cmake -S "$SOURCE" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ANDROID_ABI" \
  -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=OFF \
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE=OFF \
  -DCMAKE_C_FLAGS_RELEASE="-O2 -DNDEBUG" \
  -DCMAKE_CXX_FLAGS_RELEASE="-O2 -DNDEBUG" \
  -DCMAKE_INSTALL_PREFIX="$INSTALL" \
  -DINSTALL_DIR="$INSTALL" \
  -DINSTALL_DIR_LAYOUT=Unix \
  -DINSTALL_DIR_WITH_VERSION=OFF \
  -DBUILD_LIBRARY_TYPE=Shared \
  -DBUILD_CPP_STANDARD=C++17 \
  -DBUILD_OPT_PROFILE=Default \
  -DBUILD_RELEASE_DISABLE_EXCEPTIONS=OFF \
  -DBUILD_MODULE_FoundationClasses=ON \
  -DBUILD_MODULE_ModelingData=ON \
  -DBUILD_MODULE_ModelingAlgorithms=ON \
  -DBUILD_MODULE_DataExchange=ON \
  -DBUILD_MODULE_Visualization=OFF \
  -DBUILD_MODULE_ApplicationFramework=OFF \
  -DBUILD_MODULE_Draw=OFF \
  -DBUILD_GTEST=OFF \
  -DBUILD_DOC_Overview=OFF \
  -DBUILD_DOC_RefMan=OFF \
  -DBUILD_RESOURCES=OFF \
  -DBUILD_USE_PCH=OFF \
  -DBUILD_YACCLEX=OFF \
  -DUSE_FREETYPE=OFF \
  -DUSE_TK=OFF \
  -DUSE_TCL=OFF \
  -DUSE_TBB=OFF \
  -DUSE_VTK=OFF \
  -DUSE_FREEIMAGE=OFF \
  -DUSE_FFMPEG=OFF \
  -DUSE_RAPIDJSON=OFF \
  -DUSE_DRACO=OFF \
  -DUSE_OPENGL=OFF \
  -DUSE_GLES2=OFF \
  -DUSE_XLIB=OFF

cmake --build "$BUILD" --parallel "$JOBS"
cmake --install "$BUILD"

find "$INSTALL" -type f -name '*.so' -print | sort > "$INSTALL/LIBRARIES.txt"
for required in TKernel TKMath TKBRep TKMesh TKDESTEP; do
  if ! grep -q "/lib${required}\.so$" "$INSTALL/LIBRARIES.txt"; then
    echo "Required OCCT library lib${required}.so was not installed" >&2
    exit 4
  fi
done

cat > "$INSTALL/BUILD_INFO.txt" <<INFO
OCCT_REF=$OCCT_REF
OCCT_COMMIT=$ACTUAL_COMMIT
ANDROID_ABI=$ANDROID_ABI
ANDROID_PLATFORM=$ANDROID_PLATFORM
ANDROID_NDK_HOME=$ANDROID_NDK_HOME
BUILD_OPT_PROFILE=Default
LTO=OFF
INFO

echo "OCCT Android runtime installed at $INSTALL"
