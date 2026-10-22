package com.atlas.intelligence.data

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotJsonTest {
    @Test fun bundledSnapshotHasEightCountriesAndUsefulSeries() {
        val asset = java.io.File("app/src/main/assets/snapshot.json").let { if (it.isFile) it else java.io.File("src/main/assets/snapshot.json") }
        val text = asset.readText()
        val root = JSONObject(text)
        val series = root.getJSONObject("series")
        assertTrue(series.length() >= 8)
        listOf("IND", "USA", "CHN").forEach { iso ->
            val country = series.getJSONObject(iso)
            assertTrue(country.length() >= 15)
            val gdp = country.getJSONObject("NY.GDP.MKTP.CD").getJSONArray("points")
            assertTrue(gdp.length() > 0)
            for (i in 0 until gdp.length()) assertTrue(gdp.getJSONObject(i).has("value"))
        }
    }

    @Test fun definitionsAreUniqueAndGovernanceIsSeparateSource() {
        val ids = MetricCatalog.all.map { it.id }
        assertTrue(ids.size >= 30 && ids.size == ids.toSet().size)
        assertTrue(MetricCatalog.all.filter { it.id.endsWith(".EST") }.all { it.sourceName.contains("source=3") })
        assertTrue(MetricCatalog.all.first { it.id == "GC.BAL.CASH.GD.ZS" }.description.contains("not a measure"))
    }
}
