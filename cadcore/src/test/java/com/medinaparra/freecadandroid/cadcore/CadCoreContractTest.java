package com.medinaparra.freecadandroid.cadcore;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class CadCoreContractTest {
    @Test public void createsMechanicalPrimitiveAndRigidCopy() throws Exception {
        PreviewCadRuntime runtime = new PreviewCadRuntime();
        CadCoreRuntime.Shape shell = runtime.createPrimitive(new CadCoreRuntime.PrimitiveRequest(
                CadCoreRuntime.PrimitiveType.SHELL, "Manto", 1520, 800,
                0,0,0,0,48));
        assertTrue(shell.mesh.valid());
        assertEquals(1520.0, shell.mesh.bounds.sizeX(), 1e-3);
        assertEquals(800.0, shell.mesh.bounds.sizeY(), 1e-3);

        CadCoreRuntime.Transform move = new CadCoreRuntime.Transform(new double[]{
                1,0,0,120,
                0,1,0,-30,
                0,0,1,50,
                0,0,0,1
        });
        CadCoreRuntime.Shape translated = runtime.transformed(shell, move);
        assertEquals(shell.mesh.bounds.minX + 120, translated.mesh.bounds.minX, 1e-3);
        assertEquals(shell.mesh.bounds.minY - 30, translated.mesh.bounds.minY, 1e-3);
    }

    @Test public void rejectsScaleAsAlignmentTransform() throws Exception {
        PreviewCadRuntime runtime = new PreviewCadRuntime();
        CadCoreRuntime.Shape shaft = runtime.createPrimitive(new CadCoreRuntime.PrimitiveRequest(
                CadCoreRuntime.PrimitiveType.SHAFT, "Eje", 2200, 220,
                0,0,0,0,32));
        CadCoreRuntime.Transform scaled = new CadCoreRuntime.Transform(new double[]{
                1.1,0,0,0,
                0,1,0,0,
                0,0,1,0,
                0,0,0,1
        });
        try {
            runtime.transformed(shaft, scaled);
            fail("Non-rigid transform must be rejected");
        } catch (CadCoreException expected) {
            assertEquals("NON_RIGID_TRANSFORM", expected.code);
        }
    }

    @Test public void validatesStepHeaderButDoesNotPretendToImportIt() throws Exception {
        File file = File.createTempFile("pulley-support", ".step");
        try {
            String step = "ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('test'),'2;1');\nENDSEC;\nDATA;\nENDSEC;\nEND-ISO-10303-21;\n";
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(step.getBytes(StandardCharsets.US_ASCII));
            }
            CadCoreRuntime.StepRequest request = new CadCoreRuntime.StepRequest(file,0.2,15.0);
            String sha = PreviewCadRuntime.validateStep(request);
            assertEquals(64, sha.length());
            try {
                new PreviewCadRuntime().importStep(request);
                fail("Preview runtime must never claim native STEP import");
            } catch (CadCoreException expected) {
                assertEquals("STEP_RUNTIME_UNAVAILABLE", expected.code);
            }
        } finally {
            assertTrue(file.delete() || !file.exists());
        }
    }
}
