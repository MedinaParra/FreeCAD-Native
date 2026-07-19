"""Host-side contract tests for the headless SolidFreeCAD Android engine."""
from __future__ import annotations

import math
import pathlib
import sys
import types

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

native = types.ModuleType("_freecad_native")
counter = {"value": 0}


def new_id(*_args):
    counter["value"] += 1
    return counter["value"]


native.create_document = new_id
native.close_document = lambda *_args: None
native.reset = lambda: None
native.set_active_document = lambda *_args: None
native.add_box = new_id
native.add_cylinder = new_id
native.add_sphere = new_id
native.add_cone = new_id
native.add_torus = new_id
native.add_prism = new_id
native.add_fuse = new_id
native.add_cut = new_id
native.add_common = new_id
native.set_parameter = lambda *_args: None
native.set_boolean_operands = lambda *_args: None
native.set_placement = lambda *_args: None
native.set_visibility = lambda *_args: None
native.recompute = lambda *_args: True
native.kernel_info = lambda: "host-contract"
native.last_error = lambda *_args: ""
native.summary = lambda *_args: "host-contract"
sys.modules["_freecad_native"] = native

import FreeCAD as App
import FreeCADCore

FreeCADCore.install(App)
import Part


def expect(exception, callback):
    try:
        callback()
    except exception:
        return
    raise AssertionError(f"Expected {exception.__name__}")


def run():
    App._reset()

    x = App.Vector(1, 0, 0)
    y = App.Vector(0, 1, 0)
    assert x.cross(y) == App.Vector(0, 0, 1)
    assert math.isclose(x.getAngle(y), math.pi / 2)
    expect(ValueError, lambda: App.Vector().getAngle(x))
    expect(ZeroDivisionError, lambda: x / 0)

    doc = App.newDocument("Contract")
    box = doc.addObject("Part::Box", "Box")
    box.addProperty("App::PropertyString", "Code", "Identity")
    box.addProperty("App::PropertyLength", "DrivenHeight", "Dimensions")
    box.Code = "SF-001"
    box.setExpression("DrivenHeight", "Box.Width * 2")

    doc.openTransaction("resize")
    box.Width = 25
    doc.abortTransaction()
    assert box.Width == 10

    group = doc.addObject("App::DocumentObjectGroup", "Parts")
    group.addObject(box)
    assert box in group.Group
    assert doc.recompute()
    assert box.DrivenHeight == 20
    assert box.State == []

    other = App.newDocument("Other")
    foreign = other.addObject("Part::Box", "Foreign")
    cut = doc.addObject("Part::Cut", "Cut")
    expect(ValueError, lambda: setattr(cut, "Base", foreign))
    expect(ValueError, lambda: setattr(cut, "Base", cut))
    expect(ValueError, lambda: setattr(box, "Width", float("nan")))

    shape = Part.makeBox(10, 20, 30)
    assert shape.isValid() and shape.isClosed() and shape.ShapeType == "Solid"
    expect(ValueError, lambda: Part.makeBox(0, 1, 1))
    expect(ValueError, lambda: shape.fuse(shape))
    expect(ValueError, lambda: Part.makeCylinder(float("inf"), 10))

    print("SolidFreeCAD headless contract: PASS")


if __name__ == "__main__":
    run()
