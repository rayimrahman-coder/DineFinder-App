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

## Supabase database and public configuration

`supabase.public.properties` contains this project's **public publishable client
configuration** and is intentionally distributed with the app. Supabase requires
this public client key even for public reads. It is not a Google key or an admin
credential. Restaurant access is public read-only; users can read only their own
account profile. Never replace it with a service-role key or database password.

The database must exist before live discovery can work. In your Supabase SQL
Editor, run [supabase/setup.sql](supabase/setup.sql) if it has not been applied.
Enable **Authentication → Providers → Email** for optional signup/sign-in.
The script includes the supplied HALAL food courts:

- **Cafe UMSKAL** — 5.280285448386224, 115.17640486697412.
- **Anjung Selera Sungai Pagar** — 5.28381416215476, 115.17329350236913.

A phone outside their 1 km radius correctly shows no results. Unknown hours,
ratings, and prices are not invented. Auth passwords remain managed by Supabase;
Android account sessions are held only in memory.

To connect a different project, copy `supabase.properties.example` to ignored
`supabase.properties` and supply both public values there. Nonblank local values
override the bundled configuration; blank values are ignored. For offline UI
checks, use **Profile → Settings → Use demo mode**. The demo's Kuala Lumpur
restaurants and routes are explicitly fictional.

## Build and validation

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. The APK is generated at
`app/build/outputs/apk/debug/app-debug.apk`.

Verified from a fresh source copy without local key or SDK properties: the APK
build, all **36 native unit tests**, and Android lint pass. Blank local key fields
also retain the bundled public defaults.

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
