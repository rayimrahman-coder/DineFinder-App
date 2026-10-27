#!/usr/bin/env python3
"""Preserve every source field for the 328 reviewed operational food outlets.

Usage: python3 supabase/tools/build_restaurant_profile_import.py path/to/workbook.xlsx
Requires openpyxl. No network connections or database credentials are used.
"""
import argparse
import hashlib
import json
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[2]


def quoted(value):
    return '"' + value.replace('"', '""') + '"'


def build(source, output):
    workbook = openpyxl.load_workbook(source, read_only=True, data_only=True)
    if len(workbook.sheetnames) != 1:
        raise ValueError("This import expects the supplied single-sheet export; review additional sheets first.")
    sheet = workbook.active
    rows = iter(sheet.values)
    headers = list(next(rows))
    if len(headers) != 65 or any(not isinstance(h, str) for h in headers) or len(set(headers)) != len(headers):
        raise ValueError("Expected 65 unique source columns; review a changed workbook layout first.")
    records = [(index, dict(zip(headers, values))) for index, values in enumerate(rows, 2) if any(v is not None for v in values)]
    if len(records) != 598 or len({r['place_id'] for _, r in records}) != 598:
        raise ValueError("Expected the supplied 598 unique listings; review a different export before importing.")
    by_place = {r['place_id']: r for _, r in records}
    previous = (ROOT / 'supabase/imports/20261023_labuan_outscraper.sql').read_text()
    start = previous.index("SELECT * FROM jsonb_to_recordset('") + len("SELECT * FROM jsonb_to_recordset('")
    end = previous.index("'::jsonb) AS row_data", start)
    selected = json.loads(previous[start:end].replace("''", "'"))
    if len(selected) != 328 or any(r['google_place_id'] not in by_place for r in selected):
        raise ValueError("The workbook must contain all 328 food outlets from the existing reviewed selection.")
    eligible = {r['google_place_id'] for r in selected}
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    payload = [{'source_row': row, 'discovery_eligible': True, 'raw_data': data} for row, data in records if data['place_id'] in eligible]
    for record in selected:
        raw = by_place[record['google_place_id']]
        record['description'] = raw['description'] or ''
    json_payload = json.dumps(payload, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
    json_selected = json.dumps(selected, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
    for tag in ['$workbook_data$', '$food_outlets$']:
        if tag in json_payload or tag in json_selected:
            raise ValueError("SQL literal delimiter occurs in the source data.")
    setup = (ROOT / 'supabase/setup.sql').read_text()
    table_start = setup.index('create table if not exists public.restaurants (')
    table_end = setup.index('\n);', table_start) + len('\n);')
    type_start = setup.index('do $create_result_type$')
    type_end = setup.index('$create_result_type$;', type_start) + len('$create_result_type$;')
    repair = (ROOT / 'supabase/migrations/202610230002_repair_restaurant_profile_columns.sql').read_text()
    repair = repair[repair.index('begin;') + len('begin;'):repair.rindex('commit;')]
    numeric = {'latitude': 'double precision', 'longitude': 'double precision', 'rating': 'double precision'}
    numeric.update({k: 'bigint' for k in ['reviews', 'photos_count'] + [f'reviews_per_score_{n}' for n in range(1, 6)]})
    numeric.update({'area_service': 'boolean', 'verified': 'boolean'})
    declarations = ',\n'.join('  ' + quoted(h) + ' ' + numeric.get(h, 'text') for h in headers)
    expressions = ',\n'.join("  (raw_data->>" + "'" + h.replace("'", "''") + "')" + ('::' + numeric[h] if h in numeric else '') for h in headers)
    column_names = ', '.join(map(quoted, headers))
    literal = lambda s: "'" + s.replace("'", "''") + "'"
    sql = f'''-- COMPLETE FOOD-OUTLET PROFILES: 328 operational food outlets, all 65 source columns, including NULL values.
-- Source: {source.name}; sheet: {sheet.title}
-- SHA-256: {digest}
-- Large SQL file: use restaurant_profile_batches for SQL Editor, or a direct database connection.
-- Full profiles are stored in public.restaurant_source_profiles (dashboard/admin access only).
-- The remaining 270 source listings are excluded entirely, as requested.
-- Existing restaurant IDs, names, coordinates, ratings and other supplied values are preserved.
-- Empty/unknown supported profile fields may be filled from the supplied export.
-- No Auth users, login tables or existing restaurant records are deleted.
BEGIN;
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;
DO $postgis_schema$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                 WHERE e.extname='postgis' AND n.nspname='extensions') THEN
    RAISE EXCEPTION 'PostGIS must be installed in extensions. Do not drop an existing extension; inspect its schema first.';
  END IF;
END;
$postgis_schema$;
GRANT USAGE ON SCHEMA extensions TO anon, authenticated;
{setup[table_start:table_end]}
{setup[type_start:type_end]}
{repair}
CREATE INDEX IF NOT EXISTS restaurants_location_gist ON public.restaurants USING gist(location);
ALTER TABLE public.restaurants ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.restaurants TO anon, authenticated;
DO $read_policy$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='restaurants'
                 AND policyname='Restaurant profiles are public') THEN
    CREATE POLICY "Restaurant profiles are public" ON public.restaurants
      FOR SELECT TO anon, authenticated USING (true);
  END IF;
END;
$read_policy$;
GRANT EXECUTE ON FUNCTION public.nearby_restaurants(double precision,double precision,double precision) TO anon, authenticated;
ALTER TABLE public.restaurants
  ADD COLUMN IF NOT EXISTS phone text,
  ADD COLUMN IF NOT EXISTS website text,
  ADD COLUMN IF NOT EXISTS review_count integer,
  ADD COLUMN IF NOT EXISTS google_place_id text,
  ADD COLUMN IF NOT EXISTS source_url text,
  ADD COLUMN IF NOT EXISTS source_name text,
  ADD COLUMN IF NOT EXISTS source_file text,
  ADD COLUMN IF NOT EXISTS business_status text,
  ADD COLUMN IF NOT EXISTS imported_at timestamptz;
CREATE INDEX IF NOT EXISTS restaurants_google_place_id_idx ON public.restaurants(google_place_id);
CREATE TABLE IF NOT EXISTS public.restaurant_source_profiles (
  source_sha256 text NOT NULL,
  source_file text NOT NULL,
  source_sheet text NOT NULL,
  source_row integer NOT NULL CHECK (source_row >= 2),
  discovery_eligible boolean NOT NULL,
  restaurant_id uuid REFERENCES public.restaurants(id) ON DELETE SET NULL,
{declarations},
  raw_data jsonb NOT NULL CHECK (jsonb_typeof(raw_data)='object'),
  imported_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_sha256, source_sheet, source_row)
);
-- Original business/owner/source metadata is retained for dashboard use, not exposed to guests.
ALTER TABLE public.restaurant_source_profiles ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.restaurant_source_profiles FROM PUBLIC, anon, authenticated;
CREATE TEMP TABLE dinefinder_complete_workbook ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset($workbook_data$
{json_payload}
$workbook_data$::jsonb) AS x(source_row integer, discovery_eligible boolean, raw_data jsonb);
INSERT INTO public.restaurant_source_profiles (
  source_sha256, source_file, source_sheet, source_row, discovery_eligible,
  {column_names}, raw_data
)
SELECT {literal(digest)}, {literal(source.name)}, {literal(sheet.title)}, source_row, discovery_eligible,
{expressions}, raw_data
FROM dinefinder_complete_workbook
ON CONFLICT (source_sha256, source_sheet, source_row) DO NOTHING;

CREATE TEMP TABLE dinefinder_food_outlets ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset($food_outlets$
{json_selected}
$food_outlets$::jsonb) AS x (
  id uuid, name text, cuisine text, description text, address text,
  latitude double precision, longitude double precision, rating double precision,
  price_level integer, opening_hours text, tags text[], is_halal boolean,
  phone text, website text, review_count integer, google_place_id text,
  source_url text, source_name text, source_file text, business_status text
);
-- Match prior import UUID, then Google place ID, then identical name within 50 metres.
-- Coordinates of existing records are never overwritten (including your manual corrections).
DO $merge_profiles$
DECLARE
  i record;
  target_id uuid;
BEGIN
  FOR i IN SELECT * FROM dinefinder_food_outlets ORDER BY id LOOP
    SELECT r.id INTO target_id
    FROM public.restaurants r
    WHERE r.id=i.id OR r.google_place_id=i.google_place_id OR (
      lower(regexp_replace(btrim(r.name),'\\s+',' ','g')) = lower(regexp_replace(btrim(i.name),'\\s+',' ','g'))
      AND extensions.st_dwithin(r.location,
        extensions.st_setsrid(extensions.st_makepoint(i.longitude,i.latitude),4326)::extensions.geography,50)
    )
    ORDER BY CASE WHEN r.id=i.id THEN 0 WHEN r.google_place_id=i.google_place_id THEN 1 ELSE 2 END, r.id
    LIMIT 1;
    IF target_id IS NULL THEN
      INSERT INTO public.restaurants (
        id,name,cuisine,description,address,latitude,longitude,rating,price_level,opening_hours,
        tags,is_halal,phone,website,review_count,google_place_id,source_url,source_name,source_file,business_status,imported_at
      ) VALUES (
        i.id,i.name,i.cuisine,i.description,i.address,i.latitude,i.longitude,i.rating,i.price_level,i.opening_hours,
        i.tags,i.is_halal,i.phone,i.website,i.review_count,i.google_place_id,i.source_url,i.source_name,i.source_file,i.business_status,now()
      ) RETURNING id INTO target_id;
    ELSE
      UPDATE public.restaurants r SET
        cuisine=coalesce(nullif(btrim(r.cuisine),''),i.cuisine),
        description=coalesce(nullif(r.description,''),i.description,''),
        address=coalesce(nullif(r.address,''),i.address,''),
        rating=CASE WHEN coalesce(r.rating,0)=0 THEN i.rating ELSE r.rating END,
        opening_hours=CASE WHEN coalesce(r.opening_hours,'') IN ('','Hours unavailable','Check opening hours with the restaurant')
                           THEN i.opening_hours ELSE r.opening_hours END,
        phone=coalesce(nullif(r.phone,''),i.phone),
        website=coalesce(nullif(r.website,''),i.website),
        review_count=coalesce(r.review_count,i.review_count),
        google_place_id=coalesce(nullif(r.google_place_id,''),i.google_place_id),
        source_url=coalesce(nullif(r.source_url,''),i.source_url),
        source_name=coalesce(nullif(r.source_name,''),i.source_name),
        source_file=coalesce(nullif(r.source_file,''),i.source_file),
        business_status=coalesce(nullif(r.business_status,''),i.business_status),
        imported_at=coalesce(r.imported_at,now())
      WHERE r.id=target_id;
    END IF;
    UPDATE public.restaurant_source_profiles SET restaurant_id=target_id
    WHERE source_sha256={literal(digest)} AND "place_id"=i.google_place_id;
  END LOOP;
END;
$merge_profiles$;
DO $verify_import$
BEGIN
  IF (SELECT count(*) FROM public.restaurant_source_profiles WHERE source_sha256={literal(digest)})<>328 THEN
    RAISE EXCEPTION 'Import verification failed: expected 328 source profiles';
  END IF;
  IF (SELECT count(*) FROM public.restaurant_source_profiles WHERE source_sha256={literal(digest)} AND discovery_eligible AND restaurant_id IS NOT NULL)<>328 THEN
    RAISE EXCEPTION 'Import verification failed: expected 328 linked food outlets';
  END IF;
END;
$verify_import$;
NOTIFY pgrst, 'reload schema';
COMMIT;
SELECT count(*) AS source_listings,
       count(*) FILTER (WHERE discovery_eligible AND restaurant_id IS NOT NULL) AS linked_food_outlets,
       65 AS original_columns_per_listing
FROM public.restaurant_source_profiles WHERE source_sha256={literal(digest)};
'''
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(sql)
    print(json.dumps({'file': str(output), 'source_rows': len(payload), 'source_columns': len(headers),
                      'linked_food_outlets': len(selected), 'sha256': digest, 'sql_bytes': output.stat().st_size}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('workbook', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'supabase/imports/20261027_full_restaurant_profiles.sql')
    args = parser.parse_args()
    build(args.workbook, args.output)
