package com.atlas.intelligence.data

data class Country(val iso3: String, val iso2: String, val name: String, val region: String, val capital: String, val income: String)
data class MetricDefinition(val id: String, val title: String, val category: String, val unit: String, val description: String, val sourceName: String = "World Bank", val sourceUrl: String = "https://data.worldbank.org/indicator/$id", val sourceOrganization: String = "", val sourceNote: String = "")
data class Observation(val year: Int, val value: Double)
data class MetricSeries(val definition: MetricDefinition, val points: List<Observation>, val lastUpdated: String = "", val retrievedAt: String = "", val cached: Boolean = false)
data class NewsItem(val title: String, val url: String, val publisher: String, val publishedAt: String)
data class CountryDashboard(val country: Country, val series: List<MetricSeries>, val warnings: List<String> = emptyList())

object MetricCatalog {
    private fun m(id: String, title: String, category: String, unit: String, description: String, source: String = "World Bank") = MetricDefinition(id, title, category, unit, description, source, "https://data.worldbank.org/indicator/$id")
    val all: List<MetricDefinition> = listOf(
        m("NY.GDP.MKTP.KD.ZG", "GDP growth", "Growth", "% annual", "Annual percentage growth rate of GDP at market prices based on constant local currency.", "World Bank WDI (source=2)"),
        m("NY.GDP.MKTP.CD", "GDP", "Growth", "current US$", "Gross domestic product at current prices in U.S. dollars.", "World Bank WDI (source=2)"),
        m("NY.GDP.PCAP.CD", "GDP per capita", "Growth", "current US$", "GDP divided by midyear population at current prices.", "World Bank WDI (source=2)"),
        m("FP.CPI.TOTL.ZG", "Inflation", "Prices", "% annual", "Inflation measured by the consumer price index, annual percentage change.", "World Bank WDI (source=2)"),
        m("SL.UEM.TOTL.ZS", "Unemployment", "Jobs", "% of labor force", "Share of the labor force without work but available for and seeking employment.", "World Bank WDI (source=2)"),
        m("SL.UEM.1524.ZS", "Youth unemployment", "Jobs", "% ages 15–24", "Share of the youth labor force without work but available for and seeking employment.", "World Bank WDI (source=2)"),
        m("NE.EXP.GNFS.ZS", "Exports", "Trade", "% of GDP", "Exports of goods and services as a share of GDP.", "World Bank WDI (source=2)"),
        m("NE.IMP.GNFS.ZS", "Imports", "Trade", "% of GDP", "Imports of goods and services as a share of GDP.", "World Bank WDI (source=2)"),
        m("BX.KLT.DINV.WD.GD.ZS", "Foreign direct investment", "External", "% of GDP", "Net inflows of foreign direct investment as a share of GDP.", "World Bank WDI (source=2)"),
        m("GC.BAL.CASH.GD.ZS", "Cash surplus/deficit", "Fiscal", "% of GDP", "Central government cash surplus or deficit; this is not a measure of the all-government fiscal balance.", "World Bank WDI (source=2)"),
        m("GC.DOD.TOTL.GD.ZS", "Central government debt", "Fiscal", "% of GDP", "Central government debt as a share of GDP; coverage and comparability vary by country.", "World Bank WDI (source=2)"),
        m("DT.DOD.DECT.CD", "External debt stocks", "External", "current US$", "Total external debt stocks owed to nonresidents in current U.S. dollars.", "World Bank WDI (source=2)"),
        m("CM.MKT.LCAP.GD.ZS", "Market capitalization", "Markets", "% of GDP", "Market capitalization of listed domestic companies as a share of GDP.", "World Bank WDI (source=2)"),
        m("GOV_WGI_VA.EST", "Voice and accountability", "Governance", "WGI estimate", "WGI estimate for the extent citizens participate in selecting government and enjoy freedoms of expression, association and media.", "World Bank WGI (source=3)"),
        m("GOV_WGI_PV.EST", "Political stability", "Governance", "WGI estimate", "WGI estimate of perceptions of likelihood of political instability and/or politically motivated violence.", "World Bank WGI (source=3)"),
        m("GOV_WGI_GE.EST", "Government effectiveness", "Governance", "WGI estimate", "WGI estimate of public-service quality, civil-service independence and policy credibility.", "World Bank WGI (source=3)"),
        m("GOV_WGI_RL.EST", "Rule of law", "Governance", "WGI estimate", "WGI estimate of confidence in rules of society, including contract enforcement, property rights and courts.", "World Bank WGI (source=3)"),
        m("GOV_WGI_CC.EST", "Control of corruption", "Governance", "WGI estimate", "WGI estimate of the extent public power is exercised for private gain.", "World Bank WGI (source=3)"),
        m("GOV_WGI_RQ.EST", "Regulatory quality", "Governance", "WGI estimate", "WGI estimate of government ability to formulate and implement sound policies and regulations.", "World Bank WGI (source=3)"),
        m("IT.NET.USER.ZS", "Internet users", "Technology", "% of population", "Individuals using the Internet as a share of population.", "World Bank WDI (source=2)"),
        m("IT.CEL.SETS.P2", "Mobile subscriptions", "Technology", "per 100 people", "Mobile cellular subscriptions per 100 people.", "World Bank WDI (source=2)"),
        m("GB.XPD.RSDV.GD.ZS", "Research and development", "Technology", "% of GDP", "Research and development expenditure as a share of GDP.", "World Bank WDI (source=2)"),
        m("IP.PAT.RESD", "Resident patent applications", "Technology", "applications", "Patent applications filed by residents, as reported by the World Intellectual Property Organization.", "World Bank WDI (source=2)"),
        m("SP.POP.TOTL", "Population", "People", "people", "Total population based on the de facto definition.", "World Bank WDI (source=2)"),
        m("SP.DYN.LE00.IN", "Life expectancy", "Health", "years", "Life expectancy at birth in years.", "World Bank WDI (source=2)"),
        m("SH.DYN.MORT", "Under-five mortality", "Health", "per 1,000 live births", "Probability of dying by age five per 1,000 live births.", "World Bank WDI (source=2)"),
        m("SH.XPD.CHEX.GD.ZS", "Current health expenditure", "Health", "% of GDP", "Domestic general government and private current health expenditure as a share of GDP.", "World Bank WDI (source=2)"),
        m("EN.ATM.CO2E.PC", "CO₂ emissions", "Climate", "metric tons per capita", "Carbon dioxide emissions from fossil fuels and industry per capita.", "World Bank WDI (source=2)"),
        m("EG.USE.PCAP.KG.OE", "Energy use", "Energy", "kg oil equivalent per capita", "Energy use per capita in kilograms of oil equivalent.", "World Bank WDI (source=2)"),
        m("EG.FEC.RNEW.ZS", "Renewable energy", "Energy", "% of total", "Renewable energy consumption as a share of total final energy consumption.", "World Bank WDI (source=2)"),
        m("EG.ELC.ACCS.ZS", "Electricity access", "Energy", "% of population", "Population with access to electricity.", "World Bank WDI (source=2)"),
        m("NV.IND.TOTL.ZS", "Industry value added", "Structure", "% of GDP", "Industry, including construction, value added as a share of GDP.", "World Bank WDI (source=2)"),
        m("NV.AGR.TOTL.ZS", "Agriculture value added", "Structure", "% of GDP", "Agriculture, forestry and fishing value added as a share of GDP.", "World Bank WDI (source=2)"),
        m("NY.GNS.ICTR.ZS", "Gross savings", "Structure", "% of GDP", "Gross savings as a share of GDP.", "World Bank WDI (source=2)")
    )
}
