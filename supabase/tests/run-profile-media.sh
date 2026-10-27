#!/usr/bin/env bash
# Local disposable database only. No live credentials, public ports or database writes.
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
name=dinefinder-profile-media-$$
image=postgis/postgis@sha256:01a6a70e41e6c4467c8f55f6063555ed72db2d6662cd0d571040d42eadaeb6f6
unset DOCKER_HOST DOCKER_CONTEXT DOCKER_TLS DOCKER_TLS_VERIFY DOCKER_CERT_PATH
docker --host=unix:///var/run/docker.sock run --detach --name "$name" -e POSTGRES_HOST_AUTH_METHOD=trust "$image" >/dev/null
trap 'docker --host=unix:///var/run/docker.sock rm -f "$name" >/dev/null 2>&1 || true' EXIT
sql() { docker --host=unix:///var/run/docker.sock exec --interactive "$name" psql -q -U postgres -v ON_ERROR_STOP=1 "$@"; }
ready=0
for _ in $(seq 1 60); do
  if docker --host=unix:///var/run/docker.sock exec "$name" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[ "$ready" -eq 1 ] || exit 1
sql <<'SQL'
CREATE ROLE anon;
CREATE ROLE authenticated;
CREATE TABLE public.restaurants(id uuid PRIMARY KEY, name text, rating double precision);
INSERT INTO public.restaurants VALUES ('10000000-0000-4000-8000-000000000001','Test cafe',4.6);
SQL
for _ in 1 2; do sql < "$repo/supabase/migrations/202610280001_profile_photos_reviews.sql"; done
sql <<'SQL'
DO $$ DECLARE data jsonb; BEGIN
  data:=public.restaurant_profile_details('10000000-0000-4000-8000-000000000001');
  IF data->>'photo_url' IS NOT NULL OR data->>'review_count' IS NOT NULL OR data->'reviews'<>'[]'::jsonb
    THEN RAISE EXCEPTION 'Unavailable data was invented'; END IF;
  IF public.restaurant_profile_details('10000000-0000-4000-8000-000000000099') IS NOT NULL
    THEN RAISE EXCEPTION 'Missing restaurant returned profile'; END IF;
END $$;
CREATE TABLE public.restaurant_source_profiles(
  restaurant_id uuid, imported_at timestamptz, source_sha256 text, source_row integer,
  photo text, reviews bigint, reviews_per_score_1 bigint, reviews_per_score_2 bigint,
  reviews_per_score_3 bigint, reviews_per_score_4 bigint, reviews_per_score_5 bigint,
  owner_id text
);
ALTER TABLE public.restaurant_source_profiles ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.restaurant_source_profiles FROM PUBLIC,anon,authenticated;
INSERT INTO public.restaurant_source_profiles VALUES
  ('10000000-0000-4000-8000-000000000001',now(),'test',2,'https://images.example.test/source-photo.jpg',12,0,0,0,4,8,'private-owner-marker');
INSERT INTO public.restaurant_reviews(restaurant_id,author_name,rating,review_text,published) VALUES
  ('10000000-0000-4000-8000-000000000001','Test diner',5,'A published test review',true),
  ('10000000-0000-4000-8000-000000000001','Private draft',1,'Unpublished review marker',false);
SET ROLE anon;
DO $$ DECLARE data jsonb; BEGIN
  data:=public.restaurant_profile_details('10000000-0000-4000-8000-000000000001');
  IF data->>'photo_url'<>'https://images.example.test/source-photo.jpg' OR (data->>'review_count')::integer<>12
    THEN RAISE EXCEPTION 'Public media summary mismatch'; END IF;
  IF (data->'score_counts'->>'5')::integer<>8 OR jsonb_array_length(data->'reviews')<>1
    THEN RAISE EXCEPTION 'Wrong breakdown/published review count'; END IF;
  IF data->'reviews'->0->>'text'<>'A published test review'
    THEN RAISE EXCEPTION 'Written review missing'; END IF;
  IF data::text LIKE '%private-owner-marker%' OR data::text LIKE '%Unpublished review marker%'
    THEN RAISE EXCEPTION 'Private source/draft information exposed'; END IF;
  IF has_table_privilege('anon','public.restaurant_source_profiles','SELECT')
     OR has_table_privilege('anon','public.restaurant_reviews','SELECT')
     OR has_table_privilege('authenticated','public.restaurant_reviews','INSERT')
    THEN RAISE EXCEPTION 'Unexpected raw-table access'; END IF;
END $$;
RESET ROLE;
UPDATE public.restaurants SET photo_url='https://images.example.test/manual-photo.jpg';
DO $$ BEGIN
  IF public.restaurant_profile_details('10000000-0000-4000-8000-000000000001')->>'photo_url'<>'https://images.example.test/manual-photo.jpg'
    THEN RAISE EXCEPTION 'Manual photo did not take priority'; END IF;
END $$;
SQL
printf '%s\n' 'Profile-media SQL passed: repeat setup, absent source table, public RPC, real summaries, published reviews, manual photo override and private raw/draft data.'
