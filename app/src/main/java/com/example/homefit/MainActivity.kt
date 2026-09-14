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
        setContent {
            HomeFitTheme {
                HomeFitNavDisplay(
                    workoutRepository = (application as HomeFitApplication).workoutRepository,
                )
            }
        }
    }
}
