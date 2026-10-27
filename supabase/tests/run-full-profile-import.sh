#!/usr/bin/env bash
# Disposable PostGIS integration test; never connects to the live project.
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
image=postgis/postgis@sha256:01a6a70e41e6c4467c8f55f6063555ed72db2d6662cd0d571040d42eadaeb6f6
name=dinefinder-profile-import-test-$$
unset DOCKER_HOST DOCKER_CONTEXT DOCKER_TLS DOCKER_TLS_VERIFY DOCKER_CERT_PATH
docker --host=unix:///var/run/docker.sock run --detach --name "$name" -e POSTGRES_HOST_AUTH_METHOD=trust "$image" >/dev/null
trap 'docker --host=unix:///var/run/docker.sock rm -f "$name" >/dev/null 2>&1 || true' EXIT
sql() { docker --host=unix:///var/run/docker.sock exec --interactive "$name" psql -q -U postgres -v ON_ERROR_STOP=1 "$@"; }
ready=0
for _ in $(seq 1 60); do
  if docker --host=unix:///var/run/docker.sock exec "$name" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[ "$ready" -eq 1 ] || { docker --host=unix:///var/run/docker.sock logs "$name"; exit 1; }
sql <<'SQL'
CREATE ROLE anon;
CREATE ROLE authenticated;
CREATE DATABASE workbook_fresh;
CREATE DATABASE workbook_existing;
SQL
for _ in 1 2; do sql -d workbook_fresh < "$repo/supabase/imports/20261027_full_restaurant_profiles.sql"; done
sql -d workbook_fresh <<'SQL'
DO $$ BEGIN
  IF (SELECT count(*) FROM public.restaurants)<>328 THEN RAISE EXCEPTION 'Duplicate/missing food outlets'; END IF;
  IF (SELECT count(*) FROM public.restaurant_source_profiles)<>328 THEN RAISE EXCEPTION 'Duplicate/missing source profiles'; END IF;
  IF EXISTS (SELECT 1 FROM public.restaurant_source_profiles s WHERE (SELECT count(*) FROM jsonb_object_keys(s.raw_data))<>65)
    THEN RAISE EXCEPTION 'Missing original source columns'; END IF;
  IF EXISTS (SELECT 1 FROM public.restaurant_source_profiles WHERE NOT discovery_eligible OR business_status<>'OPERATIONAL')
    THEN RAISE EXCEPTION 'Non-operational or excluded business was imported'; END IF;
  IF has_table_privilege('anon','public.restaurant_source_profiles','SELECT')
     OR has_table_privilege('authenticated','public.restaurant_source_profiles','SELECT')
     THEN RAISE EXCEPTION 'Raw archive exposed through app roles'; END IF;
END $$;
SQL
# Older manually maintained schema; the import must add missing profile columns.
sql -d workbook_existing <<'SQL'
CREATE TABLE public.restaurants (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL,
  cuisine text, latitude double precision NOT NULL, longitude double precision NOT NULL,
  rating double precision, address text
);
INSERT INTO public.restaurants VALUES
  ('10000000-0000-4000-8000-000000000002','Anjung Selera Sungai Pagar','Food court',5.283813104239185,115.17329255729997,4.7,'Owner-maintained address'),
  ('20000000-0000-4000-8000-000000000001','1 Go Cafe & Restaurant','Owner cuisine',5.2858836,115.2572679,4.9,'Owner-maintained cafe address');
SQL
sql -d workbook_existing < "$repo/supabase/imports/20261027_full_restaurant_profiles.sql"
# Reproduce accidental schema removals reported by the phone. Import repairs them.
sql -d workbook_existing <<'SQL'
ALTER TABLE public.restaurants DROP COLUMN tags, DROP COLUMN is_halal, DROP COLUMN opening_hours;
SQL
sql -d workbook_existing < "$repo/supabase/imports/20261027_full_restaurant_profiles.sql"
sql -d workbook_existing <<'SQL'
DO $$ BEGIN
  IF (SELECT count(*) FROM public.restaurants)<>329 THEN RAISE EXCEPTION 'Existing name/proximity match duplicated'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.restaurants WHERE id='10000000-0000-4000-8000-000000000002'
    AND latitude=5.283813104239185 AND longitude=115.17329255729997 AND rating=4.7 AND address='Owner-maintained address')
    THEN RAISE EXCEPTION 'Owner-maintained Anjung record changed'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.restaurants WHERE id='20000000-0000-4000-8000-000000000001'
    AND latitude=5.2858836 AND rating=4.9 AND cuisine='Owner cuisine' AND address='Owner-maintained cafe address')
    THEN RAISE EXCEPTION 'Manual profile overwritten'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.restaurant_source_profiles WHERE "name"='1 Go Cafe & Restaurant'
    AND restaurant_id='20000000-0000-4000-8000-000000000001')
    THEN RAISE EXCEPTION 'Archive failed to link existing UUID'; END IF;
END $$;
SET ROLE anon;
SELECT name FROM public.nearby_restaurants(5.283813104239185,115.17329255729997,1000);
RESET ROLE;
SQL
# Optional original-workbook comparison: every original cell value, including NULLs.
if [ "$#" -gt 0 ]; then
  comparison=$(mktemp)
  trap 'rm -f "$comparison"; docker --host=unix:///var/run/docker.sock rm -f "$name" >/dev/null 2>&1 || true' EXIT
  sql -d workbook_fresh -c "COPY (SELECT source_row, raw_data FROM public.restaurant_source_profiles ORDER BY source_row) TO STDOUT WITH CSV" > "$comparison"
  python3 - "$1" "$comparison" <<'PY'
import csv,json,sys,openpyxl
sheet=openpyxl.load_workbook(sys.argv[1],read_only=True,data_only=True).active
rows=list(sheet.values); headers=rows[0]
all_rows={i:dict(zip(headers,r)) for i,r in enumerate(rows[1:],2) if any(v is not None for v in r)}
with open(sys.argv[2]) as f: actual={int(row):json.loads(data) for row,data in csv.reader(f)}
expected={i:all_rows[i] for i in actual}
assert len(actual)==328
assert expected==actual, 'Workbook data does not match database archive'
print(f'All {len(expected)} rows × {len(headers)} source fields match original workbook cell values.')
PY
fi
printf '%s\n' 'Full restaurant-profile import passed: repeat import, schema repair, deduplication, manual-coordinate preservation, archive access and app radius search.'
