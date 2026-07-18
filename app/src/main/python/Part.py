"""Focused Part workbench compatibility layer for FreeCAD Android.

The shape factories return lightweight specifications. Assign them to the Shape
property of a `Part::Feature` object to materialize them in the native OCCT core.
"""

from __future__ import annotations

from dataclasses import dataclass

import FreeCAD as App


@dataclass(frozen=True)
class Shape:
    _kind: str
    _params: tuple
    _placement: App.Placement

    def __repr__(self):
        return f"Shape({self._kind}, {self._params})"


def _placement(base):
    if base is None:
        return App.Placement()
    if not isinstance(base, App.Vector):
        base = App.Vector(*base)
    return App.Placement(base, App.Rotation())


def makeBox(length, width, height, pnt=None, dir=None):
    if dir is not None:
        direction = dir if isinstance(dir, App.Vector) else App.Vector(*dir)
        if abs(direction.x) > 1.0e-12 or abs(direction.y) > 1.0e-12 or direction.z <= 0:
            raise NotImplementedError("makeBox currently supports the positive Z direction only")
    return Shape(
        "Part::Box",
        (float(length), float(width), float(height)),
        _placement(pnt),
    )


def makeCylinder(radius, height, pnt=None, dir=None, angle=360.0):
    if float(angle) != 360.0:
        raise NotImplementedError("Partial cylinders are not supported yet")
    if dir is not None:
        direction = dir if isinstance(dir, App.Vector) else App.Vector(*dir)
        if abs(direction.x) > 1.0e-12 or abs(direction.y) > 1.0e-12 or direction.z <= 0:
            raise NotImplementedError("makeCylinder currently supports the positive Z direction only")
    return Shape(
        "Part::Cylinder",
        (float(radius), float(height)),
        _placement(pnt),
    )


def makeSphere(radius, pnt=None):
    return Shape("Part::Sphere", (float(radius),), _placement(pnt))


def makeCone(radius1, radius2, height, pnt=None, dir=None, angle=360.0):
    if float(angle) != 360.0:
        raise NotImplementedError("Partial cones are not supported yet")
    if dir is not None:
        direction = dir if isinstance(dir, App.Vector) else App.Vector(*dir)
        if abs(direction.x) > 1.0e-12 or abs(direction.y) > 1.0e-12 or direction.z <= 0:
            raise NotImplementedError("makeCone currently supports the positive Z direction only")
    return Shape(
        "Part::Cone",
        (float(radius1), float(radius2), float(height)),
        _placement(pnt),
    )


def makeTorus(radius1, radius2, pnt=None):
    return Shape(
        "Part::Torus",
        (float(radius1), float(radius2)),
        _placement(pnt),
    )


Vector = App.Vector

__all__ = [
    "Shape",
    "Vector",
    "makeBox",
    "makeCylinder",
    "makeSphere",
    "makeCone",
    "makeTorus",
]
