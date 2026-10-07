package com.holiflot.astrominer.core.progress

import com.holiflot.astrominer.core.game.Balance
import com.holiflot.astrominer.core.game.Item
import com.holiflot.astrominer.core.lang.Feature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Oyuncunun kalıcı ilerlemesi: envanter ve kilit seviyeleri. */
class Progress(
    levels: Map<String, Int> = emptyMap(),
    inventory: Map<Item, Long> = emptyMap(),
) {
    private val levels = levels.toMutableMap()
    private val inventory = inventory.toMutableMap()

    fun level(id: String): Int = levels[id] ?: 0
    fun isUnlocked(id: String): Boolean = level(id) > 0

    fun count(item: Item): Long = inventory[item] ?: 0
    fun add(item: Item, amount: Long) {
        inventory[item] = count(item) + amount
    }

    fun inventory(): Map<Item, Long> = Item.entries.associateWith { count(it) }

    /** Bir sonraki seviyenin bedeli; maksimumdaysa null. */
    fun nextCost(id: String): Map<Item, Long>? = Unlocks.get(id).costs.getOrNull(level(id))

    fun requirementsMet(id: String): Boolean = Unlocks.get(id).requires.all(::isUnlocked)

    fun canAfford(cost: Map<Item, Long>) = cost.all { (item, n) -> count(item) >= n }

    fun canBuy(id: String): Boolean {
        val cost = nextCost(id) ?: return false
        return requirementsMet(id) && canAfford(cost)
    }

    fun buy(id: String): Boolean {
        if (!canBuy(id)) return false
        nextCost(id)!!.forEach { (item, n) -> inventory[item] = count(item) - n }
        levels[id] = level(id) + 1
        return true
    }

    val worldSize: Int get() = Balance.WORLD_SIZES[level("expand").coerceAtMost(Balance.WORLD_SIZES.lastIndex)]

    val speedMultiplier: Double
        get() = Balance.SPEED_MULTIPLIERS[level("speed").coerceAtMost(Balance.SPEED_MULTIPLIERS.lastIndex)]

    fun allowedFeatures(): Set<Feature> = Feature.entries.filter { isUnlocked(it.unlockId) }.toSet()

    fun toSave(code: String) = SaveData(
        levels = levels.toMap(),
        inventory = inventory.mapKeys { it.key.name },
        code = code,
    )

    companion object {
        fun fromSave(save: SaveData) = Progress(
            levels = save.levels,
            inventory = save.inventory.mapNotNull { (k, v) ->
                Item.entries.firstOrNull { it.name == k }?.let { it to v }
            }.toMap(),
        )
    }
}

@Serializable
data class SaveData(
    val version: Int = 1,
    val levels: Map<String, Int> = emptyMap(),
    val inventory: Map<String, Long> = emptyMap(),
    val code: String = "",
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): SaveData? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
