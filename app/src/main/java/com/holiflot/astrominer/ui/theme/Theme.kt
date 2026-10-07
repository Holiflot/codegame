package com.holiflot.astrominer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Oyun çizimleri ve kod editörü için sabit renkler. */
object Palette {
    val space = Color(0xFF0B0E1A)
    val panel = Color(0xFF151A2E)
    val panelHigh = Color(0xFF1E2440)
    val text = Color(0xFFE6E9F5)
    val textDim = Color(0xFF7A819E)

    val cellA = Color(0xFF1A1F35)
    val cellB = Color(0xFF171C30)
    val rockGrowing = Color(0xFF4A4F66)
    val rockGrown = Color(0xFF9AA0BC)
    val crystalGrowing = Color(0xFF2C6E85)
    val crystalGrown = Color(0xFF7FDBFF)
    val drone = Color(0xFFFFC857)

    val keyword = Color(0xFFC792EA)
    val command = Color(0xFF82AAFF)
    val constant = Color(0xFFFFC857)
    val number = Color(0xFFF78C6C)
    val string = Color(0xFFC3E88D)
    val comment = Color(0xFF5C6485)
    val error = Color(0xFFFF6B6B)
}

private val colors = darkColorScheme(
    primary = Palette.crystalGrown,
    onPrimary = Color(0xFF00222E),
    secondary = Palette.drone,
    onSecondary = Color(0xFF2A1E00),
    background = Palette.space,
    onBackground = Palette.text,
    surface = Palette.panel,
    onSurface = Palette.text,
    surfaceVariant = Palette.panelHigh,
    onSurfaceVariant = Palette.textDim,
    error = Palette.error,
)

@Composable
fun AstroMinerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
