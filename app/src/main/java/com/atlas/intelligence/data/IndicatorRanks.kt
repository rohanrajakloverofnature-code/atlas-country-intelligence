package com.atlas.intelligence.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

/** A calculated comparison, never an official World Bank-published rank. */
data class IndicatorRank(
    val countryIso: String,
    val metricId: String,
    val year: Int,
    val value: Double,
    val rank: Int,
    val denominator: Int,
    val direction: RankDirection,
    val label: String,
    val source: String,
    val snapshotFetchedAt: String,
) {
    /** UI-safe provenance wording for this all-reporters (territories included) comparison. */
    val scopeLabel: String
        get() = "Calculated across bundled World Bank reporting countries/economies; territories included (not a universal sovereign-country ranking)"
}

enum class RankDirection { HIGHER, LOWER }

/** Immutable in-memory lookup loaded from assets/indicator_ranks.json. */
class IndicatorRankLookup internal constructor(
    private val entries: Map<Key, IndicatorRank>,
    val source: String,
    val snapshotFetchedAt: String,
    val scope: String,
) {
    /** Returns the snapshot rank for the exact metric and calendar year, or null when absent. */
    fun rank(countryIso: String, metricId: String, year: Int): IndicatorRank? =
        entries[Key(metricId, year, countryIso.uppercase())]

    /**
     * Returns a rank only when the supplied observation still matches the bundled value.
     * This prevents a stale rank from being displayed beside a changed live value.
     */
    fun rank(countryIso: String, metricId: String, year: Int, value: Double): IndicatorRank? {
        if (!value.isFinite()) return null
        val result = rank(countryIso, metricId, year) ?: return null
        // Use an absolute tolerance: relative tolerances can hide material revisions
        // in very large GDP/population totals. Both values come from JSON doubles.
        val tolerance = 1e-9
        return result.takeIf { abs(it.value - value) <= tolerance }
    }

    fun contains(countryIso: String, metricId: String, year: Int): Boolean =
        entries.containsKey(Key(metricId, year, countryIso.uppercase()))

    internal data class Key(val metricId: String, val year: Int, val iso: String)
}

object IndicatorRanks {
    private const val ASSET = "indicator_ranks.json"
    @Volatile private var cached: IndicatorRankLookup? = null

    /** Loads the bundled lookup once per process; all parsing occurs off the UI caller's responsibility. */
    fun load(context: Context): IndicatorRankLookup {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: parse(context.applicationContext).also { cached = it }
        }
    }

    fun rank(context: Context, countryIso: String, metricId: String, year: Int): IndicatorRank? =
        load(context).rank(countryIso, metricId, year)

    fun rank(context: Context, countryIso: String, metricId: String, year: Int, value: Double): IndicatorRank? =
        load(context).rank(countryIso, metricId, year, value)

    private fun parse(context: Context): IndicatorRankLookup {
        val root = JSONObject(context.assets.open(ASSET).bufferedReader().use { it.readText() })
        val source = root.getString("source")
        val fetchedAt = root.getString("snapshotFetchedAt")
        val scope = root.getString("scope")
        val entries = HashMap<IndicatorRankLookup.Key, IndicatorRank>()
        val metrics = root.getJSONObject("metrics")
        val metricIds = metrics.keys()
        while (metricIds.hasNext()) {
            val metricId = metricIds.next()
            val metric = metrics.getJSONObject(metricId)
            val direction = when (metric.getString("direction")) {
                "lower" -> RankDirection.LOWER
                else -> RankDirection.HIGHER
            }
            val label = metric.optString("label", "rank")
            val years = metric.getJSONObject("years")
            val yearKeys = years.keys()
            while (yearKeys.hasNext()) {
                val year = yearKeys.next().toInt()
                val countries = years.getJSONObject(year.toString())
                val countryKeys = countries.keys()
                while (countryKeys.hasNext()) {
                    val iso = countryKeys.next()
                    val item = countries.getJSONObject(iso)
                    val rank = IndicatorRank(
                        countryIso = iso,
                        metricId = metricId,
                        year = year,
                        value = item.getDouble("value"),
                        rank = item.getInt("rank"),
                        denominator = item.getInt("denominator"),
                        direction = direction,
                        label = label,
                        source = source,
                        snapshotFetchedAt = fetchedAt,
                    )
                    entries[IndicatorRankLookup.Key(metricId, year, iso.uppercase())] = rank
                }
            }
        }
        return IndicatorRankLookup(entries, source, fetchedAt, scope)
    }
}
