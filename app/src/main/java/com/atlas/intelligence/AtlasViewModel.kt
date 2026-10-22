package com.atlas.intelligence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.intelligence.data.AtlasRepository
import com.atlas.intelligence.data.Country
import com.atlas.intelligence.data.CountryDashboard
import com.atlas.intelligence.data.NewsItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state retains the last valid dashboard during refresh, never during a country switch. */
data class AtlasState(
    val countries: List<Country> = emptyList(),
    val selected: Country? = null,
    val dashboard: CountryDashboard? = null,
    val news: List<NewsItem> = emptyList(),
    val loading: Boolean = true,
    val newsLoading: Boolean = false,
    val error: String? = null,
    val newsError: String? = null,
    val query: String = "",
    val watchlist: Set<String> = setOf("IND", "USA", "CHN"),
    val tab: String = "Overview",
    val compareCountries: List<Country> = emptyList(),
    val comparison: Map<String, CountryDashboard> = emptyMap(),
    val comparisonLoading: Boolean = false,
    val comparisonError: String? = null
)

class AtlasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AtlasRepository(application)
    private val preferences = application.getSharedPreferences("atlas_preferences", 0)
    private val _state = MutableStateFlow(AtlasState(watchlist = preferences.getStringSet("watchlist", setOf("IND", "USA", "CHN"))!!.toSet()))
    val state: StateFlow<AtlasState> = _state.asStateFlow()
    private var dashboardJob: Job? = null
    private var newsJob: Job? = null
    private var newsRequestedAt = 0L
    private var comparisonJob: Job? = null
    private var comparisonGeneration = 0L

    init { loadCountries() }

    private fun loadCountries() {
        viewModelScope.launch {
            try {
                val countries = repository.countries()
                require(countries.isNotEmpty()) { "The country directory is unavailable. Please retry." }
                _state.update { it.copy(countries = countries, error = null) }
                val last = preferences.getString("country", "IND")
                selectCountry(countries.firstOrNull { it.iso3 == last } ?: countries.first())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.update { it.copy(loading = false, error = "Unable to load countries. Check your connection and retry.") }
            }
        }
    }

    fun selectCountry(country: Country) {
        dashboardJob?.cancel()
        newsJob?.cancel()
        preferences.edit().putString("country", country.iso3).apply()
        _state.update { it.copy(selected = country, dashboard = null, news = emptyList(), error = null, newsError = null, loading = true, newsLoading = true, query = "") }
        loadDashboard(country, false)
        loadNews(country)
    }

    fun refresh() {
        val selected = _state.value.selected
        if (selected == null) { loadCountries(); return }
        dashboardJob?.cancel()
        newsJob?.cancel()
        _state.update { it.copy(loading = true, error = null, newsError = null, newsLoading = true) }
        loadDashboard(selected, true)
        loadNews(selected)
    }

    /** Called on app resume; no background service or notification permission needed. */
    fun refreshIfStale() {
        val current = _state.value
        val country = current.selected ?: return
        if (!current.loading && FreshnessPolicy.shouldRefresh(current.dashboard?.series.orEmpty(), preferences.getLong("refresh_${country.iso3}", 0), System.currentTimeMillis())) {
            refresh()
            return
        }
        if (!current.newsLoading && System.currentTimeMillis() - newsRequestedAt >= FreshnessPolicy.NEWS_INTERVAL_MS) {
            _state.update { it.copy(newsLoading = true, newsError = null) }
            loadNews(country)
        }
    }

    private fun loadDashboard(country: Country, force: Boolean) {
        dashboardJob = viewModelScope.launch {
            try {
                if (force) preferences.edit().putLong("refresh_${country.iso3}", System.currentTimeMillis()).apply()
                val result = repository.dashboard(country, force)
                if (_state.value.selected?.iso3 == country.iso3) {
                    _state.update { it.copy(dashboard = result, loading = false, error = null) }
                    if (result.series.isEmpty()) preferences.edit().putLong("refresh_${country.iso3}", System.currentTimeMillis()).apply()
                    if (!force && result.series.isNotEmpty() && FreshnessPolicy.shouldRefresh(result.series, preferences.getLong("refresh_${country.iso3}", 0), System.currentTimeMillis())) {
                        preferences.edit().putLong("refresh_${country.iso3}", System.currentTimeMillis()).apply()
                        _state.update { it.copy(loading = true) }
                        val refreshed = repository.dashboard(country, true)
                        if (_state.value.selected?.iso3 == country.iso3) _state.update { it.copy(dashboard = refreshed, loading = false) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (_state.value.selected?.iso3 == country.iso3) {
                    _state.update { it.copy(loading = false, error = "Country data could not be refreshed. Check your connection and try again.") }
                }
            }
        }
    }

    private fun loadNews(country: Country) {
        newsRequestedAt = System.currentTimeMillis()
        newsJob = viewModelScope.launch {
            try {
                val items = repository.news(country)
                if (_state.value.selected?.iso3 == country.iso3) {
                    _state.update { it.copy(news = items, newsLoading = false, newsError = null) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (_state.value.selected?.iso3 == country.iso3) {
                    _state.update { it.copy(newsLoading = false, newsError = "The publisher feed is temporarily unavailable. Retry to check for updates.") }
                }
            }
        }
    }

    fun setQuery(query: String) { _state.update { it.copy(query = query) } }
    fun selectTab(tab: String) { _state.update { it.copy(tab = tab) } }
    fun toggleWatchlist(country: Country) {
        _state.update {
            val updated = if (country.iso3 in it.watchlist) it.watchlist - country.iso3 else it.watchlist + country.iso3
            preferences.edit().putStringSet("watchlist", updated.toSet()).apply()
            it.copy(watchlist = updated)
        }
    }

    /** Comparison is deliberately isolated from the selected dashboard and watchlist. */
    fun setComparisonCountries(countries: List<Country>) {
        val selected = countries.distinctBy { it.iso3 }.take(3)
        comparisonGeneration += 1
        comparisonJob?.cancel()
        _state.update { it.copy(compareCountries = selected, comparison = emptyMap(), comparisonLoading = selected.size >= 2, comparisonError = null) }
        if (selected.size < 2) return
        val generation = comparisonGeneration
        comparisonJob = viewModelScope.launch {
            try {
                val loaded = selected.map { country -> country.iso3 to repository.dashboard(country, false) }.toMap()
                if (generation == comparisonGeneration && _state.value.compareCountries.map { it.iso3 } == selected.map { it.iso3 }) {
                    _state.update { it.copy(comparison = loaded, comparisonLoading = false, comparisonError = null) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == comparisonGeneration) _state.update { it.copy(comparisonLoading = false, comparisonError = "Comparison data could not be loaded. Try again.") }
            }
        }
    }

    fun addComparisonCountry(country: Country) {
        val current = _state.value.compareCountries
        if (current.none { it.iso3 == country.iso3 } && current.size < 3) setComparisonCountries(current + country)
    }

    fun removeComparisonCountry(country: Country) = setComparisonCountries(_state.value.compareCountries.filterNot { it.iso3 == country.iso3 })

    fun clearComparison() = setComparisonCountries(emptyList())
}
