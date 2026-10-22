package com.atlas.intelligence

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComparisonSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun comparisonCapsAtThreeAndDoesNotChangeDashboardOrWatchlist() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("India").fetchSemanticsNodes().isNotEmpty() }
        lateinit var vm: AtlasViewModel
        compose.runOnIdle { vm = ViewModelProvider(compose.activity)[AtlasViewModel::class.java] }
        val before = vm.state.value
        val countries = listOf("IND", "USA", "CHN", "GBR").map { iso -> before.countries.first { it.iso3 == iso } }
        compose.runOnIdle { vm.setComparisonCountries(countries + countries.first()) }
        compose.waitUntil(30_000) { compose.onRoot().fetchSemanticsNode(); !vm.state.value.comparisonLoading }
        assertEquals(listOf("IND", "USA", "CHN"), vm.state.value.compareCountries.map { it.iso3 })
        assertEquals(before.selected, vm.state.value.selected)
        assertEquals(before.watchlist, vm.state.value.watchlist)
        assertEquals(before.dashboard?.country, vm.state.value.dashboard?.country)
        assertEquals(3, vm.state.value.comparison.size)
        compose.onNodeWithContentDescription("Comparison", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Compare countries").assertIsDisplayed()
        compose.onNodeWithText("Selected 3 of 3").assertExists()
        compose.onNodeWithText("Shared value scale; no per-country normalization").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { vm.removeComparisonCountry(countries[2]) }
        compose.waitUntil(30_000) { compose.onRoot().fetchSemanticsNode(); !vm.state.value.comparisonLoading }
        assertEquals(2, vm.state.value.comparison.size)
        compose.runOnIdle { vm.clearComparison() }
        assertTrue(vm.state.value.comparison.isEmpty())
        assertEquals(before.selected, vm.state.value.selected)
    }
}
