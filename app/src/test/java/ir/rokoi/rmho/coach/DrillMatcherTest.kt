package ir.rokoi.rmho.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.8.1 drill-matching rules: grace band instead of instant mash, hold
 * buffering judged at the release, and feedback always bound to the
 * prompt the input actually matched.
 */
class DrillMatcherTest {

    private val tap = GesturePrompt(
        promptId = "P1", labelEn = "Tap", idealTimestampMs = 650,
        windowStartMs = 500, windowEndMs = 800,
        gestureType = GestureKind.TAP
    )
    private val hold = GesturePrompt(
        promptId = "P2", labelEn = "Hold & Release", idealTimestampMs = 2250,
        windowStartMs = 2000, windowEndMs = 2500,
        gestureType = GestureKind.HOLD_RELEASE, minHoldDurationMs = 400
    )
    private val flick = GesturePrompt(
        promptId = "P3", labelEn = "Forward Flick", idealTimestampMs = 2950,
        windowStartMs = 2750, windowEndMs = 3150,
        gestureType = GestureKind.DIRECTIONAL_FLICK,
        expectedFlickAngleDeg = 270f, angleToleranceDeg = 40f
    )
    private val prompts = listOf(tap, hold, flick)
    private val none = emptySet<String>()

    @Test(timeout = 10_000)
    fun inWindow_tap_hits_andConsumes() {
        val r = DrillMatcher.match(prompts, none, GestureKind.TAP, 60, null, 660)
        assertEquals(DrillVerdict.HIT, r.verdict)
        assertEquals("P1", r.prompt?.promptId)
        assertEquals(10L, r.deltaMs)
        assertTrue(r.consumesPrompt)
    }

    @Test(timeout = 10_000)
    fun earlyGrace_isEarlyNotMash_andPromptStaysOpen() {
        // 80 ms before P1 opens: inside the +/-150 ms grace band.
        val r = DrillMatcher.match(prompts, none, GestureKind.TAP, 60, null, 420)
        assertEquals(DrillVerdict.EARLY, r.verdict)
        assertEquals("P1", r.prompt?.promptId)
        assertFalse(r.consumesPrompt)
        // The same prompt can still be hit when its window actually opens.
        val hit = DrillMatcher.match(prompts, none, GestureKind.TAP, 60, null, 650)
        assertEquals(DrillVerdict.HIT, hit.verdict)
    }

    @Test(timeout = 10_000)
    fun lateGrace_isLateNotMash() {
        // 100 ms after P1 closed: still the grace band, not mash.
        val r = DrillMatcher.match(prompts, none, GestureKind.TAP, 60, null, 900)
        assertEquals(DrillVerdict.LATE, r.verdict)
        assertEquals("P1", r.prompt?.promptId)
        assertFalse(r.consumesPrompt)
    }

    @Test(timeout = 10_000)
    fun farFromEveryWindow_isMash() {
        // 1500 ms is >150 ms away from P1's late edge (950) and P2's
        // early edge (1850): a genuine extra input.
        val r = DrillMatcher.match(prompts, none, GestureKind.TAP, 60, null, 1500)
        assertEquals(DrillVerdict.MASH, r.verdict)
        assertNull(r.prompt)
    }

    @Test(timeout = 10_000)
    fun holdRelease_bufferedPress_releaseInWindow_hits() {
        // The press began at ~1400 ms (far before the window) and was held
        // 900 ms; buffering means only the release position is judged.
        val r = DrillMatcher.match(
            prompts, none, GestureKind.HOLD_RELEASE, 900, null, 2300
        )
        assertEquals(DrillVerdict.HIT, r.verdict)
        assertEquals("P2", r.prompt?.promptId)
    }

    @Test(timeout = 10_000)
    fun holdRelease_tooShortHold_isJudgedNotMash() {
        val r = DrillMatcher.match(
            prompts, none, GestureKind.HOLD_RELEASE, 250, null, 2300
        )
        assertEquals(DrillVerdict.TOO_SHORT_HOLD, r.verdict)
        assertEquals("P2", r.prompt?.promptId)
        assertTrue(r.consumesPrompt)
    }

    @Test(timeout = 10_000)
    fun tapInsideHoldWindow_isWrongGestureForThatPrompt() {
        // The banner must name P2 (the window the tap landed in), not P1.
        val r = DrillMatcher.match(prompts, none, GestureKind.TAP, 60, null, 2300)
        assertEquals(DrillVerdict.WRONG_GESTURE, r.verdict)
        assertEquals("P2", r.prompt?.promptId)
    }

    @Test(timeout = 10_000)
    fun flick_wrongAngle_isJudged_rightAngle_hits() {
        val bad = DrillMatcher.match(
            prompts, none, GestureKind.DIRECTIONAL_FLICK, 120, 90f, 2950
        )
        assertEquals(DrillVerdict.WRONG_FLICK_ANGLE, bad.verdict)
        assertEquals("P3", bad.prompt?.promptId)
        val good = DrillMatcher.match(
            prompts, none, GestureKind.DIRECTIONAL_FLICK, 120, 262f, 2960
        )
        assertEquals(DrillVerdict.HIT, good.verdict)
    }

    @Test(timeout = 10_000)
    fun consumedPrompt_isSkipped() {
        // P1 already spent this rep; a tap at its old window is far from
        // every remaining window, so it is mash — the prompt is not re-judged.
        val r = DrillMatcher.match(prompts, setOf("P1"), GestureKind.TAP, 60, null, 660)
        assertEquals(DrillVerdict.MASH, r.verdict)
    }

    @Test(timeout = 10_000)
    fun classify_thresholds() {
        assertEquals(
            GestureKind.TAP, DrillMatcher.classify(100, 5f, 90f)
        )
        assertEquals(
            GestureKind.HOLD_RELEASE, DrillMatcher.classify(300, 5f, 90f)
        )
        assertEquals(
            GestureKind.DIRECTIONAL_FLICK,
            DrillMatcher.classify(100, 120f, 90f)
        )
    }
}
