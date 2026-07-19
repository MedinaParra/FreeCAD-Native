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
import com.medinaparra.freecadandroid.io.FcStdObjectRecord
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.io.FreeCadArchiveContent
import com.medinaparra.freecadandroid.io.FreeCadArchiveWriter
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

private fun safeFcStdExportStem(value: String): String = value
    .replace(Regex("[^A-Za-z0-9._-]"), "_")
    .trim('.', '_')
    .take(96)
    .ifBlank { "FreeCAD-document" }

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
    val summary: String,
    val fcStdSession: FcStdEditSession? = null
)

private data class FcStdEditSession(
    val archive: FreeCadArchiveContent,
    val objects: List<FcStdObjectRecord>
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
    var selectedObjectName by rememberSaveable { mutableStateOf("") }
    var editLabel by rememberSaveable { mutableStateOf("") }
    var editX by rememberSaveable { mutableStateOf("0") }
    var editY by rememberSaveable { mutableStateOf("0") }
    var editZ by rememberSaveable { mutableStateOf("0") }
    var editVisible by rememberSaveable { mutableStateOf(true) }

    fun loadObjectEditor(record: FcStdObjectRecord?) {
        if (record == null) return
        selectedObjectName = record.name
        editLabel = record.label
        editX = record.placement.x.toString()
        editY = record.placement.y.toString()
        editZ = record.placement.z.toString()
        editVisible = record.visible
    }

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
                    NativeFreeCadFileBridge.importFcStdObjects(
                        archive.objects,
                        archive.displayName,
                        archive.summary
                    )
                }
                FilesScene(
                    result.mesh,
                    "FCStd: ${result.displayName}",
                    result.summary,
                    FcStdEditSession(archive, archive.objects)
                )
            }.fold(
                onSuccess = {
                    loadObjectEditor(
                        it.fcStdSession?.objects?.firstOrNull { record ->
                            record.brepFile != null && record.visible
                        } ?: it.fcStdSession?.objects?.firstOrNull { record -> record.brepFile != null }
                    )
                    FilesState(it, phase = "FCStd editable importado")
                },
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

    fun selectRelativeObject(delta: Int) {
        val session = state.scene?.fcStdSession ?: return
        val shapes = session.objects.filter { it.brepFile != null }
        if (shapes.isEmpty()) return
        val current = shapes.indexOfFirst { it.name == selectedObjectName }.coerceAtLeast(0)
        val next = (current + delta + shapes.size) % shapes.size
        loadObjectEditor(shapes[next])
    }

    fun objectsWithPendingFcStdEdit(session: FcStdEditSession): List<FcStdObjectRecord> {
        val current = session.objects.firstOrNull { it.name == selectedObjectName }
            ?: return session.objects
        val x = editX.toDouble().also { require(it.isFinite()) }
        val y = editY.toDouble().also { require(it.isFinite()) }
        val z = editZ.toDouble().also { require(it.isFinite()) }
        if (!editVisible && current.visible) {
            require(session.objects.count { it.brepFile != null && it.visible } > 1) {
                "At least one shape object must remain visible in the mobile preview"
            }
        }
        val label = editLabel.trim().ifBlank { current.name }
        require(label.length <= 4096) { "FCStd object labels cannot exceed 4096 characters" }
        val updatedPlacement = if (current.hasPlacementProperty) {
            current.placement.copy(x = x, y = y, z = z)
        } else {
            current.placement
        }
        val updated = current.copy(
            label = label,
            visible = editVisible,
            placement = updatedPlacement
        )
        return session.objects.map { if (it.name == current.name) updated else it }
    }

    fun applyFcStdEdits() {
        if (state.busy) return
        val scene = state.scene ?: return
        val session = scene.fcStdSession ?: return
        val current = session.objects.firstOrNull { it.name == selectedObjectName } ?: return
        val previous = scene
        state = FilesState(previous, true, "Aplicando edición FCStd")
        scope.launch {
            state = runCatching {
                val objects = objectsWithPendingFcStdEdit(session)
                val updated = objects.first { it.name == current.name }
                val result = withContext(Dispatchers.Default) {
                    NativeFreeCadFileBridge.importFcStdObjects(
                        objects,
                        session.archive.displayName,
                        session.archive.summary + "\nMobile edits pending save: ${updated.name}"
                    )
                }
                FilesScene(
                    result.mesh,
                    previous.source,
                    result.summary,
                    FcStdEditSession(session.archive, objects)
                )
            }.fold(
                onSuccess = { FilesState(it, phase = "Edición FCStd aplicada") },
                onFailure = {
                    FilesState(
                        previous,
                        error = AndroidDocumentLoader.readableFailure(it),
                        phase = "Error de edición FCStd"
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
    val fcstdSavePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !state.busy) {
            val previous = state.scene
            val session = previous?.fcStdSession
            if (session != null && previous != null) {
                state = FilesState(previous, true, "Guardando FCStd")
                scope.launch {
                    state = runCatching {
                        val objects = objectsWithPendingFcStdEdit(session)
                        val exported = withContext(Dispatchers.IO) {
                            val safeStem = safeFcStdExportStem(session.archive.documentName)
                            val target = File(context.cacheDir, "fcstd-export/$safeStem.FCStd")
                            FreeCadArchiveWriter.write(session.archive, objects, target)
                            context.contentResolver.openOutputStream(uri, "w").use { output ->
                                requireNotNull(output) { "Android could not open the selected export destination" }
                                target.inputStream().use { input -> input.copyTo(output) }
                            }
                            target.length()
                        }
                        previous.copy(
                            summary = previous.summary + "\nSaved FCStd bytes: $exported",
                            fcStdSession = FcStdEditSession(session.archive, objects)
                        )
                    }.fold(
                        onSuccess = { FilesState(it, phase = "FCStd guardado") },
                        onFailure = {
                            FilesState(
                                previous,
                                error = AndroidDocumentLoader.readableFailure(it),
                                phase = "Error al guardar FCStd"
                            )
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { executeMacro(sourceCode, macroName) }

    val fcStdSession = state.scene?.fcStdSession
    val selectedFcStdObject = fcStdSession?.objects?.firstOrNull { it.name == selectedObjectName }
    val editableShapeObjects = fcStdSession?.objects?.filter { it.brepFile != null }.orEmpty()

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
                    "FREECADBASE + FCSTD OBJECT EDITOR + STEP + FCMACRO",
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

            if (fcStdSession != null && selectedFcStdObject != null) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF20202A))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Objeto ${editableShapeObjects.indexOfFirst { it.name == selectedFcStdObject.name } + 1}/${editableShapeObjects.size}: " +
                            "${selectedFcStdObject.name} [${selectedFcStdObject.typeId}]",
                        color = Color(0xFF72E39A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(onClick = { selectRelativeObject(-1) }, enabled = !state.busy) {
                            Text("Anterior")
                        }
                        OutlinedButton(onClick = { selectRelativeObject(1) }, enabled = !state.busy) {
                            Text("Siguiente")
                        }
                        OutlinedButton(
                            onClick = { editVisible = !editVisible },
                            enabled = !state.busy
                        ) { Text(if (editVisible) "Visible" else "Oculto") }
                        Button(
                            onClick = {
                                val safeStem = safeFcStdExportStem(fcStdSession.archive.documentName)
                                fcstdSavePicker.launch("$safeStem-editado.FCStd")
                            },
                            enabled = !state.busy
                        ) { Text("Guardar") }
                    }
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        enabled = !state.busy,
                        singleLine = true,
                        label = { Text("Etiqueta") },
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        OutlinedTextField(
                            editX,
                            { editX = it },
                            enabled = !state.busy && selectedFcStdObject.hasPlacementProperty,
                            singleLine = true,
                            label = { Text("X") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            editY,
                            { editY = it },
                            enabled = !state.busy && selectedFcStdObject.hasPlacementProperty,
                            singleLine = true,
                            label = { Text("Y") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            editZ,
                            { editZ = it },
                            enabled = !state.busy && selectedFcStdObject.hasPlacementProperty,
                            singleLine = true,
                            label = { Text("Z") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { applyFcStdEdits() },
                            enabled = !state.busy,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) { Text("Aplicar") }
                    }
                    if (!selectedFcStdObject.hasPlacementProperty) {
                        Text(
                            "Este objeto no guarda Placement; se permite editar etiqueta y visibilidad.",
                            color = Color(0xFFFFC96B),
                            fontSize = 10.sp
                        )
                    }
                }
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
