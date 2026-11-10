package ir.rokoi.rmho.coach

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.rokoi.rmho.ui.FaSection
import ir.rokoi.rmho.ui.UiPrefs
import ir.rokoi.rmho.ui.theme.RmhoBg
import ir.rokoi.rmho.ui.theme.RmhoPrimary
import ir.rokoi.rmho.ui.theme.RmhoSecondary
import ir.rokoi.rmho.ui.theme.RmhoSurface
import ir.rokoi.rmho.ui.theme.RmhoSurfaceVariant
import ir.rokoi.rmho.ui.theme.RmhoText
import ir.rokoi.rmho.ui.theme.RmhoTextDim
import kotlin.math.abs
import kotlin.math.sqrt

/** How long before a window opens the action button starts pulsing. */
private const val PRE_CUE_MS: Long = 600L

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

private fun gestureGlyph(kind: GestureKind): String = when (kind) {
    GestureKind.TAP -> "●"
    GestureKind.HOLD_RELEASE -> "▬"
    GestureKind.DIRECTIONAL_FLICK -> "↑"
}

/** Bilingual feedback for one judged input; Persian wins when show_fa is on. */
private fun feedbackFor(result: DrillMatchResult, showFa: Boolean): String {
    val p = result.prompt
    return when (result.verdict) {
        DrillVerdict.HIT ->
            if (showFa) "آفرین! ${p?.labelEn} — اختلاف ${abs(result.deltaMs)} ms"
            else "Great! ${p?.labelEn} — off by ${abs(result.deltaMs)} ms"
        DrillVerdict.WRONG_GESTURE ->
            if (showFa) "این پنجره ${p?.labelEn} می‌خواست"
            else "That window wanted ${p?.labelEn}"
        DrillVerdict.TOO_SHORT_HOLD ->
            if (showFa) "زود رها کردی — دست‌کم ${p?.minHoldDurationMs} ms نگه دار"
            else "Released too early — hold at least ${p?.minHoldDurationMs} ms"
        DrillVerdict.WRONG_FLICK_ANGLE ->
            if (showFa) "جهت فلیک درست نبود — رو به بالا بکش"
            else "Flick direction off — flick upward"
        DrillVerdict.EARLY ->
            if (showFa) "زود زدی — صبر کن دکمه طلایی بشه"
            else "Too early — wait until the button turns gold"
        DrillVerdict.LATE ->
            if (showFa) "دیر شد — پنجره بسته شد"
            else "Too late — the window closed"
        DrillVerdict.MASH ->
            if (showFa) "ورودی اضافه = مش؛ فقط وقتی دکمه طلاییه بزن"
            else "Extra input = mash; only press when the button is gold"
    }
}

