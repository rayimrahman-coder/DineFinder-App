-- After applying the existing-table repair, public discovery must work again.
set role anon;
do $$
begin
  if (select count(*) from public.nearby_restaurants(
      5.280285448386224,115.17640486697412,1000)
      where id in ('10000000-0000-4000-8000-000000000001',
                   '10000000-0000-4000-8000-000000000002')) <> 2 then
    raise exception 'Repair did not restore public discovery of both food courts';
  end if;
  if (select count(*) from public.restaurants
      where id in ('10000000-0000-4000-8000-000000000001',
                   '10000000-0000-4000-8000-000000000002') and is_halal) <> 2 then
    raise exception 'Existing owner-supplied halal flags changed';
  end if;
end $$;
reset role;
