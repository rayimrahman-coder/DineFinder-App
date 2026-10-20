-- Restaurant discovery. Ratings and opening hours are restaurant profile data;
-- the optional seed contains fictitious examples, not verified listings.
create schema if not exists extensions;
create extension if not exists postgis with schema extensions;
grant usage on schema extensions to anon, authenticated;

create table if not exists public.restaurants (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(name) between 1 and 160),
  cuisine text not null,
  description text not null default '',
  address text not null default '',
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  location extensions.geography(point, 4326) generated always as (
    extensions.st_setsrid(extensions.st_makepoint(longitude, latitude), 4326)::extensions.geography
  ) stored,
  rating double precision not null default 0 check (rating between 0 and 5),
  price_level integer not null default 1 check (price_level between 1 and 4),
  emoji text not null default '🍽️',
  accent_hex bigint not null default 4280445331 check (accent_hex between 0 and 4294967295),
  tags text[] not null default '{}',
  opening_hours text not null default 'Hours unavailable',
  created_at timestamptz not null default now()
);

create index if not exists restaurants_location_gist
  on public.restaurants using gist(location);

alter table public.restaurants enable row level security;
revoke all on public.restaurants from anon, authenticated;
grant select on public.restaurants to anon, authenticated;

create policy "Restaurant profiles are public"
  on public.restaurants for select to anon, authenticated using (true);

-- A named result type allows the RPC's input names to remain latitude and
-- longitude while returning fields with those same names.
create type public.nearby_restaurant_result as (
  id uuid, name text, cuisine text, description text, address text,
  latitude double precision, longitude double precision,
  rating double precision, price_level integer, emoji text,
  accent_hex bigint, tags text[], opening_hours text, distance_meters double precision
);

create or replace function public.nearby_restaurants(
  latitude double precision,
  longitude double precision,
  radius_meters double precision default 1000
)
returns setof public.nearby_restaurant_result
language plpgsql stable security invoker set search_path = ''
as $$
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
      extensions.st_distance(r.location, origin) as distance_meters
    from public.restaurants r
    where extensions.st_dwithin(r.location, origin, $3)
    order by extensions.st_distance(r.location, origin), r.id
    limit 200;
end;
$$;

revoke all on function public.nearby_restaurants(double precision, double precision, double precision) from public;
grant execute on function public.nearby_restaurants(double precision, double precision, double precision) to anon, authenticated;
