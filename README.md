# ATLAS

**A native Android lens on countries, built with Kotlin and Jetpack Compose.**

Atlas brings official annual statistics, historical charts, source provenance and publisher-attributed country briefings into a dark, focused interface. No WebView. No fabricated numbers. No account or API key required.

## What you can do

- Search 217 countries and economies in the World Bank directory, including India, and switch dashboards.
- Explore growth, GDP, prices, employment, public finances, debt, investment, trade, annual market statistics, governance, technology, people and environment indicators where the provider has observations.
- Open a metric to see its definition, units, observation years, source link, retrieval metadata and annual values.
- Filter history to 5 years, 10 years or all available observations. Missing years are not invented.
- Save countries in an on-device watchlist.
- Read country-matched publisher headlines and open original articles externally.
- Use bundled official-data histories for all 217 countries/economies, including India, and the last successful device cache when the API is unavailable. The initial global bundle contains 6,095 indicator-country series and 144,396 observations; individual indicators still have coverage gaps.

## Run it

Open the repository in a current stable Android Studio and let Gradle sync. Install Android SDK Platform 35 and Build Tools 35.0.0. The app supports Android 8.0 / API 26 and later.

```sh
# JDK 17 or later; configure ANDROID_HOME or local.properties first.
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `local.properties` file is intentionally ignored. Example:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

The debug APK is for evaluation and is signed with a development key. A production release needs the publisher’s own protected signing key and a reviewed release process. Never commit signing material or provider credentials.

## Where the numbers come from

The app calls the [World Bank Indicators API v2](https://datahelpdesk.worldbank.org/knowledgebase/articles/889392-about-the-indicators-api-documentation). This is an official distribution channel, often aggregating data from national statistical offices and other international institutions. Attribution is not a guarantee that a statistic cannot later be revised.

**Observation year**, **dataset update date**, and **retrieval time** describe different things. The latest available observation can be several years old. Cross-country figures can refer to different years. A missing observation means unavailable, not zero. The included snapshots are real API observations, not demonstration data.

See [third-party notices](THIRD_PARTY_NOTICES.md) for attribution and dataset licensing, and [privacy](PRIVACY.md) for network and local-storage behavior.

## How updates work

Opening a country or returning to the app checks its cached statistics. If observations were retrieved more than 24 hours ago, Atlas requests newer annual data directly from the official API while keeping the saved dashboard visible. Manual **Refresh** bypasses that interval. An unsuccessful automatic attempt is throttled for one day to avoid repeatedly hammering an unavailable source; manual refresh is always available.

New official observations do **not** require an app-store update while the provider’s indicator/API schema remains compatible. Each metric shows its actual observation year, dataset update date and retrieval timestamp. A newly retrieved dataset can still contain older annual observations. Failed requests preserve valid cached history, with a visible note. Missing values remain missing.

Publisher briefings load on country selection and can refresh on app resume after 15 minutes, or immediately through Refresh. This is on-use updating, not continuous background monitoring or a push-alert service. The app cannot show data before its source publishes it; source corrections and revised estimates can change historical values.

To rebuild the bundled data for a future app release, run `python3 tools/refresh_global_snapshot.py`, `python3 tools/refresh_metadata.py`, and then `python3 tools/split_snapshot.py`. The app reads a small country-specific asset rather than parsing the entire global history on each country switch.

## This is a first release, not an exhaustive intelligence terminal

Country coverage is limited by source availability. This version does **not** claim to include every aspect of every country. In particular:

- Market data are annual statistical indicators, **not** live stock-index quotes or intraday histories.
- Fiscal cash balance and central-government debt are labeled as such; they are not substitutes for a nationally reported general-government deficit or debt series.
- Governance indicators describe statistical estimates, not an up-to-the-minute list of political leaders, election results, sanctions or armed conflicts.
- Briefings show finite publisher feeds, not a complete or independently verified breaking-news monitoring service. Empty feeds and outages are surfaced instead of invented stories.
- No portfolio trading, financial advice, forecasts, paid-provider integration, push-alert backend or Play Store publication is included.

A fuller production edition would add reviewed IMF/national budget and central-bank adapters, licensed market/index histories and a licensed broader news service, with secure server-side credentials. Those providers and distribution rights must be selected before promising real-time or exhaustive coverage.

## Project layout

```text
app/src/main/java/com/atlas/intelligence/
  data/              Official API, models, catalog, cache and news feeds
  ui/                Compose screens and native charts
  AtlasViewModel.kt  Lifecycle-aware state and persistent watchlist
app/src/main/assets/ Sourced country directory and offline observations
tools/               Reproducible snapshot refresh
.github/workflows/   Build, lint, tests and APK artifacts
```

CI runs unit tests, Android lint and a debug APK build on pushes and pull requests. Source code uses the MIT license; third-party datasets and news retain their own terms.
