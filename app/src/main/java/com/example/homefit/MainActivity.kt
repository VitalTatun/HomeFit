package com.example.homefit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.homefit.navigation.HomeFitNavDisplay
import com.example.homefit.ui.theme.HomeFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The M3 NavigationBar draws its own opaque background over the
        // system navigation area, so the enforced contrast scrim must be off
        // (edge-to-edge skill: Scaffold with a bottom bar, SDK 29+).
        window.isNavigationBarContrastEnforced = false
        setContent {
            HomeFitTheme {
                HomeFitNavDisplay(
                    workoutRepository = (application as HomeFitApplication).workoutRepository,
                )
            }
        }
    }
}
