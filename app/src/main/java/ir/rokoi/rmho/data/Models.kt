package ir.rokoi.rmho.data

import kotlinx.serialization.Serializable

/**
 * Rmho data model — the single source of truth for the whole app.
 * Everything content-related lives in a versioned JSON bundle so the
 * data (and images) can be updated remotely WITHOUT a new APK.
 */

@Serializable
data class DataBundle(
    val dataVersion: Int = 1,
    val adventurers: List<Adventurer> = emptyList(),
    val monsters: List<Monster> = emptyList(),
    val teamComps: List<TeamComp> = emptyList(),
    val rotations: List<RotationGuide> = emptyList()
)

@Serializable
data class Adventurer(
    val id: String,
    val nameEn: String,
    val classType: String,          // ASSAULT / DISRUPTOR / SUPPORT
    val weapon: String,             // GREAT_SWORD / LONG_SWORD / DUAL_BLADES / HAMMER / LANCE / CHARGE_BLADE / HEAVY_BOWGUN / BOW
    val element: String = "NONE",   // FIRE / WATER / THUNDER / ICE / DRAGON / NONE
    val imageUrl: String = "",
    val description: String = "",
    val descriptionFa: String = "",
    val skills: List<Skill> = emptyList(),
    val ultimate: Skill? = null,
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    // Stage 6 (Fated): rich profile sections. All defaulted so older
    // data bundles (and other Adventurers) parse unchanged.
    val rarity: String = "",
    val attack: Int? = null,
    val defense: Int? = null,
    val typeUltimates: List<TypeUltimate> = emptyList(),
    val signatureTraits: List<String> = emptyList(),
    val weaponSystemNote: String = "",
    val weaponSystemPoints: List<String> = emptyList(),
    val insightSummary: String = "",
    val insightSummaryFa: String = "",
    val insightHighlights: List<String> = emptyList(),
    val bestClassNote: String = "",
    val bestStartersNote: String = ""
)

@Serializable
data class Skill(
    val nameEn: String,
    val type: String = "ACTIVE",    // ACTIVE / PASSIVE / ULTIMATE
    val cooldownSec: Double? = null,
    val description: String = ""
)

/** An Ultimate keyed by class Type (the Fated Adventurer's three forms).
 *  The Type decides the Ultimate — the equipped weapon never does. */
@Serializable
data class TypeUltimate(
    val type: String,               // ASSAULT / DISRUPTOR / SUPPORT
    val nameEn: String,
    val effect: String = "",
    val cooldownSec: Double? = null,
    val initialEnergy: Int? = null,
    val maxEnergy: Int? = null
)

@Serializable
data class Monster(
    val id: String,
    val nameEn: String,
    val imageUrl: String = "",
    val description: String = "",
    val descriptionFa: String = "",
    val elementWeakness: Map<String, Int> = emptyMap(),  // element -> stars (0-3)
    val breakableParts: List<String> = emptyList(),
    val drops: List<String> = emptyList(),
    val notes: String = ""
)

@Serializable
data class TeamComp(
    val id: String,
    val titleEn: String = "",
    val targetMonsterId: String? = null,
    val adventurerIds: List<String> = emptyList(),
    val buddy: String = "",
    val strategy: String = "",
    val strategyFa: String = "",
    val tags: List<String> = emptyList()
)

@Serializable
data class RotationGuide(
    val id: String,
    val weapon: String? = null,
    val adventurerId: String? = null,
    val steps: List<RotationStep> = emptyList(),
    val tips: String = "",
    val tipsFa: String = "",
    val source: String = ""
)

@Serializable
data class RotationStep(
    val order: Int,
    val action: String,
    val short: String = "",          // short pad-button label, e.g. "Skill 1", "Ultimate", "Attack ×3"
    val input: String = "TAP",       // TAP / HOLD / SWIPE
    val timestampSec: Double? = null,
    val targetPart: String? = null,
    val notes: String = ""
)
