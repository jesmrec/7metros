package com.example.a7metros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.a7metros.presentation.MainScreen
import com.example.a7metros.presentation.ScoresViewModel
import com.example.a7metros.presentation.theme._7metrosTheme

class MainActivity : ComponentActivity() {

    private val scoresViewModel: ScoresViewModel by viewModels { CompositionRoot.scoresViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            _7metrosTheme {
                MainScreen(scoresViewModel)
            }
        }
    }
}
