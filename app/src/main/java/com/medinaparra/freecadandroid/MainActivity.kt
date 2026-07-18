package com.medinaparra.freecadandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medinaparra.freecadandroid.ui.MainScreen
import com.medinaparra.freecadandroid.ui.theme.FreeCadAndroidTheme

/**
 * MainActivity is the primary entry point of the FreeCADAndroidNative application.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeCadAndroidTheme {
                MainScreen()
            }
        }
    }
}
