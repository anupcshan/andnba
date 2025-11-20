package `in`.anupcshan.gswtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.anupcshan.gswtracker.ui.GameScreen
import `in`.anupcshan.gswtracker.ui.theme.GswTrackerTheme
import `in`.anupcshan.gswtracker.ui.viewmodel.GameViewModel
import `in`.anupcshan.gswtracker.ui.viewmodel.GameViewModelFactory

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
