package com.junction.catnudge

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import com.junction.catnudge.visual.CatSceneView
import com.junction.catnudge.visual.NudgeOverlayView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises user gestures against the real view and Android animation clock. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class NudgeOverlayViewTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var view: NudgeOverlayView
    private var accepts = 0
    private var dismissals = 0

    @Before fun attachVisit() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        view = NudgeOverlayView(activity.get(), { accepts++ }, { dismissals++ })
        activity.get().setContentView(view)
        layout(390, 844)
        setAnimationScale(1f)
    }

    @After fun removeVisit() {
        view.dispose()
        activity.pause().stop().destroy()
        setAnimationScale(1f)
    }

    @Test fun outsideTapsDoNothingWhileTheCatArrives() {
        view.start()
        idle(700)
        assertEquals("ARRIVING", view.visitState)
        repeat(5) { view.performClick() }
        idle(2800)
        assertEquals("READY", view.visitState)
        assertEquals(NudgeOverlayView.QUESTION, view.messageView.text.toString())
        assertEquals(0, accepts + dismissals)
    }

    @Test fun threeOutsideTapsPleadWaitForFarewellThenCloseExactlyOnce() {
        arrive()
        view.performClick()
        idle(850)
        assertEquals("Please? :(", view.messageView.text.toString())
        assertEquals("READY", view.visitState)
        assertEquals(0, dismissals)

        view.performClick()
        idle(850)
        assertEquals("fine :(", view.messageView.text.toString())
        assertEquals("FAREWELL", view.visitState)
        assertEquals(0, dismissals)
        idle(10_000)
        assertEquals("FAREWELL", view.visitState)
        assertEquals("fine :(", view.messageView.text.toString())
        assertEquals(1f, (view.cardView.parent as View).alpha, 0f)
        assertEquals(1f, view.messageView.alpha, 0f)
        assertEquals(0, dismissals)
        view.performClick()
        idle(1000)
        assertEquals("FINISHED", view.visitState)
        assertEquals(1, dismissals)
        assertEquals(0, accepts)
        view.performClick()
        view.dismiss()
        idle(1000)
        assertEquals(1, dismissals)
    }

    @Test fun rapidOutsideTapsCannotSkipThePleadingMessage() {
        arrive()
        repeat(10) { view.performClick() }
        idle(850)
        assertEquals("READY", view.visitState)
        assertEquals("Please? :(", view.messageView.text.toString())
        assertEquals(0, dismissals)
    }

    @Test fun yesOpensQuestionsOnceAndCancelsAnInFlightReaction() {
        arrive()
        view.performClick()
        idle(150)
        view.yesButton.performClick()
        view.yesButton.performClick()
        view.cardView.performClick()
        idle(3000)
        assertEquals(1, accepts)
        assertEquals(0, dismissals)
        assertEquals("FINISHED", view.visitState)
    }

    @Test fun theWholeBoxIsAnAcceptTarget() {
        arrive()
        view.cardView.performClick()
        idle(1000)
        assertEquals(1, accepts)
        assertEquals(0, dismissals)
    }

    @Test fun theVisibleCloseButtonReceivesTouchAboveTheClickableCard() {
        arrive()
        val button = view.closeButton
        val buttonOrigin = IntArray(2).also { button.getLocationOnScreen(it) }
        val rootOrigin = IntArray(2).also { view.getLocationOnScreen(it) }
        val x = (buttonOrigin[0] - rootOrigin[0]) + button.width / 2f
        val y = (buttonOrigin[1] - rootOrigin[1]) + button.height / 2f
        assertTrue(button.width >= 48 && button.height >= 48)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, x, y, 0)
        try {
            assertTrue(view.dispatchTouchEvent(down))
            assertTrue(view.dispatchTouchEvent(up))
        } finally { down.recycle(); up.recycle() }
        idle(1000)
        assertEquals(1, dismissals)
        assertEquals(0, accepts)
    }

    @Test fun yesStillWorksDuringTheFinalGoodbye() {
        arrive()
        view.performClick()
        idle(850)
        view.performClick()
        idle(850)
        idle(10_000)
        assertEquals("FAREWELL", view.visitState)
        assertEquals("fine :(", view.messageView.text.toString())
        assertEquals(0, accepts + dismissals)
        view.yesButton.performClick()
        idle(3000)
        assertEquals(1, accepts)
        assertEquals(0, dismissals)
    }

    @Test fun disposalWhileWaitingForFarewellSuppressesCallbacks() {
        arrive()
        view.performClick()
        idle(850)
        view.performClick()
        idle(850)
        idle(10_000)
        assertEquals("FAREWELL", view.visitState)
        assertEquals(0, accepts + dismissals)
        view.dispose()
        view.yesButton.performClick()
        view.dismiss()
        idle(5000)
        assertEquals(0, accepts + dismissals)
    }

    @Test fun closeWhileArrivingCancelsEntranceAndFinishesOnce() {
        view.start()
        idle(500)
        repeat(3) { view.dismiss() }
        idle(4000)
        assertEquals("FINISHED", view.visitState)
        assertEquals(1, dismissals)
        assertEquals(0, accepts)
    }

    @Test fun transparentAreaConsumesTouchSoUnderlyingAppsDoNotReceiveIt() {
        arrive()
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 12f, 12f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 12f, 12f, 0)
        try {
            assertTrue(view.dispatchTouchEvent(down))
            assertTrue(view.dispatchTouchEvent(up))
        } finally {
            down.recycle()
            up.recycle()
        }
        idle(850)
        assertEquals("Please? :(", view.messageView.text.toString())
    }

    @Test fun disabledSystemAnimationsStillAllowTheCompleteInteraction() {
        setAnimationScale(0f)
        view.start()
        idle(100)
        assertEquals("ARRIVING", view.visitState)
        assertEquals(0f, (view.cardView.parent as View).alpha, 0f)
        idle(3400)
        assertEquals("READY", view.visitState)
        view.performClick()
        idle(100)
        assertEquals("REACTING", view.visitState)
        assertTrue("Button should still animate when OS animator scale is zero", view.yesButton.scaleX > 1f)
        idle(750)
        assertEquals("Please? :(", view.messageView.text.toString())
        assertEquals("READY", view.visitState)
        view.performClick()
        idle(850)
        assertEquals("fine :(", view.messageView.text.toString())
        assertEquals("FAREWELL", view.visitState)
        idle(10_000)
        assertEquals("FAREWELL", view.visitState)
        assertEquals("fine :(", view.messageView.text.toString())
        assertEquals(0, accepts + dismissals)
        view.performClick()
        idle(1000)
        assertEquals("FINISHED", view.visitState)
        assertEquals(0, accepts)
        assertEquals(1, dismissals)
    }

    @Test fun startingBeforeFirstLayoutKeepsTheCatHiddenUntilItsOffscreenPositionIsReady() {
        view.dispose()
        view = NudgeOverlayView(activity.get(), { accepts++ }, { dismissals++ })
        activity.get().setContentView(view)
        val scene = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<CatSceneView>().single()
        assertEquals("New visit should not have been measured yet", 0, view.width)
        view.start()
        assertEquals("Cat should stay hidden before its entrance position can be calculated", View.INVISIBLE, scene.visibility)
        idle(100)
        assertEquals("ARRIVING", view.visitState)
        assertTrue("Cat should still be entering from the side", scene.translationX < -scene.width / 2f)
        assertEquals(0f, (view.cardView.parent as View).alpha, 0f)
        assertEquals(0, accepts + dismissals)
        idle(3400)
        assertEquals("READY", view.visitState)
    }

    @Test fun hostProvidedCopyIsUsedForTheInvitationBothPleasAndAcceptButton() {
        replaceVisit(NudgeCopy("A minute for your quiz?", "Just one?", "I'll wait here.", "Let's go"))
        arrive()
        assertEquals("A minute for your quiz?", view.messageView.text.toString())
        assertEquals("Let's go", view.yesButton.text.toString())
        view.performClick()
        idle(850)
        assertEquals("Just one?", view.messageView.text.toString())
        view.performClick()
        idle(850)
        assertEquals("I'll wait here.", view.messageView.text.toString())
        assertEquals("FAREWELL", view.visitState)
        assertEquals(2, view.outsideTapCount)
    }

    @Test fun restoringTheFinalPleaKeepsTheBoxOpenAndAcceptableWithoutReplayingTheEntrance() {
        val snapshots = mutableListOf<Int>()
        replaceVisit(NudgeCopy("Quiz?", "Please?", "Still here."), initialOutsideTaps = 2,
            onOutsideTapsChanged = { snapshots.add(it) })
        view.start(resume = true)
        idle(100)
        assertEquals("FAREWELL", view.visitState)
        assertEquals("Still here.", view.messageView.text.toString())
        assertEquals(2, view.outsideTapCount)
        assertEquals(1f, (view.cardView.parent as View).alpha, 0f)
        assertTrue(snapshots.isEmpty())
        idle(10_000)
        assertEquals("FAREWELL", view.visitState)
        view.yesButton.performClick()
        assertEquals(1, accepts)
        assertEquals(0, dismissals)
    }

    @Test fun anOutsideTapIsSnapshottedBeforeDisposalCanCancelItsReaction() {
        val snapshots = mutableListOf<Int>()
        replaceVisit(onOutsideTapsChanged = {
            snapshots.add(it)
            view.performClick() // A reentrant listener must not skip a plea.
        })
        arrive()
        view.performClick()
        assertEquals(listOf(1), snapshots)
        assertEquals(1, view.outsideTapCount)
        assertEquals("REACTING", view.visitState)
        view.dispose()
        idle(10_000)
        assertEquals(listOf(1), snapshots)
        assertEquals(0, accepts + dismissals)
    }

    private fun replaceVisit(
        copy: NudgeCopy = NudgeMessages.original,
        initialOutsideTaps: Int = 0,
        onOutsideTapsChanged: (Int) -> Unit = {},
    ) {
        view.dispose()
        view = NudgeOverlayView(activity.get(), { accepts++ }, { dismissals++ }, copy,
            initialOutsideTaps, onOutsideTapsChanged)
        activity.get().setContentView(view)
        layout(390, 844)
    }

    private fun arrive() {
        view.start()
        idle(3500)
        assertEquals("READY", view.visitState)
    }

    private fun layout(width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun idle(ms: Long) = AndroidAnimationClock.advance(ms)

    private fun setAnimationScale(scale: Float) {
        AndroidAnimationClock.setScale(scale)
    }
}
