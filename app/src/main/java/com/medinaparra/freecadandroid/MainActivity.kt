package com.medinaparra.freecadandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medinaparra.freecadandroid.ui.CoreWorkspaceScreen
import com.medinaparra.freecadandroid.ui.theme.FreeCadAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeCadAndroidTheme {
                CoreWorkspaceScreen()
            }
        }
    }
}
