package com.medinaparra.freecadandroid.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.medinaparra.freecadandroid.nativebridge.NativeStepScene
import com.medinaparra.freecadandroid.nativebridge.StepNativeBridge
import com.medinaparra.freecadandroid.step.OcctResourceInstaller
import com.medinaparra.freecadandroid.step.StepDocumentCache
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WorkspaceMode { MACRO, STEP }

@Composable
fun CoreWorkspaceScreen() {
    var mode by rememberSaveable { mutableStateOf(WorkspaceMode.MACRO) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101016))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { mode = WorkspaceMode.MACRO }, enabled = mode != WorkspaceMode.MACRO) {
                Text("Macros")
            }
            Button(onClick = { mode = WorkspaceMode.STEP }, enabled = mode != WorkspaceMode.STEP) {
                Text("Importar STEP")
            }
            Text(
                "Core 0.6",
                color = Color(0xFF72E39A),
                modifier = Modifier.align(Alignment.CenterVertically),
                fontWeight = FontWeight.Bold
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (mode) {
                WorkspaceMode.MACRO -> MainScreenNative()
                WorkspaceMode.STEP -> StepImportScreen()
            }
        }
    }
}

private data class StepScreenState(
    val scene: NativeStepScene? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val phase: String = "Selecciona un archivo STEP",
    val byteCount: Long = 0L
)

@Composable
private fun StepImportScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(StepScreenState()) }

    fun startImport(uri: Uri) {
        if (state.busy) return
        state = StepScreenState(
            scene = state.scene,
            busy = true,
            phase = "Copiando STEP al almacenamiento privado"
        )
        scope.launch {
            val result = runCatching {
                val cached = withContext(Dispatchers.IO) {
                    StepDocumentCache.copyIntoPrivateStorage(context, uri)
                }
                state = state.copy(
                    phase = "Preparando recursos de OpenCASCADE",
                    byteCount = cached.byteCount
                )
                withContext(Dispatchers.IO) {
                    OcctResourceInstaller.installAndConfigure(context)
                }
                state = state.copy(phase = "Traduciendo STEP a BRep y generando malla")
                val scene = withContext(Dispatchers.Default) {
                    StepNativeBridge.importStep(
                        cachedStepPath = cached.file.absolutePath,
                        displayName = cached.displayName,
                        linearDeflection = 0.35,
                        angularDeflection = 0.30
                    )
                }
                cached.file.delete()
                scene
            }
            state = result.fold(
                onSuccess = { scene ->
                    StepScreenState(
                        scene = scene,
                        busy = false,
                        phase = "STEP importado",
                        byteCount = state.byteCount
                    )
                },
                onFailure = { error ->
                    StepScreenState(
                        scene = state.scene,
                        error = error.stackTraceToString(),
                        busy = false,
                        phase = "Error de importación",
                        byteCount = state.byteCount
                    )
                }
            )
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            startImport(uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121216)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E26))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Importador STEP nativo",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "STEPControl → BRep → OCCT Mesh",
                            color = if (state.error == null) Color(0xFF72E39A) else Color(0xFFFF7B7B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        enabled = !state.busy,
                        onClick = { picker.launch(arrayOf("*/*")) }
                    ) {
                        Text("Abrir STEP")
                    }
                }
                Text(
                    state.phase,
                    color = Color(0xFFB9B6C5),
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.scene != null -> AndroidView(
                        factory = { viewContext ->
                            CadGLSurfaceView(viewContext).apply { setMesh(state.scene.mesh) }
                        },
                        update = { view -> view.setMesh(state.scene.mesh) },
                        modifier = Modifier.fillMaxSize()
                    )
                    state.busy -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(state.phase, color = Color.White)
                    }
                    else -> Text(
                        "Pulsa ‘Abrir STEP’ y selecciona un archivo .step, .stp o .p21",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
            }

            StepDiagnostics(state)
        }
    }
}

@Composable
private fun StepDiagnostics(state: StepScreenState) {
    val scene = state.scene
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
                Text("STEP", color = Color(0xFF72E39A))
            }
            Text(
                "${scene.sourceName} · ${scene.rootCount} raíces · ${scene.shapeCount} formas",
                color = Color(0xFFB9B6C5),
                fontFamily = FontFamily.Monospace
            )
        }
        val diagnostics = state.error ?: scene?.summary ?: state.phase
        Text(
            diagnostics,
            color = if (state.error == null) Color(0xFFB9B6C5) else Color(0xFFFFA0A0),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}
