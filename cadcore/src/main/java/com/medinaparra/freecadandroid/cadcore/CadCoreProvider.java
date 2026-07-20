package com.medinaparra.freecadandroid.cadcore;

/** Creates the strongest available runtime without hiding capability loss. */
public final class CadCoreProvider {
    private CadCoreProvider() { }

    public static CadCoreRuntime create() {
        try {
            return new NativeFreeCadRuntime();
        } catch (CadCoreException unavailable) {
            return new PreviewCadRuntime();
        }
    }

    public static CadCoreRuntime requireNativeStep() throws CadCoreException {
        NativeFreeCadRuntime runtime = new NativeFreeCadRuntime();
        if (!runtime.status().supports(CadCoreRuntime.Capability.STEP_IMPORT)
                || !runtime.status().supports(CadCoreRuntime.Capability.TESSELLATION)) {
            runtime.close();
            throw new CadCoreException("STEP_KERNEL_UNAVAILABLE",
                    "FreeCAD/OpenCascade STEP import and tessellation are not linked");
        }
        return runtime;
    }
}
