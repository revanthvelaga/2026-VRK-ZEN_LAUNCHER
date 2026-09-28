package com.vrk.dialer

import android.graphics.Bitmap
import android.provider.CallLog
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialerHomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val people = listOf(
        Contact("Ananya Rao", "2025550101", starred = true),
        Contact("Arjun Kumar", "2025550102", starred = true),
        Contact("Meera Shah", "2025550103", starred = true),
        Contact("Vikram Reddy", "2025550104")
    )
    private val calls = people.mapIndexed { index, c ->
        Recent(c.name, c.number, if (index == 1) CallLog.Calls.MISSED_TYPE else CallLog.Calls.INCOMING_TYPE,
            System.currentTimeMillis() - index * 3600000L, ids = listOf(index.toLong()))
    }
    private fun launch(dark: Boolean = false, onDial: (String) -> Unit = {}) {
        compose.setContent {
            DialerTheme(dark = dark) {
                Surface(Modifier.fillMaxSize()) {
                    DialerHome("", people, calls, emptyMap(), onDial, {}, {})
                }
            }
        }
    }
    private fun capture(name: String) {
        val file = File("build/outputs/design/$name.png")
        file.parentFile.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun callsAndKeypadDialTheEnteredNumber() {
        var dialed = ""
        launch(onDial = { dialed = it })
        compose.onNodeWithText("Ananya Rao").assertIsDisplayed()
        capture("calls-light")
        compose.onNodeWithContentDescription("Open keypad").performClick()
        compose.onNodeWithContentDescription("2 ABC").performClick()
        compose.onNodeWithContentDescription("0, hold for plus").performClick()
        compose.onNodeWithContentDescription("2 ABC").performClick()
        capture("keypad-light")
        compose.onNodeWithContentDescription("Call", useUnmergedTree = true).performClick()
        assertEquals("202", dialed)
        compose.onAllNodesWithText("Calls", substring = false).onLast().performClick()
        compose.onNodeWithText("All calls").assertIsDisplayed()
    }
    @Test fun missedFilterAndContactsSearchAreIndependent() {
        launch()
        compose.onNodeWithText("Missed", substring = false).performClick()
        compose.onNodeWithText("Ananya Rao").assertDoesNotExist()
        compose.onNodeWithText("Arjun Kumar").assertIsDisplayed()
        compose.onAllNodesWithText("Contacts", substring = false).onLast().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Meera")
        compose.onNodeWithText("Meera Shah").assertIsDisplayed()
        compose.onNodeWithText("Ananya Rao").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        capture("contacts-light")
    }
    @Test fun favouritesAndDarkThemeRender() {
        launch(dark = true)
        capture("calls-dark")
        compose.onAllNodesWithText("Favourites", substring = false).onLast().performClick()
        compose.onNodeWithText("Ananya Rao").assertIsDisplayed()
        capture("favourites-dark")
    }
    @Test fun swipeBetweenTabsPreservesNumber() {
        launch()
        compose.onNodeWithContentDescription("Open keypad").performClick()
        compose.onNodeWithContentDescription("2 ABC").performClick()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithText("All calls").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open keypad").performClick()
        compose.onAllNodesWithText("2", substring = false).onFirst().assertExists()
    }
    @Test fun recentTapOpensDetailsWithoutCalling() {
        var opened: Screen? = null
        var dialCount = 0
        compose.setContent {
            DialerTheme { Surface(Modifier.fillMaxSize()) {
                DialerHome("", people, calls, emptyMap(), { dialCount++ }, { opened = it }, {})
            } }
        }
        compose.onNodeWithText("Ananya Rao").performClick()
        compose.runOnIdle {
            assertEquals(0, dialCount)
            assertEquals("2025550101", (opened as Screen.Details).number)
        }
    }
    @Test
    @Config(qualifiers = "w640dp-h360dp-land-mdpi")
    fun shortWindowKeepsCallActionReachable() {
        launch()
        compose.onNodeWithContentDescription("Open keypad").performClick()
        compose.onNodeWithContentDescription("2 ABC").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Call", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        capture("keypad-landscape")
    }
}
