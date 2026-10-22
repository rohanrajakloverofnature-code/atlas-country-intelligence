package com.atlas.intelligence.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.Year
import java.util.concurrent.TimeUnit

internal object AtlasParsers {
    fun parseCountries(text: String): List<Country> {
        val root = text.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it) }
        val rows = when (root) {
            is JSONArray -> root.optJSONArray(1) ?: JSONArray()
            is JSONObject -> root.optJSONArray("countries") ?: root.optJSONArray("1") ?: JSONArray()
            else -> JSONArray()
        }
        return (0 until rows.length()).mapNotNull { i ->
            val item = rows.optJSONObject(i) ?: return@mapNotNull null
            val region = value(item, "region")
            if (region.equals("Aggregates", true)) return@mapNotNull null
            val iso3 = value(item, "iso3").ifBlank { value(item, "id") }
            val iso2 = value(item, "iso2").ifBlank { value(item, "iso2Code") }
            val name = value(item, "name")
            if (iso3.isBlank() || name.isBlank()) null else Country(iso3, iso2, name, region, value(item, "capital").ifBlank { value(item, "capitalCity") }, value(item, "income").ifBlank { value(item, "incomeLevel") })
        }.distinctBy { it.iso3 }.sortedBy { it.name }
    }

    private fun value(o: JSONObject, key: String): String = when (val raw = o.opt(key)) {
        is JSONObject -> raw.optString("value", raw.optString("id", "")).trim()
        null, JSONObject.NULL -> ""
        else -> raw.toString().trim()
    }

    fun parseSeries(definition: MetricDefinition, text: String, retrievedAt: String, cached: Boolean = false): MetricSeries? {
        val root = runCatching { JSONArray(text) }.getOrNull() ?: return null
        val metadata = root.optJSONObject(0); val rows = root.optJSONArray(1) ?: return null
        val points = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val year = row.optString("date").toIntOrNull() ?: return@mapNotNull null
            val number = when (val raw = row.opt("value")) { is Number -> raw.toDouble(); is String -> raw.toDoubleOrNull(); else -> null } ?: return@mapNotNull null
            if (!number.isFinite()) null else Observation(year, number)
        }.distinctBy { it.year }.sortedBy { it.year }
        return points.takeIf { it.isNotEmpty() }?.let { MetricSeries(definition, it, metadata?.optString("lastupdated", "") ?: "", retrievedAt, cached) }
    }

    fun parseCachedSeries(definition: MetricDefinition, o: JSONObject, retrievedAt: String): MetricSeries? {
        val rows = o.optJSONArray("points") ?: return null
        val points = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val year = row.optInt("year", Int.MIN_VALUE)
            val number = when (val raw = row.opt("value")) { is Number -> raw.toDouble(); is String -> raw.toDoubleOrNull(); else -> null } ?: return@mapNotNull null
            if (year == Int.MIN_VALUE || !number.isFinite()) null else Observation(year, number)
        }.distinctBy { it.year }.sortedBy { it.year }
        return points.takeIf { it.isNotEmpty() }?.let { MetricSeries(definition, it, o.optString("lastUpdated"), retrievedAt, true) }
    }

    fun parseNews(xml: String, aliases: Set<String>): List<NewsItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val out = mutableListOf<NewsItem>(); var inItem = false; var current = ""
        var title = StringBuilder(); var link = StringBuilder(); var date = StringBuilder(); var description = StringBuilder()
        fun append(text: String) { when (current) { "title" -> title.append(text); "link" -> link.append(text); "pubdate" -> date.append(text); "description" -> description.append(text) } }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> { val tag = parser.name.lowercase(); if (tag == "item") { inItem = true; title = StringBuilder(); link = StringBuilder(); date = StringBuilder(); description = StringBuilder() }; if (inItem && tag in setOf("title", "link", "pubdate", "description")) current = tag }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) append(parser.text)
                XmlPullParser.END_TAG -> { val tag = parser.name.lowercase(); if (tag == current) current = ""; if (tag == "item" && inItem) { val t = title.toString().trim(); val l = link.toString().trim(); val hay = "$t ${description.toString()}".lowercase(); if (t.isNotBlank() && aliases.any { Regex("(?<![a-z])${Regex.escape(it)}(?![a-z])").containsMatchIn(hay) } && isSafeBbcUrl(l)) out += NewsItem(t, l, "BBC News", date.toString().trim()); inItem = false; current = "" } }
            }; parser.next()
        }
        return out
    }

    private fun isSafeBbcUrl(url: String): Boolean = runCatching { val host = URI(url).host ?: return false; URI(url).scheme.equals("https", true) && (host.equals("bbc.com", true) || host.endsWith(".bbc.com", true) || host.equals("bbc.co.uk", true) || host.endsWith(".bbc.co.uk", true)) }.getOrDefault(false)
}

