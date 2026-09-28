package com.vrk.dialer

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-420dpi")
class SwipeCallControlTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun shortSwipeDoesNotAnswerAndFullSwipeAnswersOnce() {
        var count = 0
        compose.setContent { DialerTheme { SwipeCallControl(Icons.Default.Call, "Answer", CallGreen, true) { count++ } } }
        val answer = compose.onNodeWithContentDescription("Answer")
        answer.performTouchInput { swipe(center, center - Offset(0f, height * .2f)) }
        compose.runOnIdle { assertEquals(0, count) }
        answer.performTouchInput { swipe(center, center - Offset(0f, height * 1.2f)) }
        compose.runOnIdle { assertEquals(1, count) }
        answer.performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }
    @Test fun wrongDirectionDoesNotAnswer() {
        var count = 0
        compose.setContent { DialerTheme { SwipeCallControl(Icons.Default.Call, "Answer", CallGreen, true) { count++ } } }
        compose.onNodeWithContentDescription("Answer").performTouchInput { swipe(center, center + Offset(0f, height * 1.2f)) }
        compose.runOnIdle { assertEquals(0, count) }
    }

    @Test fun incomingUpAnswersAndCannotCommitTwice() {
        var answers = 0
        var declines = 0
        compose.setContent { DialerTheme { IncomingCallSwipe({ answers++ }, { declines++ }) } }
        val handle = compose.onNodeWithContentDescription("Incoming call control")
        handle.performTouchInput { swipe(center, center - Offset(0f, height * .2f)) }
        compose.runOnIdle { assertEquals(0, answers); assertEquals(0, declines) }
        handle.performTouchInput { swipe(center, center - Offset(0f, height * 1.3f)) }
        compose.runOnIdle { assertEquals(1, answers); assertEquals(0, declines) }
        handle.performTouchInput { swipe(center, center + Offset(0f, height * 1.3f)) }
        compose.runOnIdle { assertEquals(1, answers); assertEquals(0, declines) }
    }
    @Test fun incomingDownDeclines() {
        var answers = 0
        var declines = 0
        compose.setContent { DialerTheme { IncomingCallSwipe({ answers++ }, { declines++ }) } }
        compose.onNodeWithContentDescription("Incoming call control").performTouchInput {
            swipe(center, center + Offset(0f, height * 1.3f))
        }
        compose.runOnIdle { assertEquals(0, answers); assertEquals(1, declines) }
    }
}
