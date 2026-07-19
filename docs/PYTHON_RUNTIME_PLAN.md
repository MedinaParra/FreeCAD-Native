# Embedded Python Runtime Phase

## Objective

Add a real embedded CPython runtime to the Android OCCT core and execute FreeCAD-style macros locally on the device.

## Runtime

- CPython 3.14.6 built from the official CPython Android source tree.
- Android targets: `arm-linux-androideabi` and `aarch64-linux-android`.
- `libpython3.14.so` and dependent Android libraries are packaged inside the APK.
- The Python standard library is packaged as ABI-specific assets and extracted to the app private files directory.
- Python executes in embedded mode inside the Android application process.

## Compatibility modules

The first scripting layer is split into two parts:

1. `_freecad_native`: a built-in CPython extension module implemented in C++ and connected directly to `CadCore`.
2. `FreeCAD.py` and `Part.py`: Python compatibility modules exposing a FreeCAD-like API backed by `_freecad_native`.

This lets the Python-facing API evolve without rebuilding the full native kernel for every small compatibility change.

## Initial macro surface

```python
import FreeCAD as App
import Part

doc = App.newDocument("Model")
box = doc.addObject("Part::Box", "Base")
box.Length = 80
box.Width = 50
box.Height = 12

hole = doc.addObject("Part::Cylinder", "Hole")
hole.Radius = 7
hole.Height = 20
hole.Placement.Base = App.Vector(20, 25, -4)

cut = doc.addObject("Part::Cut", "Result")
cut.Base = box
cut.Tool = hole

doc.recompute()
```

## Initial supported API

- `FreeCAD.newDocument(name)`
- `FreeCAD.closeDocument(name_or_document)`
- `FreeCAD.activeDocument()`
- `FreeCAD.Vector(x, y, z)`
- `FreeCAD.Rotation(qx, qy, qz, qw)`
- `FreeCAD.Placement(base, rotation)`
- `Document.addObject(type_name, name)`
- `Document.getObject(name)`
- `Document.Objects`
- `Document.recompute()`
- Box, Cylinder, Sphere, Cone and Torus properties
- Boolean `Base` and `Tool` links
- `Placement` and `Visibility`

## Explicitly deferred

- Full upstream `FreeCADBase` and `FreeCADApp` linkage
- FCStd persistence
- STEP/IGES import
- Sketcher
- GUI workbenches
- arbitrary third-party Python wheels

This phase creates the scripting/runtime bridge required before upstream FreeCAD application libraries can be introduced safely.
