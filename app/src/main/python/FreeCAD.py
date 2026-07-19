"""FreeCAD-compatible scripting facade for the Android OCCT core.

This is intentionally a focused compatibility layer. It exposes the document,
object, placement and primitive APIs required by the first mobile macro phase,
while all geometry is evaluated by the native OpenCASCADE kernel.
"""

from __future__ import annotations

import math
import sys
from typing import Any

import _freecad_native as _native


def _finite_float(value, label="value"):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{label} must be finite")
    return result


class Vector:
    __slots__ = ("_x", "_y", "_z")

    def __init__(self, x: float = 0.0, y: float = 0.0, z: float = 0.0):
        self._x = _finite_float(x, "Vector.x")
        self._y = _finite_float(y, "Vector.y")
        self._z = _finite_float(z, "Vector.z")

    @property
    def x(self):
        return self._x

    @x.setter
    def x(self, value):
        self._x = _finite_float(value, "Vector.x")

    @property
    def y(self):
        return self._y

    @y.setter
    def y(self, value):
        self._y = _finite_float(value, "Vector.y")

    @property
    def z(self):
        return self._z

    @z.setter
    def z(self, value):
        self._z = _finite_float(value, "Vector.z")

    @property
    def X(self):
        return self.x

    @X.setter
    def X(self, value):
        self.x = value

    @property
    def Y(self):
        return self.y

    @Y.setter
    def Y(self, value):
        self.y = value

    @property
    def Z(self):
        return self.z

    @Z.setter
    def Z(self, value):
        self.z = value

    @property
    def Length(self):
        return math.sqrt(self.x * self.x + self.y * self.y + self.z * self.z)

    def copy(self):
        return Vector(self.x, self.y, self.z)

    def __iter__(self):
        yield self.x
        yield self.y
        yield self.z

    def __repr__(self):
        return f"Vector ({self.x:g}, {self.y:g}, {self.z:g})"


class Rotation:
    __slots__ = ("Q",)

    def __init__(self, *args):
        if not args:
            self.Q = (0.0, 0.0, 0.0, 1.0)
        elif len(args) == 4:
            qx, qy, qz, qw = (
                _finite_float(value, "Rotation quaternion") for value in args
            )
            norm = math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
            if norm <= 1.0e-15:
                raise ValueError("Rotation quaternion must not be null")
            self.Q = (qx / norm, qy / norm, qz / norm, qw / norm)
        elif len(args) == 2 and isinstance(args[0], Vector):
            axis = args[0]
            angle = math.radians(_finite_float(args[1], "Rotation angle")) * 0.5
            length = axis.Length
            if length <= 1.0e-15:
                raise ValueError("Rotation axis must not be null")
            scale = math.sin(angle) / length
            self.Q = (
                axis.x * scale,
                axis.y * scale,
                axis.z * scale,
                math.cos(angle),
            )
        else:
            raise TypeError("Rotation expects (), (qx, qy, qz, qw), or (axis, angleDegrees)")

    def __repr__(self):
        return "Rotation(" + ", ".join(f"{value:g}" for value in self.Q) + ")"


class Placement:
    __slots__ = ("_base", "_rotation", "_callback")

    def __init__(self, base: Vector | None = None, rotation: Rotation | None = None):
        self._base = (
            base.copy()
            if isinstance(base, Vector)
            else Vector(*base) if base is not None else Vector()
        )
        self._rotation = (
            Rotation(*rotation.Q)
            if isinstance(rotation, Rotation)
            else Rotation(*rotation) if rotation is not None else Rotation()
        )
        self._callback = None

    @property
    def Base(self):
        return self._base

    @Base.setter
    def Base(self, value):
        if not isinstance(value, Vector):
            value = Vector(*value)
        self._base = value.copy()
        self._notify()

    @property
    def Rotation(self):
        return self._rotation

    @Rotation.setter
    def Rotation(self, value):
        if not isinstance(value, Rotation):
            value = Rotation(*value)
        self._rotation = value
        self._notify()

    def _bind(self, callback):
        self._callback = callback
        return self

    def _notify(self):
        if self._callback is not None:
            self._callback()

    def copy(self):
        return Placement(self.Base, Rotation(*self.Rotation.Q))

    def __repr__(self):
        return f"Placement({self.Base!r}, {self.Rotation!r})"


class _ViewObject:
    __slots__ = ("_owner", "ShapeColor", "LineColor", "DisplayMode")

    def __init__(self, owner):
        self._owner = owner
        self.ShapeColor = (0.32, 0.62, 0.82)
        self.LineColor = (0.1, 0.1, 0.1)
        self.DisplayMode = "Flat Lines"

    @property
    def Visibility(self):
        return self._owner.Visibility

    @Visibility.setter
    def Visibility(self, value):
        self._owner.Visibility = bool(value)


