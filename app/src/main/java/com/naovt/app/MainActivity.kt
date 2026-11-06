package com.naovt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.naovt.app.ui.NaoApp
import com.naovt.app.ui.theme.NaoVTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NaoVTTheme {
                NaoApp()
            }
        }
    }
}
