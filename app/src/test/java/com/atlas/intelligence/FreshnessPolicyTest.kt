package com.atlas.intelligence

import com.atlas.intelligence.data.MetricCatalog
import com.atlas.intelligence.data.MetricSeries
import com.atlas.intelligence.data.Observation
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FreshnessPolicyTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z").toEpochMilli()
    private fun series(at: String) = listOf(MetricSeries(MetricCatalog.all.first(), listOf(Observation(2024, 1.0)), retrievedAt = at, cached = true))
    @Test fun freshCachedObservationsDoNotTriggerNetwork() {
        assertFalse(FreshnessPolicy.shouldRefresh(series("2026-10-22T11:00:00Z"), 0, now))
    }
    @Test fun observationsOlderThanOneDayRefreshOnUse() {
        assertTrue(FreshnessPolicy.shouldRefresh(series("2026-10-20T11:00:00Z"), 0, now))
    }
    @Test fun recentFailedAttemptIsThrottled() {
        assertFalse(FreshnessPolicy.shouldRefresh(series("2026-10-01T11:00:00Z"), now - 60_000, now))
    }
    @Test fun absentOrUnknownRetrievalTimeNeedsRefresh() {
        assertTrue(FreshnessPolicy.shouldRefresh(emptyList(), 0, now))
        assertTrue(FreshnessPolicy.shouldRefresh(series(""), 0, now))
    }
}
