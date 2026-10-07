package com.holiflot.astrominer.core.game

import kotlin.random.Random

/** Envanterde biriken kaynaklar. Koddaki adı: `Items.Stone` gibi. */
enum class Item(val title: String, val icon: String) {
    Stone("Taş", "🪨"),
    Crystal("Kristal", "💎"),
}

/** Bir hücrede duran şey. Koddaki adı: `Entities.Rock` gibi. */
enum class EntityType(val title: String) {
    Rock("Kaya"),
    Crystal("Kristal"),
}

/** Koddaki yön sabitleri. North = +y (ekranda yukarı). */
enum class Direction(val dx: Int, val dy: Int) {
    North(0, 1),
    East(1, 0),
    South(0, -1),
    West(-1, 0),
}

/** Oyun dengesini belirleyen tüm sayılar tek yerde. */
object Balance {
    /** Hareket, kazma, ekme gibi fiziksel eylemlerin süresi (tick). */
    const val ACTION_TICKS = 100L

    /** Sensör sorguları (can_mine, get_pos_x ...) çok ucuzdur. */
    const val QUERY_TICKS = 1L

    /** Her kod satırının çalışması. */
    const val STATEMENT_TICKS = 1L

    const val ROCK_GROW_TICKS = 300L
    const val CRYSTAL_GROW_TICKS = 1200L

    /** Büyüme sürelerine ±%20 rastgelelik. */
    const val GROW_VARIANCE = 0.2

    const val ROCK_YIELD = 1L
    const val CRYSTAL_YIELD = 3L

    /** "Genişletme" seviyesine göre dünya kenar uzunluğu. */
    val WORLD_SIZES = listOf(1, 3, 4, 5, 6, 8)

    /** "Hız" seviyesine göre drone hız çarpanı. */
    val SPEED_MULTIPLIERS = listOf(1.0, 1.5, 2.0, 3.0, 4.0)
}

class Tile(var entity: EntityType, var plantedAt: Long, var growTicks: Long)

data class TileSnapshot(val entity: EntityType, val growth: Float, val grown: Boolean)

data class WorldSnapshot(
    val size: Int,
    val tiles: List<TileSnapshot>,
    val droneX: Int,
    val droneY: Int,
    val time: Long,
) {
    fun tile(x: Int, y: Int) = tiles[y * size + x]
}

/**
 * Asteroit yüzeyi: size×size hücre. Büyüme tembel hesaplanır: bir hücre
 * `time - plantedAt >= growTicks` olduğunda olgundur, bu yüzden zaman ilerletmek O(1).
 */
class World(val size: Int, private val random: Random) {
    var time = 0L
        private set

    var droneX = 0
        private set
    var droneY = 0
        private set

    private val tiles = Array(size * size) {
        val grow = randomized(Balance.ROCK_GROW_TICKS)
        Tile(EntityType.Rock, plantedAt = -grow, growTicks = grow) // başlangıçta hepsi olgun
    }

    fun tile(x: Int, y: Int): Tile = tiles[y * size + x]
    fun droneTile(): Tile = tile(droneX, droneY)

    fun advance(ticks: Long) {
        time += ticks
    }

    fun isGrown(tile: Tile) = time - tile.plantedAt >= tile.growTicks

    fun growth(tile: Tile): Float = ((time - tile.plantedAt).toFloat() / tile.growTicks).coerceIn(0f, 1f)

    /** Kenarlardan sarar: doğudan çıkan batıdan girer. */
    fun moveDrone(direction: Direction) {
        droneX = Math.floorMod(droneX + direction.dx, size)
        droneY = Math.floorMod(droneY + direction.dy, size)
    }

    fun placeRock(x: Int, y: Int) {
        val grow = randomized(Balance.ROCK_GROW_TICKS)
        tile(x, y).apply {
            entity = EntityType.Rock
            plantedAt = time
            growTicks = grow
        }
    }

    /**
     * Kristal, komşu (kenar komşusu) her kristal için iki kat yavaş büyür.
     * Bu, oyuncuyu dama tahtası deseni gibi yerleşimler düşünmeye iter.
     */
    fun placeCrystal(x: Int, y: Int) {
        val neighbours = neighbours(x, y).count { it.entity == EntityType.Crystal }
        val grow = randomized(Balance.CRYSTAL_GROW_TICKS) shl neighbours
        tile(x, y).apply {
            entity = EntityType.Crystal
            plantedAt = time
            growTicks = grow
        }
    }

    fun neighbours(x: Int, y: Int): List<Tile> = Direction.entries.mapNotNull { d ->
        val nx = x + d.dx
        val ny = y + d.dy
        if (nx in 0 until size && ny in 0 until size) tile(nx, ny) else null
    }

    fun snapshot() = WorldSnapshot(
        size = size,
        tiles = tiles.map { TileSnapshot(it.entity, growth(it), isGrown(it)) },
        droneX = droneX,
        droneY = droneY,
        time = time,
    )

    private fun randomized(base: Long): Long {
        val factor = 1.0 + (random.nextDouble() * 2 - 1) * Balance.GROW_VARIANCE
        return (base * factor).toLong().coerceAtLeast(1)
    }
}
