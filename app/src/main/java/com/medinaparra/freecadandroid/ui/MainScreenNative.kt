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
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.medinaparra.freecadandroid.nativebridge.NativeBoxBridge
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView

@Composable
fun MainScreenNative() {
    val nativeResult = remember {
        runCatching {
            NativeBoxBridge.createScene() to NativeBoxBridge.buildInfo()
        }
    }
    val nativeData = nativeResult.getOrNull()
    val nativeError = nativeResult.exceptionOrNull()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121216)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E26))
                    .padding(16.dp)
            ) {
                Text(
                    text = "FreeCAD Android Native",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (nativeData != null) "NATIVE C++ ACTIVO" else "ERROR CARGANDO NDK",
                    color = if (nativeData != null) Color(0xFF72E39A) else Color(0xFFFF7B7B),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = nativeData?.second ?: nativeError?.stackTraceToString().orEmpty(),
                    color = Color(0xFFB9B6C5),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (nativeData != null) {
                val mesh = nativeData.first
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { context ->
                            CadGLSurfaceView(context).apply { setMesh(mesh) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("24 vértices", color = Color.White)
                    Text("12 triángulos", color = Color.White)
                    Text("Origen: C++", color = Color(0xFF72E39A))
                }
            } else {
                Text(
                    text = "La biblioteca libfreecad_android_core.so no pudo ejecutarse en este dispositivo.",
                    color = Color.White,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}
