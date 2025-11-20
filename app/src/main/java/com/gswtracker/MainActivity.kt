package com.gswtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gswtracker.ui.GameScreen
import com.gswtracker.ui.theme.GswTrackerTheme
import com.gswtracker.ui.viewmodel.GameViewModel
import com.gswtracker.ui.viewmodel.GameViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GswTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GameViewModel = viewModel(
                        factory = GameViewModelFactory(applicationContext)
                    )
                    GameScreen(viewModel = viewModel)
                }
            }
        }
    }
}
