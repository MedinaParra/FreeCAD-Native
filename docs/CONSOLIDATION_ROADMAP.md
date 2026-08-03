# CAD platform consolidation roadmap

## Canonical role

This repository is the canonical Android CAD platform for the MedinaParra projects. It owns the native geometry, STEP and FCStd capabilities shared by product applications.

`FC` remains a visual macro authoring application. `SolidFreeCAD` may become a product interface. `FreeCAD-Android` and `FreeCAD-sin-Error` are historical precursors and should not receive new kernel work.

## Platform boundaries

The platform is responsible for:

- OpenCASCADE runtime packaging for supported Android ABIs;
- BRep document handles and explicit native-resource lifetime;
- STEP import, tessellation, transforms and export;
- FCStd document inspection and non-destructive universal-property editing;
- versioned AAR releases with hashes and dependency manifests;
- stable Java/Kotlin/JNI contracts for consuming applications;
- device-level memory, thermal and long-session validation.

The platform is not responsible for product-specific capture flows, pulley recognition, workshop catalogs or game interfaces.

## Active technical lines

- `product/android-cad-core-sdk-v1`: reusable STEP SDK.
- `native-base-runtime`: FCStd/Base runtime and object model.
- `agent/native-step-session-v12`: persistent STEP editing sessions.
- historical OCCT, CPython and STEP I/O branches: reference only until their capabilities are represented in a canonical branch.

## Consolidation rules

1. Do not merge branches solely because their README reports success.
2. A feature is accepted only when its current workflow is green and the APK/AAR is tested on physical hardware where required.
3. Native binaries must be produced reproducibly or consumed from a versioned, hashed release.
4. Product repositories consume the public SDK contract instead of copying JNI or OCCT files.
5. Failed experimental workflows remain historical evidence, not release dependencies.

## Current blockers

- the CPython workflow calls the Android build path without the required configure phase;
- the STEP I/O workflow builds the OCCT toolkits but fails during final APK compilation;
- persistent STEP editing is not yet connected to the SolidFreeCAD user interface;
- physical long-session import, tessellation, edit, save and release tests remain incomplete;
- face identifiers are revision-local rather than persistent topological names.

## Next platform milestone

Publish one versioned Android CAD SDK candidate that includes:

- clean arm64 build from an empty cache;
- STEP read, tessellate, rigid transform and write;
- explicit handle release and leak test;
- face metadata and planar pull preview/commit/rollback;
- sample Android application;
- API documentation and compatibility matrix;
- SHA-256 and native dependency manifest;
- physical-device validation record.

## Repository migration policy

- `FreeCAD-Android`: legacy precursor; migrate only unique tested components.
- `FreeCAD-sin-Error`: duplicate precursor; no new development.
- `FC`: keep macro generation and domain-specific modeling workflows outside this kernel.
- `SolidFreeCAD`: integrate as a frontend consuming this platform contract.
- `SolidFreeCAD-Desktop`: independent desktop frontend over official FreeCAD, not an Android-kernel fork.
