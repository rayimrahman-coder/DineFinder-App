#!/usr/bin/env bash
# Disposable local database only; no live Supabase connection.
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
imports=${1:?Pass the directory containing 00_create_table.sql and import batches}
name=dinefinder-apify-app-$$
image=postgis/postgis@sha256:01a6a70e41e6c4467c8f55f6063555ed72db2d6662cd0d571040d42eadaeb6f6
unset DOCKER_HOST DOCKER_CONTEXT DOCKER_TLS DOCKER_TLS_VERIFY DOCKER_CERT_PATH
docker --host=unix:///var/run/docker.sock run --detach --name "$name" -e POSTGRES_HOST_AUTH_METHOD=trust "$image" >/dev/null
trap 'docker --host=unix:///var/run/docker.sock rm -f "$name" >/dev/null 2>&1 || true' EXIT
sql() { docker --host=unix:///var/run/docker.sock exec --interactive "$name" psql -q -U postgres -v ON_ERROR_STOP=1; }
ready=0
for _ in $(seq 1 60); do
 if docker --host=unix:///var/run/docker.sock exec "$name" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then ready=1; break; fi
 sleep 1
done
[ "$ready" -eq 1 ] || exit 1
sql <<'SQL'
CREATE ROLE anon;
CREATE ROLE authenticated;
CREATE ROLE service_role;
SQL
for _ in 1 2; do
 for part in "$imports"/*.sql; do sql < "$part"; done
 sql < "$repo/supabase/migrations/202610290001_apify_app_endpoints.sql"
done
sql <<'SQL'
-- Known geometry and malformed optional review fields, isolated test fixtures.
INSERT INTO public.apify_food_outlets(google_place_id,name,latitude,longitude,business_status,source_profile,reviews)
VALUES ('test-origin','Origin',0,0,'OPERATIONAL','{"owner_private":"never expose"}',
 '[{"reviewId":"a","name":"Diner","stars":4,"text":"Real test comment","reviewerId":"private-reviewer"},{"stars":0,"text":"No valid rating"},{"text":"  "}]'),
 ('test-near','Temporary',0.005,0,'TEMPORARILY_CLOSED','{}','[]'),
 ('test-far','Outside',0.02,0,'OPERATIONAL','{}','[]');
SET ROLE anon;
DO $$ DECLARE nearby jsonb; profile jsonb; target uuid; BEGIN
 nearby:=public.apify_nearby_restaurants(0,0,1000);
 IF jsonb_array_length(nearby)<>2 OR nearby->0->>'name'<>'Origin'
 OR nearby->1->>'description'<>'Temporarily closed'
 THEN RAISE EXCEPTION 'Radius/order/status regression: %',nearby; END IF;
 target:=(nearby->0->>'id')::uuid;
 profile:=public.apify_restaurant_profile_details(target);
 IF jsonb_array_length(profile->'reviews')<>2
 OR profile->'reviews'->0->>'text'<>'Real test comment'
 OR profile->'reviews'->1->>'rating' IS NOT NULL
 THEN RAISE EXCEPTION 'Review projection failed: %',profile; END IF;
 IF profile::text LIKE '%private-reviewer%' OR profile::text LIKE '%owner_private%'
 OR profile::text LIKE '%never expose%' THEN RAISE EXCEPTION 'Private source exposed'; END IF;
 IF has_table_privilege('anon','public.apify_food_outlets','SELECT')
 OR has_table_privilege('authenticated','public.apify_food_outlets','SELECT')
 THEN RAISE EXCEPTION 'Raw table exposed'; END IF;
 IF public.apify_restaurant_profile_details('00000000-0000-0000-0000-000000000000') IS NOT NULL
 THEN RAISE EXCEPTION 'Missing profile invented'; END IF;
 BEGIN PERFORM public.apify_nearby_restaurants(91,0,1000);
 RAISE EXCEPTION 'Invalid coordinate accepted'; EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN PERFORM public.apify_nearby_restaurants(0,0,1001);
 RAISE EXCEPTION 'Oversized radius accepted'; EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
 BEGIN PERFORM public.apify_nearby_restaurants('NaN',0,1000);
 RAISE EXCEPTION 'NaN accepted'; EXCEPTION WHEN invalid_parameter_value THEN NULL; END;
END $$;
RESET ROLE;
-- Exercise every imported profile and every source location as the public app role.
DO $$ DECLARE r record; result jsonb; details jsonb; BEGIN
 FOR r IN SELECT * FROM public.apify_food_outlets LOOP
  SET LOCAL ROLE anon;
  result:=public.apify_nearby_restaurants(r.latitude,r.longitude,1000);
  details:=public.apify_restaurant_profile_details(r.id);
  RESET ROLE;
  IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(result) item WHERE item->>'id'=r.id::text)
  THEN RAISE EXCEPTION 'Outlet not discoverable at own coordinates: %',r.name; END IF;
  IF jsonb_array_length(details->'reviews')>10 THEN RAISE EXCEPTION 'Review bound exceeded'; END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(details->'reviews') v WHERE length(v->>'text')>5000)
  THEN RAISE EXCEPTION 'Review text limit exceeded'; END IF;
 END LOOP;
END $$;
SQL
printf '%s\n' 'Apify app SQL passed: repeat imports/migration, radius and ordering, temporary closure, public RPCs, private raw data, review sanitization and all imported profiles.'
