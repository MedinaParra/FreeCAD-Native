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
removed_native_ids = []


def new_id(*_args):
    counter["value"] += 1
    return counter["value"]


native.create_document = new_id
native.close_document = lambda *_args: None
native.remove_object = lambda document_id, object_id: removed_native_ids.append(
    (document_id, object_id)
)
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
    expect(ValueError, lambda: App.Vector(float("nan"), 0, 0))
    expect(ValueError, lambda: setattr(x, "z", float("inf")))
    expect(ValueError, lambda: App.Rotation(0, 0, 0, 0))
    expect(ValueError, lambda: App.Rotation(App.Vector(), 90))

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

    driven = doc.addObject("App::Feature", "Driven")
    driven.addProperty("App::PropertyFloat", "First")
    driven.addProperty("App::PropertyFloat", "Second")
    driven.setExpression("First", "Driven.Second * 2")
    driven.setExpression("Second", "Box.Width + 1")
    assert doc.recompute()
    assert driven.First == 22 and driven.Second == 11
    driven.setExpression("First", "2 ** 100")
    expect(ValueError, doc.recompute)
    driven.setExpression("First", "Driven.Second * 2")
    driven.setExpression("Second", "Driven.First")
    expect(ValueError, doc.recompute)
    driven.setExpression("Second", None)
    driven.Second = 3
    assert doc.recompute() and driven.First == 6

    other = App.newDocument("Other")
    foreign = other.addObject("Part::Box", "Foreign")
    cut = doc.addObject("Part::Cut", "Cut")
    expect(ValueError, lambda: setattr(cut, "Base", foreign))
    expect(ValueError, lambda: setattr(cut, "Base", cut))
    expect(ValueError, lambda: setattr(box, "Width", float("nan")))

    tool = doc.addObject("Part::Box", "Tool")
    cut.Base = box
    cut.Tool = tool
    nested = doc.addObject("Part::Fuse", "Nested")
    nested.Base = cut
    nested.Tool = doc.addObject("Part::Box", "NestedTool")
    expect(ValueError, lambda: setattr(cut, "Base", nested))
    expect(RuntimeError, lambda: doc.removeObject(tool))

    shape = Part.makeBox(10, 20, 30)
    assert shape.isValid() and shape.isClosed() and shape.ShapeType == "Solid"
    expect(ValueError, lambda: Part.makeBox(0, 1, 1))
    expect(ValueError, lambda: shape.fuse(shape))
    expect(ValueError, lambda: Part.makeCylinder(float("inf"), 10))
    expect(ValueError, lambda: Part.makeCone(5, -1, 10))
    expect(ValueError, lambda: Part.makeTorus(5, 5))
    torus = doc.addObject("Part::Torus", "Torus")
    expect(ValueError, lambda: setattr(torus, "Radius1", 1))

    disposable = doc.addObject("Part::Box", "Disposable")
    disposable_id = disposable._id
    doc.removeObject(disposable)
    assert doc.getObject("Disposable") is None
    assert any(object_id == disposable_id for _, object_id in removed_native_ids)
    expect(RuntimeError, lambda: setattr(disposable, "Width", 2))

    doc.openTransaction("structural rollback")
    temporary = doc.addObject("Part::Box", "Temporary")
    box.Width = 18
    doc.abortTransaction()
    assert doc.getObject("Temporary") is None and box.Width == 10
    expect(RuntimeError, lambda: setattr(temporary, "Width", 2))

    limited = App.newDocument("Limited")
    limited._MAX_OBJECTS = 1
    limited.addObject("Part::Box", "Only")
    expect(RuntimeError, lambda: limited.addObject("Part::Box", "TooMany"))
    App.closeDocument(limited)
    expect(RuntimeError, limited.recompute)

    print("SolidFreeCAD headless contract: PASS")


if __name__ == "__main__":
    run()
