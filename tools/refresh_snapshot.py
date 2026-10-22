#!/usr/bin/env python3
"""Refresh genuine World Bank country/indicator snapshots; nulls are omitted, never imputed."""
import concurrent.futures, datetime, json, os, tempfile, urllib.parse, urllib.request
BASE = "https://api.worldbank.org/v2"
COUNTRIES = ["IND", "USA", "CHN", "DEU", "BRA", "JPN", "GBR", "ZAF"]
INDICATORS = [
"NY.GDP.MKTP.KD.ZG","NY.GDP.MKTP.CD","NY.GDP.PCAP.CD","FP.CPI.TOTL.ZG","SL.UEM.TOTL.ZS","SL.UEM.1524.ZS","NE.EXP.GNFS.ZS","NE.IMP.GNFS.ZS","BX.KLT.DINV.WD.GD.ZS","GC.BAL.CASH.GD.ZS","GC.DOD.TOTL.GD.ZS","DT.DOD.DECT.CD","CM.MKT.LCAP.GD.ZS","IT.NET.USER.ZS","IT.CEL.SETS.P2","GB.XPD.RSDV.GD.ZS","IP.PAT.RESD","SP.POP.TOTL","SP.DYN.LE00.IN","SH.DYN.MORT","SH.XPD.CHEX.GD.ZS","EN.ATM.CO2E.PC","EG.USE.PCAP.KG.OE","EG.FEC.RNEW.ZS","EG.ELC.ACCS.ZS","NV.IND.TOTL.ZS","NV.AGR.TOTL.ZS","NY.GNS.ICTR.ZS","VA.EST","PV.EST","GE.EST","RL.EST","CC.EST","RQ.EST"]
def request(iso, code, start, end):
    source = 3 if code.endswith(".EST") else 2
    query = urllib.parse.urlencode({"date": f"{start}:{end}", "format":"json", "per_page":100, "source":source})
    url = f"{BASE}/country/{iso}/indicator/{code}?{query}"
    req = urllib.request.Request(url, headers={"User-Agent":"AtlasAndroid snapshot refresh"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response: payload = json.load(response)
        rows = payload[1] if len(payload) > 1 and isinstance(payload[1], list) else []
        points = []
        indicator = {}
        for row in rows:
            indicator = row.get("indicator") or indicator
            if row.get("value") is not None:
                try: points.append({"year": int(row["date"]), "value": float(row["value"])})
                except (TypeError, ValueError): pass
        if not points: return iso, code, None, url
        return iso, code, {"indicator":indicator, "source":source, "lastUpdated":(payload[0] or {}).get("lastupdated", ""), "points":sorted(points, key=lambda x:x["year"])}, url
    except Exception as exc:
        print(f"WARN {iso} {code}: {exc}")
        return iso, code, None, url

def main():
    import argparse
    ap = argparse.ArgumentParser(); ap.add_argument("--output", default="app/src/main/assets/snapshot.json"); ap.add_argument("--start", type=int, default=2000); ap.add_argument("--end", type=int, default=datetime.datetime.now().year); args = ap.parse_args()
    out = {iso:{} for iso in COUNTRIES}; urls=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        for iso, code, data, url in pool.map(lambda x: request(*x, args.start, args.end), [(i,c) for i in COUNTRIES for c in INDICATORS]):
            urls.append(url)
            if data: out[iso][code] = data
    document = {"snapshotVersion":1, "provenance":{"source":"World Bank Indicators API v2", "requestBase":BASE, "fetchedAt":datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00","Z"), "dateRange":f"{args.start}-{args.end}", "countries":COUNTRIES, "requestUrls":sorted(set(urls)), "sourceIds":{"WDI":2,"WGI":3}, "note":"Null API values and failed requests omitted; no interpolation or forecasts."}, "series":out}
    target=os.path.abspath(args.output); os.makedirs(os.path.dirname(target), exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix="snapshot-", suffix=".json", dir=os.path.dirname(target));
    with os.fdopen(fd,"w") as f: json.dump(document,f,indent=2); f.write("\n")
    os.replace(tmp,target); print(f"wrote {target}")
if __name__ == "__main__": main()
