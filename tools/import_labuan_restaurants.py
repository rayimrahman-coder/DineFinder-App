#!/usr/bin/env python3
"""Generate append-only Supabase SQL from OpenStreetMap's Labuan listings."""
import argparse
import datetime as dt
import json
import math
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ENDPOINT = 'https://overpass-api.de/api/interpreter'
QUERY = '''[out:json][timeout:90];
area["ISO3166-2"="MY-15"]["boundary"="administrative"]->.labuan;
nwr(area.labuan)["amenity"~"^(restaurant|cafe|fast_food|food_court)$"];
out center tags;'''
CATEGORIES = {'restaurant': 'Restaurant', 'cafe': 'Cafe',
              'fast_food': 'Fast food', 'food_court': 'Food court'}
NAMESPACE = uuid.UUID('04eb4fea-58b9-4816-9ef5-fd72a76237af')


def literal(value):
    return "'" + str(value).replace('\x00', '').replace("'", "''") + "'"


def listings(payload):
    if not isinstance(payload, dict) or not isinstance(payload.get('elements'), list):
        raise ValueError('Expected an Overpass JSON object with an elements array.')
    if payload.get('remark'):
        raise ValueError('Overpass reported an incomplete query; retry before importing.')
    found, seen, skipped = [], set(), 0
    for item in payload['elements']:
        if not isinstance(item, dict):
            skipped += 1
            continue
        tags = item.get('tags', {})
        if not isinstance(tags, dict):
            skipped += 1
            continue
        name = tags.get('name') or tags.get('name:en')
        category = tags.get('amenity')
        kind, osm_id = item.get('type'), item.get('id')
        point = item if kind == 'node' else item.get('center', {})
        try:
            lat, lon = float(point['lat']), float(point['lon'])
        except (TypeError, ValueError, KeyError):
            skipped += 1
            continue
        # A broad safety bound around Labuan; the Overpass administrative area
        # supplies the actual geographic filter, rather than this bounding box.
        valid = (isinstance(name, str) and name.strip() and isinstance(category, str) and category in CATEGORIES
                 and isinstance(kind, str) and kind in {'node', 'way', 'relation'}
                 and isinstance(osm_id, int) and not isinstance(osm_id, bool) and osm_id > 0
                 and math.isfinite(lat) and math.isfinite(lon)
                 and 5.1 <= lat <= 5.45 and 115.05 <= lon <= 115.4)
        if not valid:
            skipped += 1
            continue
        if (kind, osm_id) in seen:
            continue
        seen.add((kind, osm_id))
        address = ', '.join(str(tags[k]).strip() for k in
                            ['addr:housenumber', 'addr:street', 'addr:suburb',
                             'addr:city', 'addr:postcode'] if tags.get(k))
        cuisine = str(tags.get('cuisine') or CATEGORIES[category]).replace(';', ', ')[:160]
        halal_tag = str(tags.get('diet:halal', '')).lower()
        # Names and cuisine never establish halal certification.
        halal = True if halal_tag in {'yes', 'only'} else False if halal_tag == 'no' else None
        found.append(dict(id=str(uuid.uuid5(NAMESPACE, f'{kind}/{osm_id}')),
                          name=name.strip()[:160], cuisine=cuisine, address=address,
                          latitude=lat, longitude=lon, halal=halal,
                          hours=str(tags.get('opening_hours') or 'Hours unavailable'),
                          url=f'https://www.openstreetmap.org/{kind}/{osm_id}'))
    return sorted(found, key=lambda row: (row['name'].casefold(), row['id'])), skipped


def sql(rows, retrieved):
    lines = [
        '-- Labuan OpenStreetMap food listings. Review before running in SQL Editor.',
        '-- Source: https://www.openstreetmap.org/copyright (ODbL).',
        '-- Mapped listings only; coverage and operating status are not guaranteed.',
        '-- Unknown ratings/prices are placeholders, not verified values.',
        '-- Existing records are preserved; no deletes, account edits, or RLS changes.',
        f'-- Retrieved/generated UTC: {retrieved}; new candidates: {len(rows)}',
        '-- Prerequisite: run supabase/setup.sql first.',
        'begin;', 'set local standard_conforming_strings = on;',
        'alter table public.restaurants add column if not exists source_url text;',
        'alter table public.restaurants add column if not exists source_checked_at timestamptz;',
    ]
    for row in rows:
        halal = 'null' if row['halal'] is None else str(row['halal']).lower()
        tags = "array['OpenStreetMap', 'HALAL']" if row['halal'] else "array['OpenStreetMap']"
        values = [literal(row['id']) + '::uuid', literal(row['name']), literal(row['cuisine']),
                  literal('OpenStreetMap listing; details may be outdated. ' + row['url']),
                  literal(row['address']), repr(row['latitude']), repr(row['longitude']),
                  '0', '1', literal(row['hours']), tags, halal, literal(row['url']),
                  literal(retrieved) + '::timestamptz']
        lines += [
            'insert into public.restaurants (id, name, cuisine, description, address,',
            ' latitude, longitude, rating, price_level, opening_hours, tags, is_halal,',
            ' source_url, source_checked_at)',
            'select ' + ', '.join(values),
            'where not exists (select 1 from public.restaurants r',
            # Preserve owner-supplied entries such as Cafe UMSKAL. Distinct names
            # remain separate even at a shared food-court location.
            f' where lower(btrim(r.name)) = lower({literal(row["name"])})',
            ' and extensions.st_dwithin(r.location,',
            f' extensions.st_setsrid(extensions.st_makepoint({row["longitude"]!r}, {row["latitude"]!r}), 4326)::extensions.geography, 50))',
            'on conflict (id) do nothing;',
        ]
    lines += ["notify pgrst, 'reload schema';", 'commit;',
              'select count(*) as total_restaurants from public.restaurants;', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, help='Previously exported Overpass JSON (no network).')
    parser.add_argument('--output', type=Path, default=Path('supabase/labuan_restaurants.sql'))
    args = parser.parse_args()
    try:
        if args.input:
            payload = json.loads(args.input.read_text(encoding='utf-8'))
        else:
            request = urllib.request.Request(ENDPOINT,
                data=urllib.parse.urlencode({'data': QUERY}).encode(),
                headers={'User-Agent': 'DineFinder-Labuan-Import/1.0 (manual data import)'})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read(20_000_001)
            if len(data) > 20_000_000:
                raise ValueError('Response exceeds 20 MB; output not written.')
            payload = json.loads(data)
        rows, skipped = listings(payload)
        if not rows:
            raise ValueError('No eligible named Labuan listings returned; output not written.')
        retrieved = dt.datetime.now(dt.timezone.utc).isoformat()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(sql(rows, retrieved), encoding='utf-8')
        print(f'Wrote {len(rows)} mapped listings to {args.output}; skipped {skipped} invalid/unnamed entries.')
        print('Review this SQL, then run it in your Supabase SQL Editor. No database has been modified.')
    except (OSError, ValueError, urllib.error.URLError) as error:
        print(f'Import failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
