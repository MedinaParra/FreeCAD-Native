package com.medinaparra.freecadandroid.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import com.medinaparra.freecadandroid.python.PythonAssetInstaller
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val validationMacro = """import FreeCAD as App
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

private data class CoreScene(
    val mesh: SceneMesh,
    val source: String,
    val summary: String
)

private data class CoreState(
    val scene: CoreScene? = null,
    val busy: Boolean = false,
    val phase: String = "Preparando core",
    val error: String? = null
)

@Composable
fun MainScreenCore() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var sourceCode by rememberSaveable { mutableStateOf(validationMacro) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(CoreState()) }

    fun runMacro() {
        if (state.busy) return
        val previous = state.scene
        state = CoreState(scene = previous, busy = true, phase = "Ejecutando macro Python")
        scope.launch {
            state = runCatching {
                val pythonHome = withContext(Dispatchers.IO) {
                    PythonAssetInstaller.install(context)
                }
                val scene = withContext(Dispatchers.Default) {
                    NativeCadBridge.runPythonMacro(
                        pythonHome = pythonHome,
                        sourceCode = sourceCode,
                        linearDeflection = 0.35,
                        angularDeflection = 0.30
                    )
                }
                CoreScene(scene.mesh, "Macro Python", scene.documentSummary + "\n" + scene.output)
            }.fold(
                onSuccess = { CoreState(scene = it, phase = "Macro completada") },
                onFailure = { CoreState(scene = previous, error = it.stackTraceToString(), phase = "Error") }
            )
        }
    }

    fun importStep(uri: Uri) {
        if (state.busy) return
        val previous = state.scene
        state = CoreState(scene = previous, busy = true, phase = "Importando STEP con OpenCASCADE")
        scope.launch {
            state = runCatching {
                val staged = withContext(Dispatchers.IO) { stageDocument(context, uri) }
                val scene = withContext(Dispatchers.Default) {
                    NativeStepBridge.importStep(staged.first.absolutePath, staged.second)
                }
                CoreScene(scene.mesh, "STEP: ${scene.displayName}", scene.summary)
            }.fold(
                onSuccess = { CoreState(scene = it, phase = "STEP importado") },
                onFailure = { CoreState(scene = previous, error = it.stackTraceToString(), phase = "Error STEP") }
            )
        }
    }

    val stepPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importStep(uri)
    }

    LaunchedEffect(Unit) { runMacro() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111116)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E26))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    "FreeCAD Android Core",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "CPYTHON + FREECAD API + OCCT + STEP",
                    color = if (state.error == null) Color(0xFF72E39A) else Color(0xFFFF8181),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = ::runMacro, enabled = !state.busy) { Text("Ejecutar macro") }
                    OutlinedButton(
                        onClick = { stepPicker.launch(arrayOf("model/step", "application/step", "application/octet-stream", "*/*")) },
                        enabled = !state.busy
                    ) { Text("Abrir STEP") }
                    TextButton(onClick = { showEditor = !showEditor }, enabled = !state.busy) {
                        Text(if (showEditor) "Ocultar" else "Editar")
                    }
                }
            }

            if (showEditor) {
                OutlinedTextField(
                    value = sourceCode,
                    onValueChange = { sourceCode = it },
                    enabled = !state.busy,
                    label = { Text("Macro Python") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().height(250.dp).padding(8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val scene = state.scene
                if (scene != null) {
                    AndroidView(
                        factory = { CadGLSurfaceView(it).apply { setMesh(scene.mesh) } },
                        update = { it.setMesh(scene.mesh) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text("Sin geometría", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }

            val scene = state.scene
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF18181F)).padding(10.dp)
            ) {
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
                    maxLines = 7
                )
            }
        }
    }
}

private fun stageDocument(context: Context, uri: Uri): Pair<File, String> {
    val displayName = queryDisplayName(context, uri) ?: "imported.step"
    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val directory = File(context.cacheDir, "step-import").apply { mkdirs() }
    val target = File(directory, safeName)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Android could not open the selected document" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    require(target.length() > 0L) { "The selected STEP file is empty" }
    return target to displayName
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment
}
