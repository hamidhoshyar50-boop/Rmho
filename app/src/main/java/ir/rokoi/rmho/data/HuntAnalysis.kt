package ir.rokoi.rmho.data

/**
 * Coach Phase 2 — hunt-video analysis: pure logic (no Android deps).
 *
 * The detector (HuntAnalyzer) produces timed button-press events from a
 * hunt recording; this file maps rotation steps to expected event
 * classes and aligns the two sequences with a greedy sequential
 * aligner (per-step windows; skipped steps counted; steps never
 * reached because the recording ended are NOT penalised).
 *
 * Event classes are deliberately coarse — the HUD only proves that a
 * button in the action cluster was pressed:
 *   BASIC  — the big Basic Attack disc
 *   DASH   — either Dash disc (dodge / dash skill)
 *   BURST  — the Burst button (Ultimate steps map here — it is the
 *            game's special-move button; per-weapon skill icons vary,
 *            so "which skill" is never claimed)
 *   SKILL  — either contextual skill disc
 */

enum class HuntEventClass { BASIC, DASH, BURST, SKILL }

data class HuntEvent(
    val timeSec: Double,
    val cls: HuntEventClass,
    val peakScore: Double = 0.0
)

/** One atomic expected press expanded from a rotation step. */
data class ExpectedItem(
    val stepOrder: Int,
    val stepLabel: String,
    val cls: HuntEventClass,
    val hold: Boolean = false
)

enum class StepOutcome { MATCHED, SKIPPED, NOT_REACHED }

data class StepResult(
    val stepOrder: Int,
    val stepLabel: String,
    val cls: HuntEventClass,
    val outcome: StepOutcome,
    val matchedAtSec: Double? = null,
    val gapBeforeSec: Double? = null
)

data class AlignmentReport(
    val expandedCount: Int,
    val firstCycle: List<StepResult>,
    val cyclesCompleted: Int,
    val matched: Int,
    val skipped: Int,
    val notReached: Int,
    val notTrackedSteps: List<String>,
    val extraEvents: Int,
    val orderSignals: Int,
    val avgGapSec: Double?,
    val scorePercent: Int
)

object HuntAligner {

    private val TOKEN = Regex(
        "(ultimate|burst|skills\\s*1\\+2|skill\\s*1|skill\\s*2|skill\\s*3|" +
            "attack(?:\\s*×\\s*(\\d+))?|dash|evade|parry)"
    )

    /** Expand one rotation step into the atomic presses it expects.
     *  Empty = loop marker ("Repeat") — nothing to score.
     *  [null] = a step the buttons cannot prove (squad actions,
     *  lock-on) — reported as not tracked, never as skipped. */
    fun stepItems(step: RotationStep): List<HuntEventClass?> {
        val label = step.short.ifEmpty { step.action }
        val low = label.lowercase()
        if ("repeat" in low) return emptyList()
        if ("squad" in low || "lock on" in low) return listOf(null)
        val out = mutableListOf<HuntEventClass?>()
        for (m in TOKEN.findAll(low)) {
            val tok = m.value
            when {
                tok.startsWith("ultimate") || tok.startsWith("burst") ->
                    out.add(HuntEventClass.BURST)
                tok.startsWith("skills") -> {
                    out.add(HuntEventClass.SKILL); out.add(HuntEventClass.SKILL)
                }
                tok.startsWith("skill") -> out.add(HuntEventClass.SKILL)
                tok.startsWith("attack") -> {
                    val n = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                    repeat(n.coerceIn(1, 6)) { out.add(HuntEventClass.BASIC) }
                }
                tok.startsWith("dash") || tok.startsWith("evade") ->
                    out.add(HuntEventClass.DASH)
                tok.startsWith("parry") -> out.add(HuntEventClass.BASIC)
            }
        }
        if (out.isEmpty()) {
            val act = step.action.lowercase()
            if ("attack" in act) out.add(HuntEventClass.BASIC)
            else if ("skill" in act) out.add(HuntEventClass.SKILL)
            else if ("dash" in act || "evade" in act || "dodge" in act) {
                out.add(HuntEventClass.DASH)
            }
        }
        return out
    }

