package com.atlas.intelligence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import com.atlas.intelligence.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlas.intelligence.AtlasState
import com.atlas.intelligence.AtlasViewModel
import com.atlas.intelligence.data.*
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.max

// Restrained ink surfaces, luminous violet accents, and warm comparison colors.
private val Ink = Color(0xFF0C0A14)
private val Panel = Color(0xFF171321)
private val Panel2 = Color(0xFF221B32)
private val Accent = Color(0xFFC4ADFF)
private val Peach = Color(0xFFFFC6A0)
private val Muted = Color(0xFFADA5BB)
private val Outline = Color(0xFF342C45)

@Composable
fun AtlasApp(viewModel: AtlasViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MetricSeries?>(null) }
    var trust by remember { mutableStateOf(false) }
    val tab = state.tab.ifBlank { "Overview" }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, surfaceVariant = Panel2,
        primary = Accent, onPrimary = Color(0xFF261347), primaryContainer = Color(0xFF3B285B),
        onPrimaryContainer = Color(0xFFEBDDFF), secondary = Peach,
        secondaryContainer = Panel2, onSecondaryContainer = Accent,
        onBackground = Color(0xFFF6F2FC), onSurface = Color(0xFFF6F2FC),
        onSurfaceVariant = Muted, outline = Outline
    )) {
        Scaffold(
            containerColor = Ink,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = { BottomNav(tab) { viewModel.selectTab(it) } }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TopBar(state.selected, state.loading, { picker = true }, { trust = true }, viewModel::refresh) { viewModel.selectTab("Comparison") }
                state.error?.let { ErrorBanner(it, state.dashboard?.series?.any { series -> series.cached } == true, viewModel::refresh) }
                when (tab) {
                    "Indicators" -> IndicatorsScreen(state, { detail = it })
                    "Briefing" -> BriefingScreen(state, viewModel)
                    "Watchlist" -> WatchlistScreen(state, viewModel) { picker = true }
                    "Comparison" -> ComparisonScreen(state, viewModel) { detail = it }
                    else -> OverviewScreen(state, { detail = it }, viewModel)
                }
            }
        }
        if (picker) CountryPicker(state, viewModel) { picker = false }
        detail?.let { MetricDetail(it, state.selected) { detail = null } }
        if (trust) TrustSheet { trust = false }
    }
}

@Composable
private fun TopBar(country: Country?, loading: Boolean, choose: () -> Unit, trust: () -> Unit, refresh: () -> Unit, compare: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_atlas), null, Modifier.size(40.dp), tint = Color.Unspecified)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ATLAS", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, maxLines = 1)
                Text("Country intelligence", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = trust, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Info, "About data", tint = Muted, modifier = Modifier.size(21.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = choose, color = Panel2, border = BorderStroke(1.dp, Outline), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(country?.iso3 ?: "—", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(" / Country", Modifier.weight(1f).padding(start = 4.dp), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ExpandMore, "Choose country", tint = Accent, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = refresh, enabled = !loading, modifier = Modifier.size(48.dp).background(Panel, RoundedCornerShape(16.dp))) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Refreshing data" }, color = Accent, strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Refresh", tint = Accent, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = compare, modifier = Modifier.size(48.dp).background(Panel, RoundedCornerShape(16.dp))) {
                Icon(Icons.Default.CompareArrows, "Compare countries", tint = Accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, cached: Boolean, retry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Color(0xFF33251C), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, "Warning", tint = Color(0xFFFFC46B))
            Text(if (cached) "Showing cached data · $message" else message, Modifier.weight(1f).padding(horizontal = 10.dp), color = Color(0xFFFFD9A0), fontSize = 12.sp)
            TextButton(onClick = retry) { Text("Retry", color = Accent) }
        }
    }
}

