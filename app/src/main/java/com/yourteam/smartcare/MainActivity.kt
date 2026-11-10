package com.yourteam.smartcare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.firebase.FirebaseApp // 🚨 ADDED: Firebase Import
import com.yourteam.smartcare.ui.navigation.NavGraph
import com.yourteam.smartcare.ui.theme.SmartCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            // 🚨 ADDED: Turn on the Firebase Engine before the UI loads!
            FirebaseApp.initializeApp(this)

            setContent {
                SmartCareTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavGraph()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("SmartCareCrash", "FATAL CRASH IN ONCREATE: ${e.message}", e)
        }
    }
}