_PRIMITIVES = {
    "Part::Box": ({"Length": 10.0, "Width": 10.0, "Height": 10.0}, "add_box"),
    "Part::Cylinder": ({"Radius": 5.0, "Height": 10.0}, "add_cylinder"),
    "Part::Sphere": ({"Radius": 5.0}, "add_sphere"),
    "Part::Cone": ({"Radius1": 5.0, "Radius2": 2.0, "Height": 10.0}, "add_cone"),
    "Part::Torus": ({"Radius1": 10.0, "Radius2": 2.0}, "add_torus"),
}

_PARAMETER_INDEX = {
    "Part::Box": {"Length": 0, "Width": 1, "Height": 2},
    "Part::Cylinder": {"Radius": 0, "Height": 1},
    "Part::Sphere": {"Radius": 0},
    "Part::Cone": {"Radius1": 0, "Radius2": 1, "Height": 2},
    "Part::Torus": {"Radius1": 0, "Radius2": 1},
}

_BOOLEAN_FUNCTIONS = {
    "Part::Fuse": "add_fuse",
    "Part::Cut": "add_cut",
    "Part::Common": "add_common",
}


class DocumentObject:
    _internal_names = {
        "_document",
        "_id",
        "_type_id",
        "_properties",
        "_placement",
        "_visibility",
        "_base_object",
        "_tool_object",
        "_shape_spec",
        "_removed",
        "Name",
        "Label",
        "ViewObject",
    }

    def __init__(self, document, type_id: str, name: str, native_id: int = 0):
        object.__setattr__(self, "_document", document)
        object.__setattr__(self, "_id", int(native_id))
        object.__setattr__(self, "_type_id", type_id)
        object.__setattr__(self, "Name", name)
        object.__setattr__(self, "Label", name)
        defaults = dict(_PRIMITIVES.get(type_id, ({}, None))[0])
        object.__setattr__(self, "_properties", defaults)
        placement = Placement()._bind(self._sync_placement)
        object.__setattr__(self, "_placement", placement)
        object.__setattr__(self, "_visibility", True)
        object.__setattr__(self, "_base_object", None)
        object.__setattr__(self, "_tool_object", None)
        object.__setattr__(self, "_shape_spec", None)
        object.__setattr__(self, "_removed", False)
        object.__setattr__(self, "ViewObject", _ViewObject(self))

    def _assert_live(self):
        if self._removed:
            raise RuntimeError(f"Object {self.Name!r} has been removed")
        self._document._assert_open()

    @property
    def TypeId(self):
        return self._type_id

    @property
    def Placement(self):
        return self._placement

    @Placement.setter
    def Placement(self, value):
        self._assert_live()
        if not isinstance(value, Placement):
            raise TypeError("Placement must be a FreeCAD.Placement")
        object.__setattr__(self, "_placement", value.copy()._bind(self._sync_placement))
        self._sync_placement()

    @property
    def Visibility(self):
        return self._visibility

    @Visibility.setter
    def Visibility(self, value):
        self._assert_live()
        object.__setattr__(self, "_visibility", bool(value))
        if self._id:
            _native.set_visibility(self._document._id, self._id, self._visibility)

    @property
    def Base(self):
        return self._base_object

    @Base.setter
    def Base(self, value):
        self._assert_live()
        if value is not None and not isinstance(value, DocumentObject):
            raise TypeError("Boolean Base must be a document object")
        if value is self:
            raise ValueError("A boolean object cannot use itself as Base")
        if value is not None and value._document is not self._document:
            raise ValueError("Boolean Base must belong to the same document")
        if value is not None and value._depends_on(self):
            raise ValueError("Boolean Base would create a dependency cycle")
        object.__setattr__(self, "_base_object", value)
        self._sync_boolean()

    @property
    def Tool(self):
        return self._tool_object

    @Tool.setter
    def Tool(self, value):
        self._assert_live()
        if value is not None and not isinstance(value, DocumentObject):
            raise TypeError("Boolean Tool must be a document object")
        if value is self:
            raise ValueError("A boolean object cannot use itself as Tool")
        if value is not None and value._document is not self._document:
            raise ValueError("Boolean Tool must belong to the same document")
        if value is not None and value is self._base_object:
            raise ValueError("Boolean Base and Tool must be different objects")
        if value is not None and value._depends_on(self):
            raise ValueError("Boolean Tool would create a dependency cycle")
        object.__setattr__(self, "_tool_object", value)
        self._sync_boolean()

    @property
    def Shape(self):
        return self._shape_spec

    @Shape.setter
    def Shape(self, value):
        self._assert_live()
        if self._type_id != "Part::Feature":
            raise AttributeError("Shape assignment is currently supported only on Part::Feature")
        object.__setattr__(self, "_shape_spec", value)
        self._materialize_shape_spec()

    def __getattr__(self, name):
        properties = object.__getattribute__(self, "_properties")
        if name in properties:
            return properties[name]
        raise AttributeError(f"{self._type_id} has no property {name!r}")

    def __setattr__(self, name, value):
        if name in self._internal_names or name.startswith("_"):
            if name == "Name" and "Name" in self.__dict__:
                raise AttributeError("Document object Name is immutable")
            if name == "Label" and "_document" in self.__dict__:
                self._assert_live()
            object.__setattr__(self, name, value)
            return
        self._assert_live()
        index_map = _PARAMETER_INDEX.get(self._type_id, {})
        if name in index_map:
            numeric = _finite_float(value, name)
            if numeric <= 0.0 and not (self._type_id == "Part::Cone" and name == "Radius2" and numeric == 0.0):
                raise ValueError(f"{name} must be positive")
            if self._type_id == "Part::Torus":
                major = numeric if name == "Radius1" else self._properties.get("Radius1", 0.0)
                minor = numeric if name == "Radius2" else self._properties.get("Radius2", 0.0)
                if minor >= major:
                    raise ValueError("Torus Radius2 must be smaller than Radius1")
            self._properties[name] = numeric
            if self._id:
                _native.set_parameter(
                    self._document._id, self._id, index_map[name], numeric
                )
            return
        descriptor = getattr(type(self), name, None)
        if hasattr(descriptor, "__set__"):
            descriptor.__set__(self, value)
            return
        object.__setattr__(self, name, value)

    def _depends_on(self, target, visiting=None):
        if self is target:
            return True
        visiting = set() if visiting is None else visiting
        marker = id(self)
        if marker in visiting:
            return False
        visiting.add(marker)
        for operand in (self._base_object, self._tool_object):
            if operand is not None and operand._depends_on(target, visiting):
                return True
        return False

    def _sync_placement(self):
        self._assert_live()
        if not self._id:
            return
        base = self._placement.Base
        qx, qy, qz, qw = self._placement.Rotation.Q
        _native.set_placement(
            self._document._id,
            self._id,
            base.x,
            base.y,
            base.z,
            qx,
            qy,
            qz,
            qw,
        )

    def _sync_boolean(self):
        self._assert_live()
        if self._type_id not in _BOOLEAN_FUNCTIONS:
            return
        if self._base_object is None or self._tool_object is None:
            return
        self._base_object._ensure_materialized()
        self._tool_object._ensure_materialized()
        if not self._id:
            function = getattr(_native, _BOOLEAN_FUNCTIONS[self._type_id])
            native_id = function(
                self._document._id,
                self.Name,
                self._base_object._id,
                self._tool_object._id,
            )
            object.__setattr__(self, "_id", int(native_id))
            self._sync_placement()
            _native.set_visibility(self._document._id, self._id, self._visibility)
        else:
            _native.set_boolean_operands(
                self._document._id,
                self._id,
                self._base_object._id,
                self._tool_object._id,
            )

    def _materialize_shape_spec(self):
        if self._id or self._shape_spec is None:
            return
        spec = self._shape_spec
        kind = getattr(spec, "_kind", None)
        params = tuple(getattr(spec, "_params", ()))
        if kind == "Part::Box":
            native_id = _native.add_box(self._document._id, self.Name, *params)
        elif kind == "Part::Cylinder":
            native_id = _native.add_cylinder(self._document._id, self.Name, *params)
        elif kind == "Part::Sphere":
            native_id = _native.add_sphere(self._document._id, self.Name, *params)
        elif kind == "Part::Cone":
            native_id = _native.add_cone(self._document._id, self.Name, *params)
        elif kind == "Part::Torus":
            native_id = _native.add_torus(self._document._id, self.Name, *params)
        else:
            raise NotImplementedError("Unsupported Part shape specification")
        object.__setattr__(self, "_id", int(native_id))
        spec_placement = getattr(spec, "_placement", None)
        if isinstance(spec_placement, Placement):
            self.Placement = spec_placement
        else:
            self._sync_placement()
        _native.set_visibility(self._document._id, self._id, self._visibility)

    def _ensure_materialized(self):
        self._assert_live()
        if self._id:
            return
        if self._type_id in _BOOLEAN_FUNCTIONS:
            self._sync_boolean()
        elif self._type_id == "Part::Feature":
            self._materialize_shape_spec()
        if not self._id:
            raise RuntimeError(f"Object {self.Name!r} is incomplete and cannot be recomputed")

    def __repr__(self):
        return f"<{self._type_id} object {self.Name}>"


