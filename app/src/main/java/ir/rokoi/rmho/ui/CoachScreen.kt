package ir.rokoi.rmho.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.data.DataBundle
import ir.rokoi.rmho.data.RotationGuide
import kotlinx.coroutines.delay

/** Coach tab — Phase 1: rotation trainer (Watch → Follow → Test). */

@Composable
fun CoachScreen(data: DataBundle, onOpenRotation: (String) -> Unit, onOpenAnalysis: () -> Unit = {}) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Text("Rotation Coach", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Learn each Adventurer's best rotation step by step: watch it, follow it on the practice pad, then test yourself from memory.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FaSection(
                "اینجا روتیشنِ هر ادونچرر رو قدم‌به‌قدم یاد می‌گیری: اول تماشا کن، بعد روی دکمه‌های تمرینی همراهش برو، و آخرش بدون راهنما خودت رو امتحان کن. این روتیشن‌ها از راهنماهای معتبر بازی جمع شده‌اند و بدون آپدیت برنامه هم تازه می‌شوند.",
                title = "مربی روتیشن"
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenAnalysis() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Analyse a hunt — NEW",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Coach Phase 2: import a screen recording of a hunt and get a mistake report — which rotation steps you hit, which you skipped, and your rough timing. Judged from the on-screen buttons only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (data.rotations.isEmpty()) {
            item {
                Text(
                    "Rotations are being prepared — check back after the next data update.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        items(data.rotations.filter { it.adventurerId != "fated" }) { rot ->
            val adv = data.adventurers.firstOrNull { it.id == rot.adventurerId }
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenRotation(rot.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (adv != null) {
                        GoldPortrait(adv.imageUrl, adv.nameEn, 64.dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            adv?.nameEn ?: weaponLabel(rot.weapon ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            listOfNotNull(
                                adv?.let { weaponLabel(it.weapon) } ?: rot.weapon?.let { weaponLabel(it) },
                                "${rot.steps.size} steps"
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (rot.tips.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                rot.tips,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        // Fated is ONE character with 8 weapon rotations: a single grouped
        // entry here; the weapon is picked inside the practice screen.
        val fatedRots = data.rotations.filter { it.adventurerId == "fated" }
        if (fatedRots.isNotEmpty()) {
            item {
                val adv = data.adventurers.firstOrNull { it.id == "fated" }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenRotation(fatedRots.first().id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (adv != null) {
                            GoldPortrait(adv.imageUrl, adv.nameEn, 64.dp)
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Fated Adventurer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "All 8 weapons • ${fatedRots.sumOf { it.steps.size }} steps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Your own hunter — one rotation per weapon. Pick a weapon inside and train it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private enum class CoachMode { WATCH, FOLLOW, TEST }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachPracticeScreen(data: DataBundle, rotationId: String, onBack: () -> Unit) {
    val entry = data.rotations.firstOrNull { it.id == rotationId }
    if (entry == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Rotation not found.", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }
    // Fated carries one rotation per weapon: the weapon selector on this
    // screen switches currentId in place; all practice state below is
    // keyed on currentId, so the Watch/Follow/Test flow resets cleanly
    // per weapon without touching navigation.
    var currentId by remember(rotationId) { mutableStateOf(rotationId) }
    val rot = data.rotations.firstOrNull { it.id == currentId } ?: entry
    val adv = data.adventurers.firstOrNull { it.id == rot.adventurerId }
    val siblings = remember(data, rot.adventurerId) {
        if (rot.adventurerId == "fated") {
            data.rotations.filter { it.adventurerId == "fated" }
        } else emptyList()
    }
    val steps = remember(currentId) { rot.steps.sortedBy { it.order } }
    val padLabels = remember(currentId) {
        steps.map { stepLabel(it) }.distinct()
    }

    var mode by remember(currentId) { mutableStateOf(CoachMode.WATCH) }
    var index by remember(currentId, mode) { mutableIntStateOf(0) }
    var playing by remember(currentId, mode) { mutableStateOf(false) }
    var flash by remember(currentId, mode) { mutableStateOf<String?>(null) } // "ok" | "bad"
    var hint by remember(currentId, mode) { mutableStateOf("") }
    var wrongTaps by remember(currentId, mode) { mutableIntStateOf(0) }
    var startedAt by remember(currentId, mode) { mutableLongStateOf(0L) }
    var lastTapAt by remember(currentId, mode) { mutableLongStateOf(0L) }
    val stepTimes = remember(currentId, mode) { mutableStateListOf<Long>() }
    val finished = index >= steps.size

    fun reset() {
        index = 0
        playing = false
        flash = null
        hint = ""
        wrongTaps = 0
        startedAt = 0L
        lastTapAt = 0L
        stepTimes.clear()
    }

    // Watch mode auto-advance.
    LaunchedEffect(playing, index, mode) {
        if (mode == CoachMode.WATCH && playing && index < steps.size) {
            delay(1500)
            index += 1
            if (index >= steps.size) playing = false
        }
    }
    // Clear the ok/bad flash shortly after it shows.
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(450)
            flash = null
        }
    }

    fun onPad(label: String) {
        if (mode == CoachMode.WATCH || finished) return
        val now = System.currentTimeMillis()
        if (startedAt == 0L) {
            startedAt = now
            lastTapAt = now
        }
        val expected = stepLabel(steps[index])
        if (label == expected) {
            stepTimes.add(now - lastTapAt)
            lastTapAt = now
            flash = "ok"
            hint = ""
            index += 1
        } else {
            wrongTaps += 1
            flash = "bad"
            hint = if (mode == CoachMode.FOLLOW) {
                "Not that one — the coach is asking for “$expected”."
            } else {
                "Wrong move — keep going, it counts at the end."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack) { Text("‹ Back to Coach") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (adv != null) {
                GoldPortrait(adv.imageUrl, adv.nameEn, 56.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    if (adv != null && adv.weapon == "ANY" && rot.weapon != null) {
                        "${adv.nameEn} — ${weaponLabel(rot.weapon ?: "")}"
                    } else adv?.nameEn ?: "Rotation",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${steps.size} steps • source: ${rot.source.ifEmpty { "community guides" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Weapon selector (Fated only): one character, 8 rotations.
        if (siblings.size > 1) {
            Text(
                "Weapon — the Fated Adventurer fights with all 8",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                siblings.forEach { sib ->
                    FilterChip(
                        selected = sib.id == rot.id,
                        onClick = { currentId = sib.id },
                        label = { Text(weaponLabel(sib.weapon ?: "")) }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Mode picker
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == CoachMode.WATCH,
                onClick = { mode = CoachMode.WATCH },
                label = { Text("1 · Watch") }
            )
            FilterChip(
                selected = mode == CoachMode.FOLLOW,
                onClick = { mode = CoachMode.FOLLOW },
                label = { Text("2 · Follow") }
            )
            FilterChip(
                selected = mode == CoachMode.TEST,
                onClick = { mode = CoachMode.TEST },
                label = { Text("3 · Test") }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (mode) {
                CoachMode.WATCH -> "Watch the rotation play out, step by step."
                CoachMode.FOLLOW -> "Tap the pad in order — the coach shows you the next move."
                CoachMode.TEST -> "From memory: perform the whole rotation. No peeking!"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        // Steps
        steps.forEachIndexed { i, step ->
            val state = when {
                i < index -> "done"
                i == index && !finished -> "current"
                else -> "todo"
            }
            val hideAction = mode == CoachMode.TEST && state != "done"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (state) {
                        "current" -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = if (state == "current") {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                } else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state == "done") {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "done",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state == "current") {
                                MaterialTheme.colorScheme.secondary
                            } else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (hideAction) "Step ${i + 1} — from memory" else step.action,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state == "done") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else MaterialTheme.colorScheme.onSurface
                        )
                        if (!hideAction && step.notes.isNotEmpty()) {
                            Text(
                                step.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!hideAction) {
                        Text(
                            inputLabel(step.input),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Controls / results
        if (finished) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Rotation complete!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (mode == CoachMode.TEST && stepTimes.isNotEmpty()) {
                        val total = (stepTimes.sum()) / 1000.0
                        val avg = stepTimes.average() / 1000.0
                        val perfect = stepTimes.count { it <= 700 }
                        val good = stepTimes.count { it in 701..1600 }
                        val slow = stepTimes.size - perfect - good
                        val accuracy = if (stepTimes.size + wrongTaps == 0) 100
                            else stepTimes.size * 100 / (stepTimes.size + wrongTaps)
                        Spacer(Modifier.height(8.dp))
                        Text("Accuracy: $accuracy%  •  Wrong taps: $wrongTaps")
                        Text("Total time: ${"%.1f".format(total)}s  •  Avg per step: ${"%.2f".format(avg)}s")
                        Text("Perfect: $perfect  •  Good: $good  •  Slow: $slow")
                        Text(
                            "Perfect = next move within 0.7s, Good = within 1.6s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (mode == CoachMode.WATCH) {
                        Spacer(Modifier.height(6.dp))
                        Text("Now try Follow mode and tap it yourself.")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { reset() }) {
                            Icon(Icons.Filled.Replay, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Practice again")
                        }
                        if (mode != CoachMode.TEST) {
                            OutlinedButton(onClick = {
                                mode = if (mode == CoachMode.WATCH) CoachMode.FOLLOW else CoachMode.TEST
                            }) { Text("Next mode ›") }
                        }
                    }
                }
            }
        } else if (mode == CoachMode.WATCH) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (index >= steps.size) index = 0
                    playing = !playing
                }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (playing) "Pause" else "Play")
                }
                OutlinedButton(onClick = { reset() }) { Text("Restart") }
            }
        } else {
            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (flash == "bad") Color(0xFFFF8A80) else MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Practice pad — tap: ${if (index < steps.size) stepLabel(steps[index]) else ""}"
                    .takeIf { mode == CoachMode.FOLLOW } ?: "Practice pad",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                padLabels.forEach { label ->
                    val isExpected = mode == CoachMode.FOLLOW &&
                        index < steps.size && stepLabel(steps[index]) == label
                    Button(
                        onClick = { onPad(label) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                flash == "ok" && isExpected -> Color(0xFF2E7D32)
                                flash == "bad" && isExpected -> Color(0xFFB71C1C)
                                isExpected -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isExpected) Color(0xFF0A1730) else Color.White
                        )
                    ) {
                        Text(label, maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { reset() }) { Text("Restart") }
        }

        if (rot.tips.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Coach tips", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(rot.tips, style = MaterialTheme.typography.bodyMedium)
        }
        if (rot.tipsFa.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FaSection(rot.tipsFa, title = "نکتهٔ مربی")
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun stepLabel(step: ir.rokoi.rmho.data.RotationStep): String =
    step.short.ifEmpty { step.action }

private fun inputLabel(input: String): String = when (input.uppercase()) {
    "HOLD" -> "HOLD"
    "SWIPE" -> "SWIPE"
    else -> "TAP"
}
