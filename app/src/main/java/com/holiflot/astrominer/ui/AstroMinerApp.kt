package com.holiflot.astrominer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holiflot.astrominer.GameViewModel

enum class Screen { Game, Unlocks, Docs }

@Composable
fun AstroMinerApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Game) }

    BackHandler(enabled = screen != Screen.Game) { screen = Screen.Game }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.Game -> GameScreen(
                state = state,
                viewModel = viewModel,
                onOpenUnlocks = { screen = Screen.Unlocks },
                onOpenDocs = { screen = Screen.Docs },
            )
            Screen.Unlocks -> UnlocksScreen(state = state, onBuy = viewModel::buy, onBack = { screen = Screen.Game })
            Screen.Docs -> DocsScreen(state = state, onBack = { screen = Screen.Game })
        }
    }
}
