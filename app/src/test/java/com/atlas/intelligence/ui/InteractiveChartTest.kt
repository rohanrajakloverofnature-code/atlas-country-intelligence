package com.atlas.intelligence.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractiveChartTest {
    @Test fun hitSelectsNearestOrderedYearAtHorizontalPosition() {
        val years = listOf(2019, 2021, 2024)
        assertEquals(2019, chartHitYear(years, 0f, 10f, 110f))
        assertEquals(2021, chartHitYear(years, 65f, 10f, 110f))
        assertEquals(2024, chartHitYear(years, 110f, 10f, 110f))
    }

    @Test fun gapsUseActualCalendarPositionsNotObservationIndexes() {
        val years = listOf(2000, 2001, 2025)
        assertEquals(2001, chartHitYear(years, 25f, 0f, 100f))
        assertEquals(2025, chartHitYear(years, 90f, 0f, 100f))
        assertEquals(2020, chartHitYear(listOf(2020), 50f, 0f, 100f))
    }

    @Test fun hitClampsOutsideChartAndHandlesInvalidBounds() {
        val years = listOf(2020, 2023)
        assertEquals(2020, chartHitYear(years, -20f, 10f, 110f))
        assertEquals(2023, chartHitYear(years, 200f, 10f, 110f))
        assertNull(chartHitYear(emptyList(), 40f, 10f, 110f))
        assertNull(chartHitYear(years, 40f, 10f, 10f))
    }
}
