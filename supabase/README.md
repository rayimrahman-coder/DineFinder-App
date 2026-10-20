# Restaurant data and optional accounts

Supabase supplies nearby restaurants, profiles, and optional email/password
accounts. Walking routes come directly from the public FOSSGIS OpenStreetMap
pedestrian routing service. No routing key or deployed Edge Function is needed.

## Connect a Supabase project

1. Create a Supabase project. In its SQL editor run
   `migrations/202610200001_restaurants.sql`, or link the project with the Supabase
   CLI and run `supabase db push` from the repository root.
2. Add trusted restaurant records to `public.restaurants` using real WGS84
   latitude/longitude. Discovery searches an indexed geography column.
3. Copy the root `.env.example.json` to ignored `env.local.json` and set your
   project URL and **public publishable key**. An older anon client key also
   works. Never put a service-role key or database password in the app.
4. Run `flutter run --dart-define-from-file=env.local.json`. In Android Studio,
   put that flag in the Flutter run configuration's Additional run args.

Supabase requires its public client key even for public restaurant reads; it is
configuration, not a secret admin credential. The app also accepts the legacy
`SUPABASE_ANON_KEY` define. No Google Maps, OpenRouteService, or other paid maps
API key is used.

The migration enables RLS and public read-only profile access for `anon` and
`authenticated` roles. Clients can execute `nearby_restaurants` and read public
profiles; writes require an authorized backend or dashboard. The RPC validates
coordinates/radii, returns up to 200 rows sorted by distance, and uses a GiST
index. Its supported radius is 100–5000 m; this app always asks for 1000 m.
Discovery distance is geographic distance, separate from walking-route length.

If PostGIS already exists in a schema other than `extensions`, inspect and adapt
this migration before applying it. Do not drop an existing extension or user data.

`seed.sql` is optional **fictional development data**, matching the offline demo
near Kuala Lumpur. Ratings, schedules, names, and addresses are samples. Use real
listings for a live project; seed rows will not appear near a user outside their
geographic radius.

## Optional sign-in

Enable the email/password provider in Supabase Auth settings. The welcome screen
has explicit sign-in and account creation, plus Continue as guest. If email
confirmation is enabled, new users confirm their email before signing in.
Accounts are authenticated by Supabase; DineFinder never stores passwords in local
preferences. Guest access uses public read-only restaurant access, without a fake
account or anonymous Auth requirement.

Without project configuration, guests can use the clearly labeled offline demo.
Live reads and authentication must be checked against your configured project.

## Routing and privacy

Navigation uses the `routed-foot` pedestrian graph at
`https://routing.openstreetmap.de/`. It sends only the requested origin and
destination, needs internet, and reports errors when a route cannot be calculated.
It never replaces a failed live request with fictional geometry. The shared public
service has usage limits; use an appropriately hosted OSRM pedestrian service if
deployment traffic exceeds community-service policies.

Nearby-search coordinates go to Supabase. Route endpoints go to the routing
service when Navigate is selected. Camera frames remain on the device. Routing
data is © OpenStreetMap contributors under ODbL; routing is provided by FOSSGIS.

## Database checks

```sh
bash supabase/tests/run-postgis.sh
```

This uses a pinned disposable PostGIS Docker image, without a public database
port or existing volumes. It checks the migration, radius boundaries, coordinate
validation, nearest ordering, and denied public writes. It does not deploy a
project or validate Supabase configuration, email delivery, or device sensors.
