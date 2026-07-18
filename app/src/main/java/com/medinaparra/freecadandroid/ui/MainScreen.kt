package com.medinaparra.freecadandroid.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.medinaparra.freecadandroid.backend.SimulatorCadBackend
import com.medinaparra.freecadandroid.viewer.CadGLSurfaceView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val backend = remember { SimulatorCadBackend() }
    val demoMesh = remember { backend.createDemoScene() }
    val caps = backend.capabilities

    // Hold reference to GLSurfaceView for interactive controls (Reset View, Zoom)
    var glViewRef by remember { mutableStateOf<CadGLSurfaceView?>(null) }

    // Pulsing animation for the simulator status dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Scaffold(
        topBar = {
            // Elegant Dark Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFF1C1B1F))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FreeCAD Android Native",
                        color = Color(0xFFE6E1E5),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp
                        )
                    )
                    
                    // Decorative sci-fi menu lines
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(width = 24.dp, height = 2.dp).background(Color(0xFFE6E1E5)))
                        Box(modifier = Modifier.size(width = 16.dp, height = 2.dp).background(Color(0xFFE6E1E5)))
                        Box(modifier = Modifier.size(width = 20.dp, height = 2.dp).background(Color(0xFFE6E1E5)))
                    }
                }
                
                // Bottom divider matching design border-[#49454F]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF49454F))
                        .align(Alignment.BottomCenter)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1C1B1F))
        ) {
            // Simulator Status Warning Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF4F378B).copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulse Dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD0BCFF).copy(alpha = dotAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SIMULADOR",
                            color = Color(0xFFD0BCFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Text(
                        text = "ID: DEV_7743_GLES30",
                        color = Color(0xFFCAC4D0),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp
                    )
                }
                // Border bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF49454F))
                        .align(Alignment.BottomCenter)
                )
            }

            // Main 3D Viewport with overlays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                // OpenGL 3D Surface View
                AndroidView(
                    factory = { context ->
                        CadGLSurfaceView(context).apply {
                            setMesh(demoMesh)
                            glViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 1. HUD: Metadata overlay (Top-Left)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "MESH: BOX_DEMO", color = Color(0xFFD0BCFF).copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "VERTICES: 8", color = Color(0xFFD0BCFF).copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "INDICES: 36", color = Color(0xFFD0BCFF).copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "STRIDE: 24b", color = Color(0xFFD0BCFF).copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                // 2. HUD: Interactive circular controls (Bottom-Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Reset View Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF49454F).copy(alpha = 0.5f))
                                .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    glViewRef?.let { view ->
                                        view.renderer.cameraController.resetToBox(
                                            demoMesh.minX, demoMesh.minY, demoMesh.minZ,
                                            demoMesh.maxX, demoMesh.maxY, demoMesh.maxZ
                                        )
                                        view.requestRender()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Camera View",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Zoom In Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF49454F).copy(alpha = 0.5f))
                                .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    glViewRef?.let { view ->
                                        view.renderer.cameraController.handleZoom(1.25f)
                                        view.requestRender()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Zoom Out Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF49454F).copy(alpha = 0.5f))
                                .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    glViewRef?.let { view ->
                                        view.renderer.cameraController.handleZoom(0.8f)
                                        view.requestRender()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Middle warning statement bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF211F26))
                    .border(width = 1.dp, color = Color(0xFF49454F))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "OpenCASCADE, FreeCAD y Python todavía no están integrados.",
                    color = Color(0xFFCAC4D0),
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 14.sp
                )
            }

            // Footer: 2-column list of platform capabilities
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1B1F))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Column Left
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CapabilityFooterRow("Backend", caps.backendName, isOk = true)
                            CapabilityFooterRow("OpenCASCADE", "NO", isOk = false)
                            CapabilityFooterRow("FreeCAD", "NO", isOk = false)
                        }

                        // Column Right
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CapabilityFooterRow("Python", "NO", isOk = false)
                            CapabilityFooterRow("STEP", "NO", isOk = false)
                            CapabilityFooterRow("FCStd", "NO", isOk = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Decorative bottom navigation handle bar representing full Android system integration
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFFE6E1E5).copy(alpha = 0.2f))
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityFooterRow(label: String, value: String, isOk: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF938F99),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                color = if (isOk) Color(0xFFD0BCFF) else Color(0xFFF2B8B5),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        // Bottom border line border-[#49454F]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF49454F))
        )
    }
}
