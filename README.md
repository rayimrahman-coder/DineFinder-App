# DineFinder

Native Android restaurant discovery in Kotlin and Jetpack Compose. No Google
Maps, Places, Directions, or routing API key is required. The public Supabase
project configuration is included, so a fresh clone can build and run without
creating a local key file.

## Run in Android Studio

1. Pull the latest GitHub code and open the repository root containing
   `settings.gradle.kts` and `app`.
2. Set Gradle JDK to **21** and install **Android SDK 36** / Build-Tools 36.0.0.
3. Sync Gradle, connect an Android **9/API 28 or newer** phone, select **app**,
   and click Run.
4. Choose **Continue as guest**, then **Scan for Restaurant**. Allow precise
   location and camera access.

See [the Android Studio guide](docs/ANDROID_STUDIO.md).

## How it works

1. Android's native `LocationManager` obtains the user's real GPS coordinates.
2. Supabase's `nearby_restaurants` RPC searches records within **1,000 metres**.
3. The live camera places restaurant markers using GPS positions and compass
   bearings. The list also works on devices without a compass.
4. Tap a marker or list item to view the restaurant's Supabase profile.
5. Select **Navigate**. The public FOSSGIS OpenStreetMap pedestrian service
   calculates a walking route; camera arrows and written instructions guide the
   user along it.
6. Arrival checks proximity to the restaurant coordinates, not only the routing
   service's snapped path endpoint.

This remains a native Android app. Geolocator is a Flutter package; native
LocationManager provides the GPS step without adding Flutter or a Google key.

Optional local ARCore plane tracking displays a 3D ground arrow on supported
phones. It needs Google Play Services for AR, but **no Google API key**. There
are no cloud anchors or Geospatial API calls. Unsupported devices use the
ordinary camera compass view. GPS/compass alignment is approximate; the app does
not recognize storefronts, obstacles, or safe crossings.

## Restaurant data source

All live restaurant discovery results and profile fields come from
`public.restaurants` through the Supabase `nearby_restaurants` RPC. The active
Home scan flow does not query Google Places or OpenStreetMap for listings and
does not merge local Room records. The previously proposed OSM importer has
been removed. Settings no longer asks for a Google API key.

Add and maintain restaurants in Supabase Table Editor or SQL Editor. Supabase
stores the listings you provide; it does not automatically contain every Labuan
business. Missing records/details stay missing instead of being invented.
See [restaurant data management](supabase/RESTAURANT_DATA.md).

## Supabase database and public configuration

`supabase.public.properties` contains this project's **public publishable client
configuration** and is intentionally distributed with the app. Supabase requires
this public client key even for public reads. It is not a Google key or an admin
credential. Restaurant access is public read-only; users can read only their own
account profile. Never replace it with a service-role key or database password.

The database must exist before live discovery can work. In your Supabase SQL
Editor, run [supabase/setup.sql](supabase/setup.sql) if it has not been applied.
Enable **Authentication → Providers → Email** for optional signup/sign-in.
For your uploaded spreadsheet, run
[supabase/imports/20261023_labuan_outscraper.sql](supabase/imports/20261023_labuan_outscraper.sql)
instead: this single file includes setup, 328 selected operational food outlets,
and the two original food courts. It preserves existing matched rows and can be
run again without creating duplicates. It does not run automatically on app startup.

The original setup includes the supplied HALAL food courts:

- **Cafe UMSKAL** — 5.280285448386224, 115.17640486697412.
- **Anjung Selera Sungai Pagar** — 5.28381416215476, 115.17329350236913.

A phone outside their 1 km radius correctly shows no results. Unknown hours,
ratings, and prices are not invented. Auth passwords remain managed by Supabase;
Android account sessions are held only in memory.

To connect a different project, copy `supabase.properties.example` to ignored
`supabase.properties` and supply both public values there. Nonblank local values
override the bundled configuration; blank values are ignored. Restaurant discovery
uses live Supabase records only; it does not automatically fall back to local seed
records or fictional restaurants.

## Camera and location permission dialog

The existing Compose permission explanation matches the supplied reference:
white rounded card, overlapping Material camera/location icons, centred copy,
and equal-width Not now/Continue buttons. Continue reuses Android's existing
runtime request launcher. Already granted camera plus foreground location skip
the explanation. Ordinary denials allow retry; blocked permissions offer app
settings and grants are checked again on return. Approximate location remains
supported by the existing scan flow without repeated permission prompts.

The card scrolls on short screens and respects system text scaling. Its opening
animation uses Compose's system animation clock. No image assets, new UI
packages, Flutter project, or backend changes were introduced.

## AR discovery screen

The camera screen uses the permission dialog's white, forest-green and mint
palette, with a compact header and no Camera/List tabs. Full-name restaurant
markers follow compass bearings and stay above the restaurant panel.

Drag the bottom panel between collapsed, half-open and expanded positions, or
use its chevron and accessibility actions. Expand it to browse and scroll nearby
restaurants; tap a marker or row to open the existing profile and navigation.
The camera stays mounted while the panel moves. Counts, names, distances and
halal labels come from the existing discovery data rather than the design mockup.

## Scan checklist

In Scan mode, tap the **shield/checklist icon** for 12 automatic checks covering permissions,
location services, GPS freshness/accuracy, internet, Supabase configuration and
search, nearby results, compass and camera preview. PASS, WAIT, FAIL and CHECK
statuses explain what was actually observed. A successful search with zero
matches is distinguished from a failed request. Camera permission alone does
not count as working video: CameraX must report live preview streaming.

Tap **Copy checks** to share the checklist, search coordinates and accuracy for
troubleshooting. No API keys or account tokens are included. The checklist
cannot by itself distinguish missing rows from an RLS policy returning no rows;
use `supabase/diagnose_nearby.sql` to compare database and public-role searches.

## Build and validation

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. The APK is generated at
`app/build/outputs/apk/debug/app-debug.apk`.

Version 0.5.0 passes the debug APK build, Android lint, and 60 targeted Supabase,
authentication, marker-layout, and screen regression tests. Physical-phone GPS/camera/AR and
live Supabase availability still require testing.

The native unit suite covers GPS calculations, 1 km filtering, marker projection,
AR direction math, restaurant metadata, route progress/arrival, and mocked
Supabase/auth/key-free routing requests. Physical GPS/camera/AR behavior and live
provider availability require phone testing. No Google API-key metadata is
included in the manifest.

The retained database can be checked independently with
`bash supabase/tests/run-postgis.sh`; this uses a disposable PostGIS database,
not the live project. See [database setup](supabase/README.md) and
[report implementation notes](docs/REPORT_ALIGNMENT.md).

Walking routes: FOSSGIS · © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) · ODbL.
Selecting Navigate sends route endpoints to the pedestrian service. Nearby-search
coordinates go to Supabase; camera images are not uploaded by the app.
