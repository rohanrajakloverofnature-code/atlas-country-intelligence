package com.atlas.intelligence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.atlas.intelligence.data.Observation
import com.atlas.intelligence.ui.ChartSeries
import com.atlas.intelligence.ui.InteractiveChart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartTouchSmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tapsAndHorizontalDragsExposeExactYearValueAndUnit() {
        compose.setContent {
            MaterialTheme {
                InteractiveChart(listOf(ChartSeries("India", listOf(Observation(2020, 4.5), Observation(2022, 6.25)), Color.Cyan)), Modifier.fillMaxWidth().height(240.dp), "% annual", showLabels = true)
            }
        }
        compose.onNode(hasContentDescription("Interactive observation chart", substring = true)).performTouchInput { click(centerRight) }
        compose.onNodeWithText("2022 · India: 6.25 % annual").assertExists()
        compose.onNode(hasContentDescription("Interactive observation chart", substring = true)).performTouchInput { swipeRight() }
        compose.onNodeWithText("2022 · India: 6.25 % annual").assertExists()
    }
    @Test fun aVerticalSwipeOnChartStillScrollsTheParent() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    InteractiveChart(listOf(ChartSeries("India", listOf(Observation(2020, 1.0), Observation(2021, 2.0)), Color.Cyan)), Modifier.fillMaxWidth().height(500.dp), "people")
                    Spacer(Modifier.height(250.dp))
                    Text("Parent scroll target")
                    Spacer(Modifier.height(450.dp))
                }
            }
        }
        compose.onNode(hasContentDescription("Interactive observation chart", substring = true)).performTouchInput { swipeUp() }
        compose.onNodeWithText("Parent scroll target").assertIsDisplayed()
    }
}
