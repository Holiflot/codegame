package com.holiflot.astrominer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.holiflot.astrominer.GameUiState
import com.holiflot.astrominer.core.game.Commands
import com.holiflot.astrominer.core.lang.Feature
import com.holiflot.astrominer.core.progress.Unlocks
import com.holiflot.astrominer.ui.theme.Palette

private data class DocEntry(val signature: String, val description: String, val unlocked: Boolean, val unlockTitle: String?)

@Composable
fun DocsScreen(state: GameUiState, onBack: () -> Unit) {
    val language = Feature.entries.map { f ->
        val def = Unlocks.get(f.unlockId)
        DocEntry(f.keyword, def.description, f in state.features, def.title)
    }
    val commands = Commands.all.map { c ->
        DocEntry(
            signature = c.signature,
            description = c.description,
            unlocked = c.unlockId == null || c.unlockId in state.unlockedIds,
            unlockTitle = c.unlockId?.let { Unlocks.get(it).title },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        ScreenHeader(title = "Belgeler", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SectionTitle("Komutlar") }
            items(commands) { DocItem(it) }
            item { SectionTitle("Dil özellikleri") }
            items(language) { DocItem(it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DocItem(entry: DocEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = if (entry.unlocked) entry.signature else "🔒 ${entry.signature}",
            fontFamily = FontFamily.Monospace,
            color = if (entry.unlocked) Palette.command else Palette.textDim,
        )
        Text(
            text = if (entry.unlocked || entry.unlockTitle == null) {
                entry.description
            } else {
                "${entry.description}\n'${entry.unlockTitle}' kilidiyle açılır."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.textDim,
        )
    }
}
