package com.motoparts.usa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF00C8D7)
val DeepWater = Color(0xFF031B2E)
val DarkNavy = Color(0xFF061321)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Teal, onPrimary = DarkNavy,
                    background = DarkNavy, onBackground = Color.White,
                    surface = DeepWater, onSurface = Color.White,
                    surfaceVariant = DeepWater, onSurfaceVariant = Color.White,
                    secondary = Teal, error = Color(0xFFFF6B6B)
                )
            ) { AppRoot() }
        }
    }
}
