package com.atlas.intelligence

import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import com.atlas.intelligence.ui.AtlasApp
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compact, large-text regression coverage for the overview shell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactOverviewKeepsControlsTargetsNotesAndDestinationsUsable() {
        lateinit var viewModel: AtlasViewModel
        compose.runOnIdle {
            viewModel = ViewModelProvider(compose.activity)[AtlasViewModel::class.java]
            compose.activity.setContent {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.3f)
                ) {
                    AtlasApp(viewModel)
                }
            }
        }

        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("ATLAS").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("India").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ATLAS").assertIsDisplayed()
        compose.onNodeWithText("Country intelligence").assertIsDisplayed()

        val controls = listOf("About data", "Refresh", "Compare countries", "Choose country")
        controls.forEach { description ->
            val node = compose.onNodeWithContentDescription(description).assertHasClickAction().fetchSemanticsNode()
            val density = compose.activity.resources.displayMetrics.density
            assertTrue("$description must have a 48dp target, actual ${node.boundsInRoot}", node.boundsInRoot.width / density >= 47.5f)
            assertTrue("$description must have a 48dp target, actual ${node.boundsInRoot}", node.boundsInRoot.height / density >= 47.5f)
        }

        val warningTexts = mutableListOf<String>()
        compose.runOnIdle { warningTexts += viewModel.state.value.dashboard?.warnings.orEmpty() }
        compose.onNode(hasText("Data notes") and hasClickAction())
            .performClick()
        compose.onNodeWithText("Data notes").assertIsDisplayed()
        compose.onNodeWithText(
            "${warningTexts.size} ${if (warningTexts.size == 1) "note" else "notes"} · tap to hide"
        ).assertIsDisplayed()
        warningTexts.forEach { warning ->
            compose.onNodeWithText(warning, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }

        listOf("Overview", "Indicators", "Briefing", "Watchlist", "Comparison").forEach { destination ->
            compose.onNodeWithContentDescription(destination, useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithText("Compare").assertIsDisplayed()
        listOf("Home", "Explore", "News", "Saved", "Compare").forEach { label ->
            compose.onNodeWithText(label).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts))
                assertTrue("$label must not be clipped", layouts.none { it.hasVisualOverflow })
            }
        }

        compose.onNodeWithContentDescription("Comparison", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Compare countries").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Compare countries").assertIsDisplayed()
        compose.onNodeWithContentDescription("Overview", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("COUNTRY PROFILE").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("COUNTRY PROFILE").assertIsDisplayed()

        // Native Robolectric capture is best-effort: older Compose/Robolectric combinations
        // can expose a root without a software-readable bitmap.
        runCatching {
            val output = File(System.getProperty("atlas.screenshot.path") ?: "build/reports/screenshots/atlas-v0.2.1-overview.png")
            output.parentFile?.mkdirs()
            val bitmap = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }.getOrElse {
                lateinit var rendered: android.graphics.Bitmap
                compose.runOnIdle {
                    val view = compose.activity.window.decorView
                    rendered = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(rendered))
                }
                rendered
            }
            output.outputStream().use { stream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.onFailure { println("Native screenshot capture unavailable: ${it.message}") }
    }
}
