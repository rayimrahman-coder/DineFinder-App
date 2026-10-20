#!/usr/bin/env bash
set -euo pipefail

# A disposable database only: no project credentials, exposed ports, or volumes.
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
image=postgis/postgis@sha256:01a6a70e41e6c4467c8f55f6063555ed72db2d6662cd0d571040d42eadaeb6f6
container_name="dinefinder-db-test-$$"
started=0
cleanup() {
  if [ "$started" -eq 1 ]; then docker rm -f "$container_name" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
docker run --detach --name "$container_name" \
  -e POSTGRES_HOST_AUTH_METHOD=trust "$image" >/dev/null
started=1
ready=0
for _ in $(seq 1 60); do
  if docker exec "$container_name" pg_isready -U postgres >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  docker logs "$container_name"
  exit 1
fi

# template0 avoids the image's pre-installed public-schema PostGIS so the
# migration can install PostGIS in Supabase's expected extensions schema.
docker exec "$container_name" psql -U postgres -v ON_ERROR_STOP=1 \
  -c 'create role anon;' -c 'create role authenticated;' \
  -c 'create database dinefinder_test template template0;'
docker exec --interactive "$container_name" psql -U postgres -d dinefinder_test -v ON_ERROR_STOP=1 \
  < "$repo_root/supabase/migrations/202610200001_restaurants.sql"
docker exec --interactive "$container_name" psql -U postgres -d dinefinder_test -v ON_ERROR_STOP=1 \
  < "$repo_root/supabase/seed.sql"
docker exec --interactive "$container_name" psql -U postgres -d dinefinder_test -v ON_ERROR_STOP=1 \
  < "$repo_root/supabase/tests/restaurants.sql"
echo 'PostGIS migration, radius search, validation, and role permissions passed.'
