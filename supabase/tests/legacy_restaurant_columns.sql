-- Run only on the disposable local import-test database, never on live data.
-- The runner deliberately removes optional fields to reproduce the legacy schema.
alter table public.restaurants drop column description;
do $$
begin
  perform * from public.nearby_restaurants(5.280285448386224,115.17640486697412,1000);
  raise exception 'Expected the reported missing-description failure';
exception when undefined_column then
  if sqlerrm not like '%description%' then raise; end if;
end $$;
