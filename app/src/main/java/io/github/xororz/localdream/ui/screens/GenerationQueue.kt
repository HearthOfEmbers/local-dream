package io.github.xororz.localdream.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import io.github.xororz.localdream.data.GenerationDefaults
import io.github.xororz.localdream.utils.schedulerDisplayName
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A generation request frozen when it was queued, so later edits on the prompt
 * page don't change it. [count] images run back to back with [seed] (or a random
 * seed per image when null). [xyCell] marks a cell of an X/Y plot.
 */
@Immutable
data class GenerationJob(
    val id: Long,
    val prompt: String,
    val negativePrompt: String,
    val steps: Int,
    val cfg: Float,
    val seed: Long?,
    val scheduler: String,
    val denoiseStrength: Float,
    val cfgRescale: Float,
    val cfgCutoff: Float,
    val count: Int,
    val xyCell: XyCell? = null,
) {
    /** One-line summary for the queue list. */
    fun summary(): String {
        val head = if (xyCell != null) "X/Y ${xyCell.label} · " else ""
        val seedText = seed?.let { " · seed $it" } ?: ""
        val countText = if (count > 1) " ×$count" else ""
        return head + "$steps steps · CFG ${formatAxisNumber(cfg)} · ${schedulerDisplayName(scheduler)}" +
            seedText + countText
    }
}

@Immutable
data class XyCell(val plotId: Long, val x: Int, val y: Int, val label: String)

/** The parameters an X/Y plot axis can sweep. */
enum class XyAxisType(val title: String) {
    NONE("None"),
    SEED("Seed"),
    STEPS("Steps"),
    CFG("CFG Scale"),
    SCHEDULER("Sampler"),
    DENOISE("Denoise"),
    CFG_RESCALE("CFG Rescale"),
    CFG_CUTOFF("Skip negative (last part)"),
    PROMPT_SR("Prompt S/R"),
    ;

    /** Example input shown under the values field. */
    val hint: String
        get() = when (this) {
            NONE -> ""
            SEED -> "e.g. 1, 2, 3 or 100-104"
            STEPS -> "e.g. 4, 6, 8 or 10-30 (+5)"
            CFG -> "e.g. 3, 5, 7.5"
            SCHEDULER -> "dpm, dpm_karras, dpm_sde, euler, euler_a, lcm"
            DENOISE -> "e.g. 0.3, 0.5, 0.7"
            CFG_RESCALE -> "e.g. 0, 0.5, 0.7"
            CFG_CUTOFF -> "share of final steps, e.g. 0, 0.2, 0.5 (0 = off)"
            PROMPT_SR -> "first value is searched in the prompt, e.g. red, blue, green"
        }
}

/**
 * Slider and fill-range settings for a numeric axis: the value range, the
 * slider's step, and the default step of the fill-range helper. [integer]
 * axes are written without decimals. Null for axes picked another way.
 */
@Immutable
data class XyAxisSlider(
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val fillStep: Float,
    val integer: Boolean = false,
)

val XyAxisType.slider: XyAxisSlider?
    get() = when (this) {
        XyAxisType.STEPS -> XyAxisSlider(
            GenerationDefaults.STEPS_MIN..GenerationDefaults.STEPS_MAX,
            step = 1f,
            fillStep = 2f,
            integer = true,
        )

        XyAxisType.CFG -> XyAxisSlider(GenerationDefaults.CFG_MIN..GenerationDefaults.CFG_MAX, 0.1f, 0.2f)

        XyAxisType.DENOISE -> XyAxisSlider(0f..1f, 0.05f, 0.1f)

        XyAxisType.CFG_RESCALE -> XyAxisSlider(0f..1f, 0.05f, 0.1f)

        XyAxisType.CFG_CUTOFF -> XyAxisSlider(0f..0.9f, 0.05f, 0.1f)

        else -> null
    }