@Composable
private fun OverviewScreen(state: AtlasState, open: (MetricSeries) -> Unit, vm: AtlasViewModel) {
    val dashboard = state.dashboard
    if (dashboard == null) {
        EmptyState(if (state.loading) "Loading country intelligence" else "Country data unavailable", if (state.loading) "Fetching sourced annual observations" else "No dashboard was returned. Retry to try again.", state.loading, if (!state.loading) vm::refresh else null)
        return
    }
    val gdp = dashboard.series.firstOrNull { it.definition.id == "NY.GDP.MKTP.CD" }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Hero(dashboard.country) }
        if (dashboard.warnings.isNotEmpty()) item { DataNotes(dashboard.warnings) }
        item { SectionTitle("Signal overview", "Annual observations · not live") }
        gdp?.let { item { GdpCard(it, open, state.selected?.iso3) } }
        item { Text("Key indicators", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        val keyIds = listOf("NY.GDP.MKTP.KD.ZG", "FP.CPI.TOTL.ZG", "BX.KLT.DINV.WD.GD.ZS", "SP.POP.TOTL")
        val key = keyIds.mapNotNull { id -> dashboard.series.firstOrNull { it.definition.id == id } }
        items(key.chunked(2)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { MetricCard(it, open, Modifier.weight(1f), state.selected?.iso3) }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
        item { TextButton(onClick = { vm.selectTab("Indicators") }) { Text("Explore full indicator catalog  →", color = Accent) } }
    }
}

@Composable
private fun Hero(country: Country) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF2B1E42), Panel))).padding(20.dp)) {
            Text("COUNTRY PROFILE", color = Accent, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(country.name, color = Color.White, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(listOf(country.capital, country.region).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                GlobeGraphic(Modifier.size(76.dp).semantics { contentDescription = "Decorative globe illustration" })
            }
            if (country.income.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(color = Accent.copy(alpha = .09f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, null, Modifier.size(13.dp), tint = Accent)
                        Text(country.income, Modifier.padding(start = 6.dp), color = Accent, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Outline)
            Spacer(Modifier.height(14.dp))
            Text("A measured view of the country", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Official annual indicators. Transparent sources. Not forecasts or live market quotes.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun GlobeGraphic(modifier: Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * .39f
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF62458A), Color(0xFF22192F))), center = center, radius = radius)
        drawCircle(color = Accent.copy(.7f), center = center, radius = radius, style = Stroke(1.dp.toPx()))
        drawOval(color = Accent.copy(.4f), topLeft = Offset(center.x - radius * .42f, center.y - radius), size = Size(radius * .84f, radius * 2), style = Stroke(.7.dp.toPx()))
        drawOval(color = Accent.copy(.4f), topLeft = Offset(center.x - radius, center.y - radius * .42f), size = Size(radius * 2, radius * .84f), style = Stroke(.7.dp.toPx()))
        drawLine(Accent.copy(.25f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), .7.dp.toPx())
        drawArc(color = Peach, startAngle = 210f, sweepAngle = 110f, useCenter = false, topLeft = Offset(center.x - radius * 1.17f, center.y - radius * .66f), size = Size(radius * 2.34f, radius * 1.32f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Peach, 2.5.dp.toPx(), Offset(center.x + radius * .83f, center.y - radius * .46f))
    }
}

@Composable
private fun DataNotes(notes: List<String>) {
    var expanded by remember(notes) { mutableStateOf(false) }
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = Accent)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Data notes", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${notes.size} ${if (notes.size == 1) "note" else "notes"} · tap to ${if (expanded) "hide" else "review"}", color = Muted, fontSize = 11.sp)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Hide data notes" else "Show data notes", tint = Accent)
            }
            if (expanded) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notes.forEach { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) { Column { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) } }

@Composable
private fun MetricCard(s: MetricSeries, open: (MetricSeries) -> Unit, modifier: Modifier = Modifier, countryIso: String? = null) {
    Surface(onClick = { open(s) }, color = Panel, border = BorderStroke(1.dp, Outline), shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(s.definition.title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open metric", tint = Accent, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(s.points.lastOrNull()?.value?.displayValue() ?: "No observation", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${s.definition.unit} · ${s.points.lastOrNull()?.year ?: "—"}", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp)); Sparkline(s.points, s.definition.unit, Modifier.fillMaxWidth().height(64.dp))
            if (s.definition.category.equals("Government", true) || s.definition.category.equals("Governance", true)) Text(s.definition.sourceNote.ifBlank { "Government measure · source period supplied by provider" }, color = Muted, fontSize = 10.sp)
            RankLine(s, countryIso)
            SourceLine(s.definition.sourceName, s.lastUpdated, s.retrievedAt, s.cached, compact = true)
        }
    }
}

