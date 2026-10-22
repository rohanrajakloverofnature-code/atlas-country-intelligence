package com.atlas.intelligence.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.atlas.intelligence.data.IndicatorRank
import com.atlas.intelligence.data.IndicatorRanks
import com.atlas.intelligence.data.MetricSeries
import com.atlas.intelligence.data.RankDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Never shows a snapshot rank against a revised or differently dated observation. */
@Composable
fun RankLine(series: MetricSeries, countryIso: String?) {
    val point = series.points.maxByOrNull { it.year } ?: return
    if (countryIso == null || series.definition.id.startsWith("GOV.")) return
    val context = LocalContext.current.applicationContext
    val rank by produceState<IndicatorRank?>(null, countryIso, series.definition.id, point) {
        value = withContext(Dispatchers.IO) {
            runCatching { IndicatorRanks.rank(context, countryIso, series.definition.id, point.year, point.value) }.getOrNull()
        }
    }
    rank?.let { result ->
        Column {
            Text("Calculated rank ${result.rank} of ${result.denominator} · ${result.year}", color = Color(0xFFB6ABFF), fontSize = 11.sp)
            Text("Reporting economies · ${if (result.direction == RankDirection.HIGHER) "highest" else "lowest"} first", color = Color(0xFF8EA5BF), fontSize = 10.sp)
            Text("Bundled World Bank snapshot · not an official published rank", color = Color(0xFF8EA5BF), fontSize = 9.sp)
        }
    }
}
