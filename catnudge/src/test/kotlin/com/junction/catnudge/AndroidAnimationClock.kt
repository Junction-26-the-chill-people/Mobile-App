package com.junction.catnudge

import android.animation.ValueAnimator
import android.os.Looper
import org.junit.Assert.assertEquals
import org.robolectric.shadows.ShadowUiAutomation
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Robolectric's Activity setup can disable animations; use after attaching the test Activity. */
internal object AndroidAnimationClock {
    fun setScale(scale: Float) {
        // A PAUSED Looper alone does not pause vsync: the default Choreographer advances time
        // on every newly posted frame and can finish an animation inside a short idleFor call.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        ShadowUiAutomation.setAnimationScaleCompat(scale)
        ReflectionHelpers.callStaticMethod<Any?>(ValueAnimator::class.java, "setDurationScale",
            ClassParameter.from(java.lang.Float.TYPE, scale))
        assertEquals(scale, ReflectionHelpers.getStaticField<Float>(ValueAnimator::class.java, "sDurationScale"), 0f)
    }

    fun advance(milliseconds: Long) {
        // Native vsync becomes due as the clock advances; step each frame so a long idle call
        // does not move directly to the endpoint before scheduling the following frame.
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }
}
