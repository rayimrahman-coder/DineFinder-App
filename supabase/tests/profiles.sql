\set ON_ERROR_STOP on
-- Run only in a disposable test database after setup.sql or both migrations.
-- Supabase Auth is stubbed by the local test harness; this transaction rolls back.
begin;
insert into auth.users (id, email, raw_user_meta_data) values
  ('20000000-0000-4000-8000-000000000001', 'one@example.test', '{"display_name":"  First Diner  "}'),
  ('20000000-0000-4000-8000-000000000002', 'second@example.test', '{}'),
  ('20000000-0000-4000-8000-000000000003', null, '{}');

do $trigger_checks$
begin
  if (select display_name from public.profiles where id = '20000000-0000-4000-8000-000000000001') <> 'First Diner' then
    raise exception 'Signup trigger should use the trimmed metadata display name';
  end if;
  if (select display_name from public.profiles where id = '20000000-0000-4000-8000-000000000002') <> 'second' then
    raise exception 'Signup trigger should fall back to the email prefix';
  end if;
  if (select display_name from public.profiles where id = '20000000-0000-4000-8000-000000000003') <> 'Diner' then
    raise exception 'An Auth user without an email needs a safe display name';
  end if;
  delete from auth.users where id = '20000000-0000-4000-8000-000000000003';
  if exists (select 1 from public.profiles where id = '20000000-0000-4000-8000-000000000003') then
    raise exception 'Deleting an Auth user should cascade to its profile';
  end if;
end;
$trigger_checks$;

-- Prove that the restrictive owner guards remain effective even when an older
-- application left a permissive policy behind. These test policies roll back.
create policy "Test legacy broad read" on public.profiles
  for select to authenticated using (true);
create policy "Test legacy broad update" on public.profiles
  for update to authenticated using (true) with check (true);

set local role authenticated;
set local request.jwt.claim.sub = '20000000-0000-4000-8000-000000000001';
set local request.jwt.claims = '{"sub":"20000000-0000-4000-8000-000000000001","role":"authenticated"}';

do $owner_checks$
declare
  changed integer;
begin
  if (select count(*) from public.profiles) <> 1 then
    raise exception 'A signed-in user should see only their own profile';
  end if;
  update public.profiles set display_name = 'Updated Diner'
    where id = '20000000-0000-4000-8000-000000000001';
  get diagnostics changed = row_count;
  if changed <> 1 then raise exception 'A user should be able to rename their own profile'; end if;

  update public.profiles set display_name = 'Not allowed'
    where id = '20000000-0000-4000-8000-000000000002';
  get diagnostics changed = row_count;
  if changed <> 0 then raise exception 'A user modified someone else''s profile'; end if;

  begin
    update public.profiles set id = '20000000-0000-4000-8000-000000000004';
    raise exception 'Changing profile IDs must be denied';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.profiles set created_at = now();
    raise exception 'Changing profile creation timestamps must be denied';
  exception when insufficient_privilege then null;
  end;
  begin
    insert into public.profiles (id, display_name)
      values ('20000000-0000-4000-8000-000000000004', 'Forbidden');
    raise exception 'Clients must not create profile rows directly';
  exception when insufficient_privilege then null;
  end;
  begin
    delete from public.profiles;
    raise exception 'Clients must not delete profile rows directly';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.profiles set display_name = repeat('x', 81);
    raise exception 'An oversized display name was accepted';
  exception when check_violation then null;
  end;
  begin
    update public.profiles set display_name = '   ';
    raise exception 'A blank display name was accepted';
  exception when check_violation then null;
  end;
end;
$owner_checks$;

set local role anon;
do $guest_checks$
declare
  matches integer;
begin
  begin
    perform * from public.profiles;
    raise exception 'Guests must not read private profiles';
  exception when insufficient_privilege then null;
  end;
  select count(*) into matches
  from public.nearby_restaurants(5.280285448386224, 115.17640486697412, 1000)
  where id in ('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002')
    and is_halal is true;
  if matches <> 2 then raise exception 'Both supplied HALAL food courts should be discoverable within 1 km'; end if;
  if exists (
    select 1 from public.restaurants
    where id in ('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002')
      and (rating <> 0 or address <> '' or opening_hours <> 'Hours unavailable')
  ) then raise exception 'Unknown listing details must not be fabricated'; end if;
end;
$guest_checks$;
rollback;
