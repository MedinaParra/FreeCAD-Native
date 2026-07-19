"""Validate the Android FCStd manifest assumptions against official FreeCAD 1.1.1 files."""
from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET
import zipfile


SAMPLES = (
    "data/examples/PartDesignExample.FCStd",
    "data/examples/AssemblyExample.FCStd",
    "data/examples/ArchDetail.FCStd",
    "data/examples/FEMExample.FCStd",
)


def direct_child(element: ET.Element, tag: str) -> ET.Element | None:
    return next((child for child in element if child.tag == tag), None)


def validate(path: pathlib.Path) -> tuple[int, int, int, int]:
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        assert "Document.xml" in names, f"{path.name}: missing Document.xml"
        payload = archive.read("Document.xml")
        assert b"<!DOCTYPE" not in payload.upper() and b"<!ENTITY" not in payload.upper()
        root = ET.fromstring(payload)
        objects_node = direct_child(root, "Objects")
        object_data = direct_child(root, "ObjectData")
        assert objects_node is not None and object_data is not None
        types = {
            child.attrib["name"]: child.attrib["type"]
            for child in objects_node
            if child.tag == "Object"
        }
        records = [child for child in object_data if child.tag == "Object"]
        assert records and set(record.attrib["name"] for record in records).issubset(types)

        shape_files: list[str] = []
        placements = 0
        visibility = 0
        internal_links: list[tuple[str, str]] = []
        for record in records:
            properties = direct_child(record, "Properties")
            if properties is None:
                continue
            for prop in properties:
                if prop.tag != "Property":
                    continue
                name = prop.attrib.get("name")
                if name == "Shape":
                    part = prop.find("Part")
                    if part is not None and part.attrib.get("file"):
                        shape_files.append(part.attrib["file"])
                elif name == "Placement" and prop.find("PropertyPlacement") is not None:
                    placements += 1
                elif name == "Visibility" and prop.find("Bool") is not None:
                    visibility += 1
                elif name == "LinkedObject":
                    link = prop.find("XLink")
                    if (
                        link is not None
                        and not link.attrib.get("file")
                        and link.attrib.get("name")
                    ):
                        internal_links.append((record.attrib["name"], link.attrib["name"]))

        existing_shapes = [name for name in shape_files if name in names and archive.getinfo(name).file_size]
        assert existing_shapes, f"{path.name}: no non-empty object Shape BREP"
        assert placements or visibility, f"{path.name}: no editable universal properties"
        for source, target in internal_links:
            assert target in types, f"{path.name}: {source} links missing object {target}"
        if path.name == "AssemblyExample.FCStd":
            shape_owners: set[str] = set()
            for record in records:
                properties = direct_child(record, "Properties")
                if properties is not None and any(
                    prop.tag == "Property"
                    and prop.attrib.get("name") == "Shape"
                    and prop.find("Part") is not None
                    and prop.find("Part").attrib.get("file") in existing_shapes
                    for prop in properties
                ):
                    shape_owners.add(record.attrib["name"])
            assert internal_links, "official Assembly sample no longer exercises App::Link"
            assert all(target in shape_owners for _, target in internal_links)
        return len(records), len(existing_shapes), placements, len(internal_links)


def run(root: pathlib.Path) -> None:
    total_objects = total_shapes = total_placements = total_links = 0
    for relative in SAMPLES:
        objects, shapes, placements, links = validate(root / relative)
        total_objects += objects
        total_shapes += shapes
        total_placements += placements
        total_links += links
        print(
            f"{pathlib.Path(relative).name}: objects={objects}, shapes={shapes}, "
            f"placements={placements}, links={links}"
        )
    assert total_shapes >= 10
    assert total_links >= 10
    print(
        "Official FreeCAD 1.1.1 FCStd contract: PASS "
        f"({total_objects} objects, {total_shapes} shapes, {total_placements} placements, "
        f"{total_links} links)"
    )


if __name__ == "__main__":
    source_root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".deps/freecad")
    run(source_root)
