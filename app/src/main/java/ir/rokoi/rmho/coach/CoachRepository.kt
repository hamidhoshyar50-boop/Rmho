package ir.rokoi.rmho.coach

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coach repository: lazily parses assets/coach/curriculum_v1.json on
 * Dispatchers.IO behind a Mutex, caches the bundle in memory, and
 * merges stored per-node progress with the DAG unlock rules.
 */
class CoachRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = CoachProgressStore(appContext)
    private val mutex = Mutex()

    @Volatile
    private var cachedBundle: CoachCurriculumBundle? = null

    suspend fun bundle(): CoachCurriculumBundle {
        cachedBundle?.let { return it }
        return mutex.withLock {
            cachedBundle?.let { return@withLock it }
            val parsed = withContext(Dispatchers.IO) {
                val text = appContext.assets.open(ASSET_PATH)
                    .bufferedReader().use { it.readText() }
                CoachJson.decodeFromString(CoachCurriculumBundle.serializer(), text)
            }
            cachedBundle = parsed
            parsed
        }
    }

    /** Progress for every node in [bundle], with unlock state resolved. */
    fun progressMap(bundle: CoachCurriculumBundle): Map<String, UserNodeProgress> {
        val stored = store.loadAll()
        // First pass: stored progress with a provisional unlock=true so
        // medal values are visible to the prerequisite checks.
        val provisional = mutableMapOf<String, UserNodeProgress>()
        for (node in bundle.nodes) {
            provisional[node.nodeId] =
                stored[node.nodeId]?.toUserProgress(isUnlocked = true)
                    ?: UserNodeProgress(nodeId = node.nodeId, isUnlocked = true)
        }
        // Second pass: resolve the real unlock state per node.
        val out = mutableMapOf<String, UserNodeProgress>()
        for (node in bundle.nodes) {
            val unlocked = CoachProgressLogic.isNodeUnlocked(node, provisional)
            val base = provisional.getValue(node.nodeId)
            out[node.nodeId] = if (unlocked) {
                base.copy(isUnlocked = true)
            } else {
                base.copy(isUnlocked = false, medal = MasteryMedal.LOCKED)
            }
        }
        return out
    }

    fun progress(bundle: CoachCurriculumBundle, nodeId: String): UserNodeProgress =
        progressMap(bundle)[nodeId] ?: UserNodeProgress(nodeId = nodeId)

    private fun mutate(
        bundle: CoachCurriculumBundle,
        nodeId: String,
        change: (UserNodeProgress) -> UserNodeProgress
    ): UserNodeProgress {
        val current = progress(bundle, nodeId)
        val updated = change(current)
        store.save(StoredCoachProgress.fromUserProgress(updated))
        return progress(bundle, nodeId)
    }

    fun recordTheoryRead(bundle: CoachCurriculumBundle, nodeId: String): UserNodeProgress =
        mutate(bundle, nodeId) {
            CoachProgressLogic.withTheoryRead(it, System.currentTimeMillis())
        }

    fun recordLabInspected(bundle: CoachCurriculumBundle, nodeId: String): UserNodeProgress =
        mutate(bundle, nodeId) {
            CoachProgressLogic.withLabInspected(it, System.currentTimeMillis())
        }

    fun recordDrillResult(
        bundle: CoachCurriculumBundle,
        nodeId: String,
        accuracyPercent: Int,
        cleanReps: Int,
        passAccuracyPercent: Int
    ): UserNodeProgress = mutate(bundle, nodeId) {
        CoachProgressLogic.withDrillResult(
            it, accuracyPercent, cleanReps, passAccuracyPercent, System.currentTimeMillis()
        )
    }

    fun recordGauntletBranch(
        bundle: CoachCurriculumBundle,
        nodeId: String,
        branch: TriageBranch
    ): UserNodeProgress = mutate(bundle, nodeId) {
        CoachProgressLogic.withGauntletBranch(it, branch, System.currentTimeMillis())
    }

    var lastOpenedNodeId: String?
        get() = store.lastOpenedNodeId
        set(value) {
            store.lastOpenedNodeId = value
        }

    var lastOpenedTab: Int
        get() = store.lastOpenedTab
        set(value) {
            store.lastOpenedTab = value
        }

    companion object {
        const val ASSET_PATH = "coach/curriculum_v1.json"
    }
}
