package com.medinaparra.freecadandroid.cadcore;

import android.content.Context;

import java.io.File;

/** Android entry point that installs OCCT resources before enabling native STEP. */
public final class AndroidCadCoreProvider {
    private AndroidCadCoreProvider() { }

    public static CadCoreRuntime create(Context context) {
        try {
            File resources=OcctResourceInstaller.install(context.getApplicationContext());
            return new NativeFreeCadRuntime(resources);
        } catch (CadCoreException unavailable) {
            return new PreviewCadRuntime();
        }
    }

    public static CadCoreRuntime requireNativeStep(Context context) throws CadCoreException {
        File resources=OcctResourceInstaller.install(context.getApplicationContext());
        NativeFreeCadRuntime runtime=new NativeFreeCadRuntime(resources);
        if(!runtime.status().supports(CadCoreRuntime.Capability.STEP_IMPORT)
                ||!runtime.status().supports(CadCoreRuntime.Capability.TESSELLATION)){
            String diagnostic=runtime.status().diagnostic;
            runtime.close();
            throw new CadCoreException("STEP_KERNEL_UNAVAILABLE",
                    diagnostic.isEmpty()?"OCCT STEP runtime is unavailable":diagnostic);
        }
        return runtime;
    }
}
