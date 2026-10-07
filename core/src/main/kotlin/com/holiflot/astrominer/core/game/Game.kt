package com.holiflot.astrominer.core.game

import com.holiflot.astrominer.core.lang.BoolValue
import com.holiflot.astrominer.core.lang.BuiltinFunction
import com.holiflot.astrominer.core.lang.EnumValue
import com.holiflot.astrominer.core.lang.Feature
import com.holiflot.astrominer.core.lang.FeatureChecker
import com.holiflot.astrominer.core.lang.IntValue
import com.holiflot.astrominer.core.lang.Interpreter
import com.holiflot.astrominer.core.lang.InterpreterHost
import com.holiflot.astrominer.core.lang.NamespaceValue
import com.holiflot.astrominer.core.lang.NoneValue
import com.holiflot.astrominer.core.lang.Parser
import com.holiflot.astrominer.core.lang.Program
import com.holiflot.astrominer.core.lang.ScriptError
import com.holiflot.astrominer.core.lang.Value
import com.holiflot.astrominer.core.progress.Progress
import com.holiflot.astrominer.core.progress.Unlocks
import kotlin.math.ceil
import kotlin.random.Random

/** Çalışan programı arayüze bağlayan dinleyici. Varsayılanlar test için hiçbir şey yapmaz. */
interface RunListener {
    /** Bir satır çalışmadan hemen önce. Adım modu burada bekleyebilir. */
    suspend fun onStatement(line: Int) {}

    /** Oyun zamanı ilerledi; arayüz burada gerçek zamanda bekleyerek hızı ayarlar. */
    suspend fun onTicks(ticks: Long) {}

    fun onPrint(text: String) {}
}

sealed interface CompileResult {
    class Success(val program: Program) : CompileResult
    class Failure(val errors: List<ScriptError>) : CompileResult
}

/** Oyunun kalbi: ilerleme + dünya + yorumlayıcı bağlantısı. Arayüzden bağımsızdır. */
class Game(
    val progress: Progress = Progress(),
    private val random: Random = Random.Default,
) {
    var world: World = World(progress.worldSize, random)
        private set

    fun snapshot(): WorldSnapshot = world.snapshot()

    /** Satın almalar sonrası (ör. Genişletme) dünyayı yeni boyuta getirir. */
    fun syncWorldSize() {
        if (world.size != progress.worldSize) world = World(progress.worldSize, random)
    }

    fun buy(unlockId: String): Boolean = progress.buy(unlockId).also { if (it) syncWorldSize() }

    /** Program çalışmıyorken de zaman akar ki kayalar büyüsün. */
    fun idle(ticks: Long) = world.advance(ticks)

    val actionTicks: Long get() = ceil(Balance.ACTION_TICKS / progress.speedMultiplier).toLong()

    // ---- Primitive eylemler (yorumlayıcıdan bağımsız, testlerde doğrudan kullanılabilir) ----

    fun move(direction: Direction) = world.moveDrone(direction)

    fun mine(): Boolean {
        val tile = world.droneTile()
        val grown = world.isGrown(tile)
        when (tile.entity) {
            EntityType.Rock -> {
                if (!grown) return false
                progress.add(Item.Stone, Balance.ROCK_YIELD)
            }
            EntityType.Crystal -> if (grown) progress.add(Item.Crystal, Balance.CRYSTAL_YIELD)
        }
        world.placeRock(world.droneX, world.droneY)
        return grown
    }

    fun canMine(): Boolean = world.isGrown(world.droneTile())

    fun plant(entity: EntityType): Boolean {
        if (entity != EntityType.Crystal) throw ScriptError("Sadece Entities.Crystal ekilebilir")
        if (world.droneTile().entity == EntityType.Crystal) return false
        world.placeCrystal(world.droneX, world.droneY)
        return true
    }

    // ---- Derleme ----

    fun compile(source: String): CompileResult {
        val program = try {
            Parser.parse(source)
        } catch (e: ScriptError) {
            return CompileResult.Failure(listOf(e))
        }
        val featureTitles = Feature.entries.associateWith { Unlocks.get(it.unlockId).title }
        val errors = FeatureChecker(progress.allowedFeatures(), lockedNames(), featureTitles).check(program)
        return if (errors.isEmpty()) CompileResult.Success(program) else CompileResult.Failure(errors)
    }

    /** Henüz açılmamış komut ve sabitler → kilidin başlığı. */
    fun lockedNames(): Map<String, String> {
        val locked = HashMap<String, String>()
        for (cmd in Commands.all) {
            val id = cmd.unlockId ?: continue
            if (!progress.isUnlocked(id)) locked[cmd.name] = Unlocks.get(id).title
        }
        for ((name, feature) in Interpreter.LANGUAGE_BUILTIN_FEATURES) {
            if (feature != null && !progress.isUnlocked(feature.unlockId)) {
                locked[name] = Unlocks.get(feature.unlockId).title
            }
        }
        return locked
    }

    // ---- Çalıştırma ----

    suspend fun run(program: Program, listener: RunListener = object : RunListener {}) {
        val host = object : InterpreterHost {
            override suspend fun onStatement(line: Int) {
                listener.onStatement(line)
                spend(Balance.STATEMENT_TICKS, listener)
            }

            override fun print(text: String) = listener.onPrint(text)
        }
        Interpreter(host, builtins(listener)).run(program)
    }

    private suspend fun spend(ticks: Long, listener: RunListener) {
        world.advance(ticks)
        listener.onTicks(ticks)
    }

    private fun builtins(listener: RunListener): Map<String, Value> {
        val directions = Direction.entries.associate { it.name to EnumValue("Direction", it.name) }
        val entities = NamespaceValue("Entities", EntityType.entries.associate { it.name to EnumValue("Entities", it.name) })
        val items = NamespaceValue("Items", Item.entries.associate { it.name to EnumValue("Items", it.name) })

        fun action(name: String, arity: IntRange, block: (List<Value>) -> Value) =
            BuiltinFunction(name, arity) { args ->
                val result = block(args)
                spend(actionTicks, listener)
                result
            }

        fun query(name: String, block: (List<Value>) -> Value) =
            BuiltinFunction(name, if (name == "num_items") 1..1 else 0..0) { args ->
                val result = block(args)
                spend(Balance.QUERY_TICKS, listener)
                result
            }

        val functions = listOf(
            action("move", 1..1) { args ->
                val dir = args[0] as? EnumValue
                if (dir == null || dir.type != "Direction") {
                    throw ScriptError("move() bir yön ister: North, East, South veya West")
                }
                move(Direction.valueOf(dir.name))
                NoneValue
            },
            action("mine", 0..0) { BoolValue.of(mine()) },
            action("plant", 1..1) { args ->
                val e = args[0] as? EnumValue
                if (e == null || e.type != "Entities") throw ScriptError("plant() bir tür ister: Entities.Crystal")
                BoolValue.of(plant(EntityType.valueOf(e.name)))
            },
            query("can_mine") { BoolValue.of(canMine()) },
            query("get_entity_type") { EnumValue("Entities", world.droneTile().entity.name) },
            query("get_pos_x") { IntValue(world.droneX.toLong()) },
            query("get_pos_y") { IntValue(world.droneY.toLong()) },
            query("get_world_size") { IntValue(world.size.toLong()) },
            query("num_items") { args ->
                val item = args[0] as? EnumValue
                if (item == null || item.type != "Items") throw ScriptError("num_items() bir eşya ister: Items.Stone")
                IntValue(progress.count(Item.valueOf(item.name)))
            },
        ).associateBy { it.name }

        return functions + directions + mapOf("Entities" to entities, "Items" to items)
    }
}
