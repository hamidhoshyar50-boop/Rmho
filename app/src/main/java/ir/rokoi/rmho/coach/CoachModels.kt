package ir.rokoi.rmho.coach

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Coach (curriculum) domain model — Phase 1.
 *
 * Content is 100% data-driven: the app renders only this JSON model
 * (assets/coach/curriculum_v1.json), so a game-design change is a JSON
 * edit, not a recompile. All frame timings are integer milliseconds
 * (refresh-rate independent) and are PLACEHOLDER until measured from
 * the real game (see CoachCurriculumBundle.isTimingPlaceholder).
 */

@Serializable
data class CoachCurriculumBundle(
    val schemaVersion: Int = 1,
    val isTimingPlaceholder: Boolean = true,
    val nodes: List<CurriculumNode> = emptyList(),
    val lessons: Map<String, LessonUnit> = emptyMap()
)

@Serializable
data class CurriculumNode(
    val nodeId: String,
    /** "UNIVERSAL" for Track A, "LONG_SWORD" for the LS track. */
    val weaponId: String = "UNIVERSAL",
    /** Actor abstraction (weapon class OR character kit) — Gate-1 fallback. */
    val combatStyleId: String = "DEFAULT_GUILD",
    /** Position on the 5-stage Frame-State mastery ladder (1..5). */
    val stageLadderIndex: Int = 1,
    val titleEn: String,
    val subtitleFa: String = "",
    val prerequisiteNodeIds: List<String> = emptyList(),
    val estimatedMinutes: Int = 5,
    /** Native cooldown for skill-button styles; 0 for combo-tree styles. */
    val cooldownMsFallback: Long = 0
)

@Serializable
data class LessonUnit(
    val nodeId: String,
    val timingSourceNote: String = "",
    val theory: TheorySpec,
    val frameLab: FrameLabSpec,
    val drill: DrillSpec,
    val gauntlet: GauntletSpec
)

@Serializable
data class BilingualTerm(
    val termEn: String,
    val definitionFa: String = ""
)

@Serializable
data class TheoryComboStep(
    val stepIndex: Int,
    /** Gesture primitive name, e.g. TAP_PRIMARY / HOLD_PRIMARY / SWIPE_DIR_SPECIAL / TAP_EVADE. */
    val gesture: String,
    val moveNameEn: String = "",
    val moveNameFa: String = "",
    val minInputWindowMs: Long = 0,
    val maxInputWindowMs: Long = 0,
    val commitFramesMs: Long = 0,
    val cooldownMs: Long = 0,
    val coachingCueFa: String = ""
)

@Serializable
data class TheorySpec(
    val headlineEn: String,
    val summaryFa: String = "",
    val ifConditionFa: String = "",
    val thenActionFa: String = "",
    val neverRuleFa: String = "",
    val keyTerms: List<BilingualTerm> = emptyList(),
    val comboSteps: List<TheoryComboStep> = emptyList(),
    val coachingCueFa: String = ""
)

@Serializable
enum class FrameStateType {
    STARTUP, ACTIVE_HITBOX, RECOVERY, BUFFER_WINDOW,
    CANCEL_WINDOW, IFRAME, HYPER_ARMOR, MASH_PENALTY_LOCK
}

/** Normalized arena geometry: coordinates in -1..1 space. */
@Serializable
data class VectorGeometry(
    val originX: Float = 0f,
    val originY: Float = 0f,
    val targetX: Float = 0f,
    val targetY: Float = 0f,
    val arcRadius: Float = 0f,
    val sweepAngleDeg: Float = 0f,
    val facingAngleDeg: Float = 0f
)

@Serializable
data class TimelineSegment(
    val segmentId: String,
    val labelEn: String,
    val tooltipFa: String = "",
    val startMs: Long,
    val endMs: Long,
    val stateType: FrameStateType,
    val vectorGeometry: VectorGeometry? = null
)

@Serializable
enum class HapticTickType { NONE, LIGHT_TICK, HEAVY_IMPACT, BUFFER_OPEN, ERROR_BUZZ }

@Serializable
data class LabKeyframe(
    val timestampMs: Long,
    val badgeEn: String,
    val noteFa: String = "",
    val hapticTickType: HapticTickType = HapticTickType.NONE
)

