package ir.rokoi.rmho.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.data.AlignmentReport
import ir.rokoi.rmho.data.DataBundle
import ir.rokoi.rmho.data.DetectionResult
import ir.rokoi.rmho.data.HuntAligner
import ir.rokoi.rmho.data.HuntAnalyzer
import ir.rokoi.rmho.data.HuntEventClass
import ir.rokoi.rmho.data.StepOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coach Phase 2 — pick the rotation a hunt recording followed. */

@Composable
fun AnalysisPickerScreen(
    data: DataBundle,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onBack) { Text("‹ Back to Coach") }
            Text("Analyse a hunt", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pick the rotation you followed in the recording. Then import the " +
                    "screen recording of that hunt and the coach will check your " +
                    "button inputs against the rotation: matched steps, skipped " +
                    "steps and rough timing.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FaSection(
                "روتیشنی که توی اون شکار دنبال کردی رو انتخاب کن، بعد ویدیوی ضبط‌شدهٔ شکار رو وارد کن؛ مربی ورودی‌های دکمه‌ها رو با روتیشن مقایسه می‌کنه و قدم‌های درست، قدم‌های جاافتاده و زمان‌بندی تقریبی رو نشونت می‌ده.",
                title = "تحلیل شکار"
            )
        }
        items(data.rotations) { rot ->
            val adv = data.adventurers.firstOrNull { it.id == rot.adventurerId }
            val title = when {
                adv == null -> weaponLabel(rot.weapon ?: "")
                adv.weapon == "ANY" && rot.weapon != null ->
                    "${adv.nameEn} — ${weaponLabel(rot.weapon ?: "")}"
                else -> adv.nameEn
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPick(rot.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(16.dp)) {
                    if (adv != null) {
                        GoldPortrait(adv.imageUrl, adv.nameEn, 48.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${rot.steps.size} steps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Coach Phase 2 — import a hunt recording and read the mistake report. */

@Composable
fun HuntAnalysisScreen(data: DataBundle, rotationId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rot = data.rotations.firstOrNull { it.id == rotationId }

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("") }
    var videoMeta by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var detection by remember { mutableStateOf<DetectionResult?>(null) }
    var report by remember { mutableStateOf<AlignmentReport?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            videoUri = uri
            detection = null
            report = null
            error = ""
            videoName = queryDisplayName(context, uri) ?: "Selected recording"
            scope.launch {
                videoMeta = withContext(Dispatchers.IO) { probeMeta(context, uri) }
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
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Hunt analysis", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        if (rot == null) {
            Text("Rotation not found.", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        val adv = data.adventurers.firstOrNull { it.id == rot.adventurerId }
        Text(
            "Rotation: " + when {
                adv == null -> weaponLabel(rot.weapon ?: "")
                adv.weapon == "ANY" && rot.weapon != null ->
                    "${adv.nameEn} — ${weaponLabel(rot.weapon ?: "")}"
                else -> adv.nameEn
            } + " • ${rot.steps.size} steps",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("1 · Import the hunt recording", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use your phone's built-in screen recorder after a hunt, then " +
                        "import the video here. Analysis runs on your device — " +
                        "nothing is uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    enabled = !running
                ) { Text(if (videoUri == null) "Choose video…" else "Choose a different video…") }
                if (videoUri != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(videoName, style = MaterialTheme.typography.bodyMedium)
                    if (videoMeta.isNotEmpty()) {
                        Text(
                            videoMeta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("2 · Analyse", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                if (running) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Analysing… ${(progress * 100).toInt()}%")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { job?.cancel() }) { Text("Cancel") }
                } else {
                    Button(
                        onClick = {
                            val uri = videoUri ?: return@Button
                            running = true
                            progress = 0f
                            error = ""
                            detection = null
                            report = null
                            job = scope.launch {
                                try {
                                    val det = withContext(Dispatchers.Default) {
                                        HuntAnalyzer(context).analyse(uri) { p -> progress = p }
                                    }
                                    detection = det
                                    report = HuntAligner.align(det.events, rot)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    error = "Analysis cancelled."
                                } catch (e: Exception) {
                                    error = "Analysis failed: ${e.message ?: "unknown error"}"
                                } finally {
                                    running = false
                                }
                            }
                        },
                        enabled = videoUri != null
                    ) { Text("Analyse this hunt") }
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFF8A80))
                }
            }
        }

        val det = detection
        val rep = report
        if (det != null && rep != null) {
            Spacer(Modifier.height(16.dp))
            AnalysisReportView(det, rep)
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "What this report can and cannot judge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It reads the on-screen buttons only: Basic Attack, Dash, the " +
                        "Burst button and the skill buttons. Skill steps are matched " +
                        "to any skill button (the game changes skill icons per " +
                        "weapon, so which skill it was is not claimed). Hold length " +
                        "is not measured in this version.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It cannot judge positioning, dodging through attacks " +
                        "(i-frames), or damage output — no video analysis of the " +
                        "buttons could. Treat the score as a practice guide, " +
                        "not a verdict. Best results: standard (Starter) HUD, " +
                        "the full hunt in frame, no overlays covering the buttons.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FaSection(
                    "این گزارش فقط از روی دکمه‌های روی صفحه خونده می‌شه: حملهٔ اصلی، دش، دکمهٔ Burst و دکمه‌های مهارت. قدم‌های مهارتی با هر دکمهٔ مهارتی تطبیق داده می‌شن و مدت نگه‌داشتن دکمه در این نسخه اندازه گرفته نمی‌شه. موقعیت، جاخالی‌دادن از وسط حمله و میزان دمیج قابل قضاوت نیست؛ نمره رو راهنمای تمرین بدون، نه حکم نهایی.",
                    title = "حدود این گزارش"
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AnalysisReportView(det: DetectionResult, rep: AlignmentReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Coach report",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Rotation score: ${rep.scorePercent}%  •  Matched ${rep.matched}  •  " +
                    "Skipped steps: ${rep.skipped}  •  Not reached: ${rep.notReached}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Full rotation cycles completed: ${rep.cyclesCompleted}  •  " +
                    "Extra inputs: ${rep.extraEvents}" +
                    (if (rep.orderSignals > 0) "  •  Out-of-order signals: ${rep.orderSignals}" else ""),
                style = MaterialTheme.typography.bodyMedium
            )
            rep.avgGapSec?.let {
                Text(
                    "Average gap between matched steps: ${"%.1f".format(it)}s",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(10.dp))
            val byClass = det.events.groupingBy { it.cls }.eachCount()
            Text(
                "Detected inputs (${det.events.size}): " +
                    HuntEventClass.values().joinToString("  •  ") { c ->
                        "${classLabel(c)} ${byClass[c] ?: 0}"
                    },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Analysed ${"%.0f".format(det.durationSec)}s of video " +
                    "(${det.videoWidth}×${det.videoHeight})  •  Detection quality: ${det.qualityLabel}  •  ${det.decoderUsed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (det.flashesSec.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Part-break moments (screen-flash candidates): " +
                        det.flashesSec.joinToString(", ") { HuntAligner.fmtTime(it) } +
                        " — a flash was detected at these times; which part " +
                        "broke is not identified.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (rep.notTrackedSteps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Not tracked (needs the squad / other controls, not the " +
                        "action buttons): ${rep.notTrackedSteps.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("First-cycle steps", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    // Group atomic items back into their rotation steps for display.
    val groups = rep.firstCycle.groupBy { it.stepOrder }
    groups.forEach { (_, rows) ->
        val label = rows.first().stepLabel
        val matchedRows = rows.filter { it.outcome == StepOutcome.MATCHED }
        val skippedRows = rows.filter { it.outcome == StepOutcome.SKIPPED }
        val reachedRows = rows.filter { it.outcome == StepOutcome.NOT_REACHED }
        val status = when {
            skippedRows.isEmpty() && reachedRows.isEmpty() -> "MATCHED"
            matchedRows.isEmpty() && skippedRows.isNotEmpty() -> "SKIPPED"
            matchedRows.isEmpty() -> "NOT REACHED"
            else -> "PARTIAL"
        }
        val color = when (status) {
            "MATCHED" -> Color(0xFF81C784)
            "SKIPPED" -> Color(0xFFFF8A80)
            "PARTIAL" -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val detail = when (status) {
            "MATCHED" -> "at " + HuntAligner.fmtTime(matchedRows.last().matchedAtSec ?: 0.0)
            "PARTIAL" -> "${matchedRows.size}/${rows.size} inputs found"
            else -> ""
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    when (status) {
                        "MATCHED" -> "✓"
                        "SKIPPED" -> "✗"
                        "PARTIAL" -> "~"
                        else -> "–"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        status + if (detail.isNotEmpty()) "  •  $detail" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }
        }
    }
}

private fun classLabel(c: HuntEventClass): String = when (c) {
    HuntEventClass.BASIC -> "Basic Attack"
    HuntEventClass.DASH -> "Dash"
    HuntEventClass.BURST -> "Burst"
    HuntEventClass.SKILL -> "Skill buttons"
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun probeMeta(context: android.content.Context, uri: Uri): String {
    return try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toDoubleOrNull() ?: 0.0) / 1000.0
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
        mmr.release()
        "Duration ${HuntAligner.fmtTime(dur)}  •  ${w}×${h}"
    } catch (e: Exception) {
        ""
    }
}
