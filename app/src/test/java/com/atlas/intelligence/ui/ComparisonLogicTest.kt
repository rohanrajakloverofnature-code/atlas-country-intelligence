package com.atlas.intelligence.ui

import com.atlas.intelligence.data.Country
import com.atlas.intelligence.data.Observation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComparisonLogicTest {
    private fun country(iso: String) = Country(iso, iso.take(2), iso, "", "", "")

    @Test fun selectionIsDistinctAndCappedAtThree() {
        assertEquals(listOf("AAA", "BBB", "CCC"), capComparisonCountries(listOf(country("AAA"), country("BBB"), country("AAA"), country("CCC"), country("DDD"))).map { it.iso3 })
    }

    @Test fun commonYearsRequirePresenceInEverySeries() {
        val result = commonObservationYears(listOf(listOf(Observation(2020, 1.0), Observation(2022, 2.0)), listOf(Observation(2021, 4.0), Observation(2022, 5.0))))
        assertEquals(listOf(2022), result)
    }

    @Test fun nearestHitUsesYearNotValueNormalization() {
        val points = listOf(Observation(2018, 1000.0), Observation(2020, 1200.0), Observation(2024, 1800.0))
        assertEquals(2020, nearestObservation(points, 2021)?.year)
        assertEquals(1200.0, nearestObservation(points, 2021)?.value ?: 0.0, 0.0)
        assertNull(nearestObservation(emptyList(), 2021))
    }
}