@Composable
private fun TypeADrill(
    spec: TypeAGestureRhythm,
    onComplete: (accuracyPercent: Int, cleanReps: Int) -> Unit
) {
    val view = LocalView.current
    val showFa = UiPrefs.showFa
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var startNanos by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var pressed by remember { mutableStateOf(false) }
    var currentRep by remember { mutableIntStateOf(0) }
    var successes by remember { mutableIntStateOf(0) }
    var cleanReps by remember { mutableIntStateOf(0) }
    var mashThisRep by remember { mutableIntStateOf(0) }
    var totalMash by remember { mutableIntStateOf(0) }
    var lockoutUntilElapsed by remember { mutableLongStateOf(0L) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var finalAccuracy by remember { mutableIntStateOf(0) }
    val consumed = remember { mutableStateMapOf<String, Boolean>() }

    fun elapsedMs(): Long =
        if (startNanos == 0L) 0L else (System.nanoTime() - startNanos) / 1_000_000L

    fun loopPos(elapsed: Long): Long =
        if (spec.loopDurationMs > 0) elapsed % spec.loopDurationMs else 0L

    fun haptic(kind: Int) {
        view.performHapticFeedback(kind)
    }

    fun rejectHaptic() {
        haptic(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
    }

    fun mashFeedback() {
        feedback = if (showFa) "ورودی اضافه = مش؛ فقط وقتی دکمه طلاییه بزن"
        else "Extra input = mash; only press when the button is gold"
    }

    fun closeRep() {
        val allHit = spec.prompts.all { consumed[it.promptId] == true }
        if (allHit && mashThisRep <= spec.maxAllowedExtraTapsPerRep) {
            cleanReps++
        }
        consumed.clear()
        mashThisRep = 0
    }

    // Loop ticker: one wall-clock frame per iteration (position, rep
    // rollover, finish). Must loop — a single frame call freezes the pad.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameMillis { }
            val elapsed = elapsedMs()
            positionMs = loopPos(elapsed)
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
        totalMash = 0
        currentRep = 0
        lockoutUntilElapsed = 0L
        finalAccuracy = 0
        finished = false
        feedback = null
        positionMs = 0L
        startNanos = System.nanoTime()
        running = true
    }

    fun evaluateGesture(kind: GestureKind, durationMs: Long, angleDeg: Float?, downPosMs: Long) {
        if (!running || finished) return
        val elapsed = elapsedMs()
        if (elapsed < lockoutUntilElapsed) {
            totalMash++
            mashThisRep++
            mashFeedback()
            rejectHaptic()
            return
        }
        // A hold is judged by where its RELEASE lands (buffering); taps and
        // flicks by where the press started.
        val evalPos = if (kind == GestureKind.HOLD_RELEASE) loopPos(elapsed) else downPosMs
        val result = DrillMatcher.match(
            prompts = spec.prompts,
            consumedIds = consumed.keys.toSet(),
            kind = kind,
            durationMs = durationMs,
            angleDeg = angleDeg,
            posMs = evalPos
        )
        if (result.consumesPrompt) {
            result.prompt?.let { consumed[it.promptId] = true }
        }
        when (result.verdict) {
            DrillVerdict.HIT -> {
                successes++
                haptic(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            DrillVerdict.MASH -> {
                totalMash++
                mashThisRep++
                lockoutUntilElapsed = elapsed + spec.antiMashPenaltyMs
                rejectHaptic()
            }
            DrillVerdict.EARLY, DrillVerdict.LATE ->
                haptic(HapticFeedbackConstants.CLOCK_TICK)
            else -> rejectHaptic()
        }
        feedback = feedbackFor(result, showFa)
    }

    val nextPrompt = spec.prompts.firstOrNull { consumed[it.promptId] != true }
    val windowOpen = running && nextPrompt != null &&
        positionMs in nextPrompt.windowStartMs..nextPrompt.windowEndMs
    val preCue = running && nextPrompt != null && !windowOpen &&
        positionMs < nextPrompt.windowStartMs &&
        nextPrompt.windowStartMs - positionMs <= PRE_CUE_MS
    val msUntilWindow = when {
        nextPrompt == null || windowOpen -> 0L
        positionMs < nextPrompt.windowStartMs -> nextPrompt.windowStartMs - positionMs
        else -> (spec.loopDurationMs - positionMs) + nextPrompt.windowStartMs
    }

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
            Text("Mash: $totalMash")
        }

        // ---- Rhythm strip: whole loop, windows, playhead ----
        RhythmStrip(spec = spec, positionMs = positionMs, consumed = consumed)

        // ---- Next-prompt countdown / "now" cue ----
        if (running && nextPrompt != null) {
            Text(
                if (windowOpen) {
                    if (showFa) "حالا بزن! ${nextPrompt.labelEn}" else "NOW — ${nextPrompt.labelEn}!"
                } else {
                    if (showFa) "بعدی: ${nextPrompt.labelEn} — ${msUntilWindow} ms دیگه"
                    else "Next: ${nextPrompt.labelEn} in ${msUntilWindow} ms"
                },
                color = if (windowOpen) {
                    RmhoSecondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold
            )
        }

        // ---- Feedback banner (always names the judged prompt) ----
        Text(
            feedback ?: if (showFa) {
                if (running) "منتظر پنجره‌ی طلایی باش…" else "برای شروع، روی دکمه‌ی بزرگ بزن"
            } else {
                if (running) "Wait for the gold window…" else "Tap the big button to start"
            },
            color = RmhoSecondary,
            fontWeight = FontWeight.Bold
        )

        // ---- Gesture pad: visible movement anchor + big action button ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .background(RmhoSurfaceVariant)
                .pointerInput(spec.drillId) {
                    val flickThreshold = 36.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!running && !finished) {
                            // The action button is the start affordance.
                            waitForUpOrCancellation()
                            startDrill()
                            return@awaitEachGesture
                        }
                        if (!running || finished) return@awaitEachGesture
                        pressed = true
                        val downPos = loopPos(elapsedMs())
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up != null) {
                            val duration = up.uptimeMillis - down.uptimeMillis
                            val dx = up.position.x - down.position.x
                            val dy = up.position.y - down.position.y
                            val dist = sqrt(dx * dx + dy * dy)
                            val kind = DrillMatcher.classify(duration, dist, flickThreshold)
                            val angle = if (kind == GestureKind.DIRECTIONAL_FLICK) {
                                DrillMatcher.flickAngleDeg(dx, dy)
                            } else {
                                null
                            }
                            evaluateGesture(kind, duration, angle, downPos)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MovementPadAnchor()
                ActionButton(
                    prompt = nextPrompt,
                    running = running,
                    finished = finished,
                    windowOpen = windowOpen,
                    preCue = preCue,
                    pressed = pressed,
                    showFa = showFa
                )
            }
        }

        if (!running && !finished) {
            Button(onClick = { startDrill() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showFa) "شروع تمرین" else "Start drill")
            }
        }
        if (finished) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val verdict = when {
                        finalAccuracy >= spec.goldAccuracyPercent ->
                            if (showFa) "در حد طلا!" else "GOLD pace"
                        finalAccuracy >= spec.passAccuracyPercent ->
                            if (showFa) "قبول — در حد نقره" else "Passed — Silver pace"
                        else ->
                            if (showFa) "هنوز نه — ادامه بده" else "Not yet — keep practicing"
                    }
                    Text(
                        "Accuracy: $finalAccuracy% — $verdict",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (showFa) "دورهای تمیز: $cleanReps/${spec.requiredReps}"
                        else "Clean reps: $cleanReps/${spec.requiredReps}"
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { startDrill() }) {
                        Text(if (showFa) "دوباره" else "Run again")
                    }
                }
            }
        }
    }
}

