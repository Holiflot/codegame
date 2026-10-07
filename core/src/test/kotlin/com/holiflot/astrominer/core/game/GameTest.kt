package com.holiflot.astrominer.core.game

import com.holiflot.astrominer.core.progress.Progress
import com.holiflot.astrominer.core.progress.SaveData
import com.holiflot.astrominer.core.progress.Unlocks
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameTest {
    private fun runCode(game: Game, source: String) {
        val result = game.compile(source)
        if (result is CompileResult.Failure) error(result.errors.joinToString { it.display() })
        runBlocking { game.run((result as CompileResult.Success).program) }
    }

    @Test
    fun freshGameOnlyAllowsMine() {
        val game = Game(random = Random(1))
        assertEquals(1, game.world.size)
        runCode(game, "mine()")
        assertEquals(1, game.progress.count(Item.Stone))

        val locked = game.compile("while True:\n    mine()")
        assertIs<CompileResult.Failure>(locked)
        assertTrue("Döngüler" in locked.errors.single().message)
    }

    @Test
    fun rockMustRegrowBeforeMiningAgain() {
        val game = Game(random = Random(1))
        assertTrue(game.mine())
        assertFalse(game.mine())
        game.idle(Balance.ROCK_GROW_TICKS * 2)
        assertTrue(game.mine())
        assertEquals(2, game.progress.count(Item.Stone))
    }

    @Test
    fun buyingExpandGrowsWorldAndUnlocksMove() {
        val game = Game(Progress(inventory = mapOf(Item.Stone to 100)), Random(1))
        assertTrue(game.buy("while"))
        assertTrue(game.buy("expand"))
        assertEquals(3, game.world.size)
        assertEquals(75, game.progress.count(Item.Stone))

        runCode(game, "move(East)\nmove(North)\nmove(West)\nmove(West)")
        assertEquals(2, game.world.droneX)
        assertEquals(1, game.world.droneY)
    }

    @Test
    fun cannotBuyWithoutRequirements() {
        val game = Game(Progress(inventory = mapOf(Item.Stone to 1000)))
        assertFalse(game.buy("expand")) // "while" gerekli
        assertTrue(game.buy("while"))
        assertTrue(game.buy("expand"))
    }

    @Test
    fun adjacentCrystalsGrowSlower() {
        val all = Unlocks.all.associate { it.id to it.maxLevel }
        val game = Game(Progress(levels = all), Random(1))
        val world = game.world
        game.plant(EntityType.Crystal)
        game.move(Direction.East)
        game.plant(EntityType.Crystal)
        val first = world.tile(0, 0)
        val second = world.tile(1, 0)
        assertTrue(second.growTicks > first.growTicks * 1.5)
    }

    @Test
    fun crystalHarvestLoop() {
        val all = Unlocks.all.associate { it.id to it.maxLevel }
        val game = Game(Progress(levels = all), Random(7))
        runCode(
            game,
            """
            def each_tile(action):
                for y in range(get_world_size()):
                    for x in range(get_world_size()):
                        action()
                        move(East)
                    move(North)

            def plant_checker():
                if (get_pos_x() + get_pos_y()) % 2 == 0:
                    mine()
                    plant(Entities.Crystal)

            def harvest():
                if get_entity_type() == Entities.Crystal:
                    while not can_mine():
                        pass
                    mine()

            each_tile(plant_checker)
            each_tile(harvest)
            """.trimIndent(),
        )
        val tiles = game.world.size * game.world.size
        assertEquals(((tiles + 1) / 2) * Balance.CRYSTAL_YIELD, game.progress.count(Item.Crystal))
    }

    @Test
    fun saveRoundTrip() {
        val progress = Progress(levels = mapOf("while" to 1), inventory = mapOf(Item.Stone to 42))
        val json = progress.toSave("mine()").toJson()
        val loaded = SaveData.fromJson(json)!!
        assertEquals("mine()", loaded.code)
        val restored = Progress.fromSave(loaded)
        assertEquals(42, restored.count(Item.Stone))
        assertTrue(restored.isUnlocked("while"))
    }

    @Test
    fun everyCommandHasAKnownUnlock() {
        Commands.all.mapNotNull { it.unlockId }.forEach { Unlocks.get(it) }
        Unlocks.all.flatMap { it.requires }.forEach { Unlocks.get(it) }
    }
}
