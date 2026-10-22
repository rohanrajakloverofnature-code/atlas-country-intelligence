#!/usr/bin/env python3
"""Build calculated indicator ranks from the immutable bundled World Bank snapshots.

Ranks are competition ranks (1, 1, 3), computed only among reporters with a
non-null observation for the same metric and calendar year. They are not World
Bank-published ranks and include all bundled countries/economies, territories
included.
"""
from __future__ import annotations
import argparse, json
from collections import defaultdict
from pathlib import Path

METRICS = {
    "NY.GDP.MKTP.CD": {"title": "GDP", "direction": "higher"},
    "NY.GDP.PCAP.CD": {"title": "GDP per capita", "direction": "higher"},
    "SP.POP.TOTL": {"title": "Population", "direction": "higher"},
    "SP.DYN.LE00.IN": {"title": "Life expectancy", "direction": "higher"},
    "IT.NET.USER.ZS": {"title": "Internet users", "direction": "higher"},
    "EG.FEC.RNEW.ZS": {"title": "Renewable energy", "direction": "higher"},
    "EG.ELC.ACCS.ZS": {"title": "Electricity access", "direction": "higher"},
    "GB.XPD.RSDV.GD.ZS": {"title": "Research and development", "direction": "higher"},
    "NE.EXP.GNFS.ZS": {"title": "Exports", "direction": "higher"},
    "NE.IMP.GNFS.ZS": {"title": "Imports", "direction": "higher"},
    "BX.KLT.DINV.WD.GD.ZS": {"title": "Foreign direct investment", "direction": "higher"},
    "NY.GDP.MKTP.KD.ZG": {"title": "GDP growth", "direction": "higher", "label": "highest growth"},
    "FP.CPI.TOTL.ZG": {"title": "Inflation", "direction": "lower"},
    "SL.UEM.TOTL.ZS": {"title": "Unemployment", "direction": "lower"},
}

def build(source_dir: Path, output: Path) -> None:
    records = defaultdict(lambda: defaultdict(dict))  # metric -> year -> iso -> value
    fetched = set()
    files = sorted(source_dir.glob("*.json"))
    for path in files:
        payload = json.loads(path.read_text())
        fetched_at = payload.get("provenance", {}).get("fetchedAt")
        if fetched_at: fetched.add(fetched_at)
        for iso, series in payload.get("series", {}).items():
            for metric_id in METRICS:
                metric = series.get(metric_id)
                if not metric: continue
                for point in metric.get("points", []):
                    value = point.get("value")
                    year = point.get("year")
                    if value is not None and year is not None and int(year) >= 2000:
                        records[metric_id][str(int(year))][iso] = float(value)
    if len(fetched) != 1:
        raise SystemExit(f"Expected one source snapshot timestamp, found {sorted(fetched)}")
    metrics = {}
    for metric_id, meta in METRICS.items():
        years = {}
        for year, values in sorted(records[metric_id].items(), key=lambda x: int(x[0])):
            ordered = sorted(values.items(), key=lambda kv: kv[1], reverse=meta["direction"] == "higher")
            entries = {}
            rank = 0
            previous = None
            for position, (iso, value) in enumerate(ordered, start=1):
                if previous is None or value != previous: rank = position
                entries[iso] = {"rank": rank, "denominator": len(ordered), "year": int(year), "value": value}
                previous = value
            years[year] = entries
        metrics[metric_id] = {"title": meta["title"], "direction": meta["direction"], "label": meta.get("label", "rank"), "years": years}
    result = {
        "schemaVersion": 1,
        "scope": "217 bundled World Bank reporting countries/economies; territories included; not a universal sovereign-country ranking",
        "source": "Calculated from bundled World Bank observations",
        "snapshotFetchedAt": sorted(fetched)[0],
        "notes": ["Ranks are calculated by Atlas, not official World Bank published ranks.", "Only identical metric and calendar observation years are compared.", "Null observations are omitted; zero is a valid observation and is never treated as missing.", "No World Bank Governance Indicators are included."],
        "metrics": metrics,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, separators=(",", ":"), sort_keys=False) + "\n")
    print(f"wrote {output} ({len(files)} snapshots, {sum(len(y) for m in metrics.values() for y in m['years'].values())} metric-years)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, default=Path("app/src/main/assets/snapshots"))
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/indicator_ranks.json"))
    args = parser.parse_args()
    build(args.source_dir, args.output)
