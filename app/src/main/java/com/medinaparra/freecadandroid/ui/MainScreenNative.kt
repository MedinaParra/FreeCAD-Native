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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeCadScene
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainScreenNative() {
    val nativeResult = produceState<Result<NativeCadScene>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            runCatching { NativeCadBridge.createOcctDemoScene() }
        }
    }.value

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121216)) {
        when {
            nativeResult == null -> LoadingCore()
            nativeResult.isFailure -> NativeCoreFailure(nativeResult.exceptionOrNull())
            else -> NativeCoreViewer(checkNotNull(nativeResult.getOrNull()))
        }
    }
}

@Composable
private fun LoadingCore() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("Inicializando OpenCASCADE…", color = Color.White)
        }
    }
}

@Composable
private fun NativeCoreFailure(error: Throwable?) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "ERROR EN FREECAD CORE",
            color = Color(0xFFFF7B7B),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            error?.stackTraceToString().orEmpty(),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NativeCoreViewer(scene: NativeCadScene) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E26))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "FreeCAD Android Core",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "OCCT BREP + DOCUMENT + RECOMPUTE ACTIVOS",
                color = Color(0xFF72E39A),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = scene.buildInfo,
                color = Color(0xFFB9B6C5),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context ->
                    CadGLSurfaceView(context).apply { setMesh(scene.mesh) }
                },
                update = { view -> view.setMesh(scene.mesh) },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${scene.mesh.vertexCount} vértices", color = Color.White)
            Text("${scene.mesh.triangleCount} triángulos", color = Color.White)
            Text("Origen: OCCT", color = Color(0xFF72E39A))
        }
        Text(
            text = scene.documentSummary.trim(),
            color = Color(0xFFB9B6C5),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
