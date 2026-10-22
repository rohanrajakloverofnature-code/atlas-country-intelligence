package com.atlas.intelligence

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Local Android-runtime smoke coverage; not a replacement for real-device QA. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AtlasUiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun offlineDashboardAndNavigationWork() {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("India").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("India").assertIsDisplayed()
        runCatching {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val output = java.io.File("build/outputs/previews/atlas-overview.png")
            output.parentFile.mkdirs()
            output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithContentDescription("Indicators", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Explore sourced annual measures").assertIsDisplayed()
        compose.onNodeWithContentDescription("Watchlist", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your saved country lens").assertIsDisplayed()
        compose.onNodeWithContentDescription("Overview", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("About data").performClick()
        compose.onNodeWithText("How to read ATLAS").assertIsDisplayed()
        compose.onNodeWithText("Understood").performClick()
        compose.onNodeWithContentDescription("Choose country", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Search countries").assertExists()
    }
}
