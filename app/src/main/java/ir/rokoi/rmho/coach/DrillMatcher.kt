package ir.rokoi.rmho.coach

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Pure prompt-matching engine for Type A gesture-rhythm drills.
 *
 * Extracted from the UI (v0.8.1) so the rules are unit-testable and the
 * feedback banner can always name the prompt an input was judged against:
 *
 * 1. Grace band: an input landing within [GRACE_MS] before a window opens
 *    or after it closes is judged EARLY / LATE against that prompt — it is
 *    NOT mash and does not consume the prompt, so the player can still
 *    score when the window actually opens.
 * 2. Hold buffering: a HOLD_RELEASE is judged by where its RELEASE lands,
 *    never by when the press started (buffering is the lesson's point).
 *    The caller passes the release position as [posMs] for holds and the
 *    press position for taps/flicks.
 * 3. Only an input more than [GRACE_MS] away from every unconsumed prompt
 *    window is MASH (the caller applies the lockout penalty for it).
 *
 * A prompt is consumed only by an in-window judged attempt (HIT,
 * WRONG_GESTURE, TOO_SHORT_HOLD, WRONG_FLICK_ANGLE); the caller marks it
 * consumed so one prompt gets exactly one judged attempt per rep.
 */
enum class DrillVerdict {
    HIT, WRONG_GESTURE, TOO_SHORT_HOLD, WRONG_FLICK_ANGLE, EARLY, LATE, MASH
}

data class DrillMatchResult(
    val verdict: DrillVerdict,
    /** The prompt this input was judged against; null only for MASH. */
    val prompt: GesturePrompt?,
    /** Signed offset from the prompt's ideal timestamp (0 for MASH). */
    val deltaMs: Long
) {
    /** True when the attempt spends the prompt for this rep. */
    val consumesPrompt: Boolean
        get() = verdict == DrillVerdict.HIT ||
            verdict == DrillVerdict.WRONG_GESTURE ||
            verdict == DrillVerdict.TOO_SHORT_HOLD ||
            verdict == DrillVerdict.WRONG_FLICK_ANGLE
}

object DrillMatcher {

    /** Forgiveness around each prompt window, in milliseconds. */
    const val GRACE_MS: Long = 150L

    /** Minimum press duration that still counts as a hold, not a tap. */
    const val HOLD_CLASSIFY_MS: Long = 220L

    /** Classify a finished press: flick by travel, hold by duration, else tap. */
    fun classify(durationMs: Long, distancePx: Float, flickThresholdPx: Float): GestureKind =
        when {
            distancePx >= flickThresholdPx -> GestureKind.DIRECTIONAL_FLICK
            durationMs >= HOLD_CLASSIFY_MS -> GestureKind.HOLD_RELEASE
            else -> GestureKind.TAP
        }

    /** Flick direction in screen degrees: 0 = right, 90 = down, 270 = up. */
    fun flickAngleDeg(dx: Float, dy: Float): Float {
        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (deg < 0f) deg += 360f
        return deg
    }

    fun angularDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /**
     * Judge one input.
     *
     * @param prompts the drill's prompts, in order
     * @param consumedIds prompts already spent in the current rep
     * @param posMs loop position the input is judged at: release position
     *  for HOLD_RELEASE, press position for TAP / DIRECTIONAL_FLICK
     */
    fun match(
        prompts: List<GesturePrompt>,
        consumedIds: Set<String>,
        kind: GestureKind,
        durationMs: Long,
        angleDeg: Float?,
        posMs: Long
    ): DrillMatchResult {
        val active = prompts.filter { it.promptId !in consumedIds }
        // 1) Inside a window: full judgement against that prompt.
        for (p in active) {
            if (posMs in p.windowStartMs..p.windowEndMs) {
                return judge(p, kind, durationMs, angleDeg, posMs)
            }
        }
        // 2) Grace band: timing feedback only — not mash, prompt stays open.
        for (p in active) {
            val delta = posMs - p.idealTimestampMs
            if (posMs < p.windowStartMs && p.windowStartMs - posMs <= GRACE_MS) {
                return DrillMatchResult(DrillVerdict.EARLY, p, delta)
            }
            if (posMs > p.windowEndMs && posMs - p.windowEndMs <= GRACE_MS) {
                return DrillMatchResult(DrillVerdict.LATE, p, delta)
            }
        }
        // 3) Nowhere near any open window: mash.
        return DrillMatchResult(DrillVerdict.MASH, null, 0L)
    }

    private fun judge(
        p: GesturePrompt,
        kind: GestureKind,
        durationMs: Long,
        angleDeg: Float?,
        posMs: Long
    ): DrillMatchResult {
        val delta = posMs - p.idealTimestampMs
        return when {
            kind != p.gestureType ->
                DrillMatchResult(DrillVerdict.WRONG_GESTURE, p, delta)
            p.gestureType == GestureKind.HOLD_RELEASE &&
                durationMs < p.minHoldDurationMs ->
                DrillMatchResult(DrillVerdict.TOO_SHORT_HOLD, p, delta)
            p.gestureType == GestureKind.DIRECTIONAL_FLICK &&
                p.expectedFlickAngleDeg != null && angleDeg != null &&
                angularDistance(angleDeg, p.expectedFlickAngleDeg) >
                p.angleToleranceDeg ->
                DrillMatchResult(DrillVerdict.WRONG_FLICK_ANGLE, p, delta)
            else -> DrillMatchResult(DrillVerdict.HIT, p, delta)
        }
    }
}