    fun expand(rot: RotationGuide): Pair<List<ExpectedItem>, List<String>> {
        val items = mutableListOf<ExpectedItem>()
        val notTracked = mutableListOf<String>()
        for (step in rot.steps.sortedBy { it.order }) {
            val label = step.short.ifEmpty { step.action }
            val classes = stepItems(step)
            if (classes.size == 1 && classes[0] == null) {
                notTracked.add(label)
                continue
            }
            for (c in classes) {
                if (c != null) items.add(
                    ExpectedItem(step.order, label, c, step.input.equals("HOLD", true))
                )
            }
        }
        return items to notTracked
    }

    /**
     * Greedy sequential alignment across the whole hunt. The expected
     * sequence repeats in cycles. A step is SKIPPED when later events
     * exist but none matches inside its window; when the events run
     * out mid-cycle, the remaining steps are NOT_REACHED (unpenalised).
     */
    fun align(
        events: List<HuntEvent>,
        rot: RotationGuide,
        baseWindowSec: Double = 6.0,
        cycleWindowSec: Double = 12.0
    ): AlignmentReport {
        val (expected, notTracked) = expand(rot)
        val ev = events.sortedBy { it.timeSec }
        if (expected.isEmpty() || ev.isEmpty()) {
            return AlignmentReport(
                expected.size, emptyList(), 0, 0, 0,
                if (ev.isEmpty()) expected.size else 0,
                notTracked, ev.size, 0, null, 0
            )
        }
        var ei = 0
        var lastT = ev.first().timeSec
        var matched = 0
        var skipped = 0
        var extra = 0
        var orderSignals = 0
        var cycles = 0
        var notReached = 0
        val gaps = mutableListOf<Double>()
        val firstCycle = mutableListOf<StepResult>()
        var done = false

        while (!done) {
            var cycleMatched = 0
            var idx = 0
            while (idx < expected.size) {
                val item = expected[idx]
                val window = if (idx == 0) cycleWindowSec else baseWindowSec
                // scan events inside the window for a class match
                var found = -1
                var scan = ei
                while (scan < ev.size && ev[scan].timeSec - lastT <= window) {
                    if (ev[scan].cls == item.cls) {
                        found = scan
                        break
                    }
                    val n1 = expected.getOrNull(idx + 1)?.cls
                    val n2 = expected.getOrNull(idx + 2)?.cls
                    if (ev[scan].cls == n1 || ev[scan].cls == n2) orderSignals++
                    scan++
                }
                when {
                    found >= 0 -> {
                        extra += found - ei
                        ei = found + 1
                        gaps.add(ev[found].timeSec - lastT)
                        lastT = ev[found].timeSec
                        matched++
                        cycleMatched++
                        if (cycles == 0) firstCycle.add(
                            StepResult(
                                item.stepOrder, item.stepLabel, item.cls,
                                StepOutcome.MATCHED, ev[found].timeSec, gaps.last()
                            )
                        )
                        idx++
                    }
                    ei < ev.size -> {
                        // events remain, but none matched in the window
                        skipped++
                        if (cycles == 0) firstCycle.add(
                            StepResult(item.stepOrder, item.stepLabel, item.cls, StepOutcome.SKIPPED)
                        )
                        lastT += window
                        idx++
                    }
                    else -> {
                        // out of events: the rest of this cycle was
                        // never reached — do not penalise it
                        notReached += expected.size - idx
                        if (cycles == 0) {
                            for (k in idx until expected.size) {
                                val it2 = expected[k]
                                firstCycle.add(
                                    StepResult(it2.stepOrder, it2.stepLabel, it2.cls, StepOutcome.NOT_REACHED)
                                )
                            }
                        }
                        idx = expected.size
                        done = true
                    }
                }
            }
            if (cycleMatched > 0) cycles++
            if (ei >= ev.size) done = true
        }
        extra += (ev.size - ei).coerceAtLeast(0)
        val scored = matched + skipped
        return AlignmentReport(
            expandedCount = expected.size,
            firstCycle = firstCycle,
            cyclesCompleted = cycles,
            matched = matched,
            skipped = skipped,
            notReached = notReached,
            notTrackedSteps = notTracked,
            extraEvents = extra,
            orderSignals = orderSignals,
            avgGapSec = if (gaps.isEmpty()) null else gaps.sum() / gaps.size,
            scorePercent = if (scored == 0) 0 else matched * 100 / scored
        )
    }

    fun fmtTime(sec: Double): String {
        val m = (sec / 60).toInt()
        val s = sec - m * 60
        return "%d:%04.1f".format(m, s)
    }
}
