package ir.rokoi.rmho.coach

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.ui.FaSection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Drill host: Type A is fully playable; B/C are phase-2 stubs. */
@Composable
fun DrillTab(
    spec: DrillSpec,
    onComplete: (accuracyPercent: Int, cleanReps: Int) -> Unit
) {
    when (spec) {
        is TypeAGestureRhythm -> TypeADrill(spec, onComplete)
        is TypeBReactionCue -> StubDrill(spec.instructionsFa, spec.noteFa)
        is TypeCStateRouting -> StubDrill(spec.instructionsFa, spec.noteFa)
    }
}

@Composable
private fun StubDrill(instructionsFa: String, noteFa: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Drill", style = MaterialTheme.typography.titleMedium)
        if (instructionsFa.isNotEmpty()) FaSection(instructionsFa, title = "شرح تمرین")
        if (noteFa.isNotEmpty()) FaSection(noteFa, title = "یادداشت")
        Text(
            "This drill type arrives in Phase 2.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun angularDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

@Composable
private fun TypeADrill(
    spec: TypeAGestureRhythm,
    onComplete: (accuracyPercent: Int, cleanReps: Int) -> Unit
) {
    val view = LocalView.current
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var startNanos by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var currentRep by remember { mutableIntStateOf(0) }
    var successes by remember { mutableIntStateOf(0) }
    var cleanReps by remember { mutableIntStateOf(0) }
    var mashThisRep by remember { mutableIntStateOf(0) }
    var lockoutUntilElapsed by remember { mutableLongStateOf(0L) }
    var feedback by remember { mutableStateOf("Press Start, then play the prompts in their windows.") }
    var finalAccuracy by remember { mutableIntStateOf(0) }
    val consumed = remember { mutableStateMapOf<String, Boolean>() }

    fun elapsedMs(): Long =
        if (startNanos == 0L) 0L else (System.nanoTime() - startNanos) / 1_000_000L

    fun haptic(kind: Int) {
        view.performHapticFeedback(kind)
    }

    fun closeRep() {
        val allHit = spec.prompts.all { consumed[it.promptId] == true }
        if (allHit && mashThisRep <= spec.maxAllowedExtraTapsPerRep) {
            cleanReps++
        }
        consumed.clear()
        mashThisRep = 0
    }

    // Loop ticker: monotonic wall-clock, rep rollover, finish.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        withInfiniteAnimationFrameMillis {
            val elapsed = elapsedMs()
            positionMs = if (spec.loopDurationMs > 0) {
                elapsed % spec.loopDurationMs
            } else {
                0L
            }
            val repIndex = if (spec.loopDurationMs > 0) {
                (elapsed / spec.loopDurationMs).toInt()
            } else {
                0
            }
            while (currentRep < repIndex && currentRep < spec.requiredReps) {
                closeRep()
                currentRep++
            }
            if (repIndex >= spec.requiredReps && !finished) {
                finished = true
                running = false
                val total = (spec.prompts.size * spec.requiredReps).coerceAtLeast(1)
                finalAccuracy = successes * 100 / total
                onComplete(finalAccuracy, cleanReps)
            }
        }
    }

    fun startDrill() {
        consumed.clear()
        successes = 0
        cleanReps = 0
        mashThisRep = 0
        currentRep = 0
        lockoutUntilElapsed = 0L
        finalAccuracy = 0
        finished = false
        feedback = "Go! Watch the prompt windows."
        startNanos = System.nanoTime()
        running = true
    }

    fun evaluateGesture(kind: GestureKind, durationMs: Long, angleDeg: Float?) {
        if (!running || finished) return
        val elapsed = elapsedMs()
        val pos = if (spec.loopDurationMs > 0) elapsed % spec.loopDurationMs else 0L
        if (elapsed < lockoutUntilElapsed) {
            mashThisRep++
            feedback = "Mash during lockout — input ignored."
            haptic(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            )
            return
        }
        val candidate = spec.prompts.firstOrNull { p ->
            consumed[p.promptId] != true && pos >= p.windowStartMs && pos <= p.windowEndMs
        }
        if (candidate == null) {
            mashThisRep++
            lockoutUntilElapsed = elapsed + spec.antiMashPenaltyMs
            feedback = "Extra input — mash penalty (${spec.antiMashPenaltyMs} ms lockout)."
            haptic(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            )
            return
        }
        consumed[candidate.promptId] = true
        val delta = pos - candidate.idealTimestampMs
        when {
            kind != candidate.gestureType ->
                feedback = "Wrong gesture for ${candidate.labelEn} — needed ${candidate.gestureType}."
            candidate.gestureType == GestureKind.HOLD_RELEASE &&
                durationMs < candidate.minHoldDurationMs ->
                feedback = "Released too early — hold at least ${candidate.minHoldDurationMs} ms."
            candidate.gestureType == GestureKind.DIRECTIONAL_FLICK &&
                candidate.expectedFlickAngleDeg != null && angleDeg != null &&
                angularDistance(angleDeg, candidate.expectedFlickAngleDeg) >
                candidate.angleToleranceDeg ->
                feedback = "Flick direction off — aim within ±${candidate.angleToleranceDeg.toInt()}°."
            else -> {
                successes++
                feedback = "${candidate.labelEn}: ${if (delta >= 0) "+" else ""}$delta ms"
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    val nextPrompt = spec.prompts.firstOrNull { consumed[it.promptId] != true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Drill — Gesture Rhythm", style = MaterialTheme.typography.titleMedium)
        if (spec.instructionsFa.isNotEmpty()) {
            FaSection(spec.instructionsFa, title = "شرح تمرین")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Rep ${minOf(currentRep + 1, spec.requiredReps)}/${spec.requiredReps}",
                fontWeight = FontWeight.Bold
            )
            Text("Hits: $successes")
            Text("Mash: $mashThisRep")
        }
        LinearProgressIndicator(
            progress = {
                if (spec.loopDurationMs > 0) {
                    positionMs.toFloat() / spec.loopDurationMs.toFloat()
                } else {
                    0f
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            feedback,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )

        // ---- Gesture pad ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(spec.drillId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null && running && !finished) {
                            val duration = up.uptimeMillis - down.uptimeMillis
                            val dx = up.position.x - down.position.x
                            val dy = up.position.y - down.position.y
                            val dist = sqrt(dx * dx + dy * dy)
                            val flickThreshold = 36.dp.toPx()
                            val kind = when {
                                dist >= flickThreshold -> GestureKind.DIRECTIONAL_FLICK
                                duration >= 220L -> GestureKind.HOLD_RELEASE
                                else -> GestureKind.TAP
                            }
                            val angle = if (kind == GestureKind.DIRECTIONAL_FLICK) {
                                var deg = Math.toDegrees(
                                    atan2(dy.toDouble(), dx.toDouble())
                                ).toFloat()
                                if (deg < 0f) deg += 360f
                                deg
                            } else {
                                null
                            }
                            evaluateGesture(kind, duration, angle)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (nextPrompt != null && running) {
                    Text(
                        nextPrompt.labelEn,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FaLine(nextPrompt.hintFa)
                    Text(
                        "window ${nextPrompt.windowStartMs}–${nextPrompt.windowEndMs} ms • now $positionMs ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!running && !finished) {
                    Text("Gesture pad — tap, hold, or flick here")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("◀ Movement Pad", style = MaterialTheme.typography.labelSmall)
                Text("Action Pad ▶", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (!running && !finished) {
            Button(onClick = { startDrill() }, modifier = Modifier.fillMaxWidth()) {
                Text("Start drill")
            }
        }
        if (finished) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val verdict = when {
                        finalAccuracy >= spec.goldAccuracyPercent -> "GOLD pace"
                        finalAccuracy >= spec.passAccuracyPercent -> "Passed — Silver pace"
                        else -> "Not yet — keep practicing"
                    }
                    Text(
                        "Accuracy: $finalAccuracy% — $verdict",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Clean reps: $cleanReps/${spec.requiredReps}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { startDrill() }) {
                        Text("Run again")
                    }
                }
            }
        }
    }
}
