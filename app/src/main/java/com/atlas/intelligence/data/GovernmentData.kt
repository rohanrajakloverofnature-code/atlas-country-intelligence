package com.atlas.intelligence.data

import android.content.Context
import org.json.JSONObject

/** Bundled observations from national statistical and monetary authorities.
 *
 * This is intentionally separate from the World Bank catalog: the official
 * releases use different periods, units, revisions, and definitions. A series
 * may therefore contain one point, and missing history is never filled in.
 */
object GovernmentData {
    private const val ASSET_PREFIX = "government_"
    private const val ASSET_SUFFIX = ".json"

    /** Loads only the official-government series bundled for [country]. */
    fun load(context: Context, country: Country): List<MetricSeries> {
        val iso = country.iso3.uppercase()
        val file = "$ASSET_PREFIX${iso.lowercase()}$ASSET_SUFFIX"
        return runCatching {
            context.assets.open(file).bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(emptyList())
    }

    /** Human-readable coverage, useful for the dashboard's source disclosure. */
    fun coverageNote(country: Country): String = when (country.iso3.uppercase()) {
        "IND" -> "25 government-source signals from reviewed Indian government releases. Historical snapshots use release-specific periods; see each source note."
        "USA" -> "28 government-source signals from BLS, BEA and Census releases. Historical release snapshots, not live feeds; not global coverage."
        "GBR" -> "25 government-source signals from ONS releases. Exact reference periods and geographic scopes vary; historical snapshots, not global coverage."
        else -> "No bundled government-source observations for ${country.name}; World Bank data remains separate."
    }

    internal fun parse(text: String): List<MetricSeries> {
        val root = JSONObject(text)
        val retrievedAt = root.optString("retrievedAt", "")
        val array = root.optJSONArray("series") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                val pointsJson = item.optJSONArray("points")
                if (id.isBlank() || title.isBlank() || pointsJson == null) continue
                val iso = root.optString("country")
                if (iso.isBlank() || !id.startsWith("GOV.$iso.")) continue
                if (item.optString("unit").isBlank() || item.optString("sourceName").isBlank() ||
                    !item.optString("sourceUrl").startsWith("https://") || item.optString("sourceNote").isBlank()) continue
                val points = buildList {
                    for (pointIndex in 0 until pointsJson.length()) {
                        val point = pointsJson.optJSONObject(pointIndex) ?: continue
                        if (!point.has("year") || !point.has("value")) continue
                        val year = point.optInt("year", Int.MIN_VALUE)
                        val value = point.optDouble("value", Double.NaN)
                        if (year != Int.MIN_VALUE && value.isFinite()) add(Observation(year, value))
                    }
                }
                if (points.isEmpty()) continue
                val definition = MetricDefinition(
                    id = id,
                    title = title,
                    category = item.optString("category", "Government"),
                    unit = item.optString("unit"),
                    description = item.optString("description"),
                    sourceName = item.optString("sourceName"),
                    sourceUrl = item.optString("sourceUrl"),
                    sourceOrganization = item.optString("sourceOrganization"),
                    sourceNote = item.optString("sourceNote")
                )
                add(MetricSeries(definition, points, retrievedAt = item.optString("retrievedAt", retrievedAt), cached = true))
            }
        }
    }
}
