package com.holiflot.astrominer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holiflot.astrominer.GameUiState
import com.holiflot.astrominer.GameViewModel
import com.holiflot.astrominer.core.game.Item
import com.holiflot.astrominer.ui.theme.Palette

@Composable
fun GameScreen(
    state: GameUiState,
    viewModel: GameViewModel,
    onOpenUnlocks: () -> Unit,
    onOpenDocs: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp),
    ) {
        TopBar(
            inventory = state.inventory,
            hasAffordableUnlock = state.unlocks.any { it.canBuy },
            onOpenUnlocks = onOpenUnlocks,
            onOpenDocs = onOpenDocs,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.42f),
            contentAlignment = Alignment.Center,
        ) {
            WorldView(state.world, Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true))
        }
        ControlBar(
            state = state,
            onRun = viewModel::run,
            onStop = viewModel::stop,
            onStep = viewModel::step,
            onResume = viewModel::resume,
            onSpeed = viewModel::cycleSpeed,
        )
        ConsoleView(state)
        QuickInsertBar(
            snippets = quickSnippets(state),
            enabled = !state.running,
            onInsert = viewModel::insert,
            onIndent = viewModel::indent,
            onOutdent = viewModel::outdent,
        )
        CodeEditor(
            value = viewModel.code,
            onValueChange = viewModel::onCodeChange,
            currentLine = state.currentLine,
            errorLines = state.errorLines,
            readOnly = state.running,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun TopBar(
    inventory: Map<Item, Long>,
    hasAffordableUnlock: Boolean,
    onOpenUnlocks: () -> Unit,
    onOpenDocs: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InventoryText(inventory, Modifier.weight(1f))
        TextButton(onClick = onOpenDocs) { Text("Belgeler") }
        TextButton(onClick = onOpenUnlocks) {
            Text(if (hasAffordableUnlock) "Kilitler ●" else "Kilitler")
        }
    }
}

@Composable
fun InventoryText(inventory: Map<Item, Long>, modifier: Modifier = Modifier) {
    Text(
        text = inventory.entries.joinToString("   ") { (item, n) -> "${item.icon} $n" },
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

@Composable
private fun ControlBar(
    state: GameUiState,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onStep: () -> Unit,
    onResume: () -> Unit,
    onSpeed: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.running) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("■ Durdur") }
        } else {
            Button(onClick = onRun) { Text("▶ Çalıştır") }
        }
        OutlinedButton(onClick = onStep, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Adım") }
        if (state.stepping) {
            OutlinedButton(onClick = onResume, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Devam") }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSpeed) { Text(state.speed.label) }
    }
}

@Composable
private fun ConsoleView(state: GameUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.panel)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val lines = state.console.takeLast(3)
        if (lines.isEmpty()) {
            Text("Konsol", color = Palette.textDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        lines.forEach { line ->
            Text(
                text = line.text,
                color = if (line.isError) Palette.error else Palette.text,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickInsertBar(
    snippets: List<String>,
    enabled: Boolean,
    onInsert: (String) -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
) {
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Chip("⇤", enabled, onOutdent) }
        item { Chip("⇥", enabled, onIndent) }
        item { Spacer(Modifier.width(4.dp)) }
        items(snippets) { snippet -> Chip(snippet, enabled) { onInsert(snippet) } }
    }
}

@Composable
private fun Chip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.panelHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (enabled) Palette.text else Palette.textDim,
        )
    }
}