class Document:
    def __init__(self, name: str):
        self.Name = str(name)
        self.Label = self.Name
        self._id = int(_native.create_document(self.Name))
        self._objects = []
        self._by_name = {}
        self._closed = False

    def _assert_open(self):
        if self._closed:
            raise RuntimeError(f"Document {self.Name!r} is closed")

    @property
    def Objects(self):
        self._assert_open()
        return list(self._objects)

    def addObject(self, type_name: str, name: str):
        self._assert_open()
        type_name = str(type_name)
        name = self._unique_name(str(name))
        native_id = 0
        if type_name in _PRIMITIVES:
            defaults, function_name = _PRIMITIVES[type_name]
            function = getattr(_native, function_name)
            native_id = function(self._id, name, *defaults.values())
        elif type_name not in _BOOLEAN_FUNCTIONS and type_name != "Part::Feature":
            raise NotImplementedError(f"Object type {type_name!r} is not supported yet")
        obj = DocumentObject(self, type_name, name, native_id)
        self._objects.append(obj)
        self._by_name[name] = obj
        return obj

    def getObject(self, name: str):
        self._assert_open()
        return self._by_name.get(str(name))

    def removeObject(self, name_or_object):
        self._assert_open()
        obj = (
            name_or_object
            if isinstance(name_or_object, DocumentObject)
            else self._by_name.get(str(name_or_object))
        )
        if obj is None:
            return None
        if obj._document is not self or obj._removed:
            raise ValueError("Object does not belong to this document")
        dependents = [
            candidate.Name
            for candidate in self._objects
            if candidate is not obj
            and (candidate._base_object is obj or candidate._tool_object is obj)
        ]
        if dependents:
            raise RuntimeError(
                f"Cannot remove {obj.Name!r}; referenced by {', '.join(dependents)}"
            )
        if obj._id:
            _native.remove_object(self._id, obj._id)
        self._objects.remove(obj)
        self._by_name.pop(obj.Name, None)
        object.__setattr__(obj, "_id", 0)
        object.__setattr__(obj, "_removed", True)
        return None

    def recompute(self):
        self._assert_open()
        names = [obj.Name for obj in self._objects]
        if len(names) != len(set(names)) or set(names) != set(self._by_name):
            raise RuntimeError("Document object index is inconsistent")
        for obj in self._objects:
            obj._ensure_materialized()
            obj._sync_placement()
            if obj._id:
                _native.set_visibility(self._id, obj._id, obj._visibility)
        _native.set_active_document(self._id)
        return bool(_native.recompute(self._id))

    def _unique_name(self, requested: str):
        base = requested or "Object"
        if base not in self._by_name:
            return base
        index = 1
        while f"{base}{index:03d}" in self._by_name:
            index += 1
        return f"{base}{index:03d}"

    def __getattr__(self, name):
        obj = self._by_name.get(name)
        if obj is not None:
            return obj
        raise AttributeError(name)

    def __repr__(self):
        return f"<Document {self.Name!r} with {len(self._objects)} objects>"


