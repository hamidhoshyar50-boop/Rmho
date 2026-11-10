package ir.rokoi.rmho.coach

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun frameStateColor(state: FrameStateType): Color = when (state) {
    FrameStateType.STARTUP -> Color(0xFFFFB74D)
    FrameStateType.ACTIVE_HITBOX -> Color(0xFFFF5252)
    FrameStateType.RECOVERY -> Color(0xFF78909C)
    FrameStateType.BUFFER_WINDOW -> Color(0xFF4FC3F7)
    FrameStateType.CANCEL_WINDOW -> Color(0xFF69F0AE)
    FrameStateType.IFRAME -> Color(0xFFE040FB)
    FrameStateType.HYPER_ARMOR -> Color(0xFFFFD740)
    FrameStateType.MASH_PENALTY_LOCK -> Color(0xFFD50000)
}

/**
 * Parametric Vector Frame Lab: a top-down arena Canvas plus a 3-track
 * timeline, driven purely by JSON segments. Wall-clock (monotonic)
 * deltas only — no fixed frame steps — and no per-frame allocations
 * in the draw path.
 */
@Composable
fun FrameLabTab(
    spec: FrameLabSpec,
    seekMs: Long?,
    onSeekConsumed: () -> Unit,
    onInspected: () -> Unit
) {
    val view = LocalView.current
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(spec.defaultPlaybackSpeed) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var inspected by remember { mutableStateOf(false) }

    fun markInspected() {
        if (!inspected) {
            inspected = true
            onInspected()
        }
    }

    fun fireHaptic(type: HapticTickType) {
        val constant = when (type) {
            HapticTickType.NONE -> return
            HapticTickType.LIGHT_TICK -> HapticFeedbackConstants.CLOCK_TICK
            HapticTickType.HEAVY_IMPACT -> HapticFeedbackConstants.LONG_PRESS
            HapticTickType.BUFFER_OPEN -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticTickType.ERROR_BUZZ ->
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    LaunchedEffect(seekMs) {
        val s = seekMs
        if (s != null) {
            positionMs = s.coerceIn(0L, spec.totalDurationMs)
            playing = false
            markInspected()
            onSeekConsumed()
        }
    }

    LaunchedEffect(playing, speed) {
        if (!playing) return@LaunchedEffect
        var lastFrameMs = -1L
        var lastPos = positionMs
        withInfiniteAnimationFrameMillis { frameMs ->
            if (lastFrameMs < 0L) {
                lastFrameMs = frameMs
                return@withInfiniteAnimationFrameMillis
            }
            val delta = frameMs - lastFrameMs
            lastFrameMs = frameMs
            if (delta <= 0L) return@withInfiniteAnimationFrameMillis
            val advanced = lastPos + (delta * speed).toLong()
            val wrapped = advanced >= spec.totalDurationMs
            val newPos = if (spec.totalDurationMs > 0) {
                advanced % spec.totalDurationMs
            } else {
                0L
            }
            for (kf in spec.keyframes) {
                val crossed = if (wrapped) {
                    kf.timestampMs > lastPos || kf.timestampMs <= newPos
                } else {
                    kf.timestampMs > lastPos && kf.timestampMs <= newPos
                }
                if (crossed) fireHaptic(kf.hapticTickType)
            }
            lastPos = newPos
            positionMs = newPos
        }
    }

    val total = spec.totalDurationMs.coerceAtLeast(1L)
    val playerSeg = spec.playerTrack.firstOrNull {
        positionMs >= it.startMs && positionMs < it.endMs
    }
    val hitboxSeg = spec.hitboxTrack.firstOrNull {
        positionMs >= it.startMs && positionMs < it.endMs
    }
    val windowSeg = spec.inputWindowTrack.firstOrNull {
        positionMs >= it.startMs && positionMs < it.endMs
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Frame Lab — vector simulation (placeholder timings)",
            style = MaterialTheme.typography.titleMedium
        )

        // ---- Arena ----
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFF12161F))
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(size.width, size.height) / 2f * 0.92f
            for (i in 1..3) {
                drawCircle(
                    color = Color(0xFF26324A),
                    radius = radius * i / 3f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            // Monster (from the active hitbox segment, else default spot)
            val mGeom = hitboxSeg?.vectorGeometry
            val mx = cx + (mGeom?.originX ?: 0f) * radius
            val my = cy + (mGeom?.originY ?: -0.45f) * radius
            drawCircle(color = Color(0xFF8E24AA), radius = 16.dp.toPx(), center = Offset(mx, my))
            if (mGeom != null && mGeom.arcRadius > 0f) {
                val arcR = mGeom.arcRadius * radius
                val start = mGeom.facingAngleDeg - mGeom.sweepAngleDeg / 2f
                drawArc(
                    color = Color(0xFFFF5252).copy(alpha = 0.45f),
                    startAngle = start,
                    sweepAngle = mGeom.sweepAngleDeg,
                    useCenter = true,
                    topLeft = Offset(mx - arcR, my - arcR),
                    size = Size(arcR * 2, arcR * 2)
                )
            }
            // Hunter (interpolated along the active player segment)
            val pGeom = playerSeg?.vectorGeometry
            val hx: Float
            val hy: Float
            if (playerSeg != null && pGeom != null) {
                val span = (playerSeg.endMs - playerSeg.startMs).coerceAtLeast(1L)
                val t = ((positionMs - playerSeg.startMs).toFloat() / span).coerceIn(0f, 1f)
                hx = cx + (pGeom.originX + (pGeom.targetX - pGeom.originX) * t) * radius
                hy = cy + (pGeom.originY + (pGeom.targetY - pGeom.originY) * t) * radius
            } else {
                hx = cx
                hy = cy + 0.35f * radius
            }
            if (pGeom != null && pGeom.arcRadius > 0f &&
                playerSeg?.stateType == FrameStateType.ACTIVE_HITBOX
            ) {
                val arcR = pGeom.arcRadius * radius
                drawArc(
                    color = Color(0xFF4DA3FF).copy(alpha = 0.5f),
                    startAngle = pGeom.facingAngleDeg - pGeom.sweepAngleDeg / 2f,
                    sweepAngle = pGeom.sweepAngleDeg,
                    useCenter = true,
                    topLeft = Offset(hx - arcR, hy - arcR),
                    size = Size(arcR * 2, arcR * 2)
                )
            }
            drawCircle(color = Color(0xFF4DA3FF), radius = 10.dp.toPx(), center = Offset(hx, hy))
            if (pGeom != null) {
                val rad = Math.toRadians(pGeom.facingAngleDeg.toDouble())
                drawLine(
                    color = Color(0xFFEBF3FF),
                    start = Offset(hx, hy),
                    end = Offset(
                        hx + (cos(rad) * 22.dp.toPx()).toFloat(),
                        hy + (sin(rad) * 22.dp.toPx()).toFloat()
                    ),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // ---- Timeline (forced LTR) ----
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Player", style = MaterialTheme.typography.labelSmall)
                Text("Hitbox", style = MaterialTheme.typography.labelSmall)
                Text("Input window", style = MaterialTheme.typography.labelSmall)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color(0xFF12161F))
            ) {
                val tracks = listOf(spec.playerTrack, spec.hitboxTrack, spec.inputWindowTrack)
                val trackH = size.height / 3f - 6.dp.toPx()
                val gap = 9.dp.toPx()
                tracks.forEachIndexed { ti, segments ->
                    val y = ti * (trackH + gap)
                    for (segment in segments) {
                        val x0 = (segment.startMs.toFloat() / total) * size.width
                        val x1 = (segment.endMs.toFloat() / total) * size.width
                        drawRect(
                            color = frameStateColor(segment.stateType),
                            topLeft = Offset(x0, y),
                            size = Size((x1 - x0).coerceAtLeast(2f), trackH)
                        )
                    }
                }
                val px = (positionMs.toFloat() / total) * size.width
                drawLine(
                    color = Color.White,
                    start = Offset(px, 0f),
                    end = Offset(px, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
            Slider(
                value = positionMs.toFloat(),
                onValueChange = {
                    positionMs = it.toLong()
                    markInspected()
                },
                valueRange = 0f..total.toFloat()
            )
        }

        // ---- Controls ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = {
                playing = !playing
                if (playing) markInspected()
            }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play"
                )
            }
            listOf(0.25f, 0.5f, 1.0f).forEach { s ->
                FilterChip(
                    selected = speed == s,
                    onClick = { speed = s },
                    label = { Text("${s}×") }
                )
            }
            Text(
                "$positionMs / ${spec.totalDurationMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Inspector ----
        playerSeg?.let {
            Text("Player: ${it.labelEn}", color = frameStateColor(it.stateType))
            FaLine(it.tooltipFa)
        }
        hitboxSeg?.let {
            Text("Hitbox: ${it.labelEn}", color = frameStateColor(it.stateType))
            FaLine(it.tooltipFa)
        }
        windowSeg?.let {
            Text("Window: ${it.labelEn}", color = frameStateColor(it.stateType))
            FaLine(it.tooltipFa)
        }
        spec.keyframes.firstOrNull { it.timestampMs >= positionMs }?.let { kf ->
            Spacer(Modifier.height(2.dp))
            Text(
                "Next keyframe @ ${kf.timestampMs} ms: ${kf.badgeEn}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            FaLine(kf.noteFa)
        }
    }
}
