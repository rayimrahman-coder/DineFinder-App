-- Apply after 202610200001_restaurants.sql. Supabase Auth owns passwords;
-- public.profiles contains only a user's editable display name and metadata.
do $postgis_schema$
declare
  installed_schema text;
begin
  select n.nspname into installed_schema
  from pg_catalog.pg_extension e
  join pg_catalog.pg_namespace n on n.oid = e.extnamespace
  where e.extname = 'postgis';
  if installed_schema is distinct from 'extensions' then
    raise exception 'DineFinder requires PostGIS in the extensions schema (found: %). Inspect your project and adapt the qualified PostGIS references before running setup; do not drop an existing extension.',
      coalesce(installed_schema, 'not installed');
  end if;
end;
$postgis_schema$;

alter table public.restaurants add column if not exists is_halal boolean;
comment on column public.restaurants.is_halal is
  'Nullable when halal status is unknown. Verify and maintain listing data separately.';

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
    select r.id, r.name, r.cuisine, r.description, r.address,
      r.latitude, r.longitude, r.rating, r.price_level, r.emoji,
      r.accent_hex, r.tags, r.opening_hours,
      extensions.st_distance(r.location, origin) as distance_meters,
      r.is_halal
    from public.restaurants r
    where extensions.st_dwithin(r.location, origin, $3)
    order by extensions.st_distance(r.location, origin), r.id
    limit 200;
end;
$nearby$;

revoke all on function public.nearby_restaurants(double precision, double precision, double precision) from public;
grant execute on function public.nearby_restaurants(double precision, double precision, double precision) to anon, authenticated;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (
    length(display_name) between 1 and 80 and length(btrim(display_name)) > 0
  ),
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
revoke all on public.profiles from public, anon, authenticated;
revoke all (id, display_name, created_at) on public.profiles from public, anon, authenticated;
grant select on public.profiles to authenticated;
grant update (display_name) on public.profiles to authenticated;

drop policy if exists "Read own DineFinder profile" on public.profiles;
create policy "Read own DineFinder profile"
  on public.profiles for select to authenticated
  using ((select auth.uid()) = id);

drop policy if exists "Update own DineFinder display name" on public.profiles;
create policy "Update own DineFinder display name"
  on public.profiles for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

-- Restrictive guards keep an older permissive policy from broadening access.
drop policy if exists "Restrict DineFinder profile reads to owner" on public.profiles;
create policy "Restrict DineFinder profile reads to owner"
  on public.profiles as restrictive for select to authenticated
  using ((select auth.uid()) = id);
drop policy if exists "Restrict DineFinder profile updates to owner" on public.profiles;
create policy "Restrict DineFinder profile updates to owner"
  on public.profiles as restrictive for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

create or replace function public.handle_dinefinder_profile()
returns trigger language plpgsql security definer set search_path = ''
as $profile_trigger$
declare
  profile_name text;
begin
  profile_name := left(coalesce(
    case when jsonb_typeof(new.raw_user_meta_data -> 'display_name') = 'string'
      then nullif(btrim(new.raw_user_meta_data ->> 'display_name'), '') end,
    nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
    'Diner'
  ), 80);
  insert into public.profiles (id, display_name)
    values (new.id, profile_name)
    on conflict (id) do nothing;
  return new;
end;
$profile_trigger$;
revoke all on function public.handle_dinefinder_profile() from public, anon, authenticated;

drop trigger if exists on_auth_user_created_dinefinder_profile on auth.users;
create trigger on_auth_user_created_dinefinder_profile
  after insert on auth.users
  for each row execute function public.handle_dinefinder_profile();

-- Existing Auth accounts gain profiles without changing an existing profile.
insert into public.profiles (id, display_name, created_at)
select u.id, left(coalesce(
    case when jsonb_typeof(u.raw_user_meta_data -> 'display_name') = 'string'
      then nullif(btrim(u.raw_user_meta_data ->> 'display_name'), '') end,
    nullif(split_part(coalesce(u.email, ''), '@', 1), ''),
    'Diner'
  ), 80), coalesce(u.created_at, now())
from auth.users u
on conflict (id) do nothing;

-- Only the names, coordinates, and HALAL status below were supplied by the user.
-- Zero ratings, blank addresses/descriptions, and unavailable hours mean unknown.
-- Existing verified ratings, prices, addresses, and schedules survive re-runs.
insert into public.restaurants as existing (
  id, name, cuisine, description, address, latitude, longitude,
  rating, price_level, opening_hours, tags, is_halal
) values
  ('10000000-0000-4000-8000-000000000001', 'Cafe UMSKAL', 'Food court', '', '',
   5.280285448386224, 115.17640486697412, 0, 1, 'Hours unavailable', array['HALAL'], true),
  ('10000000-0000-4000-8000-000000000002', 'Anjung Selera Sungai Pagar', 'Food court', '', '',
   5.28381416215476, 115.17329350236913, 0, 1, 'Hours unavailable', array['HALAL'], true)
on conflict (id) do update set
  name = excluded.name,
  cuisine = excluded.cuisine,
  latitude = excluded.latitude,
  longitude = excluded.longitude,
  is_halal = excluded.is_halal,
  tags = array(
    select distinct tag from unnest(existing.tags || excluded.tags) as tags(tag) order by tag
  );

-- Refresh PostgREST's cached tables and the extended RPC result shape.
notify pgrst, 'reload schema';