/** Smaller left circle: the movement-stick anchor (visual reference). */
@Composable
private fun MovementPadAnchor() {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(RmhoSurface)
            .border(2.dp, RmhoTextDim, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "MOVE",
                color = RmhoTextDim,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "pad",
                color = RmhoTextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * The big ACTION PAD button: shows the currently-required gesture, pulses
 * during the pre-cue, glows gold while its window is open, and visibly
 * depresses while a finger is on the pad. Before the drill starts it is
 * the start affordance.
 */
@Composable
private fun ActionButton(
    prompt: GesturePrompt?,
    running: Boolean,
    finished: Boolean,
    windowOpen: Boolean,
    preCue: Boolean,
    pressed: Boolean,
    showFa: Boolean
) {
    val transition = rememberInfiniteTransition(label = "drillPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "drillPulseScale"
    )
    val scale = when {
        pressed -> 0.92f
        preCue -> pulse
        else -> 1f
    }
    val ringColor = if (windowOpen || pressed) RmhoSecondary else RmhoPrimary
    val ringWidth = if (windowOpen) 6.dp else 3.dp
    Box(
        modifier = Modifier
            .size(148.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (pressed) RmhoSurfaceVariant else RmhoSurface)
            .border(ringWidth, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            when {
                !running && !finished -> {
                    Text(
                        "▶",
                        color = RmhoSecondary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (showFa) "شروع تمرین" else "Start Drill",
                        color = RmhoText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                prompt != null -> {
                    Text(
                        gestureGlyph(prompt.gestureType),
                        color = if (windowOpen) RmhoSecondary else RmhoPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        prompt.labelEn,
                        color = RmhoText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (showFa && prompt.hintFa.isNotEmpty()) {
                        FaLine(prompt.hintFa)
                    }
                }
                else -> Text(
                    "…",
                    color = RmhoTextDim,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

/**
 * Rhythm strip: the whole loop at a glance — each prompt window as a
 * colored segment (blue = upcoming, gold = open now, dim = done), a glyph
 * for its gesture, a tick at the ideal moment, and the moving playhead.
 */
@Composable
private fun RhythmStrip(
    spec: TypeAGestureRhythm,
    positionMs: Long,
    consumed: Map<String, Boolean>
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val loop = spec.loopDurationMs.coerceAtLeast(1).toFloat()
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = RmhoSurface,
            size = size,
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        spec.prompts.forEach { p ->
            val x0 = (p.windowStartMs / loop) * w
            val x1 = (p.windowEndMs / loop) * w
            val segW = (x1 - x0).coerceAtLeast(4.dp.toPx())
            val segTop = 5.dp.toPx()
            val segH = h - 10.dp.toPx()
            val midX = x0 + segW / 2f
            val midY = h / 2f
            val segmentColor = when {
                consumed[p.promptId] == true -> RmhoTextDim
                positionMs in p.windowStartMs..p.windowEndMs -> RmhoSecondary
                else -> RmhoPrimary
            }
            drawRoundRect(
                color = segmentColor,
                topLeft = Offset(x0, segTop),
                size = Size(segW, segH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            when (p.gestureType) {
                GestureKind.TAP -> drawCircle(
                    color = RmhoBg,
                    radius = 3.5.dp.toPx(),
                    center = Offset(midX, midY)
                )
                GestureKind.HOLD_RELEASE -> drawLine(
                    color = RmhoBg,
                    start = Offset(x0 + 4.dp.toPx(), midY),
                    end = Offset(x0 + segW - 4.dp.toPx(), midY),
                    strokeWidth = 3.5.dp.toPx()
                )
                GestureKind.DIRECTIONAL_FLICK -> {
                    drawLine(
                        color = RmhoBg,
                        start = Offset(midX, midY + 5.dp.toPx()),
                        end = Offset(midX, midY - 5.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = RmhoBg,
                        start = Offset(midX - 3.5.dp.toPx(), midY - 1.5.dp.toPx()),
                        end = Offset(midX, midY - 5.5.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = RmhoBg,
                        start = Offset(midX + 3.5.dp.toPx(), midY - 1.5.dp.toPx()),
                        end = Offset(midX, midY - 5.5.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
            // Ideal-moment tick at the top edge of the segment.
            val xi = (p.idealTimestampMs / loop) * w
            drawLine(
                color = Color.White,
                start = Offset(xi, 1.dp.toPx()),
                end = Offset(xi, 5.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
        // Playhead.
        val xp = (positionMs / loop) * w
        drawLine(
            color = Color.White,
            start = Offset(xp, 0f),
            end = Offset(xp, h),
            strokeWidth = 3.dp.toPx()
        )
    }
}
