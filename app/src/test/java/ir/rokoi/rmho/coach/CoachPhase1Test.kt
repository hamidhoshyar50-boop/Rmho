package ir.rokoi.rmho.coach

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-1 Coach gates: the bundled curriculum must parse and the two
 * playable lessons (A1, A2) must be structurally complete; the medal
 * logic must promote drill pass -> SILVER and gauntlet pass -> GOLD.
 */
class CoachPhase1Test {

    private fun loadBundle(): CoachCurriculumBundle {
        val candidates = listOf(
            File("src/main/assets/coach/curriculum_v1.json"),
            File("app/src/main/assets/coach/curriculum_v1.json")
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("curriculum_v1.json not found from ${File(".").absolutePath}")
        return CoachJson.decodeFromString(
            CoachCurriculumBundle.serializer(), f.readText()
        )
    }

    @Test(timeout = 30_000)
    fun curriculum_parses_withFullManifest() {
        val bundle = loadBundle()
        assertEquals(12, bundle.nodes.size)
        assertTrue(bundle.isTimingPlaceholder)
        assertEquals(setOf("A1", "A2"), bundle.lessons.keys)
        // Every node carries the Gate-1 actor fallback id.
        assertTrue(bundle.nodes.all { it.combatStyleId == "DEFAULT_GUILD" })
        // The DAG chain is intact: A2 needs A1, B_LS_1 needs A4.
        val byId = bundle.nodes.associateBy { it.nodeId }
        assertEquals(listOf("A1"), byId.getValue("A2").prerequisiteNodeIds)
        assertEquals(listOf("A4"), byId.getValue("B_LS_1").prerequisiteNodeIds)
    }

    @Test(timeout = 30_000)
    fun a1AndA2_areCompleteLessons() {
        val bundle = loadBundle()
        for (id in listOf("A1", "A2")) {
            val lesson = bundle.lessons.getValue(id)
            assertEquals(id, lesson.nodeId)
            // Theory: all four parts present.
            assertTrue(lesson.theory.headlineEn.isNotBlank())
            assertTrue(lesson.theory.summaryFa.isNotBlank())
            assertTrue(lesson.theory.ifConditionFa.isNotBlank())
            assertTrue(lesson.theory.thenActionFa.isNotBlank())
            assertTrue(lesson.theory.neverRuleFa.isNotBlank())
            assertTrue(lesson.theory.keyTerms.isNotEmpty())
            assertTrue(lesson.theory.comboSteps.isNotEmpty())
            // Frame lab: timed, with content on every track + keyframes.
            assertTrue(lesson.frameLab.totalDurationMs > 0)
            assertTrue(lesson.frameLab.playerTrack.isNotEmpty())
            assertTrue(lesson.frameLab.hitboxTrack.isNotEmpty())
            assertTrue(lesson.frameLab.inputWindowTrack.isNotEmpty())
            assertTrue(lesson.frameLab.keyframes.isNotEmpty())
            // Drill: playable Type A with prompts.
            val drill = lesson.drill
            assertTrue("drill must be Type A in phase 1", drill is TypeAGestureRhythm)
            drill as TypeAGestureRhythm
            assertTrue(drill.prompts.isNotEmpty())
            assertTrue(drill.loopDurationMs > 0)
            // Gauntlet: exactly 3 triage branches, exactly one is the pass.
            assertEquals(3, lesson.gauntlet.triageBranches.size)
            assertEquals(1, lesson.gauntlet.triageBranches.count { it.isPassBranch })
            assertTrue(lesson.gauntlet.taskChecklistFa.isNotEmpty())
        }
    }

    @Test(timeout = 30_000)
    fun drillTimings_haveReactionLeadIn() {
        // v0.8.1 hotfix: no prompt window may open before the player has
        // had time to read the cue — A1 P1 moved from 0-240 ms to 500-800.
        val bundle = loadBundle()
        val a1 = bundle.lessons.getValue("A1").drill as TypeAGestureRhythm
        assertEquals(3600L, a1.loopDurationMs)
        assertEquals(450L, a1.antiMashPenaltyMs)
        assertEquals(75, a1.passAccuracyPercent)
        assertEquals(90, a1.goldAccuracyPercent)
        assertEquals(4, a1.requiredReps)
        val a1ByGesture = a1.prompts.associateBy { it.gestureType }
        val tap = a1ByGesture.getValue(GestureKind.TAP)
        assertEquals(500L, tap.windowStartMs)
        assertEquals(800L, tap.windowEndMs)
        assertEquals(650L, tap.idealTimestampMs)
        val hold = a1ByGesture.getValue(GestureKind.HOLD_RELEASE)
        assertEquals(2000L, hold.windowStartMs)
        assertEquals(2500L, hold.windowEndMs)
        assertEquals(2250L, hold.idealTimestampMs)
        assertEquals(400L, hold.minHoldDurationMs)
        val flick = a1ByGesture.getValue(GestureKind.DIRECTIONAL_FLICK)
        assertEquals(2750L, flick.windowStartMs)
        assertEquals(3150L, flick.windowEndMs)
        assertEquals(2950L, flick.idealTimestampMs)
        assertEquals(270.0f, flick.expectedFlickAngleDeg!!, 0.01f)
        assertEquals(40.0f, flick.angleToleranceDeg, 0.01f)

        // A2 shifted by the same lead-in: loop 3000, windows +500 ms,
        // ideals centered, everything inside the loop.
        val a2 = bundle.lessons.getValue("A2").drill as TypeAGestureRhythm
        assertEquals(3000L, a2.loopDurationMs)
        assertEquals(850L, a2.prompts[0].windowStartMs)
        assertEquals(2750L, a2.prompts.last().windowEndMs)
        for (p in a2.prompts) {
            assertTrue(p.windowStartMs >= 500L)
            assertTrue(p.windowEndMs <= a2.loopDurationMs)
            assertEquals(
                (p.windowStartMs + p.windowEndMs) / 2, p.idealTimestampMs
            )
        }
    }

    @Test(timeout = 30_000)
    fun unlockChain_followsSilverRule() {
        val bundle = loadBundle()
        val byId = bundle.nodes.associateBy { it.nodeId }
        val empty = emptyMap<String, UserNodeProgress>()
        assertTrue(CoachProgressLogic.isNodeUnlocked(byId.getValue("A1"), empty))
        assertFalse(CoachProgressLogic.isNodeUnlocked(byId.getValue("A2"), empty))
        val a1Bronze = mapOf(
            "A1" to UserNodeProgress(
                nodeId = "A1", isUnlocked = true,
                medal = MasteryMedal.BRONZE_THEORY_LAB
            )
        )
        assertFalse(CoachProgressLogic.isNodeUnlocked(byId.getValue("A2"), a1Bronze))
        val a1Silver = mapOf(
            "A1" to UserNodeProgress(
                nodeId = "A1", isUnlocked = true,
                medal = MasteryMedal.SILVER_DRILL_PASS
            )
        )
        assertTrue(CoachProgressLogic.isNodeUnlocked(byId.getValue("A2"), a1Silver))
    }

    @Test(timeout = 30_000)
    fun medalLogic_theoryLabBronze_drillSilver_gauntletGold() {
        var p = UserNodeProgress(nodeId = "A1", isUnlocked = true)
        p = CoachProgressLogic.withTheoryRead(p, 1_000L)
        assertEquals(MasteryMedal.UNLOCKED, p.medal)
        p = CoachProgressLogic.withLabInspected(p, 2_000L)
        assertEquals(MasteryMedal.BRONZE_THEORY_LAB, p.medal)

        // A failed drill does not promote.
        val failed = CoachProgressLogic.withDrillResult(
            p, accuracyPercent = 40, cleanReps = 0,
            passAccuracyPercent = 75, nowMs = 3_000L
        )
        assertEquals(MasteryMedal.BRONZE_THEORY_LAB, failed.medal)
        assertEquals(40, failed.bestDrillAccuracy)

        // A passed drill promotes to SILVER.
        val silver = CoachProgressLogic.withDrillResult(
            failed, accuracyPercent = 82, cleanReps = 4,
            passAccuracyPercent = 75, nowMs = 4_000L
        )
        assertEquals(MasteryMedal.SILVER_DRILL_PASS, silver.medal)
        assertEquals(82, silver.bestDrillAccuracy)
        assertEquals(4, silver.drillCompletedReps)

        // A symptom branch records the diagnosis but not the pass.
        val symptom = TriageBranch(
            branchId = "MASH_LOCK_A1", symptomLabelFa = "x",
            remediationTabIndex = 1, seekToLabMs = 300L, isPassBranch = false
        )
        val afterSymptom = CoachProgressLogic.withGauntletBranch(silver, symptom, 5_000L)
        assertFalse(afterSymptom.gauntletPassed)
        assertEquals(MasteryMedal.SILVER_DRILL_PASS, afterSymptom.medal)
        assertEquals("MASH_LOCK_A1", afterSymptom.lastSelectedTriageBranchId)

        // The pass branch promotes to GOLD.
        val pass = TriageBranch(
            branchId = "PASS_A1", symptomLabelFa = "y",
            remediationTabIndex = 0, isPassBranch = true
        )
        val gold = CoachProgressLogic.withGauntletBranch(afterSymptom, pass, 6_000L)
        assertTrue(gold.gauntletPassed)
        assertEquals(MasteryMedal.GOLD_GAUNTLET_MASTERED, gold.medal)
    }

    @Test(timeout = 30_000)
    fun storedProgress_roundTrips() {
        val original = StoredCoachProgress(
            nodeId = "A1", theoryRead = true, labInspected = true,
            medal = MasteryMedal.SILVER_DRILL_PASS, bestDrillAccuracy = 88,
            cleanReps = 7, gauntletPassed = false,
            lastTriageBranchId = "LATE_INPUT_A1", updatedAtEpochMs = 42L
        )
        val raw = CoachJson.encodeToString(StoredCoachProgress.serializer(), original)
        val decoded = CoachJson.decodeFromString(StoredCoachProgress.serializer(), raw)
        assertEquals(original, decoded)
        val user = decoded.toUserProgress(isUnlocked = true)
        assertEquals(MasteryMedal.SILVER_DRILL_PASS, user.medal)
        assertEquals(7, user.drillCompletedReps)
        val locked = decoded.toUserProgress(isUnlocked = false)
        assertEquals(MasteryMedal.LOCKED, locked.medal)
    }
}
