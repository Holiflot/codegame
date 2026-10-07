package com.holiflot.astrominer.core.game

/**
 * Oyuncuya görünen bir komut ya da sabit. Belgeler ekranı ve hızlı ekleme çubuğu bu
 * listeden beslenir.
 *
 * @param unlockId null ise baştan açıktır
 * @param snippet hızlı ekleme çubuğuna dokununca eklenen metin
 */
data class CommandDoc(
    val name: String,
    val signature: String,
    val description: String,
    val unlockId: String?,
    val snippet: String,
)

object Commands {
    val all: List<CommandDoc> = listOf(
        CommandDoc(
            "mine", "mine()",
            "Drone'un altındaki şeyi kazar. Olgun kayadan Taş, olgun kristalden Kristal verir. " +
                "Olgunlaşmamış kristali kazarsan kırılır! Bir şey kazıldıysa True döner.",
            null, "mine()",
        ),
        CommandDoc(
            "move", "move(yön)",
            "Drone'u bir hücre North, East, South veya West yönüne götürür. Kenardan çıkınca karşı kenardan girer.",
            "expand", "move(North)",
        ),
        CommandDoc("North", "North", "Yukarı yön.", "expand", "North"),
        CommandDoc("East", "East", "Sağ yön.", "expand", "East"),
        CommandDoc("South", "South", "Aşağı yön.", "expand", "South"),
        CommandDoc("West", "West", "Sol yön.", "expand", "West"),
        CommandDoc(
            "can_mine", "can_mine()",
            "Altındaki şey olgunsa True döner.",
            "senses", "can_mine()",
        ),
        CommandDoc(
            "get_entity_type", "get_entity_type()",
            "Altındaki şeyin türünü döner: Entities.Rock veya Entities.Crystal.",
            "senses", "get_entity_type()",
        ),
        CommandDoc(
            "Entities", "Entities.Rock / Entities.Crystal",
            "Hücrede durabilecek şeylerin türleri.",
            "senses", "Entities.",
        ),
        CommandDoc(
            "plant", "plant(Entities.Crystal)",
            "Altındaki hücreye kristal eker (zaten kristal varsa False döner). Komşu her kristal büyümeyi iki kat yavaşlatır.",
            "crystal", "plant(Entities.Crystal)",
        ),
        CommandDoc("get_pos_x", "get_pos_x()", "Drone'un x konumu (0 = en batı).", "position", "get_pos_x()"),
        CommandDoc("get_pos_y", "get_pos_y()", "Drone'un y konumu (0 = en güney).", "position", "get_pos_y()"),
        CommandDoc("get_world_size", "get_world_size()", "Alanın kenar uzunluğu.", "position", "get_world_size()"),
        CommandDoc(
            "num_items", "num_items(Items.Stone)",
            "Envanterdeki eşya sayısı.",
            "variables", "num_items(Items.Stone)",
        ),
        CommandDoc(
            "Items", "Items.Stone / Items.Crystal",
            "Envanter eşyalarının türleri.",
            "variables", "Items.",
        ),
        CommandDoc("print", "print(değer, ...)", "Konsola yazar.", null, "print()"),
    )
}
