package com.vrk.dialer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
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
class AppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun reset() { appearancePrefs(compose.activity).edit().clear().commit() }
    @Test fun previewDoesNotApplyUntilConfirmedAndChoicePersists() {
        compose.setContent { DialerTheme { AppearanceScreen {} } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Luminous", substring = false))
        compose.onNodeWithText("Luminous", substring = false).performClick()
        assertEquals(PhoneTheme.SAPPHIRE, readAppearance(appearancePrefs(compose.activity)).theme)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Apply Luminous"))
        compose.onNodeWithText("Apply Luminous").performClick()
        compose.runOnIdle { assertEquals(PhoneTheme.LUMINOUS, readAppearance(appearancePrefs(compose.activity)).theme) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Dark", substring = false))
        compose.onNodeWithText("Dark", substring = false).performClick()
        compose.runOnIdle { assertEquals(DisplayMode.DARK, readAppearance(appearancePrefs(compose.activity)).mode) }
    }
    @Test fun invalidStoredValuesRecoverToDefaults() {
        val p = appearancePrefs(compose.activity)
        p.edit().putString("theme", "obsolete").putString("mode", "invalid").commit()
        assertEquals(PhoneTheme.SAPPHIRE, readAppearance(p).theme)
        assertEquals(DisplayMode.SYSTEM, readAppearance(p).mode)
    }
    @Test fun allThemesDialCorrectlyAndRenderLightAndDark() {
        var called = ""
        compose.setContent { DialerTheme { Surface(Modifier.fillMaxSize()) {
            DialerHome("", emptyList(), emptyList(), emptyMap(), { called = it }, {}, {})
        } } }
        compose.onNodeWithContentDescription("Open keypad", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("2 ABC").performClick()
        compose.onNodeWithContentDescription("0, hold for plus").performClick()
        PhoneTheme.entries.forEach { theme ->
            listOf(DisplayMode.LIGHT, DisplayMode.DARK).forEach { mode ->
                compose.runOnIdle { appearancePrefs(compose.activity).edit().putString("theme", theme.name).putString("mode", mode.name).apply() }
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Call").performScrollTo().performClick()
                assertEquals("20", called)
                capture("${theme.name.lowercase()}-${mode.name.lowercase()}")
            }
        }
    }
    @Test fun flowKeypadCanCollapseAndReturnWithoutLosingDigits() {
        compose.setContent { DialerTheme(theme = PhoneTheme.FLOW) { Surface(Modifier.fillMaxSize()) {
            DialerHome("", emptyList(), emptyList(), emptyMap(), {}, {}, {})
        } } }
        compose.onNodeWithContentDescription("Open keypad", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("2 ABC").performClick()
        compose.onNodeWithText("Hide keypad").performScrollTo().performClick()
        compose.onNodeWithContentDescription("2 ABC").assertDoesNotExist()
        compose.onNodeWithText("Show keypad").performClick()
        compose.onNodeWithContentDescription("2 ABC").assertExists()
        compose.onAllNodesWithText("2", substring = false).assertCountEquals(2)
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/design/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
