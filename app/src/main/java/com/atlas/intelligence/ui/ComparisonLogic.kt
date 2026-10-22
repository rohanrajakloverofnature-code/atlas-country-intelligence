package com.atlas.intelligence.ui

import com.atlas.intelligence.data.Country
import com.atlas.intelligence.data.Observation

/** Pure comparison rules kept outside Canvas so they are straightforward to test. */
fun capComparisonCountries(countries: List<Country>): List<Country> = countries.distinctBy { it.iso3 }.take(3)

fun commonObservationYears(series: List<List<Observation>>): List<Int> =
    series.map { points -> points.asSequence().map { it.year }.toSet() }
        .reduceOrNull { left, right -> left intersect right }
        ?.sorted()
        .orEmpty()

fun nearestObservation(points: List<Observation>, year: Int): Observation? =
    points.minByOrNull { kotlin.math.abs(it.year - year) }
