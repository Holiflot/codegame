package com.holiflot.astrominer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.holiflot.astrominer.GameUiState
import com.holiflot.astrominer.UnlockRow
import com.holiflot.astrominer.core.game.Item
import com.holiflot.astrominer.ui.theme.Palette

@Composable
fun UnlocksScreen(state: GameUiState, onBuy: (String) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        ScreenHeader(title = "Kilitler", onBack = onBack) { InventoryText(state.inventory) }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(state.unlocks, key = { it.def.id }) { row -> UnlockCard(row, onBuy) }
        }
    }
}

@Composable
private fun UnlockCard(row: UnlockRow, onBuy: (String) -> Unit) {
    val maxed = row.nextCost == null
    val locked = row.missing.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = if (locked) Palette.space else Palette.panel),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (locked) "🔒 ${row.def.title}" else row.def.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (row.def.maxLevel > 1) {
                    Text("Seviye ${row.level}/${row.def.maxLevel}", color = Palette.textDim)
                } else if (row.level > 0) {
                    Text("Açık ✓", color = Palette.crystalGrown)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(row.def.description, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
            if (locked) {
                Spacer(Modifier.height(6.dp))
                Text("Gerekli: ${row.missing.joinToString()}", style = MaterialTheme.typography.bodySmall)
            } else if (!maxed) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(costText(row.nextCost!!), modifier = Modifier.weight(1f))
                    Button(onClick = { onBuy(row.def.id) }, enabled = row.canBuy) {
                        Text(if (row.level == 0) "Aç" else "Yükselt")
                    }
                }
            }
        }
    }
}

private fun costText(cost: Map<Item, Long>) = cost.entries.joinToString("  ") { (item, n) -> "${item.icon} $n" }

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("← Geri") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}