@Composable
private fun GdpCard(s: MetricSeries, open: (MetricSeries) -> Unit, countryIso: String? = null) {
    Surface(onClick = { open(s) }, color = Panel, border = BorderStroke(1.dp, Outline), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { Row { Column(Modifier.weight(1f)) { Text("GDP", color = Color.White, fontWeight = FontWeight.Bold); Text(s.definition.unit, color = Muted, fontSize = 12.sp) }; Text("View detail →", color = Accent, fontSize = 12.sp) }; Spacer(Modifier.height(12.dp)); InteractiveChart(listOf(ChartSeries("GDP", s.points, Accent)), Modifier.fillMaxWidth().height(178.dp), s.definition.unit, showLabels = true); Text("All available annual observations · gaps are not interpolated", color = Muted, fontSize = 11.sp); RankLine(s, countryIso); SourceLine(s.definition.sourceName, s.lastUpdated, s.retrievedAt, s.cached, compact = true) }
    }
}

@Composable
private fun SourceLine(source: String, updated: String = "", retrieved: String = "", cached: Boolean = false, compact: Boolean = false) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("Source: $source", color = Muted, fontSize = 10.sp)
        val timing = buildString {
            if (updated.isNotBlank()) append("Dataset updated: ${if (compact) compactTimestamp(updated) else updated}")
            if (retrieved.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Retrieved: ${if (compact) compactTimestamp(retrieved) else retrieved}") }
            if (cached) { if (isNotEmpty()) append(" · "); append("cached snapshot") }
        }
        if (timing.isNotBlank()) Text(timing, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun IndicatorsScreen(state: AtlasState, open: (MetricSeries) -> Unit) {
    val categories = remember { (MetricCatalog.all.map { it.category } + "Government").distinct() }
    var category by remember(state.selected?.iso3) { mutableStateOf("All") }
    val series = state.dashboard?.series.orEmpty().filter { category == "All" || it.definition.category == category }
    val availableIds = state.dashboard?.series.orEmpty().map { it.definition.id }.toSet()
    val missing = MetricCatalog.all.filter { (category == "All" || it.category == category) && it.id !in availableIds }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Indicators", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Explore sourced annual measures", color = Muted)
            Text("Government indicators are reported national snapshots; coverage varies by source.", color = Muted, fontSize = 12.sp)
            Text("${availableIds.count { id -> MetricCatalog.all.any { it.id == id }} } of ${MetricCatalog.all.size} World Bank indicators have observations · ${availableIds.count { id -> MetricCatalog.all.none { it.id == id }} } government indicators", color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + categories).forEach { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }
        items(series, key = { it.definition.id }) { MetricCard(it, open, Modifier.fillMaxWidth(), state.selected?.iso3) }
        if (category == "Government" && series.isEmpty() && !state.loading) item { EmptyState("No government coverage", "This country has no sourced Government observations in the current dataset.", false) }
        if (missing.isNotEmpty() && !state.loading) {
            item { SectionTitle("Coverage gaps", "Unavailable is not zero · try Refresh for newer coverage") }
            items(missing, key = { "missing_${it.id}" }) { definition ->
                Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(definition.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("No sourced observations in the current dataset/cache", color = Muted, fontSize = 12.sp)
                        Text("${definition.id} · ${definition.sourceName}", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefingScreen(state: AtlasState, vm: AtlasViewModel) { val uri = LocalUriHandler.current; LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Text("Briefing", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Reported coverage · not independently verified", color = Muted) }; state.newsError?.let { item { ErrorBanner(it, false, vm::refresh) } }; if (state.newsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }; if (!state.newsLoading && state.news.isEmpty()) item { EmptyState("No briefing available", "No sourced publisher items were returned for this country", false) }; items(state.news) { n -> Surface(onClick = { runCatching { uri.openUri(n.url) } }, color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(n.publisher.uppercase(Locale.ROOT), color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(n.title, color = Color.White, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Text(n.publishedAt, color = Muted, fontSize = 12.sp); SourceLine(n.publisher) } } } } }

@Composable
private fun WatchlistScreen(state: AtlasState, vm: AtlasViewModel, add: () -> Unit) { val watched = state.countries.filter { it.iso3 in state.watchlist }; LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Watchlist", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Your saved country lens", color = Muted) }; IconButton(onClick = add) { Icon(Icons.Default.Add, "Add country", tint = Accent) } } }; if (watched.isEmpty()) item { EmptyState("Your watchlist is empty", "Save countries from the picker to keep them close", false) }; items(watched) { c -> Surface(onClick = { vm.selectCountry(c); vm.selectTab("Overview") }, color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(c.name, color = Color.White, fontWeight = FontWeight.SemiBold); Text("${c.iso3} · ${c.region}", color = Muted, fontSize = 12.sp) }; IconButton(onClick = { vm.toggleWatchlist(c) }) { Icon(Icons.Default.Star, "Remove from watchlist", tint = Accent) } } } } } }

@Composable
private fun ComparisonScreen(state: AtlasState, vm: AtlasViewModel, open: (MetricSeries) -> Unit) {
    var query by remember { mutableStateOf("") }
    var metricId by remember { mutableStateOf("NY.GDP.MKTP.CD") }
    var range by remember { mutableStateOf("ALL") }
    val candidates = state.countries.filter { it.name.contains(query, true) || it.iso3.contains(query, true) }
    val selected = state.compareCountries
    val definitions = MetricCatalog.all
    val definition = definitions.first { it.id == metricId }
    val series = selected.map { country -> country to state.comparison[country.iso3]?.series?.firstOrNull { it.definition.id == metricId && it.definition.unit == definition.unit } }
    val latestYear = series.flatMap { it.second?.points.orEmpty() }.maxOfOrNull { it.year }
    val earliestYear = latestYear?.minus(if (range == "5Y") 4 else 9)
    val shown = series.map { (country, metric) -> country to metric?.points.orEmpty().filter { range == "ALL" || earliestYear == null || it.year >= earliestYear } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Compare countries", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Overlay the same World Bank indicator on one shared scale", color = Muted, fontSize = 13.sp) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search full country directory", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { Text("Selected ${selected.size} of 3", color = Accent, fontSize = 12.sp); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { selected.forEach { c -> InputChip(selected = true, onClick = { vm.removeComparisonCountry(c) }, label = { Text(c.name) }, trailingIcon = { Icon(Icons.Default.Close, "Remove ${c.name}", Modifier.size(16.dp)) }) } } }
        if (selected.size < 3) item { candidates.take(8).forEach { c -> if (c.iso3 !in selected.map { it.iso3 }) ListItem(headlineContent = { Text(c.name, color = Color.White) }, supportingContent = { Text("${c.iso3} · ${c.region}", color = Muted) }, trailingContent = { IconButton(onClick = { vm.addComparisonCountry(c) }) { Icon(Icons.Default.Add, "Add ${c.name}", tint = Accent) } }, colors = ListItemDefaults.colors(containerColor = Panel)) } }
        if (selected.size < 2) item { EmptyState("Select at least two countries", "Comparison loads independently and never changes your dashboard or watchlist.", false) }
        if (state.comparisonLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }
        state.comparisonError?.let { item { ErrorBanner(it, false) { vm.setComparisonCountries(selected) } } }
        if (selected.size >= 2 && !state.comparisonLoading) {
            item { Text("Metric", color = Color.White, fontWeight = FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { definitions.forEach { d -> FilterChip(selected = metricId == d.id, onClick = { metricId = d.id }, label = { Text(d.title) }) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("5Y", "10Y", "ALL").forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r) }) } } }
            item { Text("${definition.title} · ${definition.unit}", color = Color.White, fontWeight = FontWeight.Bold); Text("Shared value scale; no per-country normalization", color = Muted, fontSize = 12.sp) }
            item { ComparisonChart(shown, definition.unit, Modifier.fillMaxWidth().height(250.dp)) }
            val common = commonObservationYears(shown.map { it.second })
            item { Text(if (common.isEmpty()) "No optional latest common year is available" else "Latest common year: ${common.last()}", color = Muted, fontSize = 12.sp) }
            items(shown) { (country, points) -> val last = points.lastOrNull(); val original = series.firstOrNull { it.first.iso3 == country.iso3 }?.second; Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(country.name, color = Color.White, fontWeight = FontWeight.SemiBold); Text(if (last == null) "No matching observations in this range" else "Latest: ${last.value.exactValue()} ${definition.unit} · ${last.year}", color = Accent); common.lastOrNull()?.let { year -> points.firstOrNull { it.year == year }?.let { Text("Common year $year: ${it.value.exactValue()} ${definition.unit}", color = Muted, fontSize = 12.sp) } }; original?.let { RankLine(it.copy(points = points), country.iso3); SourceLine(it.definition.sourceName, it.lastUpdated, it.retrievedAt, it.cached, compact = true) } } } }
        }
    }
}

