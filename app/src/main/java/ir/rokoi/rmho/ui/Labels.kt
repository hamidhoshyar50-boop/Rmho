package ir.rokoi.rmho.ui

/** Display labels for the game's enum values (English UI). */
fun classLabel(v: String): String = when (v) {
    "ASSAULT" -> "Assault"
    "DISRUPTOR" -> "Disruptor"
    "SUPPORT" -> "Support"
    "FATED" -> "Fated (you)"
    else -> v
}

fun weaponLabel(v: String): String = when (v) {
    "GREAT_SWORD" -> "Great Sword"
    "LONG_SWORD" -> "Long Sword"
    "DUAL_BLADES" -> "Dual Blades"
    "HAMMER" -> "Hammer"
    "LANCE" -> "Lance"
    "CHARGE_BLADE" -> "Charge Blade"
    "HEAVY_BOWGUN" -> "Heavy Bowgun"
    "BOW" -> "Bow"
    "ANY" -> "Any weapon"
    else -> v
}

fun elementLabel(v: String): String = when (v) {
    "FIRE" -> "Fire"
    "WATER" -> "Water"
    "THUNDER" -> "Thunder"
    "ICE" -> "Ice"
    "DRAGON" -> "Dragon"
    "NONE" -> "No element"
    else -> v
}
