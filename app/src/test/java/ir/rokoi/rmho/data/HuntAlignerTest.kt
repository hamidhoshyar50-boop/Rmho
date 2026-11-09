package ir.rokoi.rmho.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v0.7.0 hunt-analysis freeze: HuntAligner
 * looped forever (on the main thread -> ANR at "Analysing... 99%")
 * whenever a whole alignment cycle matched nothing while events
 * remained — its SKIPPED branch advanced the expected index and the
 * window clock but never the event pointer. Every test here carries
 * a timeout so a regression FAILS instead of hanging the build.
 */
class HuntAlignerTest {

    private fun rotation(id: String, vararg shorts: String): RotationGuide =
        RotationGuide(
            id = id,
            steps = shorts.mapIndexed { i, s ->
                RotationStep(order = i + 1, action = s, short = s)
            }
        )

    /** A rotation with NO dash steps: Skill 1, then two Attacks.
     *  (Separate steps, not "Attack ×2": the shipped stepItems()
     *  reads the repeat count from the wrong regex group, so ×N
     *  attack steps currently expand to a single press — a known
     *  v0.7.0 inaccuracy outside this hotfix's scope.) */
    private val noDashRotation = rotation("t-nodash", "Skill 1", "Attack", "Attack")

    @Test(timeout = 30_000)
    fun trailingDashOnlyEvents_terminates() {
        // The exact v0.7.0 hang repro: after a matched opening, the
        // tail of the hunt is ONLY Dash events — a class this rotation
        // never expects. The old aligner never terminated here.
        val events = mutableListOf(
            HuntEvent(1.0, HuntEventClass.SKILL),
            HuntEvent(2.0, HuntEventClass.BASIC),
            HuntEvent(3.0, HuntEventClass.BASIC)
        )
        var t = 4.0
        repeat(400) {
            events.add(HuntEvent(t, HuntEventClass.DASH))
            t += 0.5
        }
        val rep = HuntAligner.align(events, noDashRotation)
        assertEquals(3, rep.matched)
        // The zero-match cycle is unpenalised: NOT_REACHED, not skipped.
        assertEquals(0, rep.skipped)
        assertEquals(3, rep.notReached)
        assertEquals(1, rep.cyclesCompleted)
        assertEquals(100, rep.scorePercent)
    }

    @Test(timeout = 30_000)
    fun dashOnlyEvents_firstCycleZeroMatch_terminates() {
        val events = (0 until 300).map {
            HuntEvent(1.0 + it * 0.4, HuntEventClass.DASH)
        }
        val rep = HuntAligner.align(events, noDashRotation)
        assertEquals(0, rep.matched)
        assertEquals(0, rep.skipped)
        assertEquals(rep.expandedCount, rep.notReached)
        assertEquals(rep.expandedCount, rep.firstCycle.size)
        assertTrue(rep.firstCycle.all { it.outcome == StepOutcome.NOT_REACHED })
    }

    @Test(timeout = 30_000)
    fun zeroMatchCycleMidStream_stopsAlignment() {
        // Cycle 1 matches fully; then a long Dash-only stretch (a
        // zero-match cycle) and then MORE matching events. Alignment
        // must stop at the zero-match cycle, never skip ahead to the
        // later matches.
        val events = mutableListOf(
            HuntEvent(1.0, HuntEventClass.SKILL),
            HuntEvent(2.0, HuntEventClass.BASIC),
            HuntEvent(3.0, HuntEventClass.BASIC)
        )
        var t = 4.0
        repeat(60) {
            events.add(HuntEvent(t, HuntEventClass.DASH))
            t += 0.5
        }
        events.add(HuntEvent(100.0, HuntEventClass.SKILL))
        events.add(HuntEvent(101.0, HuntEventClass.BASIC))
        events.add(HuntEvent(102.0, HuntEventClass.BASIC))
        val rep = HuntAligner.align(events, noDashRotation)
        assertEquals(3, rep.matched)
        assertEquals(1, rep.cyclesCompleted)
        assertEquals(0, rep.skipped)
        assertEquals(3, rep.notReached)
    }

    @Test(timeout = 30_000)
    fun emptyEvents_terminates() {
        val rep = HuntAligner.align(emptyList(), noDashRotation)
        assertEquals(0, rep.matched)
        assertEquals(0, rep.skipped)
        assertEquals(rep.expandedCount, rep.notReached)
        assertEquals(0, rep.cyclesCompleted)
    }

    @Test(timeout = 30_000)
    fun repeatOnlyRotation_terminates() {
        val rep = HuntAligner.align(
            listOf(HuntEvent(1.0, HuntEventClass.BASIC)),
            rotation("t-repeat", "Repeat ↺")
        )
        assertEquals(0, rep.expandedCount)
        assertEquals(0, rep.matched)
        assertEquals(0, rep.skipped)
    }

    // ---- Real fixture: events the detector found in the user's real
    // ---- 110 s Barroth recording (stage7-fixture-events.json).

    private fun fixtureEvents(): List<HuntEvent> {
        val stream = javaClass.getResourceAsStream("/stage7-fixture-events.json")
            ?: error("fixture resource missing")
        val root = Json.parseToJsonElement(
            stream.bufferedReader().readText()
        ).jsonObject
        return root.getValue("events").jsonArray.map { el ->
            val a = el.jsonArray
            HuntEvent(
                a[0].jsonPrimitive.double,
                HuntEventClass.valueOf(a[1].jsonPrimitive.content)
            )
        }
    }

    private fun bundledRotation(id: String): RotationGuide {
        val candidates = listOf(
            File("src/main/assets/data.json"),
            File("app/src/main/assets/data.json")
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("bundled data.json not found from ${File(".").absolutePath}")
        val bundle = Json { ignoreUnknownKeys = true }
            .decodeFromString(DataBundle.serializer(), f.readText())
        return bundle.rotations.first { it.id == id }
    }

    @Test(timeout = 60_000)
    fun fixtureEvents_luka_terminatesWithSaneReport() {
        val events = fixtureEvents()
        assertEquals(95, events.size)
        val rep = HuntAligner.align(events, bundledRotation("rot-luka"))
        assertTrue("expected some matches, got ${rep.matched}", rep.matched > 0)
        assertEquals(rep.expandedCount, rep.firstCycle.size)
        assertTrue(rep.scorePercent in 0..100)
        assertTrue(rep.matched + rep.skipped >= rep.expandedCount)
    }

    @Test(timeout = 60_000)
    fun fixtureEvents_fatedLongSword_terminatesWithSaneReport() {
        val rep = HuntAligner.align(
            fixtureEvents(), bundledRotation("rot-fated-long-sword")
        )
        assertTrue("expected some matches, got ${rep.matched}", rep.matched > 0)
        assertEquals(rep.expandedCount, rep.firstCycle.size)
        assertTrue(rep.scorePercent in 0..100)
        assertTrue(rep.matched + rep.skipped >= rep.expandedCount)
    }
}