class _Console:
    @staticmethod
    def PrintMessage(message):
        print(str(message), end="")

    @staticmethod
    def PrintWarning(message):
        print(f"Warning: {message}", end="", file=sys.stderr)

    @staticmethod
    def PrintError(message):
        print(f"Error: {message}", end="", file=sys.stderr)


Console = _Console()
_documents = {}
ActiveDocument = None


def newDocument(name: str = "Unnamed"):
    global ActiveDocument
    name = str(name or "Unnamed")
    if name in _documents:
        closeDocument(name)
    document = Document(name)
    _documents[name] = document
    ActiveDocument = document
    _native.set_active_document(document._id)
    return document


def closeDocument(name_or_document):
    global ActiveDocument
    if isinstance(name_or_document, Document):
        document = name_or_document
    else:
        document = _documents.get(str(name_or_document))
    if document is None:
        return
    if document._closed:
        return
    _native.close_document(document._id)
    document._closed = True
    document._id = 0
    _documents.pop(document.Name, None)
    if ActiveDocument is document:
        ActiveDocument = next(iter(_documents.values()), None)
        _native.set_active_document(ActiveDocument._id if ActiveDocument else 0)


def activeDocument():
    return ActiveDocument


def getDocument(name: str):
    document = _documents.get(str(name))
    if document is None:
        raise NameError(f"No document named {name!r}")
    return document


def listDocuments():
    return dict(_documents)


def Version():
    return ("0", "5", "0", "Android", _native.kernel_info())


def _reset():
    global ActiveDocument
    for document in _documents.values():
        document._closed = True
        document._id = 0
    _documents.clear()
    ActiveDocument = None
    _native.reset()


__all__ = [
    "Vector",
    "Rotation",
    "Placement",
    "Document",
    "DocumentObject",
    "Console",
    "ActiveDocument",
    "newDocument",
    "closeDocument",
    "activeDocument",
    "getDocument",
    "listDocuments",
    "Version",
]
