#!/usr/bin/env python3
"""Validate reviewed national releases and rebuild USA/GBR app-ready assets.

This is an offline provenance-table converter, NOT a live government-data fetcher.
Update the research records only after reviewing the exact official releases.
India's audited canonical table is validated but never overwritten here.
"""
import json
import math
import re
from pathlib import Path
from urllib.parse import urlparse

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
ALLOWED = {'USA': ('bls.gov', 'bea.gov', 'census.gov'), 'GBR': ('ons.gov.uk',),
           'IND': ('gov.in', 'rbi.org.in', 'mospi.gov.in', 'pib.gov.in')}

def validate_country(iso, rows):
    assert 20 <= len(rows) <= 30, (iso, len(rows))
    assert len({row['id'] for row in rows}) == len(rows), 'duplicate metric IDs'
    for row in rows:
        assert row['id'].startswith(f'GOV.{iso}.')
        for key in ('title', 'unit', 'description', 'sourceName', 'sourceOrganization', 'sourceNote', 'retrievedAt'):
            assert row.get(key), (iso, row['id'], key)
        url = urlparse(row['sourceUrl'])
        assert url.scheme == 'https' and any(url.hostname == h or url.hostname.endswith('.' + h) for h in ALLOWED[iso]), row['sourceUrl']
        for point in row['points']:
            assert isinstance(point['year'], int) and 1900 <= point['year'] <= 2026
            assert isinstance(point['value'], (int, float)) and math.isfinite(point['value'])

def convert(iso):
    evidence = json.loads((ASSETS / f'government_{iso.lower()}_research.json').read_text())
    records = evidence if isinstance(evidence, list) else evidence['records']
    rows = []
    for rec in records:
        assert rec['countryIso'] == iso
        assert rec.get('evidence'), 'source excerpt is required'
        token = re.sub(r'[^a-z0-9_]+', '_', rec.get('short', rec['id']).lower()).strip('_')
        period = str(rec['period'])
        if str(rec['year']) not in period:
            period += f" {rec['year']}"
        unit = rec['unit']
        if iso == 'USA':
            unit = {'unemployment_rate': '% of civilian labour force',
                    'labor_force_participation_rate': '% of civilian population age 16+',
                    'employment_population_ratio': '% of civilian population age 16+'}.get(token, unit)
        note = f"Reference period: {period}. Frequency: {rec['frequency']}. "
        note += f"Release published: {rec['publishedAt']}. Historical release snapshot; not a live or necessarily latest value. "
        note += 'Monthly and rolling/quarterly observations are not calendar-year annual averages. '
        if 'SAAR' in unit:
            note += 'SAAR = seasonally adjusted annual rate; not actual homes completed during one month. '
        if 'England' in rec['title']:
            note += 'Geographic scope: England only, not the whole United Kingdom. '
        rows.append({'id': f'GOV.{iso}.{token}', 'title': f"{rec['title']} ({period})", 'category': 'Government',
                     'unit': unit, 'description': rec['description'], 'sourceName': rec['sourceName'],
                     'sourceOrganization': rec['sourceName'], 'sourceUrl': rec['sourceUrl'], 'sourceNote': note,
                     'points': [{'year': rec['year'], 'value': rec['value']}], 'retrievedAt': rec['retrievedAt'],
                     'publishedAt': rec['publishedAt'], 'evidence': rec['evidence']})
    validate_country(iso, rows)
    out = {'country': iso, 'retrievedAt': max(r['retrievedAt'] for r in rows),
           'coverageNote': 'Reviewed historical government release snapshots. Exact period and geography are shown per signal. No inferred history.', 'series': rows}
    (ASSETS / f'government_{iso.lower()}.json').write_text(json.dumps(out, indent=2, ensure_ascii=False) + '\n')
    print(f'{iso}: {len(rows)} verified-source records converted')

if __name__ == '__main__':
    for country in ('USA', 'GBR'):
        convert(country)
    india = json.loads((ASSETS / 'government_ind.json').read_text())
    validate_country('IND', india['series'])
    print(f"IND: {len(india['series'])} canonical records validated (not overwritten)")
