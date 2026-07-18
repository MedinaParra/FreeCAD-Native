package com.medinaparra.freecadandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeMacroScene
import com.medinaparra.freecadandroid.python.PythonAssetInstaller
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val defaultMacro = """import FreeCAD as App
import Part

print("Ejecutando macro FreeCAD en Android...")
doc = App.newDocument("MacroAndroid")

base = doc.addObject("Part::Box", "Base")
base.Length = 80
base.Width = 50
base.Height = 10

result = base
for index, (x, y) in enumerate(((12, 12), (68, 12), (12, 38), (68, 38)), start=1):
    hole = doc.addObject("Part::Cylinder", f"Hole{index}")
    hole.Radius = 4
    hole.Height = 14
    hole.Placement.Base = App.Vector(x, y, -2)

    cut = doc.addObject("Part::Cut", f"Cut{index}")
    cut.Base = result
    cut.Tool = hole
    result = cut

tower = doc.addObject("Part::Cylinder", "Tower")
tower.Radius = 12
tower.Height = 30
tower.Placement.Base = App.Vector(40, 25, 10)

body = doc.addObject("Part::Fuse", "Body")
body.Base = result
body.Tool = tower

cap = doc.addObject("Part::Sphere", "Cap")
cap.Radius = 13
cap.Placement.Base = App.Vector(40, 25, 37)

final_result = doc.addObject("Part::Fuse", "FinalResult")
final_result.Base = body
final_result.Tool = cap

doc.recompute()
print(f"Documento: {doc.Name}; objetos: {len(doc.Objects)}")
"""

private data class MacroScreenState(
    val scene: NativeMacroScene? = null,
    val error: String? = null,
    val isBusy: Boolean = false,
    val phase: String = "Preparando núcleo"
)

@Composable
fun MainScreenNative() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var sourceCode by rememberSaveable { mutableStateOf(defaultMacro) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(MacroScreenState()) }

    fun executeMacro() {
        if (state.isBusy) return
        val macroSnapshot = sourceCode
        val previousScene = state.scene
        state = MacroScreenState(
            scene = previousScene,
            isBusy = true,
            phase = "Preparando CPython 3.14"
        )
        scope.launch {
            val result = runCatching {
                val pythonHome = withContext(Dispatchers.IO) {
                    PythonAssetInstaller.install(context)
                }
                state = state.copy(phase = "Ejecutando macro y recalculando BRep")
                withContext(Dispatchers.Default) {
                    NativeCadBridge.runPythonMacro(
                        pythonHome = pythonHome,
                        sourceCode = macroSnapshot,
                        linearDeflection = 0.35,
                        angularDeflection = 0.30
                    )
                }
            }
            state = result.fold(
                onSuccess = { scene ->
                    MacroScreenState(
                        scene = scene,
                        isBusy = false,
                        phase = "Macro completada"
                    )
                },
                onFailure = { error ->
                    MacroScreenState(
                        scene = previousScene,
                        error = error.stackTraceToString(),
                        isBusy = false,
                        phase = "Error"
                    )
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        executeMacro()
    }

    val currentScene = state.scene
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121216)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MacroHeader(
                state = state,
                showEditor = showEditor,
                onToggleEditor = { showEditor = !showEditor },
                onRun = ::executeMacro
            )

            if (showEditor) {
                OutlinedTextField(
                    value = sourceCode,
                    onValueChange = { sourceCode = it },
                    enabled = !state.isBusy,
                    label = { Text("Macro Python") },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    currentScene != null -> AndroidView(
                        factory = { viewContext ->
                            CadGLSurfaceView(viewContext).apply {
                                setMesh(currentScene.mesh)
                            }
                        },
                        update = { view -> view.setMesh(currentScene.mesh) },
                        modifier = Modifier.fillMaxSize()
                    )

                    state.isBusy -> LoadingMacro(state.phase)

                    else -> Text(
                        "No hay una geometría para visualizar",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }

            MacroDiagnostics(state = state, scene = currentScene)
        }
    }
}

@Composable
private fun LoadingMacro(phase: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(phase, color = Color.White)
        Text(
            "La primera extracción del runtime puede tardar unos segundos",
            color = Color(0xFFB9B6C5),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MacroHeader(
    state: MacroScreenState,
    showEditor: Boolean,
    onToggleEditor: () -> Unit,
    onRun: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E26))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "FreeCAD Android Core",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (state.error == null) {
                        "CPYTHON + FREECAD API + OCCT ACTIVOS"
                    } else {
                        "ERROR DE MACRO"
                    },
                    color = if (state.error == null) {
                        Color(0xFF72E39A)
                    } else {
                        Color(0xFFFF7B7B)
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TextButton(onClick = onToggleEditor, enabled = !state.isBusy) {
                Text(if (showEditor) "Ocultar macro" else "Editar macro")
            }
            Button(onClick = onRun, enabled = !state.isBusy) {
                Text(if (state.isBusy) "Ejecutando" else "Ejecutar")
            }
        }
        Text(
            state.scene?.pythonVersion?.lineSequence()?.firstOrNull()
                ?: "Python 3.14.6 embebido / OpenCASCADE 7.9.2",
            color = Color(0xFFB9B6C5),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MacroDiagnostics(state: MacroScreenState, scene: NativeMacroScene?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181F))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (scene != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${scene.mesh.vertexCount} vértices", color = Color.White)
                Text("${scene.mesh.triangleCount} triángulos", color = Color.White)
                Text("Origen: macro Python", color = Color(0xFF72E39A))
            }
        }

        val diagnostics = when {
            state.error != null -> state.error
            scene != null -> buildString {
                append(scene.documentSummary.trim())
                if (scene.output.isNotBlank()) {
                    append("\n")
                    append(scene.output.trim())
                }
            }
            else -> state.phase
        }

        Text(
            diagnostics,
            color = if (state.error == null) Color(0xFFB9B6C5) else Color(0xFFFFA0A0),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 7,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
    }
}
