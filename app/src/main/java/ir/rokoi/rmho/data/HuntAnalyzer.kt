package ir.rokoi.rmho.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Coach Phase 2 — on-device hunt-video detector.
 *
 * Algorithm (identical to the server prototype that was calibrated on
 * the user's real 1280x576 hunt recording, /root/rmho-rec/rec.mp4):
 *  - decode sequentially with MediaCodec (never a getFrameAtTime loop)
 *  - work on small sampled signatures of the Y (luma) plane on a
 *    relative-coordinate grid — no full bitmaps are kept
 *  - a press = a frame-differencing spike inside a button ROI,
 *    compensated by the global frame difference (camera shake /
 *    flashes move the whole frame; a press moves one disc)
 *  - adaptive per-ROI threshold: max(5, median + 5 * 1.4826 * MAD)
 *  - a second pass over every decoded frame tracks the red-channel
 *    excess in a central ROI: spikes are part-break *candidates*
 *    (it timestamps that something flashed — never which part)
 *  - falls back to MediaMetadataRetriever sampling if codec setup
 *    fails on an exotic file.
 *
 * ROIs are relative to the frame and were calibrated on the verified
 * reference layout (Starter control mode): Basic Attack disc
 * (0.838, 0.722), Burst (0.828, 0.539), Dash discs (0.757, 0.631) and
 * (0.757, 0.826), skill discs (0.906, 0.847) and (0.694, 0.826).
 */

data class DetectionResult(
    val events: List<HuntEvent>,
    val flashesSec: List<Double>,
    val durationSec: Double,
    val videoWidth: Int,
    val videoHeight: Int,
    val decoderUsed: String,
    val qualityLabel: String,
    val sampledFrames: Int
)

class HuntAnalyzer(private val context: Context) {

    companion object {
        private val ROI_BOXES = linkedMapOf(
            "BASIC" to doubleArrayOf(0.800, 0.640, 0.876, 0.804),
            "BURST" to doubleArrayOf(0.790, 0.445, 0.866, 0.633),
            "DASH" to doubleArrayOf(0.715, 0.530, 0.800, 0.730),
            "DASH2" to doubleArrayOf(0.720, 0.750, 0.795, 0.902),
            "SKILL" to doubleArrayOf(0.865, 0.760, 0.947, 0.935),
            "SKILL2" to doubleArrayOf(0.655, 0.740, 0.733, 0.912)
        )
        private val CLASS_OF = mapOf(
            "BASIC" to HuntEventClass.BASIC,
            "BURST" to HuntEventClass.BURST,
            "DASH" to HuntEventClass.DASH,
            "DASH2" to HuntEventClass.DASH,
            "SKILL" to HuntEventClass.SKILL,
            "SKILL2" to HuntEventClass.SKILL
        )
        private val CENTRAL = doubleArrayOf(0.25, 0.20, 0.75, 0.80)
        private const val SAMPLE_STEP_SEC = 0.1
    }

    /** Collected per-sample signatures + the red-flash series. */
    private class Samples {
        val times = mutableListOf<Double>()
        val roiSigs = HashMap<String, MutableList<FloatArray>>()
        val globalSigs = mutableListOf<FloatArray>()
        val red = mutableListOf<Pair<Double, Double>>()
    }

    suspend fun analyse(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): DetectionResult {
        return try {
            analyseWithCodec(uri, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analyseWithRetriever(uri, onProgress)
        }
    }

    // ---------------- MediaCodec path (primary) ----------------

    private suspend fun analyseWithCodec(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): DetectionResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                track = i
                format = f
                break
            }
        }
        require(track >= 0 && format != null) { "No video track in the picked file." }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val srcW = format.getInteger(MediaFormat.KEY_WIDTH)
        val srcH = format.getInteger(MediaFormat.KEY_HEIGHT)
        val rotation = if (format.containsKey("rotation-degrees")) {
            format.getInteger("rotation-degrees")
        } else 0
        val durationSec = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0
        } else 0.0

        val codec = MediaCodec.createDecoderByType(mime)
        val samples = Samples()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var nextSampleT = 0.0
            var lastProgress = -1f
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val pts = info.presentationTimeUs / 1_000_000.0
                    if (info.size > 0) {
                        val image = codec.getOutputImage(outIdx)
                        if (image != null) {
                            // red-flash pass: every decoded frame
                            samples.red.add(pts to redExcess(image, rotation))
                            if (pts + 1e-6 >= nextSampleT) {
                                collectSignatures(image, rotation, samples)
                                samples.times.add(pts)
                                while (nextSampleT <= pts) nextSampleT += SAMPLE_STEP_SEC
                            }
                            image.close()
                        }
                        if (durationSec > 0) {
                            val p = (pts / durationSec).toFloat().coerceIn(0f, 1f)
                            if (p - lastProgress >= 0.01f) {
                                lastProgress = p
                                onProgress(p)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // fine — output format settled; nothing to do
                }
                if (inputDone && outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // brief spin is acceptable; loop continues until EOS
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }
        require(samples.times.size >= 10) { "Too few frames could be decoded from this video." }
        return buildResult(samples, durationSec, srcW, srcH, "MediaCodec (sequential decode)")
    }

    /** Sample the Y plane of a decoded YUV_420_888 image on grids. */
    private fun collectSignatures(image: android.media.Image, rotation: Int, out: Samples) {
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        fun luma(fx: Double, fy: Double): Float {
            val (sx, sy) = mapPoint(fx, fy, w, h, rotation)
            val v = yBuf.get(sy * yRow + sx * yPix).toInt() and 0xFF
            return v.toFloat()
        }
        for ((name, boxArr) in ROI_BOXES) {
            val sig = FloatArray(36)
            var k = 0
            for (gy in 0 until 6) {
                for (gx in 0 until 6) {
                    val fx = boxArr[0] + (boxArr[2] - boxArr[0]) * (gx + 0.5) / 6.0
                    val fy = boxArr[1] + (boxArr[3] - boxArr[1]) * (gy + 0.5) / 6.0
                    sig[k++] = luma(fx, fy)
                }
            }
            out.roiSigs.getOrPut(name) { mutableListOf() }.add(sig)
        }
        val g = FloatArray(64)
        var k = 0
        for (gy in 0 until 8) {
            for (gx in 0 until 8) {
                g[k++] = luma((gx + 0.5) / 8.0, (gy + 0.5) / 8.0)
            }
        }
        out.globalSigs.add(g)
    }

    /** Mean red excess R - (G+B)/2 over the central ROI, approximated
     *  from the Y/U/V planes (BT.601). Positive spikes = red flashes. */
    private fun redExcess(image: android.media.Image, rotation: Int): Double {
        val w = image.width
        val h = image.height
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        fun planeVal(p: android.media.Image.Plane, x: Int, y: Int): Int {
            return p.buffer.get(y * p.rowStride + x * p.pixelStride).toInt() and 0xFF
        }
        var sum = 0.0
        var n = 0
        for (gy in 0 until 6) {
            for (gx in 0 until 8) {
                val fx = CENTRAL[0] + (CENTRAL[2] - CENTRAL[0]) * (gx + 0.5) / 8.0
                val fy = CENTRAL[1] + (CENTRAL[3] - CENTRAL[1]) * (gy + 0.5) / 6.0
                val (sx, sy) = mapPoint(fx, fy, w, h, rotation)
                val yy = planeVal(yP, sx, sy).toDouble()
                val uu = planeVal(uP, sx / 2, sy / 2).toDouble() - 128.0
                val vv = planeVal(vP, sx / 2, sy / 2).toDouble() - 128.0
                val r = yy + 1.402 * vv
                val g = yy - 0.344136 * uu - 0.714136 * vv
                val b = yy + 1.772 * uu
                sum += r - (g + b) / 2.0
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun mapPoint(fx: Double, fy: Double, w: Int, h: Int, rotation: Int): Pair<Int, Int> {
        val (rx, ry) = when (rotation) {
            90 -> fy to (1.0 - fx)
            180 -> (1.0 - fx) to (1.0 - fy)
            270 -> (1.0 - fy) to fx
            else -> fx to fy
        }
        val x = (rx * (w - 1)).toInt().coerceIn(0, w - 1)
        val y = (ry * (h - 1)).toInt().coerceIn(0, h - 1)
        return x to y
    }

    // ---------------- Retriever fallback ----------------

    private suspend fun analyseWithRetriever(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): DetectionResult {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val durationSec = (
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toDoubleOrNull() ?: 0.0
            ) / 1000.0
        val srcW = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val srcH = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        require(durationSec > 1.0 && srcW > 0 && srcH > 0) {
            "This file could not be read as a video."
        }
        val samples = Samples()
        val step = 0.2 // 5 fps on the fallback path
        var t = 0.0
        try {
            while (t < durationSec) {
                coroutineContext.ensureActive()
                val frame = mmr.getFrameAtTime(
                    (t * 1_000_000).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (frame != null) {
                    val scaled = if (frame.width > 640) {
                        Bitmap.createScaledBitmap(
                            frame, 640, (frame.height * 640.0 / frame.width).toInt(), true
                        )
                    } else frame
                    collectSignaturesFromBitmap(scaled, rotation, samples)
                    samples.times.add(t)
                    samples.red.add(t to redExcessFromBitmap(scaled, rotation))
                    if (scaled !== frame) scaled.recycle()
                    frame.recycle()
                }
                onProgress((t / durationSec).toFloat().coerceIn(0f, 1f))
                t += step
            }
        } finally {
            runCatching { mmr.release() }
        }
        require(samples.times.size >= 10) { "Too few frames could be read from this video." }
        return buildResult(samples, durationSec, srcW, srcH, "MediaMetadataRetriever (fallback)")
    }

    private fun collectSignaturesFromBitmap(bmp: Bitmap, rotation: Int, out: Samples) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        fun luma(fx: Double, fy: Double): Float {
            val (sx, sy) = mapPoint(fx, fy, w, h, rotation)
            val c = px[sy * w + sx]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            return (0.299 * r + 0.587 * g + 0.114 * b).toFloat()
        }
        for ((name, boxArr) in ROI_BOXES) {
            val sig = FloatArray(36)
            var k = 0
            for (gy in 0 until 6) {
                for (gx in 0 until 6) {
                    val fx = boxArr[0] + (boxArr[2] - boxArr[0]) * (gx + 0.5) / 6.0
                    val fy = boxArr[1] + (boxArr[3] - boxArr[1]) * (gy + 0.5) / 6.0
                    sig[k++] = luma(fx, fy)
                }
            }
            out.roiSigs.getOrPut(name) { mutableListOf() }.add(sig)
        }
        val g = FloatArray(64)
        var k = 0
        for (gy in 0 until 8) {
            for (gx in 0 until 8) {
                g[k++] = luma((gx + 0.5) / 8.0, (gy + 0.5) / 8.0)
            }
        }
        out.globalSigs.add(g)
    }

    private fun redExcessFromBitmap(bmp: Bitmap, rotation: Int): Double {
        val w = bmp.width
        val h = bmp.height
        var sum = 0.0
        var n = 0
        for (gy in 0 until 6) {
            for (gx in 0 until 8) {
                val fx = CENTRAL[0] + (CENTRAL[2] - CENTRAL[0]) * (gx + 0.5) / 8.0
                val fy = CENTRAL[1] + (CENTRAL[3] - CENTRAL[1]) * (gy + 0.5) / 6.0
                val (sx, sy) = mapPoint(fx, fy, w, h, rotation)
                val c = bmp.getPixel(sx, sy)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += r - (g + b) / 2.0
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    // ---------------- shared detection (mirrors the prototype) ----------------

    private fun buildResult(
        samples: Samples,
        durationSec: Double,
        w: Int,
        h: Int,
        decoderUsed: String
    ): DetectionResult {
        val times = samples.times
        val n = times.size
        // global diffs per sample step
        val globalDiff = DoubleArray(n)
        for (i in 1 until n) {
            globalDiff[i] = meanAbsDiff(samples.globalSigs[i], samples.globalSigs[i - 1])
        }
        val unclear = (1 until n).count { globalDiff[it] > 25.0 }
        val unclearShare = if (n > 1) unclear.toDouble() / (n - 1) else 1.0

        val rawEvents = mutableListOf<HuntEvent>()
        for ((name, sigs) in samples.roiSigs) {
            val cls = CLASS_OF[name] ?: continue
            val scores = DoubleArray(n)
            for (i in 1 until n) {
                scores[i] = meanAbsDiff(sigs[i], sigs[i - 1]) - 0.7 * globalDiff[i]
            }
            val thr = adaptiveThreshold(scores)
            var lastT = -10.0
            for (i in 1 until n) {
                val t = times[i]
                if (scores[i] > thr && t - lastT >= 0.40) {
                    rawEvents.add(HuntEvent(t, cls, scores[i]))
                    lastT = t
                }
            }
        }
        rawEvents.sortBy { it.timeSec }
        val events = mutableListOf<HuntEvent>()
        for (e in rawEvents) {
            val prev = events.lastOrNull()
            if (prev != null && prev.cls == e.cls && e.timeSec - prev.timeSec < 0.35) {
                if (e.peakScore > prev.peakScore) {
                    events[events.size - 1] = prev.copy(peakScore = e.peakScore)
                }
                continue
            }
            events.add(e)
        }

        // red-flash candidates
        val flashes = mutableListOf<Double>()
        if (samples.red.size > 10) {
            val vals = samples.red.map { it.second }.toDoubleArray()
            val thr = median(vals) + 8.0 * 1.4826 * mad(vals, median(vals))
            var lastT = -10.0
            for ((t, v) in samples.red) {
                if (v > thr && t - lastT >= 1.5) {
                    flashes.add(t)
                    lastT = t
                }
            }
        }

        val quality = when {
            n < 100 -> "Low"
            unclearShare < 0.05 && events.size >= 8 -> "High"
            unclearShare < 0.15 -> "Medium"
            else -> "Low"
        }
        return DetectionResult(
            events = events,
            flashesSec = flashes,
            durationSec = if (durationSec > 0) durationSec else (times.lastOrNull() ?: 0.0),
            videoWidth = w,
            videoHeight = h,
            decoderUsed = decoderUsed,
            qualityLabel = quality,
            sampledFrames = n
        )
    }

    private fun meanAbsDiff(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) s += abs(a[i] - b[i])
        return s / a.size
    }

    private fun median(v: DoubleArray): Double {
        val s = v.sortedArray()
        return if (s.isEmpty()) 0.0 else s[s.size / 2]
    }

    private fun mad(v: DoubleArray, med: Double): Double {
        if (v.isEmpty()) return 0.0
        val dev = DoubleArray(v.size) { abs(v[it] - med) }
        return median(dev)
    }

    private fun adaptiveThreshold(scores: DoubleArray): Double {
        val med = median(scores)
        return maxOf(5.0, med + 5.0 * 1.4826 * mad(scores, med))
    }
}
