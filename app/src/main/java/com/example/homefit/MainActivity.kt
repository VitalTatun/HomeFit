package com.example.homefit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import com.example.homefit.navigation.HomeFitNavDisplay
import com.example.homefit.ui.theme.HomeFitTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeFitTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("HomeFit") }
                        )
                    }
                ) { innerPadding ->
                    HomeFitNavDisplay(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
