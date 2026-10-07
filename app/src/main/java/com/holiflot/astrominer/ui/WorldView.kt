package com.holiflot.astrominer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.holiflot.astrominer.core.game.EntityType
import com.holiflot.astrominer.core.game.TileSnapshot
import com.holiflot.astrominer.core.game.WorldSnapshot
import com.holiflot.astrominer.ui.theme.Palette

/** Asteroit yüzeyini çizer. y=0 en altta (South), North yukarı. */
@Composable
fun WorldView(world: WorldSnapshot, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.space),
    ) {
        val n = world.size
        val cell = size.minDimension / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                val topLeft = Offset(x * cell, (n - 1 - y) * cell)
                drawRect(
                    color = if ((x + y) % 2 == 0) Palette.cellA else Palette.cellB,
                    topLeft = topLeft,
                    size = Size(cell, cell),
                )
                val center = topLeft + Offset(cell / 2, cell / 2)
                val tile = world.tile(x, y)
                when (tile.entity) {
                    EntityType.Rock -> drawRock(center, cell, tile)
                    EntityType.Crystal -> drawCrystal(center, cell, tile)
                }
            }
        }
        drawDrone(Offset(world.droneX * cell + cell / 2, (n - 1 - world.droneY) * cell + cell / 2), cell)
    }
}

private fun DrawScope.drawRock(center: Offset, cell: Float, tile: TileSnapshot) {
    val radius = cell * (0.12f + 0.22f * tile.growth)
    drawCircle(color = if (tile.grown) Palette.rockGrown else Palette.rockGrowing, radius = radius, center = center)
    if (tile.grown) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = radius * 0.25f,
            center = center + Offset(radius * 0.3f, radius * 0.2f),
        )
    }
}

private fun DrawScope.drawCrystal(center: Offset, cell: Float, tile: TileSnapshot) {
    val h = cell * (0.12f + 0.3f * tile.growth)
    val w = h * 0.6f
    val path = Path().apply {
        moveTo(center.x, center.y - h)
        lineTo(center.x + w, center.y)
        lineTo(center.x, center.y + h)
        lineTo(center.x - w, center.y)
        close()
    }
    drawPath(path, color = if (tile.grown) Palette.crystalGrown else Palette.crystalGrowing)
    if (tile.grown) {
        drawPath(path, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = cell * 0.02f))
    }
}

private fun DrawScope.drawDrone(center: Offset, cell: Float) {
    drawCircle(color = Palette.drone.copy(alpha = 0.18f), radius = cell * 0.45f, center = center)
    drawCircle(color = Palette.drone, radius = cell * 0.2f, center = center, style = Stroke(width = cell * 0.05f))
    val arm = cell * 0.3f
    listOf(Offset(-arm, -arm), Offset(arm, -arm), Offset(-arm, arm), Offset(arm, arm)).forEach { d ->
        drawCircle(color = Palette.drone, radius = cell * 0.05f, center = center + d)
    }
}
