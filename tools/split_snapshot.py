#!/usr/bin/env python3
"""Build small native-app lookup assets from the auditable global snapshot."""
import json
from pathlib import Path


def main():
    assets = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
    root = json.loads((assets / 'snapshot.json').read_text())
    destination = assets / 'snapshots'
    destination.mkdir(exist_ok=True)
    fetched = root.get('provenance', {}).get('fetchedAt', '')
    for iso, data in root['series'].items():
        if not iso.isalnum() or len(iso) != 3:
            raise ValueError(f'Invalid country code: {iso}')
        document = {'provenance': {'fetchedAt': fetched, 'source': 'World Bank Indicators API v2'}, 'series': {iso: data}}
        (destination / f'{iso}.json').write_text(json.dumps(document, separators=(',', ':')) + '\n')
    print(f'Wrote {len(root["series"])} small per-country lookup assets')


if __name__ == '__main__':
    main()
