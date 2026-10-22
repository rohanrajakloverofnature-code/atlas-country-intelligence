#!/usr/bin/env python3
"""Verify the checked-in India government observations and provenance fields.

This intentionally validates the asset only; refresh_government_data.py is not
used because the parent task will replace that generator.
"""
import json
from pathlib import Path

ASSET = Path(__file__).resolve().parents[1] / "app/src/main/assets/government_ind.json"
EXPECTED = {
    "GOV.IND.gdp_growth": (2024, 8.2),
    "GOV.IND.per_capita_income": (2024, 7.6),
    "GOV.IND.cpi_inflation": (2024, 5.4),
    "GOV.IND.fiscal_deficit": (2024, 5.6),
    "GOV.IND.trade_exports": (2024, 437.1),
    "GOV.IND.trade_imports": (2024, 675.4),
    "GOV.IND.trade_deficit": (2024, 238.3),
    "GOV.IND.services_exports": (2024, 341.1),
    "GOV.IND.current_account": (2024, -0.7),
    "GOV.IND.fdi_inflows": (2024, 70.95),
    "GOV.IND.industry_growth": (2024, 9.7),
    "GOV.IND.manufacturing_growth": (2024, 9.9),
    "GOV.IND.agriculture_growth": (2024, 1.4),
    "GOV.IND.services_growth": (2024, 7.6),
    "GOV.IND.capex": (2024, 948506),
    "GOV.IND.gst": (2024, 20.18),
    "GOV.IND.plfs_ur": (2024, 3.2),
    "GOV.IND.plfs_lfpr": (2024, 60.1),
    "GOV.IND.plfs_wpr": (2024, 58.2),
    "GOV.IND.repo_rate": (2024, 6.5),
    "GOV.IND.sdf_rate": (2024, 6.25),
    "GOV.IND.electricity": (2024, 1739.1),
    "GOV.IND.renewable_capacity": (2024, 190.57),
    "GOV.IND.road_construction": (2024, 12349),
    "GOV.IND.rail_freight": (2024, 1591.0),
}

def main():
    data = json.loads(ASSET.read_text())
    rows = data.get("series", [])
    assert data.get("country") == "IND", "wrong country"
    assert len(rows) == 25, f"expected 25 India records, got {len(rows)}"
    got = {r["id"]: r for r in rows}
    assert set(got) == set(EXPECTED), "record IDs differ from audited 25"
    for ident, (year, value) in EXPECTED.items():
        row = got[ident]
        assert row["points"] == [{"year": year, "value": value}], f"{ident}: value/period mismatch"
        assert row.get("evidence"), f"{ident}: missing evidence excerpt"
        assert row.get("publicationDate"), f"{ident}: missing publication date"
        url = row.get("sourceUrl", "")
        assert url.startswith("https://"), f"{ident}: non-HTTPS source"
        assert any(domain in url for domain in ("indiabudget.gov.in", "rbi.org.in", "mospi.gov.in", "pib.gov.in")), f"{ident}: non-official source"
    assert got["GOV.IND.sdf_rate"]["unit"] == "% per annum"
    assert "not a CRR" in got["GOV.IND.sdf_rate"]["evidence"]
    print(f"OK: {len(rows)} India government records, periods, values, evidence, and official URLs verified")

if __name__ == "__main__":
    main()
