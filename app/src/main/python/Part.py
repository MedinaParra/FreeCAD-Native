"""Focused Part workbench compatibility layer for FreeCAD Android.

The factories build lightweight parametric specifications. They can be assigned to
Part::Feature.Shape or combined with fuse/cut/common and displayed with Part.show().
All actual geometry is still evaluated by the native OpenCASCADE core.
"""

from __future__ import annotations

from dataclasses import dataclass, field
import math
import FreeCAD as App


def _finite(value, name):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{name} must be finite")
    return result


def _positive(value, name):
    result = _finite(value, name)
    if result <= 0.0:
        raise ValueError(f"{name} must be positive")
    return result


def _nonnegative(value, name):
    result = _finite(value, name)
    if result < 0.0:
        raise ValueError(f"{name} must be non-negative")
    return result


@dataclass
class Shape:
    _kind: str
    _params: tuple = field(default_factory=tuple)
    _placement: App.Placement = field(default_factory=App.Placement)
    _left: "Shape | None" = None
    _right: "Shape | None" = None

    @property
    def Placement(self):
        return self._placement

    @Placement.setter
    def Placement(self, value):
        if not isinstance(value, App.Placement):
            raise TypeError("Shape.Placement must be a FreeCAD.Placement")
        self._placement = value.copy()

    def copy(self):
        return Shape(
            self._kind,
            tuple(self._params),
            self._placement.copy(),
            self._left.copy() if self._left is not None else None,
            self._right.copy() if self._right is not None else None,
        )

    def translate(self, vector):
        if not isinstance(vector, App.Vector):
            vector = App.Vector(*vector)
        base = self._placement.Base
        self._placement.Base = App.Vector(
            base.x + vector.x,
            base.y + vector.y,
            base.z + vector.z,
        )
        return None

    def translated(self, vector):
        result = self.copy()
        result.translate(vector)
        return result

    @property
    def ShapeType(self):
        return "Solid"

    def isValid(self):
        try:
            _validate_shape(self, set(), set())
            return True
        except (TypeError, ValueError):
            return False

    def isClosed(self):
        return True

    def fuse(self, other):
        return _boolean_shape("Part::Fuse", self, other)

    def cut(self, other):
        return _boolean_shape("Part::Cut", self, other)

    def common(self, other):
        return _boolean_shape("Part::Common", self, other)

    def removeSplitter(self):
        # The mobile core currently returns the boolean result without a separate
        # refine pass. Keeping this method makes common generated macros runnable.
        return self

    def isNull(self):
        return False

    def __repr__(self):
        if self._left is not None:
            return f"Shape({self._kind}, left={self._left!r}, right={self._right!r})"
        return f"Shape({self._kind}, {self._params})"


def _boolean_shape(kind, left, right):
    if not isinstance(left, Shape) or not isinstance(right, Shape):
        raise TypeError("Boolean operations require two Part.Shape values")
    if left is right:
        raise ValueError("Boolean operations require two distinct shapes")
    _validate_shape(left, set(), set())
    _validate_shape(right, set(), set())
    return Shape(kind, (), App.Placement(), left, right)


def _validate_shape(shape, visiting, visited):
    if not isinstance(shape, Shape):
        raise TypeError("Expected a Part.Shape")
    marker = id(shape)
    if marker in visiting:
        raise ValueError("Part.Shape dependency cycle detected")
    if marker in visited:
        return
    visiting.add(marker)
    if shape._kind in {"Part::Fuse", "Part::Cut", "Part::Common"}:
        if shape._left is shape._right:
            raise ValueError("Boolean operations require two distinct shapes")
        _validate_shape(shape._left, visiting, visited)
        _validate_shape(shape._right, visiting, visited)
    elif shape._kind == "Part::Box":
        for value, name in zip(shape._params, ("length", "width", "height")):
            _positive(value, name)
    elif shape._kind == "Part::Cylinder":
        _positive(shape._params[0], "radius")
        _positive(shape._params[1], "height")
    elif shape._kind == "Part::Sphere":
        _positive(shape._params[0], "radius")
    elif shape._kind == "Part::Cone":
        _positive(shape._params[0], "radius1")
        _nonnegative(shape._params[1], "radius2")
        _positive(shape._params[2], "height")
    elif shape._kind == "Part::Torus":
        major = _positive(shape._params[0], "radius1")
        minor = _positive(shape._params[1], "radius2")
        if minor >= major:
            raise ValueError("radius2 must be smaller than radius1")
    elif shape._kind == "Part::Prism":
        if len(shape._params) != 4:
            raise ValueError("Invalid prism specification")
        coordinates, dx, dy, dz = shape._params
        coordinates = tuple(coordinates)
        if len(coordinates) < 9 or len(coordinates) % 3:
            raise ValueError("A prism requires at least three XYZ points")
        for coordinate in coordinates:
            _finite(coordinate, "prism coordinate")
        extrusion = App.Vector(dx, dy, dz)
        if extrusion.Length <= 1.0e-12:
            raise ValueError("Prism extrusion vector must not be null")
    else:
        raise ValueError(f"Unsupported shape kind {shape._kind!r}")
    visiting.remove(marker)
    visited.add(marker)