class AtlasRepository(private val context: Context) {
    companion object {
        const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        const val REFRESH_TIMEOUT_MS = 35_000L
        fun indicatorUrl(base: String, country: String, indicators: String, source: Int, year: Int = Year.now().value) = "$base/country/$country/indicator/$indicators?date=2000:$year&format=json&per_page=100&source=$source"
    }
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val base = "https://api.worldbank.org/v2"; private val snapshotFile get() = File(context.filesDir, "snapshot.json"); private val countriesFile get() = File(context.filesDir, "countries.json"); private val now get() = Instant.now().toString()
    private val catalog: List<MetricDefinition> by lazy {
        val metadata = runCatching { JSONObject(context.assets.open("indicator_metadata.json").bufferedReader().use { it.readText() }).optJSONObject("indicators") }.getOrNull()
        MetricCatalog.all.map { definition ->
            val item = metadata?.optJSONObject(definition.id)
            definition.copy(sourceOrganization = item?.optString("sourceOrganization").orEmpty(), sourceNote = item?.optString("sourceNote").orEmpty())
        }
    }

    suspend fun countries(): List<Country> = withContext(Dispatchers.IO) {
        readCountriesCache()?.let { return@withContext it }
        // The bundled directory is the startup source of truth. Do not hold the first
        // screen on a countries request; an expired cache can be refreshed explicitly.
        runCatching { AtlasParsers.parseCountries(context.assets.open("countries.json").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
    }

    suspend fun dashboard(country: Country, forceRefresh: Boolean): CountryDashboard = withContext(Dispatchers.IO) {
        val stored = readDashboard(country)
        val bundled = loadSnapshotDashboard(country)
        val fallback = stored ?: bundled
        if (!forceRefresh && fallback != null) return@withContext fallback
        // Countries without an offline snapshot automatically load from the API.
        val merged = (bundled?.series.orEmpty() + stored?.series.orEmpty()).associateBy { it.definition.id }.toMutableMap()
        val received = java.util.concurrent.ConcurrentHashMap<String, MetricSeries>()
        val finished = kotlinx.coroutines.withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            coroutineScope {
                val semaphore = Semaphore(4)
                catalog.map { definition ->
                    async {
                        semaphore.withPermit {
                            try { fetchSeries(country, definition)?.let { received[definition.id] = it } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* Keep prior valid history, never invent a value. */ }
                        }
                    }
                }.awaitAll()
            }
            true
        } ?: false
        merged.putAll(received)
        val warnings = mutableListOf<String>()
        if (!finished) warnings += "Refresh time limit reached; completed requests and valid cached history were retained."
        val cachedCount = merged.values.count { it.cached }
        if (cachedCount > 0) warnings += "$cachedCount indicators use cached observations; see each metric's retrieval date."
        val absent = catalog.size - merged.size
        if (absent > 0) warnings += "$absent catalog indicators have no available observations for this country."
        if (merged.isEmpty()) warnings += "No sourced observations could be loaded. Check your connection and retry."
        val result = CountryDashboard(country, merged.values.sortedBy { it.definition.category + it.definition.title }, warnings)
        if (result.series.isNotEmpty()) atomicWrite(File(context.filesDir, "dashboard_${country.iso3}.json"), serializeDashboard(result))
        result
    }

    suspend fun news(country: Country): List<NewsItem> = withContext(Dispatchers.IO) { AtlasParsers.parseNews(get("https://feeds.bbci.co.uk/news/world/rss.xml"), countryAliases(country)).take(20) }
    private fun countryAliases(c: Country): Set<String> = buildSet { add(c.name.lowercase()); add(c.iso3.lowercase()); add(c.iso2.lowercase()); when (c.iso3.uppercase()) { "USA" -> addAll(setOf("united states", "united states of america", "america")); "GBR" -> addAll(setOf("united kingdom", "great britain", "britain", "england")); "ZAF" -> add("south africa"); "KOR" -> add("south korea"); "PRK" -> add("north korea") } }.filter { it.length > 2 }.toSet()
    private suspend fun fetchSeries(country: Country, definition: MetricDefinition): MetricSeries? { val source = if (definition.sourceName.contains("source=3")) 3 else 2; return AtlasParsers.parseSeries(definition, get(indicatorUrl(base, country.iso3, definition.id, source)), now) }
    private suspend fun get(url: String): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json, application/rss+xml, text/xml").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, exception: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(exception))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val result = runCatching { response.use { if (!it.isSuccessful) error("HTTP ${it.code}"); it.body?.string() ?: error("empty response") } }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
    private fun readCountriesCache(): List<Country>? = runCatching { if (!countriesFile.isFile) return null; val root = JSONObject(countriesFile.readText()); val fetched = Instant.parse(root.optString("fetchedAt")); if (System.currentTimeMillis() - fetched.toEpochMilli() > CACHE_TTL_MS) return null; AtlasParsers.parseCountries(root.toString()).takeIf { it.isNotEmpty() } }.getOrNull()
    private fun bodyAsRows(body: String): JSONArray = runCatching { JSONArray(body).optJSONArray(1) ?: JSONArray() }.getOrDefault(JSONArray())
    private fun loadJsonSnapshot(): JSONObject? = runCatching { JSONObject(if (snapshotFile.isFile) snapshotFile.readText() else context.assets.open("snapshot.json").bufferedReader().use { it.readText() }) }.getOrNull()
    private fun loadSnapshotDashboard(country: Country): CountryDashboard? = runCatching {
        val smallAsset = runCatching { JSONObject(context.assets.open("snapshots/${country.iso3}.json").bufferedReader().use { it.readText() }) }.getOrNull()
        val root = smallAsset ?: loadJsonSnapshot() ?: return null
        val rows = root.optJSONObject("series")?.optJSONObject(country.iso3) ?: return null
        val fetched = root.optJSONObject("provenance")?.optString("fetchedAt").orEmpty()
        val list = catalog.mapNotNull { definition -> rows.optJSONObject(definition.id)?.let { AtlasParsers.parseCachedSeries(definition, it, it.optString("retrievedAt").ifBlank { fetched }) } }
        list.takeIf { it.isNotEmpty() }?.let { CountryDashboard(country, it.sortedBy { s -> s.definition.category + s.definition.title }, listOf("Bundled official-data snapshot; use Refresh to request newer annual observations.")) }
    }.getOrNull()
    private fun readDashboard(country: Country): CountryDashboard? = runCatching { val f = File(context.filesDir, "dashboard_${country.iso3}.json"); if (!f.isFile) return null; val root = JSONObject(f.readText()); val a = root.optJSONArray("series") ?: return null; val list = (0 until a.length()).mapNotNull { i -> val o = a.optJSONObject(i) ?: return@mapNotNull null; catalog.find { it.id == o.optString("id") }?.let { AtlasParsers.parseCachedSeries(it, o, o.optString("retrievedAt")) } }; list.takeIf { it.isNotEmpty() }?.let { CountryDashboard(country, it, listOf("Saved device cache; use Refresh to request newer annual observations.")) } }.getOrNull()
    private fun serializeDashboard(d: CountryDashboard): String { val root = JSONObject().put("retrievedAt", now); val a = JSONArray(); d.series.forEach { s -> val o = JSONObject().put("id", s.definition.id).put("lastUpdated", s.lastUpdated).put("retrievedAt", s.retrievedAt); val p = JSONArray(); s.points.forEach { x -> p.put(JSONObject().put("year", x.year).put("value", x.value)) }; o.put("points", p); a.put(o) }; root.put("series", a).put("warnings", JSONArray(d.warnings)); return root.toString() }
    private fun atomicWrite(file: File, text: String) { val tmp = File(file.parentFile, file.name + ".tmp"); tmp.writeText(text); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) } }
}
