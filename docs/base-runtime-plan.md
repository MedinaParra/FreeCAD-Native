# FreeCADBase Android runtime

This branch integrates a verified subset of the upstream FreeCAD 1.1.1 Base library into the Android native core.

## Hardened runtime 0.10

- Native recompute follows boolean dependencies and commits shapes atomically.
- Vectors, rotations, placements and primitive dimensions reject non-finite or invalid values.
- Expressions are size-bounded, dependency-ordered, cycle-checked and rolled back on failure.
- Closed documents and removed objects fail fast; referenced objects cannot be deleted.
- Native documents, objects, names, prism profiles, custom properties and undo history have explicit limits.

The runtime remains headless and intentionally excludes the original FreeCAD GUI so it can serve as the engine for SolidFreeCAD.
