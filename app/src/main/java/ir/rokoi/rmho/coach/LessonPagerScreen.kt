package ir.rokoi.rmho.coach

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.ui.FaSection
import kotlinx.coroutines.launch

private val CoachTabTitles = listOf("Theory", "Frame Lab", "Drill", "Gauntlet")

/**
 * One lesson = one 4-tab pager (Theory / Frame Lab / Drill / Gauntlet).
 * Pager swipe is disabled on the interactive tabs (Frame Lab scrubber,
 * Drill gesture pad) so gestures never fight the pager.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonPagerScreen(nodeId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { CoachRepository(context) }
    val scope = rememberCoroutineScope()
    var bundle by remember { mutableStateOf<CoachCurriculumBundle?>(null) }
    var progress by remember { mutableStateOf<UserNodeProgress?>(null) }
    var labSeekMs by remember { mutableStateOf<Long?>(null) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { CoachTabTitles.size })

    LaunchedEffect(Unit) {
        runCatching { repo.bundle() }.onSuccess { b ->
            bundle = b
            progress = repo.progress(b, nodeId)
            repo.lastOpenedNodeId = nodeId
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        repo.lastOpenedTab = pagerState.currentPage
    }

    val b = bundle
    val node = b?.nodes?.firstOrNull { it.nodeId == nodeId }
    val lesson = b?.lessons?.get(nodeId)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (node != null) "$nodeId • ${node.titleEn}" else nodeId,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                val p = progress
                if (p != null) {
                    Text(
                        medalLabel(p.medal),
                        color = medalColor(p.medal),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        when {
            b == null -> CircularProgressIndicator(
                modifier = Modifier.padding(24.dp)
            )
            lesson == null -> Column(Modifier.padding(20.dp)) {
                Text(
                    "Coming soon",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This lesson is part of the curriculum and arrives in a later phase.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (node != null) FaLine(node.subtitleFa)
            }
            else -> {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    CoachTabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(title) }
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerState.currentPage == 0 ||
                        pagerState.currentPage == 3,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> TheoryTab(
                            lesson = lesson,
                            onTheorySeen = {
                                progress = repo.recordTheoryRead(b, nodeId)
                            }
                        )
                        1 -> FrameLabTab(
                            spec = lesson.frameLab,
                            seekMs = labSeekMs,
                            onSeekConsumed = { labSeekMs = null },
                            onInspected = {
                                progress = repo.recordLabInspected(b, nodeId)
                            }
                        )
                        2 -> DrillTab(
                            spec = lesson.drill,
                            onComplete = { accuracy, cleanReps ->
                                progress = repo.recordDrillResult(
                                    b, nodeId, accuracy, cleanReps,
                                    lesson.drill.passAccuracyPercent
                                )
                            }
                        )
                        else -> GauntletTab(
                            spec = lesson.gauntlet,
                            onBranch = { branch ->
                                progress = repo.recordGauntletBranch(b, nodeId, branch)
                                if (!branch.isPassBranch) {
                                    if (branch.seekToLabMs != null) {
                                        labSeekMs = branch.seekToLabMs
                                    }
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            branch.remediationTabIndex
                                                .coerceIn(0, CoachTabTitles.lastIndex)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TheoryTab(lesson: LessonUnit, onTheorySeen: () -> Unit) {
    val theory = lesson.theory
    LaunchedEffect(lesson.nodeId) { onTheorySeen() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(theory.headlineEn, style = MaterialTheme.typography.titleLarge)
        if (theory.summaryFa.isNotEmpty()) {
            FaSection(theory.summaryFa, title = "شرح درس")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("The Rule", fontWeight = FontWeight.Bold)
                if (theory.ifConditionFa.isNotEmpty()) {
                    Text("IF", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    FaLine(theory.ifConditionFa)
                }
                if (theory.thenActionFa.isNotEmpty()) {
                    Text("THEN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    FaLine(theory.thenActionFa)
                }
                if (theory.neverRuleFa.isNotEmpty()) {
                    Text(
                        "NEVER",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    FaLine(theory.neverRuleFa)
                }
            }
        }
        if (theory.keyTerms.isNotEmpty()) {
            Text("Key terms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            theory.keyTerms.forEach { term ->
                Column {
                    Text(
                        term.termEn,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    FaLine(term.definitionFa)
                }
            }
        }
        if (theory.comboSteps.isNotEmpty()) {
            Text("Combo steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            theory.comboSteps.forEach { step ->
                Column {
                    Text(
                        "${step.stepIndex} — ${step.moveNameEn} (${step.gesture})",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "window ${step.minInputWindowMs}–${step.maxInputWindowMs} ms • commit ${step.commitFramesMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (step.moveNameFa.isNotEmpty()) FaLine(step.moveNameFa)
                    FaLine(step.coachingCueFa)
                }
            }
        }
        if (theory.coachingCueFa.isNotEmpty()) {
            FaSection(theory.coachingCueFa, title = "نکته‌ی مربی")
        }
        if (lesson.timingSourceNote.isNotEmpty()) {
            Text(
                lesson.timingSourceNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GauntletTab(spec: GauntletSpec, onBranch: (TriageBranch) -> Unit) {
    var chosen by remember { mutableStateOf<TriageBranch?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Gauntlet — in-game mission", style = MaterialTheme.typography.titleMedium)
        spec.taskChecklistFa.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.padding(start = 8.dp)) { FaLine(item) }
            }
        }
        if (spec.combatStyleFallbackNoteFa.isNotEmpty()) {
            FaSection(spec.combatStyleFallbackNoteFa, title = "اگر سبکت فرق دارد")
        }
        if (spec.triageQuestionFa.isNotEmpty()) {
            FaSection(spec.triageQuestionFa, title = "خودارزیابی")
        }
        spec.triageBranches.forEach { branch ->
            OutlinedButton(
                onClick = {
                    chosen = branch
                    onBranch(branch)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    FaLine(branch.symptomLabelFa)
                }
            }
        }
        val c = chosen
        if (c != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (c.isPassBranch) {
                        Text(
                            "Gauntlet passed — GOLD medal earned.",
                            fontWeight = FontWeight.Bold,
                            color = medalColor(MasteryMedal.GOLD_GAUNTLET_MASTERED)
                        )
                    } else {
                        Text(
                            "Diagnosis — taking you to the fix…",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (c.rootCauseFa.isNotEmpty()) {
                        FaSection(c.rootCauseFa, title = "تشخیص و نسخه")
                    }
                }
            }
        }
    }
}
