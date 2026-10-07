package com.holiflot.astrominer.core.progress

import com.holiflot.astrominer.core.game.Item
import com.holiflot.astrominer.core.game.Item.Crystal
import com.holiflot.astrominer.core.game.Item.Stone

/**
 * Satın alınabilir bir yükseltme. [costs] listesinin her elemanı bir seviyenin bedelidir;
 * maksimum seviye = costs.size.
 */
data class UnlockDef(
    val id: String,
    val title: String,
    val description: String,
    val costs: List<Map<Item, Long>>,
    val requires: List<String> = emptyList(),
) {
    val maxLevel get() = costs.size
}

private fun cost(vararg pairs: Pair<Item, Int>): Map<Item, Long> =
    pairs.associate { it.first to it.second.toLong() }

/** Kilit ağacı. Sıra, kilitler ekranındaki sıradır. */
object Unlocks {
    val all: List<UnlockDef> = listOf(
        UnlockDef(
            id = "while",
            title = "Döngüler",
            description = "while döngüsü. Artık Çalıştır'a her seferinde basmana gerek yok.",
            costs = listOf(cost(Stone to 5)),
        ),
        UnlockDef(
            id = "expand",
            title = "Genişletme",
            description = "Madencilik alanını büyütür. İlk seviye move() komutunu ve yönleri açar.",
            costs = listOf(
                cost(Stone to 20),
                cost(Stone to 80),
                cost(Stone to 200, Crystal to 20),
                cost(Stone to 500, Crystal to 80),
                cost(Stone to 1200, Crystal to 250),
            ),
            requires = listOf("while"),
        ),
        UnlockDef(
            id = "senses",
            title = "Sensörler",
            description = "can_mine() ve get_entity_type(): drone altındakini algılar.",
            costs = listOf(cost(Stone to 15)),
            requires = listOf("while"),
        ),
        UnlockDef(
            id = "if",
            title = "Koşullar",
            description = "if / elif / else ile karar ver.",
            costs = listOf(cost(Stone to 30)),
            requires = listOf("senses"),
        ),
        UnlockDef(
            id = "speed",
            title = "Hız",
            description = "Drone eylemleri daha kısa sürer.",
            costs = listOf(
                cost(Stone to 40),
                cost(Stone to 150),
                cost(Stone to 400, Crystal to 40),
                cost(Stone to 1000, Crystal to 150),
            ),
            requires = listOf("expand"),
        ),
        UnlockDef(
            id = "operators",
            title = "Operatörler",
            description = "Aritmetik (+ - * / // %), karşılaştırma (== < >) ve mantık (and or not); abs, min, max.",
            costs = listOf(cost(Stone to 50)),
            requires = listOf("if"),
        ),
        UnlockDef(
            id = "crystal",
            title = "Kristaller",
            description = "plant(Entities.Crystal). Kristaller yavaş büyür ama değerlidir. Yan yana ekilenler daha da yavaş büyür!",
            costs = listOf(cost(Stone to 80)),
            requires = listOf("expand", "if"),
        ),
        UnlockDef(
            id = "position",
            title = "Konum",
            description = "get_pos_x(), get_pos_y() ve get_world_size().",
            costs = listOf(cost(Stone to 60)),
            requires = listOf("expand", "operators"),
        ),
        UnlockDef(
            id = "variables",
            title = "Değişkenler",
            description = "x = 5 gibi atamalar ve num_items(Items.Stone).",
            costs = listOf(cost(Stone to 100)),
            requires = listOf("operators"),
        ),
        UnlockDef(
            id = "for",
            title = "For döngüsü",
            description = "for i in range(n): ile sayarak döngü kur.",
            costs = listOf(cost(Stone to 150, Crystal to 10)),
            requires = listOf("variables"),
        ),
        UnlockDef(
            id = "functions",
            title = "Fonksiyonlar",
            description = "def ile kendi komutlarını yaz; return ve global.",
            costs = listOf(cost(Stone to 250, Crystal to 30)),
            requires = listOf("for"),
        ),
        UnlockDef(
            id = "lists",
            title = "Listeler",
            description = "[1, 2, 3], indeksleme, append/pop/insert, len() ve 'in'.",
            costs = listOf(cost(Stone to 400, Crystal to 60)),
            requires = listOf("functions"),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): UnlockDef = byId[id] ?: error("Bilinmeyen kilit: $id")
}