@Composable
private fun ComparisonChart(series: List<Pair<Country, List<Observation>>>, unit: String, modifier: Modifier) {
    val colors = listOf(Accent, Peach, Color(0xFFDBA7D9))
    InteractiveChart(
        series = series.mapIndexed { index, (country, points) -> ChartSeries(country.name, points, colors[index % colors.size]) },
        modifier = modifier,
        unit = unit,
        axes = true,
        showLabels = true,
        instruction = "Tap or drag horizontally to inspect the same calendar year; missing observations stay missing"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPicker(state: AtlasState, vm: AtlasViewModel, close: () -> Unit) {
    var query by remember { mutableStateOf(state.query) }
    val countries = state.countries.filter { it.name.contains(query, true) || it.iso3.contains(query, true) }
    ModalBottomSheet(onDismissRequest = close, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Choose country", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(query, { query = it; vm.setQuery(it) }, Modifier.fillMaxWidth(), placeholder = { Text("Search countries", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (countries.isEmpty()) item { Text("No matching countries", color = Muted, modifier = Modifier.padding(16.dp)) }
                items(countries, key = { it.iso3 }) { country ->
                    ListItem(
                        headlineContent = { Text(country.name, color = Color.White) },
                        supportingContent = { Text("${country.iso3} · ${country.region}", color = Muted) },
                        trailingContent = {
                            IconButton(onClick = { vm.toggleWatchlist(country) }) {
                                Icon(if (country.iso3 in state.watchlist) Icons.Default.Star else Icons.Default.StarBorder, if (country.iso3 in state.watchlist) "Remove from watchlist" else "Add to watchlist", tint = if (country.iso3 in state.watchlist) Accent else Muted)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Panel),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.selectCountry(country); close() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricDetail(s: MetricSeries, country: Country?, close: () -> Unit) {
    val uri = LocalUriHandler.current
    var range by remember { mutableStateOf("ALL") }
    val shown = filtered(s.points, range)
    ModalBottomSheet(onDismissRequest = close, containerColor = Panel) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(s.definition.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(s.definition.description, color = Muted, fontSize = 13.sp)
                SourceLine(s.definition.sourceName, s.lastUpdated, s.retrievedAt, s.cached)
                RankLine(s, country?.iso3)
                if (s.definition.sourceOrganization.isNotBlank()) Text("Original providers: ${s.definition.sourceOrganization}", color = Muted, fontSize = 12.sp)
                if (s.definition.sourceNote.isNotBlank()) Text(s.definition.sourceNote, color = Muted, fontSize = 12.sp)
                Text("Indicator: ${s.definition.id} · ${s.definition.unit}", color = Muted, fontSize = 12.sp)
                TextButton(onClick = { runCatching { uri.openUri(s.definition.sourceUrl) } }) { Text("Open official source ↗", color = Accent) }
                country?.let { c ->
                    val source = if (s.definition.sourceName.contains("WGI")) 3 else 2
                    val apiUrl = "https://api.worldbank.org/v2/country/${c.iso3}/indicator/${s.definition.id}?format=json&source=$source&date=2000:${java.time.Year.now().value}&per_page=100"
                    if (!s.definition.category.equals("Governance", true) && !s.definition.category.equals("Government", true)) {
                        TextButton(onClick = { runCatching { uri.openUri(apiUrl) } }) { Text("Inspect source API data ↗", color = Accent) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("5Y", "10Y", "ALL").forEach { FilterChip(selected = range == it, onClick = { range = it }, label = { Text(it) }) }
                }
            }
            item { InteractiveChart(listOf(ChartSeries(s.definition.title, shown, Accent)), Modifier.fillMaxWidth().height(238.dp), s.definition.unit, axes = true, showLabels = true) }
            item {
                Text("Reported observations", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${s.definition.unit} · as supplied by the source; missing years are not filled", color = Muted, fontSize = 12.sp)
            }
            items(shown.asReversed()) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(p.year.toString(), color = Muted)
                    Text("${p.value.exactValue()} ${s.definition.unit}", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TrustSheet(close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("How to read ATLAS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("India and the full World Bank directory are supported. Country coverage depends on each provider's available observations.", color = Muted)
            Text("Updates: opening a country or returning to the app checks whether cached statistics are over 24 hours old. Older data refreshes while the saved dashboard stays visible. Use Refresh to check immediately. Briefings refresh on country selection and after 15 minutes when returning to the app.", color = Muted, fontSize = 13.sp)
            Text("This is an on-use freshness check, not a background alert service. New observations appear only after the official source publishes them; annual data can lag. Observation year is not retrieval date.", color = Muted, fontSize = 13.sp)
            Text("Missing years stay missing. Market statistics are annual measures, not live index quotes. Governance figures are estimates, not current officeholder lists.", color = Muted, fontSize = 13.sp)
            Text("Briefings are publisher-reported and not independently verified here.", color = Muted, fontSize = 13.sp)
            TextButton(onClick = close) { Text("Understood", color = Accent) }
        }
    }
}

@Composable
private fun BottomNav(tab: String, select: (String) -> Unit) {
    Column {
        HorizontalDivider(color = Outline)
        NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
            listOf("Overview" to Icons.Default.Home, "Indicators" to Icons.Default.Insights, "Briefing" to Icons.Default.Article, "Watchlist" to Icons.Default.Star, "Comparison" to Icons.Default.CompareArrows).forEach { (name, icon) ->
                NavigationBarItem(
                    selected = tab == name, onClick = { select(name) },
                    icon = { Icon(icon, name, Modifier.size(22.dp)) },
                    label = { Text(when (name) { "Overview" -> "Home"; "Indicators" -> "Explore"; "Briefing" -> "News"; "Watchlist" -> "Saved"; else -> "Compare" }, modifier = Modifier.requiredWidth(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, letterSpacing = 0.sp, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, fontWeight = if (tab == name) FontWeight.SemiBold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Color(0xFF35264D), unselectedIconColor = Muted, unselectedTextColor = Muted)
                )
            }
        }
    }
}

@Composable private fun EmptyState(title: String, body: String, loading: Boolean, retry: (() -> Unit)? = null) { Box(Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { if (loading) CircularProgressIndicator(color = Accent); Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp)); Text(body, color = Muted, fontSize = 12.sp); if (retry != null) TextButton(onClick = retry) { Text("Retry", color = Accent) } } } }

@Composable
private fun Sparkline(points: List<Observation>, unit: String, modifier: Modifier) {
    InteractiveChart(
        listOf(ChartSeries("Value", points, Accent)), modifier, unit, axes = false,
        instruction = "Tap or drag horizontally to inspect a year and exact value"
    )
}

private fun compactTimestamp(value: String): String = value.replace("T", " ").take(10)

private fun filtered(points: List<Observation>, range: String): List<Observation> { if (range == "ALL" || points.isEmpty()) return points; val last = points.maxOf { it.year }; val years = if (range == "5Y") 5 else 10; return points.filter { it.year >= last - years + 1 } }
private fun Double.exactValue(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
private fun Double.displayValue(): String {
    val magnitude = kotlin.math.abs(this)
    return when {
        magnitude >= 1e12 -> String.format(Locale.US, "%.2fT", this / 1e12)
        magnitude >= 1e9 -> String.format(Locale.US, "%.2fB", this / 1e9)
        magnitude >= 1e6 -> String.format(Locale.US, "%.2fM", this / 1e6)
        magnitude >= 1e3 -> String.format(Locale.US, "%.1fK", this / 1e3)
        else -> String.format(Locale.US, "%.2f", this)
    }
}
