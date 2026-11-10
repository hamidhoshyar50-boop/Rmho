package ir.rokoi.rmho.coach

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Coach progress persistence — Phase 1 (no Room / DataStore).
 *
 * One SharedPreferences file "coach_progress" with a compact JSON
 * record per node (schema follows the dsh engineering note: per-lesson
 * JSON records carrying theoryRead / medal / cleanReps / gauntletPassed
 * plus an inner schemaVersion for future migration). Writes are atomic
 * per lesson and survive app updates and content refreshes.
 */

@Serializable
data class StoredCoachProgress(
    val schemaVersion: Int = 1,
    val nodeId: String,
    val theoryRead: Boolean = false,
    val labInspected: Boolean = false,
    val medal: MasteryMedal = MasteryMedal.UNLOCKED,
    val bestDrillAccuracy: Int = 0,
    val cleanReps: Int = 0,
    val gauntletPassed: Boolean = false,
    val lastTriageBranchId: String? = null,
    val updatedAtEpochMs: Long = 0
) {
    fun toUserProgress(isUnlocked: Boolean): UserNodeProgress = UserNodeProgress(
        nodeId = nodeId,
        isUnlocked = isUnlocked,
        medal = if (isUnlocked) medal else MasteryMedal.LOCKED,
        theoryCompleted = theoryRead,
        labInspected = labInspected,
        bestDrillAccuracy = bestDrillAccuracy,
        drillCompletedReps = cleanReps,
        gauntletPassed = gauntletPassed,
        lastSelectedTriageBranchId = lastTriageBranchId,
        updatedAtEpochMs = updatedAtEpochMs
    )

    companion object {
        fun fromUserProgress(p: UserNodeProgress): StoredCoachProgress = StoredCoachProgress(
            nodeId = p.nodeId,
            theoryRead = p.theoryCompleted,
            labInspected = p.labInspected,
            medal = if (p.medal == MasteryMedal.LOCKED) MasteryMedal.UNLOCKED else p.medal,
            bestDrillAccuracy = p.bestDrillAccuracy,
            cleanReps = p.drillCompletedReps,
            gauntletPassed = p.gauntletPassed,
            lastTriageBranchId = p.lastSelectedTriageBranchId,
            updatedAtEpochMs = p.updatedAtEpochMs
        )
    }
}

/**
 * Pure medal/unlock logic — no Android dependencies, unit-tested.
 *
 * Medal ladder: LOCKED < UNLOCKED < BRONZE (theory + lab) < SILVER
 * (drill pass) < GOLD (gauntlet pass). A node unlocks when it has no
 * prerequisites or every prerequisite is at SILVER or better.
 */
object CoachProgressLogic {

    fun isNodeUnlocked(node: CurriculumNode, progress: Map<String, UserNodeProgress>): Boolean {
        if (node.prerequisiteNodeIds.isEmpty()) return true
        return node.prerequisiteNodeIds.all { pre ->
            (progress[pre]?.medal ?: MasteryMedal.LOCKED)
                .atLeast(MasteryMedal.SILVER_DRILL_PASS)
        }
    }

    fun withTheoryRead(p: UserNodeProgress, nowMs: Long): UserNodeProgress =
        refreshMedal(p.copy(theoryCompleted = true, updatedAtEpochMs = nowMs))

    fun withLabInspected(p: UserNodeProgress, nowMs: Long): UserNodeProgress =
        refreshMedal(p.copy(labInspected = true, updatedAtEpochMs = nowMs))

    /** Record a finished drill run. [cleanReps] = reps completed cleanly in this run. */
    fun withDrillResult(
        p: UserNodeProgress,
        accuracyPercent: Int,
        cleanReps: Int,
        passAccuracyPercent: Int,
        nowMs: Long
    ): UserNodeProgress {
        var out = p.copy(
            bestDrillAccuracy = maxOf(p.bestDrillAccuracy, accuracyPercent),
            drillCompletedReps = p.drillCompletedReps + cleanReps,
            updatedAtEpochMs = nowMs
        )
        if (accuracyPercent >= passAccuracyPercent &&
            !out.medal.atLeast(MasteryMedal.SILVER_DRILL_PASS)
        ) {
            out = out.copy(medal = MasteryMedal.SILVER_DRILL_PASS)
        }
        return refreshMedal(out)
    }

    fun withGauntletBranch(p: UserNodeProgress, branch: TriageBranch, nowMs: Long): UserNodeProgress {
        var out = p.copy(
            lastSelectedTriageBranchId = branch.branchId,
            updatedAtEpochMs = nowMs
        )
        if (branch.isPassBranch) {
            out = out.copy(gauntletPassed = true, medal = MasteryMedal.GOLD_GAUNTLET_MASTERED)
        }
        return refreshMedal(out)
    }

    /** Bronze is earned by theory + lab; never demote a higher medal. */
    private fun refreshMedal(p: UserNodeProgress): UserNodeProgress {
        if (!p.isUnlocked) return p.copy(medal = MasteryMedal.LOCKED)
        var medal = p.medal
        if (medal == MasteryMedal.LOCKED) medal = MasteryMedal.UNLOCKED
        if (p.theoryCompleted && p.labInspected &&
            !medal.atLeast(MasteryMedal.BRONZE_THEORY_LAB)
        ) {
            medal = MasteryMedal.BRONZE_THEORY_LAB
        }
        if (p.gauntletPassed) medal = MasteryMedal.GOLD_GAUNTLET_MASTERED
        return p.copy(medal = medal)
    }
}

val CoachJson: Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    encodeDefaults = true
}

class CoachProgressStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(nodeId: String): StoredCoachProgress? {
        val raw = prefs.getString(key(nodeId), null) ?: return null
        return runCatching {
            CoachJson.decodeFromString(StoredCoachProgress.serializer(), raw)
        }.getOrNull()
    }

    fun loadAll(): Map<String, StoredCoachProgress> {
        val out = mutableMapOf<String, StoredCoachProgress>()
        for ((k, v) in prefs.all) {
            if (!k.startsWith(NODE_PREFIX) || v !is String) continue
            runCatching {
                CoachJson.decodeFromString(StoredCoachProgress.serializer(), v)
            }.getOrNull()?.let { out[it.nodeId] = it }
        }
        return out
    }

    fun save(progress: StoredCoachProgress) {
        prefs.edit()
            .putString(
                key(progress.nodeId),
                CoachJson.encodeToString(StoredCoachProgress.serializer(), progress)
            )
            .apply()
    }

    var lastOpenedNodeId: String?
        get() = prefs.getString(KEY_LAST_NODE, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_NODE, value).apply()
        }

    var lastOpenedTab: Int
        get() = prefs.getInt(KEY_LAST_TAB, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_TAB, value).apply()
        }

    private fun key(nodeId: String) = NODE_PREFIX + nodeId

    companion object {
        const val FILE_NAME = "coach_progress"
        private const val NODE_PREFIX = "node:"
        private const val KEY_LAST_NODE = "ui:last_node"
        private const val KEY_LAST_TAB = "ui:last_tab"
    }
}
