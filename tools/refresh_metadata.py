#!/usr/bin/env python3
"""Fetch official metadata; never infer an original provider from an indicator name."""
import concurrent.futures
import datetime
import json
import os
import pathlib
import tempfile
import urllib.request
from refresh_snapshot import INDICATORS, BASE


def fetch(code):
    source = 3 if code.endswith('.EST') else 2
    url = f'{BASE}/indicator/{code}?format=json&source={source}'
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'Atlas official metadata refresh'})
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.load(response)
        rows = payload[1] if len(payload) > 1 and isinstance(payload[1], list) else []
        row = next((x for x in rows if x.get('id') == code), None)
        if row:
            return code, dict(row, requestUrl=url)
        print(f'No metadata: {code}', flush=True)
    except Exception as exc:
        print(f'Metadata unavailable: {code}: {exc}', flush=True)
    return code, None


def main():
    target = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets/indicator_metadata.json'
    existing = json.loads(target.read_text()) if target.exists() else {}
    data = existing.get('indicators', {})
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for code, row in pool.map(fetch, INDICATORS):
            if row:
                data[code] = row
    if not data:
        raise RuntimeError('No metadata retrieved; preserving existing file')
    result = {'provenance': {'source': 'World Bank Indicators API v2 indicator metadata', 'fetchedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'note': 'Original providers and methodology reproduced from official metadata; absent fields remain unavailable.'}, 'indicators': data}
    fd, temp = tempfile.mkstemp(dir=target.parent, prefix='metadata-')
    with os.fdopen(fd, 'w') as output:
        json.dump(result, output, ensure_ascii=False, indent=2)
        output.write('\n')
    os.replace(temp, target)
    print(f'Wrote {len(data)} official indicator metadata records: {target}', flush=True)


if __name__ == '__main__':
    main()