/** The values typed into an axis box, split on commas, blanks dropped. */
fun xyValueList(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Appends [value] to the comma-separated [raw] list. */
fun appendXyValue(raw: String, value: String): String {
    val values = xyValueList(raw)
    return (values + value).joinToString(", ")
}

/** Adds [value] to [raw], or removes it when it is already listed. */
fun toggleXyValue(raw: String, value: String): String {
    val values = xyValueList(raw)
    return if (value in values) (values - value).joinToString(", ") else (values + value).joinToString(", ")
}

/**
 * Every value from [from] to [to] (either direction) in steps of [step],
 * snapped to the step's precision so 0.2 steps don't drift into 1.4000001.
 * Null when the inputs can't make a list or it would exceed [XY_MAX_CELLS].
 */
fun fillXyRange(from: Float, to: Float, step: Float, integer: Boolean): List<String>? {
    if (step <= 0f || from.isNaN() || to.isNaN()) return null
    val count = (kotlin.math.abs(to - from) / step + 1e-4f).toInt() + 1
    if (count > XY_MAX_CELLS) return null
    val direction = if (to >= from) 1f else -1f
    return (0 until count).map { i ->
        val v = from + direction * step * i
        if (integer) v.roundToInt().toString() else formatAxisNumber(v)
    }.distinct()
}

/** A parsed axis: its type plus the user-visible label and raw value per entry. */
@Immutable
data class XyAxis(val type: XyAxisType, val values: List<String>)

/** An X/Y plot being collected: results land in [cells] as their jobs finish. */
class XyPlotRun(
    val id: Long,
    val x: XyAxis,
    val y: XyAxis,
    val baseParams: GenerationParameters,
) {
    private val cols = x.values.size
    private val rows = max(1, y.values.size)
    val cells: Array<Bitmap?> = arrayOfNulls(cols * rows)
    var failed = 0

    fun put(cell: XyCell, bitmap: Bitmap) {
        cells[cell.y * cols + cell.x] = bitmap
    }

    val isDone: Boolean get() = cells.count { it != null } + failed >= cells.size

    fun buildGrid(): Bitmap? = buildXyGrid(
        cells.toList(),
        cols,
        rows,
        x.values.map { "${x.type.title}: $it" },
        if (y.type == XyAxisType.NONE) emptyList() else y.values.map { "${y.type.title}: $it" },
    )
}

const val XY_MAX_CELLS = 100

private val RANGE_REGEX = Regex("""^\s*(-?[\d.]+)\s*-\s*(-?[\d.]+)\s*(?:\(\s*\+?\s*([\d.]+)\s*\))?\s*$""")

/**
 * Parses an axis value list: comma separated, and for integer axes a range
 * "a-b" or "a-b (+step)". Returns null with no usable value or an invalid one.
 */
fun parseXyAxis(type: XyAxisType, raw: String): XyAxis? {
    if (type == XyAxisType.NONE) return XyAxis(type, emptyList())
    val parts = xyValueList(raw)
    val values = mutableListOf<String>()
    for (part in parts) {
        val range = if (type == XyAxisType.SEED || type == XyAxisType.STEPS) RANGE_REGEX.find(part) else null
        if (range != null) {
            val a = range.groupValues[1].toLongOrNull() ?: return null
            val b = range.groupValues[2].toLongOrNull() ?: return null
            val step = range.groupValues[3].toLongOrNull()?.coerceAtLeast(1) ?: 1L
            val progression = if (a <= b) (a..b step step) else (a downTo b step step)
            for (v in progression) {
                if (!isValidAxisValue(type, v.toString()) || values.size >= XY_MAX_CELLS) return null
                values += v.toString()
            }
        } else {
            if (!isValidAxisValue(type, part)) return null
            values += part
        }
        if (values.size > XY_MAX_CELLS) return null
    }
    // Prompt S/R needs the search term plus at least one replacement.
    val minCount = if (type == XyAxisType.PROMPT_SR) 2 else 1
    return if (values.size >= minCount) XyAxis(type, values) else null
}

/** Sampler ids an axis accepts, in the order the picker shows them. */
val XY_SCHEDULER_IDS = listOf(
    "lcm", "euler_a", "euler", "dpm", "dpm_sde", "euler_a_karras", "euler_karras", "dpm_karras", "dpm_sde_karras",
)

private fun isValidAxisValue(type: XyAxisType, value: String): Boolean = when (type) {
    XyAxisType.NONE -> false

    XyAxisType.SEED -> value.toLongOrNull()?.let { it >= 0 } == true

    XyAxisType.STEPS -> value.toIntOrNull()?.let {
        it.toFloat() in GenerationDefaults.STEPS_MIN..GenerationDefaults.STEPS_MAX
    } == true

    XyAxisType.CFG -> value.toFloatOrNull()?.let {
        it in GenerationDefaults.CFG_MIN..GenerationDefaults.CFG_MAX
    } == true

    XyAxisType.SCHEDULER -> value in XY_SCHEDULER_IDS

    XyAxisType.DENOISE, XyAxisType.CFG_RESCALE -> value.toFloatOrNull()?.let { it in 0f..1f } == true

    XyAxisType.CFG_CUTOFF -> value.toFloatOrNull()?.let { it in 0f..0.9f } == true

    XyAxisType.PROMPT_SR -> true
}

/** Applies one axis value to [job]. [axis] values index [index]. */
fun applyXyValue(job: GenerationJob, axis: XyAxis, index: Int): GenerationJob {
    val value = axis.values[index]
    return when (axis.type) {
        XyAxisType.NONE -> job

        XyAxisType.SEED -> job.copy(seed = value.toLong())

        XyAxisType.STEPS -> job.copy(steps = value.toInt())

        XyAxisType.CFG -> job.copy(cfg = value.toFloat())

        XyAxisType.SCHEDULER -> job.copy(scheduler = value)

        XyAxisType.DENOISE -> job.copy(denoiseStrength = value.toFloat())

        XyAxisType.CFG_RESCALE -> job.copy(cfgRescale = value.toFloat())

        // Same "skip for the last N" reading as the Advanced settings slider.
        XyAxisType.CFG_CUTOFF -> job.copy(cfgCutoff = 1f - value.toFloat())

        // A1111 semantics: the first value is the search term and stays as-is.
        XyAxisType.PROMPT_SR -> if (index == 0) {
            job
        } else {
            val search = axis.values[0]
            job.copy(
                prompt = job.prompt.replace(search, value),
                negativePrompt = job.negativePrompt.replace(search, value),
            )
        }
    }
}

internal fun formatAxisNumber(value: Float): String = if (value == value.roundToInt().toFloat()) {
    value.roundToInt().toString()
} else {
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private const val XY_MAX_GRID_EDGE = 4096

/**
 * Tiles [cells] (row-major, [cols] x [rows]) into one image with the X labels
 * as a header row and the Y labels down the left. Missing cells stay grey.
 * Returns null when no cell has an image.
 */
fun buildXyGrid(cells: List<Bitmap?>, cols: Int, rows: Int, xLabels: List<String>, yLabels: List<String>): Bitmap? {
    val sample = cells.firstOrNull { it != null } ?: return null
    // Downscale so the whole sheet stays a reasonable size for large grids.
    val scale = min(1f, XY_MAX_GRID_EDGE.toFloat() / (max(cols, rows) * max(sample.width, sample.height)))
    val cellW = (sample.width * scale).roundToInt()
    val cellH = (sample.height * scale).roundToInt()
    val textSize = max(18f, cellW / 18f)
    val gap = max(2, (textSize / 6).roundToInt())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        this.textSize = textSize
        typeface = Typeface.DEFAULT_BOLD
    }
    val headerH = (textSize * 2.2f).roundToInt()
    val labelW = if (yLabels.isEmpty()) {
        0
    } else {
        (yLabels.maxOf { paint.measureText(it) } + textSize).roundToInt()
            .coerceAtMost(cellW * 2)
    }

    val width = labelW + cols * cellW + (cols + 1) * gap
    val height = headerH + rows * cellH + (rows + 1) * gap
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(Color.WHITE)
    val placeholder = Paint().apply { color = Color.LTGRAY }
    val filter = Paint(Paint.FILTER_BITMAP_FLAG)

    for (c in 0 until cols) {
        val left = labelW + gap + c * (cellW + gap)
        drawCentered(canvas, paint, xLabels.getOrElse(c) { "" }, left, 0, cellW, headerH)
    }
    for (r in 0 until rows) {
        val top = headerH + gap + r * (cellH + gap)
        if (yLabels.isNotEmpty()) {
            drawCentered(canvas, paint, yLabels.getOrElse(r) { "" }, 0, top, labelW, cellH)
        }
        for (c in 0 until cols) {
            val left = labelW + gap + c * (cellW + gap)
            val dst = Rect(left, top, left + cellW, top + cellH)
            val bmp = cells.getOrNull(r * cols + c)
            if (bmp != null) canvas.drawBitmap(bmp, null, dst, filter) else canvas.drawRect(dst, placeholder)
        }
    }
    return out
}

private fun drawCentered(canvas: Canvas, paint: Paint, text: String, left: Int, top: Int, w: Int, h: Int) {
    // Ellipsize labels wider than their slot.
    var label = text
    while (label.length > 1 && paint.measureText(label) > w - paint.textSize / 2) {
        label = label.dropLast(2) + "…"
    }
    val x = left + (w - paint.measureText(label)) / 2f
    val y = top + h / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, x, y, paint)
}
