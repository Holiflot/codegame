package com.holiflot.astrominer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.holiflot.astrominer.ui.AstroMinerApp
import com.holiflot.astrominer.ui.theme.AstroMinerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstroMinerTheme {
                AstroMinerApp(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.save()
    }
}
