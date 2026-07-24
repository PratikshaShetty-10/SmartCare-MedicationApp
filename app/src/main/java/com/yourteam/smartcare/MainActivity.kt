package com.yourteam.smartcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.yourteam.smartcare.ui.navigation.NavGraph
import com.yourteam.smartcare.ui.theme.SmartCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCareTheme {
                // Calls the central routing graph which handles its own state
                NavGraph()
            }
        }
    }
}