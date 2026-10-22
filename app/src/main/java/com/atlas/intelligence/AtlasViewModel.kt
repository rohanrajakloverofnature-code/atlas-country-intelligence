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
    val tab: String = "Overview"
)

class AtlasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AtlasRepository(application)
    private val preferences = application.getSharedPreferences("atlas_preferences", 0)
    private val _state = MutableStateFlow(AtlasState(watchlist = preferences.getStringSet("watchlist", setOf("IND", "USA", "CHN"))!!.toSet()))
    val state: StateFlow<AtlasState> = _state.asStateFlow()
    private var dashboardJob: Job? = null
    private var newsJob: Job? = null

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

    private fun loadDashboard(country: Country, force: Boolean) {
        dashboardJob = viewModelScope.launch {
            try {
                val result = repository.dashboard(country, force)
                if (_state.value.selected?.iso3 == country.iso3) {
                    _state.update { it.copy(dashboard = result, loading = false, error = null) }
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
}
