package com.atlas.intelligence.data

import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndicatorRanksTest {
    private val lookup by lazy { IndicatorRanks.load(RuntimeEnvironment.getApplication()) }

    @Test fun exactYearAndValueAreRequiredForFreshRank() {
        val india = lookup.rank("IND", "SP.POP.TOTL", 2024)
        assertNotNull(india)
        assertEquals(2024, india!!.year)
        assertNull(lookup.rank("IND", "SP.POP.TOTL", 2023, india.value))
        assertNull(lookup.rank("IND", "SP.POP.TOTL", 2024, india.value + 1.0))
        assertNotNull(lookup.rank("IND", "SP.POP.TOTL", 2024, india.value))
    }

    @Test fun missingObservationIsNullAndZeroIsNotMissing() {
        assertNull(lookup.rank("IND", "SP.DYN.LE00.IN", 2099))
        val values = lookup.rank("IND", "NY.GDP.MKTP.KD.ZG", 2020)
        assertNotNull(values)
        assertFalse(values!!.value == 0.0 && lookup.rank("IND", "NY.GDP.MKTP.KD.ZG", 2020) == null)
    }

    @Test fun tiesUseCompetitionRanksAndSameYearDenominator() {
        val json = RuntimeEnvironment.getApplication().assets.open("indicator_ranks.json")
            .bufferedReader().use { it.readText() }
        // Find a real tied pair in the generated observations; this avoids inventing test data.
        val root = org.json.JSONObject(json).getJSONObject("metrics")
        var tie: Pair<org.json.JSONObject, org.json.JSONObject>? = null
        val metrics = root.keys()
        while (metrics.hasNext() && tie == null) {
            val metric = root.getJSONObject(metrics.next()).getJSONObject("years")
            val years = metric.keys()
            while (years.hasNext() && tie == null) {
                val countries = metric.getJSONObject(years.next())
                val seen = mutableMapOf<Double, org.json.JSONObject>()
                val keys = countries.keys()
                while (keys.hasNext()) {
                    val item = countries.getJSONObject(keys.next())
                    val prior = seen.put(item.getDouble("value"), item)
                    if (prior != null) tie = prior to item
                }
            }
        }
        assertNotNull("bundled data should contain at least one exact tie", tie)
        assertEquals(tie!!.first.getInt("rank"), tie!!.second.getInt("rank"))
        assertTrue(tie!!.first.getInt("rank") <= tie!!.first.getInt("denominator"))
    }

    @Test fun coverageIncludesBundledCountriesAndTruthfulCalculatedScope() {
        val root = org.json.JSONObject(RuntimeEnvironment.getApplication().assets.open("indicator_ranks.json")
            .bufferedReader().use { it.readText() })
        assertTrue(root.getString("scope").contains("217 bundled World Bank reporting countries/economies"))
        assertEquals("Calculated from bundled World Bank observations", lookup.source)
        assertTrue(lookup.scope.contains("territories included"))
        assertTrue(lookup.scope.contains("not a universal sovereign-country ranking"))
        assertTrue(root.getJSONArray("notes").toString().contains("not official World Bank"))
        assertNull(lookup.rank("IND", "GOV_WGI_RL.EST", 2022))
        assertNotNull(lookup.rank("IND", "NY.GDP.MKTP.CD", 2025))
    }
}
