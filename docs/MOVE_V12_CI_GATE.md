# Native STEP Move CI gate

This gate validates the reversible whole-body Move increment.

- `previewMove` accepts a finite non-zero XYZ vector.
- The native implementation uses `BRepBuilderAPI_Transform`.
- Preview participates in the existing commit, rollback and save-copy session contract.
- ARM32 and ARM64 builds must export the JNI Move symbol and preserve inherited STEP Pull tests.