@Serializable
data class FrameLabSpec(
    val totalDurationMs: Long,
    val defaultPlaybackSpeed: Float = 1.0f,
    val arenaRadiusMeters: Float = 6.0f,
    val playerTrack: List<TimelineSegment> = emptyList(),
    val hitboxTrack: List<TimelineSegment> = emptyList(),
    val inputWindowTrack: List<TimelineSegment> = emptyList(),
    val keyframes: List<LabKeyframe> = emptyList()
)

@Serializable
enum class GestureKind { TAP, HOLD_RELEASE, DIRECTIONAL_FLICK }

@Serializable
enum class TargetZone { LEFT_MOVEMENT_PAD, RIGHT_ACTION_PAD, SPIRIT_SKILL_BUTTON }

@Serializable
data class GesturePrompt(
    val promptId: String,
    val labelEn: String,
    val hintFa: String = "",
    val idealTimestampMs: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val gestureType: GestureKind,
    val minHoldDurationMs: Long = 0,
    val expectedFlickAngleDeg: Float? = null,
    val angleToleranceDeg: Float = 35f,
    val targetZone: TargetZone = TargetZone.RIGHT_ACTION_PAD
)

@Serializable
sealed class DrillSpec {
    abstract val drillId: String
    abstract val instructionsFa: String
    abstract val passAccuracyPercent: Int
    abstract val goldAccuracyPercent: Int
    abstract val requiredReps: Int
}

@Serializable
@SerialName("TYPE_A_GESTURE_RHYTHM")
data class TypeAGestureRhythm(
    override val drillId: String,
    override val instructionsFa: String = "",
    override val passAccuracyPercent: Int = 75,
    override val goldAccuracyPercent: Int = 90,
    override val requiredReps: Int = 4,
    val loopDurationMs: Long,
    val antiMashPenaltyMs: Long = 450,
    val maxAllowedExtraTapsPerRep: Int = 2,
    val prompts: List<GesturePrompt> = emptyList()
) : DrillSpec()

/** Phase-2 stub: parses and renders an instructions card only. */
@Serializable
@SerialName("TYPE_B_REACTION_CUE")
data class TypeBReactionCue(
    override val drillId: String,
    override val instructionsFa: String = "",
    override val passAccuracyPercent: Int = 90,
    override val goldAccuracyPercent: Int = 100,
    override val requiredReps: Int = 1,
    val noteFa: String = ""
) : DrillSpec()

/** Phase-2 stub: parses and renders an instructions card only. */
@Serializable
@SerialName("TYPE_C_STATE_ROUTING")
data class TypeCStateRouting(
    override val drillId: String,
    override val instructionsFa: String = "",
    override val passAccuracyPercent: Int = 90,
    override val goldAccuracyPercent: Int = 100,
    override val requiredReps: Int = 1,
    val noteFa: String = ""
) : DrillSpec()

@Serializable
data class TriageBranch(
    val branchId: String,
    val symptomLabelFa: String,
    val rootCauseFa: String = "",
    /** 0 = Theory, 1 = Frame Lab, 2 = Drill. */
    val remediationTabIndex: Int = 0,
    val seekToLabMs: Long? = null,
    val isPassBranch: Boolean = false
)

@Serializable
data class GauntletSpec(
    val gauntletId: String,
    val taskChecklistFa: List<String> = emptyList(),
    val combatStyleFallbackNoteFa: String = "",
    val triageQuestionFa: String = "",
    val triageBranches: List<TriageBranch> = emptyList()
)

@Serializable
enum class MasteryMedal {
    LOCKED, UNLOCKED, BRONZE_THEORY_LAB, SILVER_DRILL_PASS, GOLD_GAUNTLET_MASTERED;

    fun atLeast(other: MasteryMedal): Boolean = ordinal >= other.ordinal
}

/** UI-facing progress for one node (unlock state computed by the repository). */
data class UserNodeProgress(
    val nodeId: String,
    val isUnlocked: Boolean = false,
    val medal: MasteryMedal = MasteryMedal.LOCKED,
    val theoryCompleted: Boolean = false,
    val labInspected: Boolean = false,
    val bestDrillAccuracy: Int = 0,
    val drillCompletedReps: Int = 0,
    val gauntletPassed: Boolean = false,
    val lastSelectedTriageBranchId: String? = null,
    val updatedAtEpochMs: Long = 0
)
