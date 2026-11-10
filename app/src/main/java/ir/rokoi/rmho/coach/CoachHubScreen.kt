package ir.rokoi.rmho.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.ui.FaSection
import ir.rokoi.rmho.ui.UiPrefs
import ir.rokoi.rmho.ui.theme.RmhoPrimary
import ir.rokoi.rmho.ui.theme.RmhoSecondary

/** Medal accent colors for hub cards. */
fun medalColor(medal: MasteryMedal): Color = when (medal) {
    MasteryMedal.GOLD_GAUNTLET_MASTERED -> RmhoSecondary
    MasteryMedal.SILVER_DRILL_PASS -> Color(0xFFC0C0C0)
    MasteryMedal.BRONZE_THEORY_LAB -> Color(0xFFCD7F32)
    MasteryMedal.UNLOCKED -> RmhoPrimary
    MasteryMedal.LOCKED -> Color(0xFF546E8F)
}

fun medalLabel(medal: MasteryMedal): String = when (medal) {
    MasteryMedal.GOLD_GAUNTLET_MASTERED -> "Mastered"
    MasteryMedal.SILVER_DRILL_PASS -> "Drill passed"
    MasteryMedal.BRONZE_THEORY_LAB -> "Theory + Lab done"
    MasteryMedal.UNLOCKED -> "Not started"
    MasteryMedal.LOCKED -> "Locked"
}

/** One RTL Persian line, honouring the app-wide show_fa preference. */
@Composable
fun FaLine(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    if (text.isEmpty() || !UiPrefs.showFa) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * Coach destination (Phase 1): the curriculum DAG on top, with the
 * classic Rotation Trainer and Hunt Analysis kept one tap away.
 */
@Composable
fun CoachHubScreen(
    onOpenLesson: (String) -> Unit,
    onOpenClassic: () -> Unit,
    onOpenAnalysis: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { CoachRepository(context) }
    var bundle by remember { mutableStateOf<CoachCurriculumBundle?>(null) }
    var progress by remember { mutableStateOf<Map<String, UserNodeProgress>>(emptyMap()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repo.bundle() }
            .onSuccess { b ->
                bundle = b
                progress = repo.progressMap(b)
            }
            .onFailure { loadError = it.message ?: "parse error" }
    }

    val b = bundle
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Text("Combat Coach", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "A step-by-step curriculum that takes you from button-mashing to frame-aware mastery: theory, a frame lab, drills and a self-test gauntlet for every lesson.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FaSection(
                "اینجا قدم‌به‌قدم از ضربه‌زدنِ بی‌هدف به تسلط فریم‌محور می‌رسی: هر درس چهار بخش دارد — تئوری کوتاه، آزمایشگاه فریم، تمرین عملی و آزمون نهایی با خودارزیابی.",
                title = "مربی مبارزه"
            )
            if (b != null && b.isTimingPlaceholder) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Note: lesson frame timings are placeholders until measured from the real game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (b == null && loadError == null) {
            item { CircularProgressIndicator() }
        }
        if (loadError != null) {
            item {
                Text(
                    "Coach content failed to load: $loadError",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (b != null) {
            val lastNode = repo.lastOpenedNodeId
            val resumeNode = b.nodes.firstOrNull { it.nodeId == lastNode }
            if (resumeNode != null &&
                progress[resumeNode.nodeId]?.isUnlocked == true
            ) {
                item {
                    Card(
                        onClick = { onOpenLesson(resumeNode.nodeId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                            Spacer(Modifier.height(0.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    "Resume: ${resumeNode.nodeId} • ${resumeNode.titleEn}",
                                    fontWeight = FontWeight.Bold
                                )
                                FaLine(resumeNode.subtitleFa)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Curriculum",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(b.nodes, key = { it.nodeId }) { node ->
                val p = progress[node.nodeId]
                val hasLesson = b.lessons.containsKey(node.nodeId)
                DagNodeCard(
                    node = node,
                    progress = p,
                    hasLesson = hasLesson,
                    onClick = {
                        if (p?.isUnlocked == true && hasLesson) onOpenLesson(node.nodeId)
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Classic Tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(
                onClick = onOpenClassic,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Rotation Trainer", fontWeight = FontWeight.Bold)
                    Text(
                        "The original per-Adventurer rotation trainer: watch, follow, test.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FaLine("مربی روتیشن کلاسیک برای هر ادونچرر")
                }
            }
        }
        item {
            Card(
                onClick = onOpenAnalysis,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hunt Analysis", fontWeight = FontWeight.Bold)
                    Text(
                        "Analyse a hunt recording against a rotation, frame by frame.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FaLine("تحلیل ویدیوی شکار در برابر روتیشن")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DagNodeCard(
    node: CurriculumNode,
    progress: UserNodeProgress?,
    hasLesson: Boolean,
    onClick: () -> Unit
) {
    val medal = progress?.medal ?: MasteryMedal.LOCKED
    val locked = progress?.isUnlocked != true
    Card(
        onClick = onClick,
        enabled = !locked && hasLesson,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, medalColor(medal)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    "${node.nodeId} • STAGE ${node.stageLadderIndex} • ${node.titleEn}",
                    fontWeight = FontWeight.Bold,
                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(4.dp))
            FaLine(node.subtitleFa)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        " Locked — earn Silver on the previous lesson first",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        medalLabel(medal),
                        color = medalColor(medal),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "  •  ~${node.estimatedMinutes} min",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasLesson) {
                        Text(
                            "  •  Coming soon",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
