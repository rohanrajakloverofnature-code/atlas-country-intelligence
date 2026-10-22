package com.atlas.intelligence.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GovernmentDataTest {
    private fun asset(name: String): JSONObject {
        val root = File("app/src/main/assets/$name").let { if (it.isFile) it else File("src/main/assets/$name") }
        return JSONObject(root.readText())
    }

    @Test fun indiaHasTwentyFiveDistinctGovernmentSeries() {
        val series = GovernmentData.parse(asset("government_ind.json").toString())
        assertEquals(25, series.size)
        assertEquals(25, series.map { it.definition.id }.toSet().size)
        assertTrue(series.all { it.definition.category == "Government" })
        assertTrue(series.all { it.definition.id.startsWith("GOV.IND.") })
    }

    @Test fun everyBundledObservationHasFullProvenanceAndExactPeriodNote() {
        listOf("government_ind.json", "government_usa.json", "government_gbr.json").forEach { name ->
            val series = GovernmentData.parse(asset(name).toString())
            assertFalse(series.isEmpty())
            series.forEach { item ->
                assertTrue(item.definition.sourceUrl.startsWith("https://"))
                assertTrue(item.definition.sourceOrganization.isNotBlank())
                assertTrue(item.definition.sourceNote.contains("20"))
                assertTrue(item.definition.unit.isNotBlank())
                assertTrue(item.points.all { it.year in 1900..2100 && it.value.isFinite() })
            }
        }
    }

    @Test fun otherCountryCoverageIsExplicitlyLimited() {
        val usa = GovernmentData.parse(asset("government_usa.json").toString())
        val uk = GovernmentData.parse(asset("government_gbr.json").toString())
        assertEquals(28, usa.size)
        assertEquals(25, uk.size)
        assertTrue(GovernmentData.coverageNote(Country("USA", "US", "United States", "", "", "")).contains("not global"))
    }

    @Test fun parserPreservesFiscalAndRollingPeriodYearWithoutInventingHistory() {
        val india = GovernmentData.parse(asset("government_ind.json").toString())
        val growth = india.first { it.definition.id == "GOV.IND.gdp_growth" }
        assertEquals(2024, growth.points.single().year)
        assertTrue(growth.definition.sourceNote.contains("April 2023–March 2024"))
        assertEquals(1, growth.points.size)
    }
}
