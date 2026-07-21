# FreeCAD-Native 0.12 — persistent STEP editing session

This milestone extends the existing one-shot STEP tessellation bridge with a session-local OCCT document contract.

## Native contract

- `openSession`: reads a STEP file once and retains its `TopoDS_Shape` behind an opaque 64-bit handle.
- Each rendered triangle carries the 1-based OCCT face identifier that produced it.
- Face descriptors expose center of mass, outward planar normal, area and planarity.
- `previewPull`: builds a reversible planar-face prism and applies fuse for positive distance or cut for negative distance.
- `commit`: replaces the current BRep with the preview and increments the session revision.
- `rollback`: discards the preview and retessellates the last committed BRep.
- `saveCopy`: writes the committed BRep to a new STEP file.
- `close`: releases the native session explicitly.

## Safety boundaries

- Face identifiers are stable only inside one committed session revision. They are regenerated after every commit.
- The first Pull milestone accepts planar faces only.
- Imported source files are never overwritten by the native bridge.
- A preview never replaces the committed shape until `commit` succeeds.
- Boolean failures surface as Java exceptions and preserve the committed BRep.

## Android integration target

SolidFreeCAD must use the triangle-to-face map instead of mesh-derived face IDs when a STEP session is available. The UI flow is:

`select planar OCCT face -> enter/drag distance -> preview -> cancel or commit -> save edited copy`.

## Incremental body Move

The session also supports a reversible translation preview for the complete imported BRep. `previewMove` accepts an exact XYZ vector, uses `BRepBuilderAPI_Transform`, participates in the same commit/rollback contract and preserves the source STEP file.