def _placement(base):
    if base is None:
        return App.Placement()
    if not isinstance(base, App.Vector):
        base = App.Vector(*base)
    return App.Placement(base, App.Rotation())


def _require_positive_z(direction, operation):
    if direction is None:
        return
    direction = direction if isinstance(direction, App.Vector) else App.Vector(*direction)
    if abs(direction.x) > 1.0e-12 or abs(direction.y) > 1.0e-12 or direction.z <= 0:
        raise NotImplementedError(
            f"{operation} currently supports the positive Z direction only"
        )


def makeBox(length, width, height, pnt=None, dir=None):
    _require_positive_z(dir, "makeBox")
    return Shape(
        "Part::Box",
        (_positive(length, "length"), _positive(width, "width"), _positive(height, "height")),
        _placement(pnt),
    )


def makeCylinder(radius, height, pnt=None, dir=None, angle=360.0):
    angle = _finite(angle, "angle")
    if angle != 360.0:
        raise NotImplementedError("Partial cylinders are not supported yet")
    _require_positive_z(dir, "makeCylinder")
    return Shape(
        "Part::Cylinder",
        (_positive(radius, "radius"), _positive(height, "height")),
        _placement(pnt),
    )


def makeSphere(radius, pnt=None, angle1=-90.0, angle2=90.0, angle3=360.0):
    angles = (
        _finite(angle1, "angle1"),
        _finite(angle2, "angle2"),
        _finite(angle3, "angle3"),
    )
    if angles != (-90.0, 90.0, 360.0):
        raise NotImplementedError("Partial spheres are not supported yet")
    return Shape("Part::Sphere", (_positive(radius, "radius"),), _placement(pnt))


def makeCone(radius1, radius2, height, pnt=None, dir=None, angle=360.0):
    angle = _finite(angle, "angle")
    if angle != 360.0:
        raise NotImplementedError("Partial cones are not supported yet")
    _require_positive_z(dir, "makeCone")
    return Shape(
        "Part::Cone",
        (_positive(radius1, "radius1"), _nonnegative(radius2, "radius2"), _positive(height, "height")),
        _placement(pnt),
    )


def makeTorus(radius1, radius2, pnt=None, dir=None, angle1=0.0, angle2=360.0, angle3=360.0):
    _require_positive_z(dir, "makeTorus")
    if (_finite(angle1, "angle1"), _finite(angle2, "angle2"), _finite(angle3, "angle3")) != (0.0, 360.0, 360.0):
        raise NotImplementedError("Partial toruses are not supported yet")
    major = _positive(radius1, "radius1")
    minor = _positive(radius2, "radius2")
    if minor >= major:
        raise ValueError("radius2 must be smaller than radius1")
    return Shape(
        "Part::Torus",
        (major, minor),
        _placement(pnt),
    )


def _materialize(shape, document, name, sequence):
    if not isinstance(shape, Shape):
        raise TypeError("Part.show expects a Part.Shape")
    _validate_shape(shape, set(), set())

    if shape._kind in {"Part::Fuse", "Part::Cut", "Part::Common"}:
        left = _materialize(shape._left, document, f"{name}_Base", sequence)
        right = _materialize(shape._right, document, f"{name}_Tool", sequence)
        obj = document.addObject(shape._kind, name)
        obj.Base = left
        obj.Tool = right
        return obj

    obj = document.addObject("Part::Feature", name)
    obj.Shape = shape
    return obj


def show(shape, name="Shape"):
    """Display a shape using the active document, like FreeCAD Part.show()."""
    document = App.ActiveDocument or App.newDocument("Unnamed")
    obj = _materialize(shape, document, str(name or "Shape"), [0])
    document.recompute()
    return obj


def _install_boolean_visibility_policy():
    """Match FreeCAD's default rule: boolean inputs are hidden after recompute."""
    if getattr(App.Document, "_android_boolean_visibility_installed", False):
        return

    original_recompute = App.Document.recompute
    boolean_types = {"Part::Fuse", "Part::Cut", "Part::Common"}

    def recompute_with_boolean_visibility(document):
        consumed_objects = set()
        for obj in document.Objects:
            if obj.TypeId not in boolean_types:
                continue
            if obj.Base is not None:
                consumed_objects.add(obj.Base)
            if obj.Tool is not None:
                consumed_objects.add(obj.Tool)

        for obj in consumed_objects:
            object.__setattr__(obj, "_visibility", False)
            if obj._id:
                App._native.set_visibility(document._id, obj._id, False)

        return original_recompute(document)

    App.Document.recompute = recompute_with_boolean_visibility
    App.Document._android_boolean_visibility_installed = True


_install_boolean_visibility_policy()

Vector = App.Vector

__all__ = [
    "Shape",
    "Vector",
    "makeBox",
    "makeCylinder",
    "makeSphere",
    "makeCone",
    "makeTorus",
    "show",
]
