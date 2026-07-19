# FreeCADBase Android runtime

This branch integrates a verified subset of the upstream FreeCAD 1.1.1 Base library into the Android native core.

## Hardened runtime 0.10

- Native recompute follows boolean dependencies and commits shapes atomically.
- Vectors, rotations, placements and primitive dimensions reject non-finite or invalid values.
- Expressions are size-bounded, dependency-ordered, cycle-checked and rolled back on failure.
- Closed documents and removed objects fail fast; referenced objects cannot be deleted.
- Native documents, objects, names, prism profiles, custom properties and undo history have explicit limits.

The runtime remains headless and intentionally excludes the original FreeCAD GUI so it can serve as the engine for SolidFreeCAD.

## FCStd object editor 0.11

- Parses `Document.xml` and `GuiDocument.xml` with external XML entities disabled.
- Maps each `Shape` BREP to its FreeCAD object instead of displaying every archive payload.
- Resolves internal `App::Link`/`XLink` instances used by FreeCAD assemblies.
- Honors object visibility and `Placement` when generating the mobile preview.
- Edits labels, visibility and XYZ placement for generic shape objects.
- Saves a new FCStd while preserving workbench-specific properties and unknown archive entries.
- Validates the format assumptions against official PartDesign, Assembly, BIM and FEM examples from FreeCAD 1.1.1.

Parametric recomputation of arbitrary Sketcher, PartDesign, BIM, FEM and external-workbench objects remains a later milestone; version 0.11 preserves those objects losslessly when applying universal metadata edits.
