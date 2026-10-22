package com.atlas.intelligence

import com.atlas.intelligence.data.MetricSeries
import java.time.Instant

internal object FreshnessPolicy {
    const val STATISTICS_INTERVAL_MS = 24 * 60 * 60 * 1000L
    const val NEWS_INTERVAL_MS = 15 * 60 * 1000L

    fun shouldRefresh(series: List<MetricSeries>, lastAttempt: Long, now: Long): Boolean {
        // Avoid repeated requests during an outage or for permanently unavailable data.
        if (lastAttempt > 0 && now - lastAttempt < STATISTICS_INTERVAL_MS) return false
        if (series.isEmpty()) return true
        // Government releases are deliberately bundled snapshots, not refresh adapters.
        // Their historical dates must not trigger extra World Bank network requests.
        val refreshable = series.filterNot { it.definition.id.startsWith("GOV.") }
        if (refreshable.isEmpty()) return true
        return refreshable.any {
            val fetched = runCatching { Instant.parse(it.retrievedAt).toEpochMilli() }.getOrNull()
            fetched == null || now - fetched >= STATISTICS_INTERVAL_MS
        }
    }
}
