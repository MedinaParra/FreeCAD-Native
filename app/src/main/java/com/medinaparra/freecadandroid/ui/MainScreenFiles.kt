package com.medinaparra.freecadandroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.medinaparra.freecadandroid.io.AndroidDocumentLoader
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import com.medinaparra.freecadandroid.python.PythonAssetInstaller
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val filesValidationMacro = """import FreeCAD as App
import Part

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

private data class FilesScene(
    val mesh: SceneMesh,
    val source: String,
    val summary: String
)

private data class FilesState(
    val scene: FilesScene? = null,
    val busy: Boolean = false,
    val phase: String = "Preparando core",
    val error: String? = null
)

@Composable
fun MainScreenFiles() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var sourceCode by rememberSaveable { mutableStateOf(filesValidationMacro) }
    var macroName by rememberSaveable { mutableStateOf("MacroAndroid.FCMacro") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(FilesState()) }

    fun executeMacro(code: String, name: String) {
        if (state.busy) return
        val previous = state.scene
        state = FilesState(previous, true, "Ejecutando $name")
        scope.launch {
            state = runCatching {
                val home = withContext(Dispatchers.IO) { PythonAssetInstaller.install(context) }
                val result = withContext(Dispatchers.Default) {
                    NativeCadBridge.runPythonMacro(home, code, 0.35, 0.30)
                }
                FilesScene(
                    result.mesh,
                    "FCMacro: $name",
                    result.documentSummary + "\n" + result.output
                )
            }.fold(
                onSuccess = { FilesState(it, phase = "Macro completada") },
                onFailure = {
                    FilesState(
                        previous,
                        error = AndroidDocumentLoader.friendlyMacroError(it, name),
                        phase = "Error de macro"
                    )
                }
            )
        }
    }

    fun importStep(uri: Uri) {
        if (state.busy) return
        val previous = state.scene
        state = FilesState(previous, true, "Importando STEP")
        scope.launch {
            state = runCatching {
                val staged = withContext(Dispatchers.IO) {
                    AndroidDocumentLoader.stage(context, uri, "step-import", "imported.step")
                }
                val result = withContext(Dispatchers.Default) {
                    NativeStepBridge.importStep(staged.first.absolutePath, staged.second)
                }
                FilesScene(result.mesh, "STEP: ${result.displayName}", result.summary)
            }.fold(
                onSuccess = { FilesState(it, phase = "STEP importado") },
                onFailure = {
                    FilesState(
                        previous,
                        error = AndroidDocumentLoader.readableFailure(it),
                        phase = "Error STEP"
                    )
                }
            )
        }
    }

    fun importFcStd(uri: Uri) {
        if (state.busy) return
        val previous = state.scene
        state = FilesState(previous, true, "Abriendo FCStd")
        scope.launch {
            state = runCatching {
                val staged = withContext(Dispatchers.IO) {
                    AndroidDocumentLoader.stage(context, uri, "fcstd-source", "Document.FCStd")
                }
                val archive = withContext(Dispatchers.IO) {
                    FreeCadArchiveReader.extract(
                        staged.first,
                        File(context.cacheDir, "fcstd-extracted"),
                        staged.second
                    )
                }
                val result = withContext(Dispatchers.Default) {
                    NativeFreeCadFileBridge.importFcStdBreps(
                        archive.brepFiles.map { it.absolutePath },
                        archive.displayName,
                        archive.summary
                    )
                }
                FilesScene(result.mesh, "FCStd: ${result.displayName}", result.summary)
            }.fold(
                onSuccess = { FilesState(it, phase = "FCStd importado") },
                onFailure = {
                    FilesState(
                        previous,
                        error = AndroidDocumentLoader.readableFailure(it),
                        phase = "Error FCStd"
                    )
                }
            )
        }
    }

    val macroPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !state.busy) {
            val previous = state.scene
            state = FilesState(previous, true, "Leyendo FCMacro")
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { AndroidDocumentLoader.readMacro(context, uri) }
                }.fold(
                    onSuccess = { loaded ->
                        macroName = loaded.first
                        sourceCode = loaded.second
                        showEditor = true
                        state = FilesState(previous, phase = "FCMacro cargada")
                        executeMacro(loaded.second, loaded.first)
                    },
                    onFailure = {
                        state = FilesState(
                            previous,
                            error = AndroidDocumentLoader.readableFailure(it),
                            phase = "Error FCMacro"
                        )
                    }
                )
            }
        }
    }
    val stepPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) importStep(it)
    }
    val fcstdPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) importFcStd(it)
    }

    LaunchedEffect(Unit) { executeMacro(sourceCode, macroName) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF111116)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF1E1E26))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    "FreeCAD Android Core",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "CPYTHON + OCCT + STEP + FCMACRO + FCSTD",
                    color = if (state.error == null) Color(0xFF72E39A) else Color(0xFFFF8181),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Button(onClick = { executeMacro(sourceCode, macroName) }, enabled = !state.busy) {
                        Text("Ejecutar")
                    }
                    OutlinedButton(
                        onClick = { macroPicker.launch(arrayOf("text/plain", "text/x-python", "*/*")) },
                        enabled = !state.busy
                    ) { Text("FCMacro") }
                    TextButton(onClick = { showEditor = !showEditor }, enabled = !state.busy) {
                        Text(if (showEditor) "Ocultar" else "Editar")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedButton(
                        onClick = { stepPicker.launch(arrayOf("model/step", "application/octet-stream", "*/*")) },
                        enabled = !state.busy
                    ) { Text("STEP") }
                    OutlinedButton(
                        onClick = { fcstdPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        enabled = !state.busy
                    ) { Text("FCStd") }
                    Text(state.phase, color = Color(0xFFC5C1CF), fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
            }

            if (showEditor) {
                OutlinedTextField(
                    sourceCode,
                    { sourceCode = it },
                    enabled = !state.busy,
                    label = { Text(macroName) },
                    textStyle = TextStyle(FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(250.dp).padding(8.dp)
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val scene = state.scene
                if (scene != null) {
                    AndroidView(
                        factory = { CadGLSurfaceView(it).apply { setMesh(scene.mesh) } },
                        update = { it.setMesh(scene.mesh) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (state.busy) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Text("Sin geometría", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }

            val scene = state.scene
            Column(Modifier.fillMaxWidth().background(Color(0xFF18181F)).padding(10.dp)) {
                if (scene != null) {
                    Text(
                        "${scene.mesh.vertexCount} vértices | ${scene.mesh.triangleCount} triángulos | ${scene.source}",
                        color = Color(0xFF72E39A)
                    )
                }
                Text(
                    state.error ?: scene?.summary ?: state.phase,
                    color = if (state.error == null) Color(0xFFC5C1CF) else Color(0xFFFF9B9B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 10
                )
            }
        }
    }
}
