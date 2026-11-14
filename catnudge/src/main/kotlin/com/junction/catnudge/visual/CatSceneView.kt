package com.junction.catnudge.visual

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Original, resolution-independent kitten illustration. No network or sprite assets are needed. */
class CatSceneView(context: Context) : View(context) {
    var walkAmount = 0f
    /** Horizontal travel in the illustration's 300 Ã— 160 coordinate space. */
    var walkDistance = 0f
    var dropProgress = 0f
    var pawProgress = 0f
    var lidProgress = 0f
    var restProgress = 0f
    var sadness = 0
    private var running = false
    private var born = SystemClock.uptimeMillis()
    private var parcelClick: (() -> Unit)? = null
    private val parcelBounds = RectF()
    private var parcelPressed = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val line = Color.parseColor("#795747")
    private val cream = Color.parseColor("#FFF0D4")
    private val ginger = Color.parseColor("#DDA67D")
    private val pink = Color.parseColor("#EDA6A0")

    internal data class FootPose(
        val leg: String,
        val x: Float,
        val y: Float,
        val groundY: Float,
        val planted: Boolean,
    )
    /** Actual positions consumed by the limb renderer, useful when inspecting the gait. */
    internal var renderedFootPoses: List<FootPose> = emptyList()
        private set

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = "A little cream kitten delivers a peach parcel and curls up beside it"
    }

    fun start() {
        born = SystemClock.uptimeMillis()
        running = true
        postInvalidateOnAnimation()
    }

    fun stop() { running = false }

    fun setOnParcelClickListener(listener: (() -> Unit)?) {
        parcelClick = listener
        isClickable = listener != null
        importantForAccessibility = if (listener == null) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
        if (listener != null) contentDescription = "Open the kitten's parcel and answer a question or two"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (parcelClick == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parcelPressed = parcelBounds.contains(event.x, event.y)
                return parcelPressed
            }
            MotionEvent.ACTION_UP -> {
                val click = parcelPressed && parcelBounds.contains(event.x, event.y)
                parcelPressed = false
                if (click) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { parcelPressed = false; return true }
        }
        return parcelPressed
    }

    override fun performClick(): Boolean {
        super.performClick()
        parcelClick?.invoke()
        return parcelClick != null
    }

    /** Static pose useful as the app's hero illustration. Call start() as well to make her breathe. */
    fun showResting() {
        walkAmount = 0f
        dropProgress = 1f
        pawProgress = 1f
        lidProgress = 1f
        restProgress = 1f
        invalidate()
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 300f, height / 160f)
        canvas.save()
        canvas.translate((width - 300 * scale) / 2f, height - 160 * scale)
        canvas.scale(scale, scale)
        val seconds = (SystemClock.uptimeMillis() - born) / 1000f
        val r = restProgress.coerceIn(0f, 1f)
        val walking = walkAmount.coerceIn(0f, 1f) * (1f - r)
        val gaitBob = sin(walkDistance / GAIT_CYCLE_DISTANCE * PI * 4f).toFloat() * .8f * walking
        val breath = sin(seconds * 2.1f) * .65f * (1f - walking)
        val bodyY = mix(99f, 120f, r) + gaitBob + breath
        val headY = mix(83f, 112f, r) + gaitBob * 1.1f + breath
        val headX = mix(142f, 150f, r)
        val farHind = gaitFoot("far hind", 81f, 135f, .5f, 68f, r, walking)
        val farFront = gaitFoot("far front", 132f, 135f, .75f, 135f, r, walking)
        val nearHind = gaitFoot("near hind", 76f, 140f, 0f, 70f, r, walking)
        val nearFront = gaitFoot("near front", 126f, 140f, .25f, 140f, r, walking)
        renderedFootPoses = listOf(farHind, farFront, nearHind, nearFront)

        // A small, soft ground shadow anchors the otherwise transparent character.
        oval(canvas, 33f, 137f, 232f, 148f, 0x11000000)
        oval(canvas, 51f, 139f, 196f, 145f, 0x0C000000)

        // Tail, rounded tip and the little tabby patch are behind the body.
        val tail = Path().apply {
            moveTo(72f, bodyY + 13)
            cubicTo(39f, mix(109f, 141f, r), mix(35f, 24f, r), mix(56f, 128f, r), mix(49f, 41f, r), mix(50f, 119f, r) + sin(seconds * 2.4f) * (3f - 2f * r))
        }
        stroke(canvas, tail, line, 16f)
        stroke(canvas, tail, ginger, 11f)
        val tailTip = Path().apply {
            moveTo(mix(42f, 30f, r), mix(67f, 129f, r))
            quadTo(mix(41f, 30f, r), mix(49f, 120f, r), mix(49f, 41f, r), mix(50f, 119f, r) + sin(seconds * 2.4f) * (3f - 2f * r))
        }
        stroke(canvas, tailTip, cream, 10f)

        // Far limbs sit behind the body; their four-beat phase differs from the nearer pair.
        walkingLeg(canvas, 81f, mix(bodyY + 9f, bodyY + 17f, r), farHind, true, true, r)
        walkingLeg(canvas, 129f, mix(bodyY + 8f, bodyY + 17f, r), farFront, false, true, r)
        outlinedOval(canvas, mix(64f, 47f, r), bodyY - mix(27f, 22f, r), mix(144f, 156f, r), bodyY + 19f, cream)
        oval(canvas, mix(72f, 58f, r), bodyY - 25f, mix(107f, 97f, r), bodyY + 7f, ginger)
        outlinedOval(canvas, mix(100f, 107f, r), bodyY - 15f, mix(144f, 160f, r), bodyY + 20f, cream, false)
        walkingLeg(canvas, mix(76f, 70f, r), mix(bodyY + 10f, bodyY + 18f, r), nearHind, true, false, r)
        walkingLeg(canvas, mix(126f, 140f, r), mix(bodyY + 10f, bodyY + 18f, r), nearFront, false, false, r)
        if (r > 0f) {
            // Only the resting pose has a fixed tucked paw. During travel every visible paw
            // belongs to the planted-stance/lifted-swing gait above.
            outlinedOval(canvas, 140f - 12f * r, 140f - 11f * r + breath, 140f + 13f * r, 141f + breath, cream)
            if (r > .65f) {
                stroke(canvas, path(137f, 136f + breath, 137f, 139f + breath), line, 1f)
                stroke(canvas, path(143f, 136f + breath, 143f, 139f + breath), line, 1f)
            }
        }

        // The ears have warm pink interiors; the large head is intentionally a little oversized.
        val earLift = if (sadness == 0) 0f else 3f
        val leftEar = Path().apply {
            moveTo(headX - 28, headY - 15)
            quadTo(headX - 32, headY - 49 + earLift, headX - 23, headY - 42 + earLift)
            lineTo(headX - 7, headY - 26)
            close()
        }
        val rightEar = Path().apply {
            moveTo(headX + 5, headY - 27)
            quadTo(headX + 24, headY - 51 + earLift, headX + 27, headY - 40 + earLift)
            lineTo(headX + 29, headY - 12)
            close()
        }
        shape(canvas, leftEar, cream)
        shape(canvas, rightEar, cream)
        shape(canvas, Path().apply {
            moveTo(headX - 25, headY - 22); lineTo(headX - 26, headY - 37 + earLift); lineTo(headX - 14, headY - 25); close()
        }, pink, false)
        shape(canvas, Path().apply {
            moveTo(headX + 14, headY - 25); lineTo(headX + 23, headY - 37 + earLift); lineTo(headX + 24, headY - 20); close()
        }, pink, false)
        outlinedOval(canvas, headX - 34, headY - 30, headX + 34, headY + 28, cream)
        val foreheadPatch = Path().apply {
            moveTo(headX - 15, headY - 28)
            quadTo(headX - 4, headY - 33, headX + 9, headY - 28)
            quadTo(headX + 11, headY - 10, headX + 2, headY - 11)
            quadTo(headX - 5, headY - 12, headX - 15, headY - 28)
        }
        shape(canvas, foreheadPatch, ginger, false)
        stroke(canvas, path(headX - 6, headY - 27, headX - 3, headY - 20), line, 1.1f)
        stroke(canvas, path(headX + 2, headY - 27, headX + 3, headY - 21), line, 1.1f)

        // Tiny cheek blush, bean eyes, eyelid blinks, nose and a tiny w-shaped mouth.
        oval(canvas, headX - 28, headY + 8, headX - 14, headY + 15, 0x77EDA6A0)
        oval(canvas, headX + 17, headY + 8, headX + 30, headY + 15, 0x77EDA6A0)
        val blink = seconds % 4.7f > 4.52f
        eye(canvas, headX - 14, headY + 1, blink, r, sadness)
        eye(canvas, headX + 16, headY + 1, blink, r, sadness)
        if (sadness > 1) {
            shape(canvas, Path().apply {
                moveTo(headX + 24, headY + 7)
                cubicTo(headX + 18, headY + 13, headX + 19, headY + 17, headX + 24, headY + 17)
                cubicTo(headX + 29, headY + 17, headX + 30, headY + 13, headX + 24, headY + 7)
            }, Color.parseColor("#AFD5DB"), false)
        }
        shape(canvas, Path().apply {
            moveTo(headX - 1, headY + 10); quadTo(headX + 4, headY + 7, headX + 7, headY + 10)
            lineTo(headX + 3, headY + 14); close()
        }, pink, false)
        stroke(canvas, Path().apply {
            moveTo(headX + 3, headY + 14)
            quadTo(headX + 3, headY + 20, headX - 3, headY + 17)
            moveTo(headX + 3, headY + 14)
            quadTo(headX + 4, headY + 20, headX + 10, headY + 17)
        }, line, 1.4f)
        stroke(canvas, path(headX - 24, headY + 16, headX - 38, headY + 12), line, 1.1f)
        stroke(canvas, path(headX - 25, headY + 21, headX - 38, headY + 23), line, 1.1f)
        stroke(canvas, path(headX + 25, headY + 16, headX + 38, headY + 12), line, 1.1f)
        stroke(canvas, path(headX + 25, headY + 21, headX + 38, headY + 23), line, 1.1f)

        // The parcel dangles from a ribbon held in her mouth, then lands beside her paws.
        val d = dropProgress.coerceIn(0f, 1f)
        val boxX = mix(headX + 47f, 216f, d)
        val boxY = mix(headY + 38f, 128f, d)
        parcelBounds.set(
            (width - 300 * scale) / 2f + (boxX - 28f) * scale,
            height - 160 * scale + (boxY - 30f) * scale,
            (width - 300 * scale) / 2f + (boxX + 28f) * scale,
            height - 160 * scale + (boxY + 23f) * scale,
        )
        if (d < 1f) {
            paint.alpha = ((1f - d) * 255).toInt()
            stroke(canvas, Path().apply {
                moveTo(headX + 8, headY + 17)
                quadTo(headX + 31, headY + 17, boxX, boxY - 20)
            }, line, 1.7f)
            paint.alpha = 255
        }
        parcel(canvas, boxX, boxY, lidProgress)

        // The opening paw makes visible contact with the lid before the kitten tucks it away.
        val reach = sin(pawProgress.coerceIn(0f, 1f) * PI).toFloat()
        if (reach > .01f) {
            paw(canvas, headX + 6, bodyY + 13, mix(headX + 14, 198f, reach), mix(bodyY + 19, 108f, reach), cream, 12f)
            oval(canvas, mix(headX + 11, 193f, reach), mix(bodyY + 17, 103f, reach), mix(headX + 23, 207f, reach), mix(bodyY + 28, 114f, reach), cream)
        }
        if (r > .9f && sadness == 0) {
            val sparkle = .45f + .3f * sin(seconds * 1.8f)
            spark(canvas, 247f, 104f, 3f, (sparkle * 140).toInt())
        }
        canvas.restore()
        if (running) postInvalidateOnAnimation()
    }

    private fun parcel(canvas: Canvas, x: Float, y: Float, open: Float) {
        val f = open.coerceIn(0f, 1f)
        // A soft glow peeks through as the lid opens.
        if (f > 0) {
            oval(canvas, x - 27, y - 25, x + 27, y + 13, Color.argb((25 * f).toInt(), 255, 219, 151))
        }
        rect(canvas, x - 21, y - 18, x + 21, y + 17, 5f, Color.parseColor("#EAB18A"))
        rect(canvas, x - 18, y - 16, x + 18, y + 14, 3f, Color.parseColor("#F3C5A2"), false)
        rect(canvas, x - 4, y - 17, x + 4, y + 16, 1f, Color.parseColor("#FDE8C6"), false)
        if (f > .05f) {
            oval(canvas, x - 19, y - 21, x + 19, y - 13, Color.argb((f * 255).toInt(), 156, 111, 83))
            oval(canvas, x - 15, y - 19, x + 15, y - 14, Color.argb((f * 230).toInt(), 255, 225, 173))
        }
        shape(canvas, Path().apply {
            moveTo(x + 9, y - 1)
            cubicTo(x + 5, y - 5, x + 1, y + 1, x + 9, y + 7)
            cubicTo(x + 17, y + 1, x + 13, y - 5, x + 9, y - 1)
        }, Color.parseColor("#CF806C"), false)
        canvas.save()
        canvas.rotate(-f * 70f, x - 23, y - 19)
        rect(canvas, x - 24, y - 24, x + 24, y - 15, 3f, Color.parseColor("#F5D0AF"))
        rect(canvas, x - 4, y - 23, x + 4, y - 16, 0f, Color.parseColor("#FFF0D4"), false)
        canvas.restore()
    }

    private fun eye(canvas: Canvas, x: Float, y: Float, blink: Boolean, rest: Float, sadness: Int) {
        if (blink || (rest > .7f && sadness == 0)) {
            stroke(canvas, Path().apply { moveTo(x - 5, y + 1); quadTo(x, y + 5, x + 5, y + 1) }, line, 2.3f)
        } else {
            oval(canvas, x - 4.3f, y - 5, x + 4.3f, y + 6, line)
            oval(canvas, x - 2.8f, y - 4, x - .1f, y - .6f, Color.WHITE)
            if (sadness > 0) {
                val tilt = if (x < 150) -1 else 1
                stroke(canvas, path(x - 5, y - 10f - tilt * 1.5f, x + 5, y - 10f + tilt * 1.5f), line, 1.5f)
            }
        }
    }

    private fun spark(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Int) {
        val color = Color.argb(alpha, 212, 155, 107)
        stroke(canvas, path(x - radius, y, x + radius, y), color, 1.2f)
        stroke(canvas, path(x, y - radius, x, y + radius), color, 1.2f)
    }

    private fun gaitFoot(
        name: String,
        neutralX: Float,
        groundY: Float,
        phaseOffset: Float,
        tuckedX: Float,
        rest: Float,
        walking: Float,
    ): FootPose {
        val cycle = walkDistance / GAIT_CYCLE_DISTANCE + phaseOffset
        val phase = cycle - floor(cycle.toDouble()).toFloat()
        val stanceSpan = STANCE_FRACTION * GAIT_CYCLE_DISTANCE
        val planted = phase < STANCE_FRACTION
        val swing = ((phase - STANCE_FRACTION) / (1f - STANCE_FRACTION)).coerceIn(0f, 1f)
        val strideX = if (planted) {
            // Parent translation advances by the same distance that this local foot retreats:
            // their sum is constant throughout stance, so the paw stays on the ground.
            stanceSpan / 2f - phase * GAIT_CYCLE_DISTANCE
        } else {
            -stanceSpan / 2f + stanceSpan * smooth(swing)
        }
        val lift = if (planted) 0f else sin(swing * PI).toFloat() * 8.5f * walking
        val ground = mix(groundY, 141f, rest)
        return FootPose(name, mix(neutralX + strideX, tuckedX, rest), ground - lift, ground, planted && rest == 0f)
    }

    private fun walkingLeg(
        canvas: Canvas,
        hipX: Float,
        hipY: Float,
        foot: FootPose,
        hind: Boolean,
        far: Boolean,
        rest: Float,
    ) {
        val bend = (if (hind) -5f else 3.5f) * (1f - rest)
        val kneeX = mix(hipX, foot.x, .48f) + bend
        val kneeY = mix(hipY, foot.y - 4f, .47f)
        val limb = Path().apply {
            moveTo(hipX, hipY)
            quadTo(hipX + bend * .25f, hipY + 5f, kneeX, kneeY)
            quadTo(foot.x - 2f, foot.y - 9f, foot.x, foot.y - 3f)
        }
        val fill = if (far) Color.parseColor("#E5CEAC") else cream
        stroke(canvas, limb, line, if (far) 9.3f else 10.5f)
        stroke(canvas, limb, fill, if (far) 5.3f else 6.5f)
        val radius = if (far) 5.5f else 6.8f
        outlinedOval(canvas, foot.x - radius, foot.y - 6.5f, foot.x + radius, foot.y, fill)
        if (!far) {
            stroke(canvas, path(foot.x + 1.5f, foot.y - 3f, foot.x + 1.5f, foot.y - .9f), line, .85f)
            stroke(canvas, path(foot.x + 4.5f, foot.y - 3f, foot.x + 4.5f, foot.y - 1.2f), line, .85f)
        }
    }

    private fun paw(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, fill: Int, size: Float) {
        val p = path(x1, y1, x2, y2)
        stroke(canvas, p, line, size + 3.3f)
        stroke(canvas, p, fill, size)
    }
    private fun path(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
    private fun mix(a: Float, b: Float, amount: Float) = a + (b - a) * amount
    private fun smooth(value: Float) = value * value * (3f - 2f * value)
    private fun oval(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, fill: Int) {
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawOval(left, top, right, bottom, paint)
    }
    private fun outlinedOval(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, fill: Int, outline: Boolean = true) {
        oval(canvas, left, top, right, bottom, fill)
        if (outline) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f; paint.color = line; canvas.drawOval(left, top, right, bottom, paint) }
    }
    private fun rect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Int, outline: Boolean = true) {
        paint.style = Paint.Style.FILL; paint.color = fill
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        if (outline) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f; paint.color = line; canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint) }
    }
    private fun shape(canvas: Canvas, path: Path, fill: Int, outline: Boolean = true) {
        paint.style = Paint.Style.FILL; paint.color = fill; canvas.drawPath(path, paint)
        if (outline) stroke(canvas, path, line, 2.2f)
    }
    private fun stroke(canvas: Canvas, path: Path, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = width
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val GAIT_CYCLE_DISTANCE = 44f
        const val STANCE_FRACTION = .62f
    }
}
