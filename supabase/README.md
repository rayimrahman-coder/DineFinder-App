# Live database, accounts, and food courts

Supabase provides restaurant data and email/password accounts for the native
Android app. The complete setup SQL is ready to run, but the live database has
not been updated automatically. A public publishable client key cannot create
tables or run privileged SQL.

## 1. Paste the complete setup into Supabase

1. Open your project in the [Supabase dashboard](https://supabase.com/dashboard).
2. Select **SQL Editor → New query**.
3. Open [setup.sql](setup.sql), copy its entire contents, paste it into the query,
   and select **Run**.

For the supplied project, the public service URL is
`https://oueafqgadqggyinpmyoi.supabase.co`. Use the dashboard account with project
administration rights for SQL setup. The public client configuration is bundled in `supabase.public.properties`.
It is intentionally shipped in the Android app; the SQL editor still requires
your project-owner dashboard login.

The single transaction creates or updates:

- PostGIS-backed `public.restaurants`, its geography index, and the
  `nearby_restaurants` RPC.
- Public read-only restaurant access for guests and signed-in users.
- Private `public.profiles` rows containing `id`, `display_name`, and creation
  metadata, protected by owner-only read/update RLS.
- A sign-up trigger to create profiles for new Auth users, plus backfill for
  existing users.
- The two supplied HALAL food-court records below.

The script is idempotent and has been checked by repeated runs in a disposable
PostGIS database with Auth stubs. It preserves existing profile data and verified
restaurant details when replayed. If PostGIS is already installed outside the
`extensions` schema, it raises an actionable error: inspect and adapt the
qualified references before retrying. Do not drop an existing extension or user
data to force setup through.

Projects managed with the Supabase CLI can use the versioned migrations instead:
`202610200001_restaurants.sql` followed by
`202610210001_profiles_and_umskal.sql`. The latter upgrades an existing base
installation. The single-paste `setup.sql` covers both migrations.

## 2. Enable email/password sign-in

Under **Authentication → Providers**, enable **Email**. The app offers sign-in,
account creation, and Continue as guest. Supabase Auth manages password storage
and verification; `public.profiles` contains display names rather than passwords.
If email confirmation is enabled, users confirm their email before signing in.

A new Auth account receives a profile from the trigger. Existing accounts are
backfilled during setup. Users can read their own profile and update only its
display name; they cannot read other users' profiles or alter the owner ID.
Guests use public restaurant read access without an Auth account or profile row.

Check account creation by looking under **Authentication → Users**, then the
matching row in `public.profiles`. Native sessions are kept only in memory; the
app does not persist passwords or session tokens, and users sign in again after
a process restart or session expiry.

## 3. Connect the Android app

The native app already reads its project URL and public publishable key from
`supabase.public.properties`. No Google or routing key and no manually created
local properties file are needed to run the project.

The public client key cannot create tables. Run the SQL above once if the live
database has not been set up. Then sync Gradle and run the app. To switch projects,
copy `supabase.properties.example` to ignored `supabase.properties` and enter
both public values; nonblank overrides take priority. Never use a service-role
key or database password in either client configuration file.

## Supplied food courts and search behavior

| Food court | Latitude | Longitude | Supplied status |
| --- | --- | --- | --- |
| Cafe UMSKAL | 5.280285448386224 | 115.17640486697412 | HALAL |
| Anjung Selera Sungai Pagar | 5.28381416215476 | 115.17329350236913 | HALAL |

Names, coordinates, and HALAL flags came from the project owner. Ratings,
addresses, opening hours, and prices were not supplied or independently verified.
The database's required fallback price field is not a verified price listing.
Existing verified profile values survive replay of the supplied-location update.

Discovery uses real WGS84 coordinates and a fixed **1000 m** geographic radius.
The RPC accepts 100–5000 m, validates coordinates, returns up to 200 rows sorted
by distance, and uses a GiST index. Search distance is separate from walking-route
length. A phone outside the food courts' radius will not see those records.
`seed.sql` is an optional, separate collection of fictional Kuala Lumpur listings;
it is not part of the live `setup.sql` data.

## Routing and privacy

Walking directions use the public FOSSGIS OpenStreetMap `routed-foot` service at
`https://routing.openstreetmap.de/`. No OpenRouteService key, walking-route Edge
Function, or anonymous Auth setup is needed. Routes require internet and may be
limited by community-service availability, usage policies, or pedestrian-map
coverage. Failures are reported instead of replaced with fictional paths.

Nearby-search coordinates go to Supabase. Selecting Navigate sends route
endpoints to FOSSGIS. Camera frames remain on the device. Data is ©
[OpenStreetMap contributors](https://www.openstreetmap.org/copyright), under ODbL.
Discovery uses approximate GPS/compass bearings. Optional navigation uses local
ARCore ground-plane anchors; this does not geolocate restaurant entrances or
detect obstacles or safe crossings. No Google API key is required.

The cloud environment's draft includes the Supabase and routing hosts, but the
last observed runtime policy had not applied those additions. Draft saving does
not deploy this schema or prove live connectivity. Real Supabase authentication,
email delivery, reads, and physical-phone behavior remain to be checked.

## Local database checks

```sh
bash supabase/tests/run-postgis.sh
```

The script uses a pinned disposable PostGIS Docker image without exposing a
public database port or mounting existing data. It checks repeated full setup
and migration runs, both supplied food courts, restaurant radius/order/validation,
public write denial, profile RLS, sign-up creation, backfill, and owner-only
profile updates. It uses Auth stubs for local SQL checks and does not deploy a
Supabase project or validate actual account email delivery.

## Repair an older restaurant table

If nearby search reports `42703: column r.description does not exist`, the live
table predates fields required by the RPC. `CREATE TABLE IF NOT EXISTS` alone
does not upgrade that table. Run the complete
[existing-table repair](migrations/202610230002_repair_restaurant_profile_columns.sql)
in SQL Editor. It adds only missing profile/geography columns, keeps existing
restaurant records and policies, and casts legacy numeric/varchar fields to the
RPC's declared output types without changing stored types or values. New halal
values stay unknown, and rating/price defaults are unknown-data placeholders.
The repair does not modify Auth users or profiles. No Android reinstall is needed.

The corrected setup and spreadsheet import also reconcile existing missing
fields. Local checks reproduce the exact missing-description failure and verify
repeated repair, public-role discovery, and preserved owner-supplied halal flags.


## Import all fields for the 328 supplied food outlets

Use [the full profile import guide](IMPORT_RESTAURANT_PROFILES.md) and
[the smaller SQL Editor files](imports/restaurant_profile_batches/README.md). Only the
328 selected operational food outlets are imported; each retains all 65 source
fields. Existing restaurant coordinates and known profile values are preserved.


## Profile photos and reviews

For Android 0.6.1, follow [photo/review setup](PROFILE_PHOTOS_REVIEWS.md) and run
[migration 202610280001](migrations/202610280001_profile_photos_reviews.sql).
It exposes a narrow public profile-details endpoint while leaving raw source
profiles and unpublished written reviews private.


For Android 0.6.2, use [migration 202610280002](migrations/202610280002_profile_review_links.sql)
to return the stored `location_reviews_link`. The Reviews button opens this link
in Maps or a browser. See [the updated guide](PROFILE_PHOTOS_REVIEWS.md).


Android 0.6.3 displays this same `location_reviews_link` inside DineFinder's
review viewer instead of launching Maps or a browser. No further SQL change is
required after migration 202610280002.
