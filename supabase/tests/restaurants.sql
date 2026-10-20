\set ON_ERROR_STOP on
begin;
set local role anon;

do $$
declare
  matches integer;
begin
  select count(*) into matches from public.nearby_restaurants(3.1478, 101.7100, 1000);
  if matches <> 6 then raise exception 'Expected six sample restaurants, got %', matches; end if;

  select count(*) into matches from public.nearby_restaurants(3.1478, 101.7100, 100);
  if matches <> 0 then raise exception 'A 100 m search should exclude the sample listings'; end if;

  if exists (
    select 1 from public.nearby_restaurants(3.1478, 101.7100, 1000)
    where distance_meters <= 0 or distance_meters > 1000
  ) then raise exception 'Returned distance is outside the requested radius'; end if;

  if (select id from public.nearby_restaurants(3.1478, 101.7100, 1000) limit 1)
    <> '00000000-0000-4000-8000-000000000001'::uuid then
    raise exception 'Nearest restaurant should be first';
  end if;

  begin
    perform public.nearby_restaurants(91, 101, 1000);
    raise exception 'Invalid latitude was accepted';
  exception when invalid_parameter_value then null;
  end;
  begin
    perform public.nearby_restaurants(3, 181, 1000);
    raise exception 'Invalid longitude was accepted';
  exception when invalid_parameter_value then null;
  end;
  begin
    perform public.nearby_restaurants(3, 101, 5001);
    raise exception 'Invalid radius was accepted';
  exception when invalid_parameter_value then null;
  end;
  begin
    perform public.nearby_restaurants('NaN'::double precision, 101, 1000);
    raise exception 'NaN latitude was accepted';
  exception when invalid_parameter_value then null;
  end;
  begin
    perform public.nearby_restaurants(3, 101, null);
    raise exception 'Null radius was accepted';
  exception when invalid_parameter_value then null;
  end;
  begin
    insert into public.restaurants(name, cuisine, latitude, longitude)
      values ('Unauthorized listing', 'Test', 3, 101);
    raise exception 'Anonymous clients must not be able to insert restaurants';
  exception when insufficient_privilege then null;
  end;
end;
$$;

set local role authenticated;
do $$
begin
  if (select count(*) from public.restaurants) <> 6 then
    raise exception 'Authenticated readers should see all six sample profiles';
  end if;
  begin
    update public.restaurants set rating = 5;
    raise exception 'Authenticated clients must not be able to modify restaurant ratings';
  exception when insufficient_privilege then null;
  end;
end;
$$;
rollback;
