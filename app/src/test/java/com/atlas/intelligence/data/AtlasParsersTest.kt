package com.atlas.intelligence.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AtlasParsersTest {
    private val gdp = MetricCatalog.all.first { it.id == "NY.GDP.MKTP.CD" }

    @Test fun officialCountryArraySupportsNestedMetadataAndExcludesAggregates() {
        val payload = """[{"page":1},[
            {"id":"IND","iso2Code":"IN","name":"India","region":{"value":"South Asia"},"incomeLevel":{"value":"Lower middle income"},"capitalCity":"New Delhi"},
            {"id":"WLD","name":"World","region":{"value":"Aggregates"}}
        ]]"""
        val result = AtlasParsers.parseCountries(payload)
        assertEquals(1, result.size)
        assertEquals(Country("IND", "IN", "India", "South Asia", "New Delhi", "Lower middle income"), result.single())
    }

    @Test fun bundledCountryShapeIsAlsoSupported() {
        val result = AtlasParsers.parseCountries("""{"countries":[{"iso3":"JPN","iso2":"JP","name":"Japan","region":"East Asia","capital":"Tokyo","income":"High income"}]}""")
        assertEquals("Tokyo", result.single().capital)
    }

    @Test fun nullObservationsAreOmittedButGenuineZeroAndNegativeValuesRemain() {
        val payload = """[{"lastupdated":"2026-10-08"},[
            {"date":"2024","value":null}, {"date":"2023","value":0},
            {"date":"2022","value":-1.25}, {"date":"2021","value":"NaN"}
        ]]"""
        val result = AtlasParsers.parseSeries(gdp, payload, "2026-10-22T00:00:00Z")!!
        assertEquals(listOf(Observation(2022, -1.25), Observation(2023, 0.0)), result.points)
        assertEquals("2026-10-08", result.lastUpdated)
        assertFalse(result.cached)
    }

    @Test fun missingDataAndApiErrorAreNotManufacturedIntoAValue() {
        assertNull(AtlasParsers.parseSeries(gdp, """[{"message":[{"value":"not found"}]}]""", "now"))
        assertNull(AtlasParsers.parseSeries(gdp, """[{},[{"date":"2025","value":null}]]""", "now"))
    }

    @Test fun cachedSeriesPreservesOriginalRetrievalAndMissingYearGap() {
        val result = AtlasParsers.parseCachedSeries(gdp, JSONObject("""{"lastUpdated":"2025-09-01","points":[{"year":2020,"value":1},{"year":2022,"value":3}]}"""), "2025-10-01T00:00:00Z")!!
        assertEquals("2025-10-01T00:00:00Z", result.retrievedAt)
        assertTrue(result.cached)
        assertEquals(listOf(2020, 2022), result.points.map { it.year })
    }

    @Test fun emptyCachedSeriesIsNotAcceptedAsUsefulData() {
        assertNull(AtlasParsers.parseCachedSeries(gdp, JSONObject("""{"points":[]}"""), "old"))
    }

    @Test fun mismatchedCountryAndIndicatorCannotContaminateDashboard() {
        val payload = """[{},[
            {"indicator":{"id":"NY.GDP.MKTP.CD"},"countryiso3code":"USA","date":"2025","value":100},
            {"indicator":{"id":"FP.CPI.TOTL.ZG"},"countryiso3code":"IND","date":"2025","value":2.4}
        ]]"""
        assertNull(AtlasParsers.parseSeries(gdp, payload, "now", expectedCountry = "IND"))
    }

    @Test fun verifiedWorldBankInflationRetainsObservationYearAndPrecision() {
        val inflation = MetricCatalog.all.first { it.id == "FP.CPI.TOTL.ZG" }
        val payload = """[{"lastupdated":"2026-10-08"},[
            {"indicator":{"id":"FP.CPI.TOTL.ZG"},"countryiso3code":"IND","date":"2025","value":2.39884954182294},
            {"indicator":{"id":"FP.CPI.TOTL.ZG"},"countryiso3code":"IND","date":"2026","value":null}
        ]]"""
        val series = AtlasParsers.parseSeries(inflation, payload, "2026-10-22T19:22:00Z", expectedCountry = "IND")!!
        assertEquals(Observation(2025, 2.39884954182294), series.points.single())
        assertEquals("% annual", series.definition.unit)
        assertEquals("2026-10-08", series.lastUpdated)
    }

    @Test fun currentGovernanceCodesAndSourceArePreserved() {
        val governance = MetricCatalog.all.filter { it.category == "Governance" }
        assertEquals(6, governance.size)
        assertTrue(governance.all { it.id.startsWith("GOV_WGI_") && it.sourceName.contains("source=3") })
        val url = AtlasRepository.indicatorUrl("https://api.worldbank.org/v2", "IND", governance.first().id, 3, 2026)
        assertTrue(url.contains("source=3"))
        assertTrue(url.contains("date=2000:2026"))
    }
}
