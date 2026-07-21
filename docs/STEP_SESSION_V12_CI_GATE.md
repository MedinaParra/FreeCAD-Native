# STEP session v0.12 CI gate

This gate validates the exact native branch after adding persistent STEP editing sessions.

The build must verify:

- ARM32 and ARM64 C++ compilation;
- OCCT STEP read and write toolkits;
- JNI symbols for open, preview Pull, commit, rollback, save and close;
- one OCCT face identifier per rendered triangle;
- Kotlin payload validation and inherited tests;
- packaging of the universal APK and native runtime artifacts.

A green build proves compilation and packaging. Physical edit/save/reopen validation remains a separate product gate.
