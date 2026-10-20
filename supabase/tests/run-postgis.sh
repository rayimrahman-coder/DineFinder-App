#!/usr/bin/env bash
# Local integration checks only: no project credentials, public ports, or volumes.
# Tests both migration upgrades and the SQL-editor single-paste path.
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
image=postgis/postgis@sha256:01a6a70e41e6c4467c8f55f6063555ed72db2d6662cd0d571040d42eadaeb6f6
name=dinefinder-db-test-$$
started=0
cleanup() {
  local result=$?
  if [ "$started" -eq 1 ]; then
    if [ "$result" -ne 0 ]; then
      docker --host=unix:///var/run/docker.sock logs --tail 50 "$name" 2>&1 || true
      docker --host=unix:///var/run/docker.sock inspect "$name" \
        --format '{{.State.Status}} {{.State.OOMKilled}} {{.State.ExitCode}}' || true
    fi
    docker --host=unix:///var/run/docker.sock rm -f "$name" >/dev/null 2>&1 || true
  fi
  return "$result"
}
trap cleanup EXIT
unset DOCKER_HOST DOCKER_CONTEXT DOCKER_TLS DOCKER_TLS_VERIFY DOCKER_CERT_PATH
docker --host=unix:///var/run/docker.sock run --detach --name "$name" -e POSTGRES_HOST_AUTH_METHOD=trust "$image" >/dev/null
started=1
ready=0
# The image starts a temporary Unix-socket-only server during initialization.
# Wait on TCP inside the container so that temporary readiness cannot race a restart.
for _ in $(seq 1 60); do
  if docker --host=unix:///var/run/docker.sock exec "$name" \
      pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  docker --host=unix:///var/run/docker.sock logs "$name"
  exit 1
fi
sql() {
  docker --host=unix:///var/run/docker.sock exec --interactive "$name" \
    psql -U postgres -v ON_ERROR_STOP=1 "$@"
}
sql <<'SQL'
create role anon;
create role authenticated;
create database migration_test template template0;
create database setup_test template template0;
SQL
# Auth is simulated only in these disposable databases. This never creates an
# auth schema, password store, or fake Auth server in a connected Supabase project.
for database in migration_test setup_test; do
  sql -d "$database" <<'SQL'
create schema auth;
create table auth.users (
  id uuid primary key,
  email text,
  raw_user_meta_data jsonb default '{}'::jsonb,
  created_at timestamptz default now()
);
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
grant usage on schema auth to anon, authenticated;
insert into auth.users (id, email, raw_user_meta_data)
values ('30000000-0000-4000-8000-000000000001','existing@example.test','{"display_name":"Existing Diner"}');
SQL
done
sql -d migration_test < "$repo/supabase/migrations/202610200001_restaurants.sql"
sql -d migration_test < "$repo/supabase/seed.sql"
sql -d migration_test < "$repo/supabase/tests/restaurants.sql"
sql -d migration_test < "$repo/supabase/migrations/202610210001_profiles_and_umskal.sql"
sql -d migration_test < "$repo/supabase/migrations/202610210001_profiles_and_umskal.sql"
sql -d migration_test < "$repo/supabase/tests/profiles.sql"
for _ in 1 2; do sql -d setup_test < "$repo/supabase/setup.sql"; done
sql -d setup_test < "$repo/supabase/tests/profiles.sql"
for database in migration_test setup_test; do
  sql -d "$database" <<'SQL'
do $$ begin
  if (select display_name from public.profiles where id='30000000-0000-4000-8000-000000000001') <> 'Existing Diner' then
    raise exception 'Existing Auth users were not backfilled';
  end if;
end $$;
SQL
done
printf '%s\n' 'PostGIS radius checks, profile RLS, signup/backfill, HALAL discovery, and repeated setup/migration passed.'
