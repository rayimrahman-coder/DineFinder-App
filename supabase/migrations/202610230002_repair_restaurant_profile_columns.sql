-- Fix missing restaurant profile columns on an existing DineFinder database.
-- Paste the WHOLE file into Supabase SQL Editor and Run.
-- Existing IDs, coordinates, listings, Auth users and RLS policies are preserved.
-- Missing ratings/prices remain unknown placeholders, not verified information.
begin;
-- CREATE TABLE IF NOT EXISTS does not add fields to an older existing table.
-- Add only missing fields; never overwrite existing restaurant information.
alter table public.restaurants
  add column if not exists cuisine text not null default 'Restaurant',
  add column if not exists description text not null default '',
  add column if not exists address text not null default '',
  add column if not exists rating double precision not null default 0,
  add column if not exists price_level integer not null default 1,
  add column if not exists emoji text not null default '🍽️',
  add column if not exists accent_hex bigint not null default 4280445331,
  add column if not exists tags text[] not null default '{}',
  add column if not exists opening_hours text not null default 'Hours unavailable',
  add column if not exists is_halal boolean;

-- Older manual tables may also lack the generated geography used by the RPC.
alter table public.restaurants add column if not exists location
  extensions.geography(point, 4326) generated always as (
    extensions.st_setsrid(extensions.st_makepoint(longitude::double precision,
      latitude::double precision), 4326)::extensions.geography
  ) stored;

do $result_type$
begin
  if not exists (
    select 1 from pg_catalog.pg_attribute a
    join pg_catalog.pg_type t on t.typrelid = a.attrelid
    join pg_catalog.pg_namespace n on n.oid = t.typnamespace
    where n.nspname = 'public' and t.typname = 'nearby_restaurant_result'
      and a.attname = 'is_halal' and not a.attisdropped
  ) then
    alter type public.nearby_restaurant_result add attribute is_halal boolean;
  end if;
end;
$result_type$;

create or replace function public.nearby_restaurants(
  latitude double precision,
  longitude double precision,
  radius_meters double precision default 1000
)
returns setof public.nearby_restaurant_result
language plpgsql stable security invoker set search_path = ''
as $nearby$
declare
  origin extensions.geography;
begin
  if $1 is null or not ($1 between -90 and 90)
     or $2 is null or not ($2 between -180 and 180) then
    raise exception 'Latitude or longitude is outside its valid range' using errcode = '22023';
  end if;
  if $3 is null or not ($3 between 100 and 5000) then
    raise exception 'Radius must be between 100 and 5000 meters' using errcode = '22023';
  end if;
  origin := extensions.st_setsrid(extensions.st_makepoint($2, $1), 4326)::extensions.geography;
  return query
    select r.id, r.name::text, coalesce(r.cuisine::text, 'Restaurant'),
      coalesce(r.description::text, ''), coalesce(r.address::text, ''),
      r.latitude::double precision, r.longitude::double precision,
      coalesce(r.rating::double precision, 0), coalesce(r.price_level::integer, 1),
      coalesce(r.emoji::text, '🍽️'), coalesce(r.accent_hex::bigint, 4280445331),
      coalesce(r.tags, '{}'::text[]), coalesce(r.opening_hours::text, 'Hours unavailable'),
      extensions.st_distance(r.location, origin) as distance_meters,
      r.is_halal
    from public.restaurants r
    where extensions.st_dwithin(r.location, origin, $3)
    order by extensions.st_distance(r.location, origin), r.id
    limit 200;
end;
$nearby$;

notify pgrst, 'reload schema';
commit;
