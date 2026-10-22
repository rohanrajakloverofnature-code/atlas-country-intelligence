#!/usr/bin/env python3
"""Refresh the offline snapshot for all 217 country/economy entries.

Each indicator is fetched once from the World Bank ``country/all`` endpoint;
aggregate rows are discarded using countries.json's ISO3 allowlist. Failed
responses never erase the prior genuine series for that indicator.
"""
import argparse
import concurrent.futures
import datetime as dt
import json
import math
import os
import tempfile
import time
import urllib.parse
import urllib.request

BASE = "https://api.worldbank.org/v2"
INDICATORS = [
    "NY.GDP.MKTP.KD.ZG", "NY.GDP.MKTP.CD", "NY.GDP.PCAP.CD", "FP.CPI.TOTL.ZG",
    "SL.UEM.TOTL.ZS", "SL.UEM.1524.ZS", "NE.EXP.GNFS.ZS", "NE.IMP.GNFS.ZS",
    "BX.KLT.DINV.WD.GD.ZS", "GC.BAL.CASH.GD.ZS", "GC.DOD.TOTL.GD.ZS", "DT.DOD.DECT.CD",
    "CM.MKT.LCAP.GD.ZS", "IT.NET.USER.ZS", "IT.CEL.SETS.P2", "GB.XPD.RSDV.GD.ZS",
    "IP.PAT.RESD", "SP.POP.TOTL", "SP.DYN.LE00.IN", "SH.DYN.MORT", "SH.XPD.CHEX.GD.ZS",
    "EN.ATM.CO2E.PC", "EG.USE.PCAP.KG.OE", "EG.FEC.RNEW.ZS", "EG.ELC.ACCS.ZS",
    "NV.IND.TOTL.ZS", "NV.AGR.TOTL.ZS", "NY.GNS.ICTR.ZS", "GOV_WGI_VA.EST",
    "GOV_WGI_PV.EST", "GOV_WGI_GE.EST", "GOV_WGI_RL.EST", "GOV_WGI_CC.EST",
    "GOV_WGI_RQ.EST",
]


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def request_url(code, start, end):
    source = 3 if code.startswith("GOV_WGI_") else 2
    query = urllib.parse.urlencode({
        "date": f"{start}:{end}", "format": "json", "per_page": 20000, "source": source,
    })
    return f"{BASE}/country/all/indicator/{code}?{query}"


def fetch(code, start, end):
    url = request_url(code, start, end)
    source = 3 if code.startswith("GOV_WGI_") else 2
    error = None
    for attempt in range(2):  # initial request + one retry
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "AtlasAndroid snapshot refresh"})
            with urllib.request.urlopen(req, timeout=30) as response:
                payload = json.load(response)
            rows = payload[1] if isinstance(payload, list) and len(payload) > 1 and isinstance(payload[1], list) else []
            retrieved = utc_now()
            last_updated = (payload[0] or {}).get("lastupdated", "") if payload and isinstance(payload[0], dict) else ""
            result = {}
            for row in rows:
                iso = str(row.get("countryiso3code") or "").upper()
                value = row.get("value")
                try:
                    year = int(row.get("date"))
                    number = float(value)
                    if not iso or not math.isfinite(number):
                        continue
                except (TypeError, ValueError):
                    continue
                points = result.setdefault(iso, {})
                points[year] = {"year": year, "value": number}
            normalized = {}
            for iso, by_year in result.items():
                points = sorted(by_year.values(), key=lambda p: p["year"])
                if points:
                    indicator = next((r.get("indicator") for r in rows if r.get("countryiso3code", "").upper() == iso and r.get("indicator")), {"id": code, "value": code})
                    normalized[iso] = {
                        "indicator": indicator,
                        "source": source,
                        "lastUpdated": last_updated,
                        "points": points,
                        "retrievedAt": retrieved,
                    }
            return code, normalized, url, None
        except Exception as exc:
            error = f"{type(exc).__name__}: {exc}"
            if attempt == 0:
                time.sleep(0.2)
    return code, None, url, error


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", default="app/src/main/assets/snapshot.json")
    ap.add_argument("--countries", default="app/src/main/assets/countries.json")
    ap.add_argument("--start", type=int, default=2000)
    ap.add_argument("--end", type=int, default=2026)
    args = ap.parse_args()
    with open(args.countries, encoding="utf-8") as fh:
        directory = json.load(fh)["countries"]
    allowlist = [str(c["iso3"]).upper() for c in directory]
    allowed = set(allowlist)
    target = os.path.abspath(args.output)
    old = {}
    if os.path.exists(target):
        with open(target, encoding="utf-8") as fh:
            old = json.load(fh)
    old_series = old.get("series", {})
    series = {iso: dict(old_series.get(iso, {})) for iso in allowlist}
    fetched_at = utc_now()
    urls = []
    successful = []
    failures = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(fetch, code, args.start, args.end) for code in INDICATORS]
        for future in concurrent.futures.as_completed(futures):
            code, response, url, error = future.result()
            urls.append(url)
            if response is None:
                failures.append({"indicator": code, "url": url, "error": error})
                print(f"WARN {code}: {error}")
                continue
            successful.append(code)
            for iso, data in response.items():
                if iso in allowed:
                    series[iso][code] = data
    populated = [iso for iso in allowlist if any(series[iso].values())]
    missing = [iso for iso in allowlist if not series[iso]]
    provenance = {
        "source": "World Bank Indicators API v2",
        "requestBase": BASE,
        "fetchedAt": fetched_at,
        "dateRange": f"global{args.start}:{args.end}",
        "countries": populated,
        "countryAllowlistCount": len(allowlist),
        "requestUrls": sorted(urls),
        "sourceIds": {"WDI": 2, "WGI": 3},
        "successfulIndicators": sorted(successful),
        "failedIndicators": failures,
        "missingCountries": missing,
        "note": "Null API values, aggregates, and failed requests omitted; prior genuine series retained on failed endpoints; no interpolation or forecasts.",
    }
    document = {"snapshotVersion": 1, "provenance": provenance, "series": series}
    os.makedirs(os.path.dirname(target), exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix="snapshot-", suffix=".json", dir=os.path.dirname(target))
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(document, fh, separators=(",", ":"), ensure_ascii=False)
        fh.write("\n")
    os.replace(tmp, target)
    points = sum(len(s.get("points", [])) for country in series.values() for s in country.values())
    print(json.dumps({"output": target, "directoryCountries": len(allowlist), "populatedCountries": len(populated), "missingCountries": missing, "series": sum(len(v) for v in series.values()), "points": points, "successfulIndicators": len(successful), "failedIndicators": failures, "bytes": os.path.getsize(target)}))


if __name__ == "__main__":
    main()
