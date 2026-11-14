package com.junction.catnudge.visual

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.junction.catnudge.NudgeCopy
import com.junction.catnudge.NudgeMessages
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A self-contained visit. Its transparent background deliberately consumes outside taps. */
class NudgeOverlayView(
    context: Context,
    private val onAccept: () -> Unit,
    private val onDismiss: () -> Unit,
    private val copy: NudgeCopy = NudgeMessages.original,
    initialOutsideTaps: Int = 0,
    private val onOutsideTapsChanged: (Int) -> Unit = {},
) : FrameLayout(context) {
    private enum class Phase { WAITING, ARRIVING, READY, REACTING, FAREWELL, LEAVING, FINISHED }

    private var phase = Phase.WAITING
    internal val visitState: String get() = phase.name
    private val animations = mutableSetOf<FrameAnimation>()
    private var pendingStartListener: View.OnLayoutChangeListener? = null
    private var disposed = false
    private var outsideTaps = initialOutsideTaps.coerceIn(0, 2)
    internal val outsideTapCount: Int get() = outsideTaps
    private var hasStarted = false
    private var landscape = false
    private val parcelCat = CatSceneView(context).apply {
        visibility = View.INVISIBLE
        sadness = outsideTaps
    }
    private val yesBackground = rounded("#CC8067", 24f)

    internal val cardView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(22), dp(20), dp(22), dp(19))
        background = rounded("#FFF9EC", 25f, "#E8D9CA")
        elevation = dp(10).toFloat()
        alpha = 0f
        isClickable = true
        setOnClickListener { accept() }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    internal val messageView = TextView(context).apply {
        text = messageForCount(outsideTaps)
        textSize = 21f
        setTextColor(Color.parseColor("#574339"))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setLineSpacing(dp(3).toFloat(), 1f)
        maxLines = 3
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    internal val yesButton = Button(context).apply {
        text = copy.acceptLabel
        isAllCaps = false
        textSize = 17f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Color.WHITE)
        background = yesBackground
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(48)
        minimumHeight = dp(48)
        stateListAnimator = null
        contentDescription = "${copy.acceptLabel}, ${copy.invitation}"
        setOnClickListener { accept() }
    }
    internal val closeButton = object : Button(context) {
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8C776B")
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = dp(4).toFloat()
            canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, iconPaint)
            canvas.drawLine(cx - radius, cy + radius, cx + radius, cy - radius, iconPaint)
        }
    }.apply {
        text = ""
        elevation = dp(12).toFloat()
        stateListAnimator = null
        background = null
        setPadding(0, 0, 0, 0)
        minimumWidth = 0
        minimumHeight = 0
        minWidth = 0
        minHeight = 0
        contentDescription = "Dismiss cat nudge"
        setOnClickListener { dismiss() }
    }
    private val speechTail = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val triangle = Path().apply {
                moveTo(0f, 0f)
                lineTo(width.toFloat(), 0f)
                lineTo(width * .67f, height.toFloat())
                close()
            }
            paint.color = Color.parseColor("#FFF9EC")
            paint.style = Paint.Style.FILL
            canvas.drawPath(triangle, paint)
            paint.color = Color.parseColor("#E8D9CA")
            paint.strokeWidth = resources.displayMetrics.density
            paint.style = Paint.Style.STROKE
            canvas.drawLine(0f, 0f, width * .67f, height.toFloat(), paint)
            canvas.drawLine(width.toFloat(), 0f, width * .67f, height.toFloat(), paint)
        }
    }
    private val cardShell = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(cardView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(speechTail, LayoutParams(dp(18), dp(11), Gravity.BOTTOM or Gravity.RIGHT).apply {
            rightMargin = dp(57)
        })
        speechTail.translationY = dp(10).toFloat()
        addView(closeButton, LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.RIGHT))
        alpha = 0f
    }

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        setOnClickListener { outsideTap() }
        parcelCat.setOnParcelClickListener { accept() }
        parcelCat.contentDescription = "${copy.acceptLabel}, ${copy.invitation}"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val caption = TextView(context).apply {
            text = "SPECIAL DELIVERY"
            textSize = 9f
            letterSpacing = .16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.parseColor("#A88A73"))
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        cardView.addView(caption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(13)
        })
        cardView.addView(messageView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        cardView.addView(yesButton, LinearLayout.LayoutParams(dp(112), LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })
        addView(parcelCat, LayoutParams(dp(300), dp(160), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(12)
        })
        addView(cardShell, LayoutParams(dp(280), LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(112)
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        landscape = w > h * 1.25f && w > dp(550)
        val cardWidth = min(dp(280), w - dp(32))
        cardShell.layoutParams = (cardShell.layoutParams as LayoutParams).apply {
            width = cardWidth
            gravity = Gravity.BOTTOM or if (landscape) Gravity.RIGHT else Gravity.CENTER_HORIZONTAL
            rightMargin = if (landscape) max(dp(24), w / 2 - dp(284)) else 0
            bottomMargin = if (landscape) dp(30) else min(dp(112), max(dp(72), h / 3))
        }
        speechTail.visibility = if (landscape) View.GONE else View.VISIBLE
        parcelCat.layoutParams = (parcelCat.layoutParams as LayoutParams).apply {
            width = min(dp(300), w)
            height = min(dp(160), h / 2)
            gravity = Gravity.BOTTOM or if (landscape) Gravity.LEFT else Gravity.CENTER_HORIZONTAL
            leftMargin = if (landscape) max(dp(12), w / 2 - dp(300)) else 0
            bottomMargin = if (landscape) dp(21) else dp(12)
        }
    }

    /** Call after adding this view to WindowManager. Safe to call more than once. */
    fun start(resume: Boolean = false) {
        if (hasStarted || disposed) return
        hasStarted = true
        doOnLayout {
            if (disposed || phase != Phase.WAITING) return@doOnLayout
            if (hasValidLayout()) {
                beginOrResumeVisit(resume)
            } else {
                // A zero-sized layout can occur during window attachment. Keep the kitten hidden
                // until the real overlay bounds and her child layout are available.
                pendingStartListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (hasValidLayout()) {
                        cancelPendingStart()
                        if (!disposed && phase == Phase.WAITING) beginOrResumeVisit(resume)
                    }
                }.also { addOnLayoutChangeListener(it) }
            }
        }
    }

    private fun hasValidLayout() = width > 0 && height > 0 && parcelCat.width > 0 && parcelCat.height > 0

    private fun beginOrResumeVisit(resume: Boolean) {
        if (!resume) {
            beginVisit()
            return
        }
        // A restored visit continues where the user left it, without replaying the delivery.
        parcelCat.showResting()
        parcelCat.translationX = 0f
        parcelCat.alpha = 1f
        parcelCat.visibility = View.VISIBLE
        parcelCat.start()
        cardView.alpha = 1f
        cardShell.alpha = 1f
        cardShell.scaleX = 1f
        cardShell.scaleY = 1f
        cardShell.translationY = 0f
        settleAtSavedPrompt()
    }

    private fun settleAtSavedPrompt() {
        messageView.text = messageForCount(outsideTaps)
        messageView.alpha = 1f
        parcelCat.sadness = outsideTaps
        phase = if (outsideTaps >= 2) Phase.FAREWELL else Phase.READY
        // The polite live region reads the prompt without issuing a forced announcement.
        messageView.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun messageForCount(count: Int) = when (count) {
        0 -> copy.invitation
        1 -> copy.gentlePlea
        else -> copy.finalPlea
    }

    private fun beginVisit() {
        phase = Phase.ARRIVING
        // Position her before making her visible, including the very first rendered frame.
        val entranceX = -(parcelCat.left + parcelCat.width).toFloat()
        val sceneScale = min(parcelCat.width / 300f, parcelCat.height / 160f).coerceAtLeast(.01f)
        parcelCat.translationX = entranceX
        parcelCat.walkDistance = 0f
        parcelCat.visibility = View.VISIBLE
        parcelCat.start()
        animateFor(3050, update = { t ->
            val ms = t * 3050f
            val walk = portion(ms, 0f, 1700f)
            parcelCat.translationX = entranceX * (1f - smooth(walk))
            parcelCat.walkDistance = (parcelCat.translationX - entranceX) / sceneScale
            // After travel stops, lower any lifted feet naturally while keeping their horizontal
            // positions fixed, then let the separate unboxing/rest animation take over.
            parcelCat.walkAmount = 1f - smooth(portion(ms, 1700f, 170f))
            parcelCat.dropProgress = smooth(portion(ms, 1710f, 430f))
            parcelCat.pawProgress = portion(ms, 2060f, 500f)
            parcelCat.lidProgress = smooth(portion(ms, 2300f, 400f))
            parcelCat.restProgress = smooth(portion(ms, 2520f, 500f))
            val reveal = smooth(portion(ms, 2600f, 450f))
            cardShell.alpha = reveal
            cardView.alpha = 1f
            cardShell.scaleX = .88f + .12f * reveal
            cardShell.scaleY = .88f + .12f * reveal
            cardShell.translationY = dp(18) * (1f - reveal)
        }, finished = {
            settleAtSavedPrompt()
        })
    }

    private fun outsideTap() {
        if (disposed) return
        if (phase == Phase.FAREWELL) {
            dismiss()
            return
        }
        if (phase != Phase.READY) return
        outsideTaps++
        phase = Phase.REACTING
        onOutsideTapsChanged(outsideTaps)
        if (disposed || phase != Phase.REACTING) return
        parcelCat.sadness = outsideTaps
        val nextMessage = messageForCount(outsideTaps)
        var changed = false
        animateFor(670, update = { t ->
            cardView.translationX = (sin(t * PI * 8) * exp(-3.1 * t) * dp(11)).toFloat()
            cardView.rotation = (sin(t * PI * 6) * (1f - t) * 1.1f).toFloat()
            val highlight = sin(t * PI).toFloat()
            yesButton.scaleX = 1f + highlight * .075f
            yesButton.scaleY = 1f + highlight * .075f
            yesBackground.setColor(if (t in .08f.. .90f) Color.parseColor("#AF6553") else Color.parseColor("#CC8067"))
            if (t < .20f) {
                messageView.alpha = 1f - t / .20f
            } else {
                if (!changed) {
                    changed = true
                    messageView.text = nextMessage
                }
                messageView.alpha = min(1f, (t - .20f) / .30f)
            }
        }, finished = {
            cardView.translationX = 0f
            cardView.rotation = 0f
            yesButton.scaleX = 1f
            yesButton.scaleY = 1f
            yesBackground.setColor(Color.parseColor("#CC8067"))
            messageView.text = nextMessage
            messageView.alpha = 1f
            if (outsideTaps == 1) {
                phase = Phase.READY
            } else {
                phase = Phase.FAREWELL
            }
        })
    }

    private fun accept() {
        if (disposed || phase !in setOf(Phase.READY, Phase.REACTING, Phase.FAREWELL)) return
        phase = Phase.FINISHED
        cancelAnimations()
        onAccept()
    }

    /** Animate away, then invoke onDismiss exactly once. */
    fun dismiss() {
        if (disposed || phase == Phase.LEAVING || phase == Phase.FINISHED) return
        phase = Phase.LEAVING
        cancelPendingStart()
        cancelAnimations()
        val fromAlpha = cardShell.alpha
        val fromX = parcelCat.translationX
        val fromDistance = parcelCat.walkDistance
        val sceneScale = min(parcelCat.width / 300f, parcelCat.height / 160f).coerceAtLeast(.01f)
        val fromRest = parcelCat.restProgress
        val fromLid = parcelCat.lidProgress
        animateFor(620, update = { t ->
            cardShell.alpha = fromAlpha * (1f - min(1f, t * 2.3f))
            cardShell.translationY = dp(12) * t
            parcelCat.restProgress = fromRest * (1f - min(1f, t * 3))
            parcelCat.lidProgress = fromLid * (1f - min(1f, t * 3))
            parcelCat.walkAmount = 1f
            parcelCat.alpha = 1f - t
            parcelCat.translationX = fromX - (width + parcelCat.width) * smooth(t)
            parcelCat.walkDistance = fromDistance + (parcelCat.translationX - fromX) / sceneScale
        }, finished = {
            phase = Phase.FINISHED
            onDismiss()
        })
    }

    /** Cancel every frame callback and pending layout start when the owner removes the overlay. */
    fun dispose() {
        if (disposed) return
        disposed = true
        cancelPendingStart()
        cancelAnimations()
        parcelCat.stop()
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }

    private fun cancelAnimations() {
        animations.toList().forEach { it.cancel() }
        animations.clear()
    }

    private fun animateFor(durationMs: Long, update: (Float) -> Unit, finished: () -> Unit) {
        if (disposed) return
        val animation = FrameAnimation(durationMs, update, finished)
        animations.add(animation)
        animation.start()
    }

    private fun cancelPendingStart() {
        pendingStartListener?.let { removeOnLayoutChangeListener(it) }
        pendingStartListener = null
    }

    /** The kitten's storytelling runs on frame time, independently of system transition scales. */
    private inner class FrameAnimation(
        private val durationMs: Long,
        private val update: (Float) -> Unit,
        private val finished: () -> Unit,
    ) : Choreographer.FrameCallback {
        private val choreographer = Choreographer.getInstance()
        private var startedAt = 0L
        private var active = false

        fun start() {
            if (disposed || active) return
            active = true
            startedAt = SystemClock.uptimeMillis()
            update(0f)
            if (active && !disposed) choreographer.postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!active || disposed) return
            val elapsed = (SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
            val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            update(fraction)
            if (!active || disposed) return
            if (fraction >= 1f) {
                active = false
                animations.remove(this)
                finished()
            } else {
                choreographer.postFrameCallback(this)
            }
        }

        fun cancel() {
            active = false
            choreographer.removeFrameCallback(this)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun rounded(fill: String, radius: Float, border: String? = null) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = radius * resources.displayMetrics.density
        border?.let { setStroke(dp(1), Color.parseColor(it)) }
    }
    private fun portion(value: Float, start: Float, duration: Float) = ((value - start) / duration).coerceIn(0f, 1f)
    private fun smooth(value: Float) = value * value * (3f - 2f * value)

    companion object { const val QUESTION = "answer a question or two?" }
}
