package com.atlas.intelligence.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.StringReader
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser

class AtlasRepository(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val base = "https://api.worldbank.org/v2"
    private val snapshotFile get() = File(context.filesDir, "snapshot.json")
    private val countriesFile get() = File(context.filesDir, "countries.json")
    private val now get() = Instant.now().toString()

    suspend fun countries(): List<Country> = withContext(Dispatchers.IO) {
        readCountries(countriesFile)?.let { return@withContext it }
        val bundled = runCatching { readCountriesFromJson(context.assets.open("countries.json").bufferedReader().use { it.readText() }) }.getOrNull()
        try {
            val body = get("$base/country?format=json&per_page=400")
            val list = parseCountries(body)
            if (list.isNotEmpty()) { atomicWrite(countriesFile, body); return@withContext list }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* bundled fallback below */ }
        bundled ?: emptyList()
    }

    suspend fun dashboard(country: Country, forceRefresh: Boolean): CountryDashboard = withContext(Dispatchers.IO) {
        if (!forceRefresh) readDashboard(country)?.let { return@withContext it }
        val warnings = mutableListOf<String>()
        val result = mutableListOf<MetricSeries>()
        val snapshot = loadJsonSnapshot()
        val retrieved = now
        for (definition in MetricCatalog.all) {
            val cached = snapshot?.optJSONObject("series")?.optJSONObject(country.iso3)?.optJSONObject(definition.id)
            if (cached != null) result += parseSeries(definition, cached, retrieved, true)
        }
        val missing = MetricCatalog.all.filter { definition -> result.none { it.definition.id == definition.id } }
        for (definition in missing) {
            try {
                val source = if (definition.sourceName.contains("WGI")) 3 else 2
                val url = "$base/country/${country.iso3}/indicator/${definition.id}?date=2000:${java.time.Year.now().value}&format=json&per_page=100&source=$source"
                val body = get(url)
                val parsed = parseApiSeries(definition, body, retrieved)
                if (parsed != null) result += parsed
            } catch (e: CancellationException) { throw e } catch (e: Exception) { warnings += "${definition.title}: unavailable (${e.message ?: "request failed"})" }
        }
        if (result.isEmpty()) warnings += "No verified observations were available; no estimates were generated."
        val dashboard = CountryDashboard(country, result.sortedBy { it.definition.category + it.definition.title }, warnings.distinct())
        if (result.isNotEmpty()) atomicWrite(File(context.filesDir, "dashboard_${country.iso3}.json"), serializeDashboard(dashboard))
        dashboard
    }

    suspend fun news(country: Country): List<NewsItem> = withContext(Dispatchers.IO) {
        val aliases = setOf(country.name.lowercase(), country.iso3.lowercase(), country.iso2.lowercase()).filter { it.length > 2 }
        try {
            val xml = get("https://feeds.bbci.co.uk/news/world/rss.xml")
            parseNews(xml, aliases).take(20)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    private fun get(url: String): String {
        val response = client.newCall(Request.Builder().url(url).header("Accept", "application/json, application/rss+xml, text/xml").build()).execute()
        response.use { if (!it.isSuccessful) error("HTTP ${it.code}"); return it.body?.string() ?: error("empty response") }
    }

    private fun readCountries(file: File): List<Country>? = if (file.isFile) runCatching { parseCountries(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() } else null
    private fun readCountriesFromJson(text: String): List<Country> = parseCountries(text)
    private fun parseCountries(text: String): List<Country> {
        val root = JSONObject(text); val array = if (root.has("countries")) root.getJSONArray("countries") else root.optJSONArray("1") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val region = o.optString("region"); if (region == "Aggregates") return@mapNotNull null
            Country(o.optString("iso3", o.optString("id")), o.optString("iso2", o.optString("iso2Code")), o.optString("name"), region, o.optString("capital", o.optString("capitalCity")), o.optString("income"))
        }.filter { it.iso3.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.iso3 }.sortedBy { it.name }
    }

    private fun loadJsonSnapshot(): JSONObject? = runCatching {
        val text = if (snapshotFile.isFile) snapshotFile.readText() else context.assets.open("snapshot.json").bufferedReader().use { it.readText() }
        JSONObject(text)
    }.getOrNull()

    private fun parseApiSeries(definition: MetricDefinition, text: String, retrieved: String): MetricSeries? {
        val root = JSONArray(text); if (root.length() < 2 || root.opt(1) !is JSONArray) return null
        val meta = root.optJSONObject(0); val arr = root.getJSONArray(1); val points = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { o -> val v = o.optDouble("value", Double.NaN); if (v.isNaN()) null else Observation(o.optString("date").toIntOrNull() ?: return@let null, v) } }.sortedBy { it.year }
        return points.takeIf { it.isNotEmpty() }?.let { MetricSeries(definition, it, meta?.optString("lastupdated", "") ?: "", retrieved, false) }
    }
    private fun parseSeries(definition: MetricDefinition, o: JSONObject, retrieved: String, cached: Boolean) = MetricSeries(definition, (0 until o.optJSONArray("points").length()).mapNotNull { i -> o.optJSONArray("points").optJSONObject(i)?.let { Observation(it.optInt("year"), it.optDouble("value")) } }, o.optString("lastUpdated"), retrieved, cached)

    private fun readDashboard(country: Country): CountryDashboard? = runCatching {
        val f = File(context.filesDir, "dashboard_${country.iso3}.json"); if (!f.isFile) return@runCatching null
        val root = JSONObject(f.readText()); val a = root.getJSONArray("series"); val list = (0 until a.length()).mapNotNull { i -> val o = a.optJSONObject(i) ?: return@mapNotNull null; val d = MetricCatalog.all.find { it.id == o.optString("id") } ?: return@mapNotNull null; parseSeries(d, o, o.optString("retrievedAt"), true) }
        CountryDashboard(country, list, root.optJSONArray("warnings")?.let { (0 until it.length()).map { j -> it.optString(j) } } ?: emptyList())
    }.getOrNull()
    private fun serializeDashboard(d: CountryDashboard): String { val root=JSONObject().put("retrievedAt",now); val a=JSONArray(); d.series.forEach { s -> val o=JSONObject().put("id",s.definition.id).put("lastUpdated",s.lastUpdated).put("retrievedAt",s.retrievedAt); val p=JSONArray(); s.points.forEach { x -> p.put(JSONObject().put("year",x.year).put("value",x.value)) }; o.put("points",p); a.put(o) }; root.put("series",a).put("warnings",JSONArray(d.warnings)); return root.toString() }
    private fun atomicWrite(file: File, text: String) { val tmp=File(file.parentFile, file.name+".tmp"); tmp.writeText(text); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) } }

    private fun parseNews(xml: String, aliases: Set<String>): List<NewsItem> {
        val p=Xml.newPullParser(); p.setInput(StringReader(xml)); val out=mutableListOf<NewsItem>(); var title=""; var link=""; var date=""; var desc=""; var inItem=false; var tag=""
        while (p.eventType != XmlPullParser.END_DOCUMENT) { when(p.eventType) { XmlPullParser.START_TAG -> { tag=p.name.lowercase(); if(tag=="item") {inItem=true; title="";link="";date="";desc=""} }; XmlPullParser.TEXT -> if(inItem) when(tag){"title"->title+=p.text;"link"->link+=p.text;"pubdate"->date+=p.text;"description"->desc+=p.text}; XmlPullParser.END_TAG -> if(p.name.equals("item",true)&&inItem){ val hay="$title $desc".lowercase(); if(aliases.any { hay.contains(it) }&&title.isNotBlank()&&link.isNotBlank()) out+=NewsItem(title.trim(),link.trim(),"BBC News",date.trim()); inItem=false; tag="" } }; p.next() }; return out
    }
}
