# DineFinder

DineFinder is a **native Android app written in Kotlin and Jetpack Compose**.
It finds restaurants within **1 km**, opens restaurant profiles, and displays
walking directions through a camera compass. The app includes email/password
sign-in, account creation, guest access, Home scanning, and a Profile screen
with Settings, Help, and Log out.

Restaurant data and optional accounts use Supabase. Walking directions come
from the public FOSSGIS OpenStreetMap pedestrian routing service. No Google Maps,
OpenRouteService, or paid routing API key is required. Local ARCore tracking also
requires no Google API key; cloud anchors and Geospatial services are not used.

## Open and run

Install Android Studio and open this repository's **root folder**, containing
`settings.gradle.kts`, `gradlew`, and the `app` module. Let Gradle sync, select an
Android device, and click **Run ▶** for the `app` configuration.

The toolchain uses JDK 21, Gradle 9.1.0, Android Gradle Plugin 9.0.1, Kotlin
2.3.20, and Android SDK 36. The minimum device version is Android 7.0/API 24.
Install SDK Build-Tools 36.0.0 and Android SDK Platform-Tools through SDK Manager.

For step-by-step cloning, device setup, and live configuration, see
[the Android Studio guide](docs/ANDROID_STUDIO.md).

## Try the guest demo

Without Supabase configuration, select **Continue as guest** to use the labeled
offline demo. Home displays a radar and **Scan for Restaurant**. Open a sample
restaurant's profile and select **GUIDE ME** to try simulated camera guidance
and route progress. Profile contains Settings, Help, and Log out.

The demo's Kuala Lumpur restaurants, ratings, addresses, schedules, and paths
are fictional. They test the screens and interactions and do not represent real
business listings or walkable streets. The demo works offline after installation.

## Set up the live database

The schema is provided in [supabase/setup.sql](supabase/setup.sql). Open your
Supabase project in the dashboard, select **SQL Editor → New query**, paste that
entire file, and select **Run**. This creates restaurant discovery, private user
profiles, the sign-up trigger, and the two supplied food-court records. The SQL
is idempotent and has been tested locally; applying it to the live project is
still a user action. A public client key cannot create database tables.

Supabase Auth manages email/password accounts and password security. The new
`public.profiles` table stores each account's display name and metadata, with
owner-only read/update access. Creating an Auth user creates its profile;
existing users are backfilled. Guests can read public restaurant data without
an Auth account or profile row. Enable the **email/password** provider under
Supabase Authentication for sign-in and account creation. If email confirmation
is enabled, confirm the email before signing in.

Copy `supabase.properties.example` to the ignored root file
`supabase.properties` if it does not already exist. Set your project URL and
public publishable client key:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLIC_PUBLISHABLE_KEY
```

Sync Gradle and rebuild after changing configuration. The client key is included
in the compiled app; use only the public publishable key. Keep service-role keys
and database passwords out of the app. Blank configuration opens the offline
guest demo. A build with project configuration opens Live; the database setup
must be completed before testing live accounts or restaurant reads. Guests can
also select **Profile → Settings → Use demo mode** to test the fictional samples.

The setup includes these user-supplied locations:

| Food court | Latitude | Longitude | Supplied status |
| --- | --- | --- | --- |
| Cafe UMSKAL | 5.280285448386224 | 115.17640486697412 | HALAL |
| Anjung Selera Sungai Pagar | 5.28381416215476 | 115.17329350236913 | HALAL |

Names, coordinates, and HALAL status came from the project owner. No verified
rating, opening hours, address, or pricing was supplied. The fictional Kuala
Lumpur demo collection is separate from these live records.

In Live mode, **Scan for Restaurant** requests foreground location and camera
access and queries Supabase within 1 km of the phone's actual coordinates.
Tapping a marker opens its profile. **GUIDE ME** requests pedestrian directions
without a routing key. Live discovery and navigation need internet access; the
two food courts appear only when they fall within the search radius. For more
setup and verification steps, see [the database guide](supabase/README.md).

The app uses Supabase HTTPS REST and Auth endpoints. Signed-in sessions are kept
in memory; passwords and session tokens are not persisted. Sign in again after
restarting the process or when the session expires.

## Camera guidance and privacy

CameraX supplies the camera view, Android LocationManager supplies foreground
location, and SensorManager supplies heading information. Markers estimate the
bearing to each restaurant. Navigation follows the route's next point, with
walking instructions and progress. Discovery uses approximate **GPS/compass
markers**. During navigation, compatible
phones can use **local ARCore plane tracking** to place an extruded 3D arrow on a
detected ground surface. Move the phone slowly toward the ground to initialize
tracking. Use **Use compass camera** to switch back, or **Try local 3D AR** to
retry. Unsupported devices automatically use the camera compass view.

The local ground anchor does not establish the geographic position of a
restaurant entrance. Directions still depend on GPS, compass calibration, and
OpenStreetMap paths. Neither mode detects obstacles or safe crossings. Use the
written directions and remain aware of your surroundings.

A physical Android phone is the primary live target. Camera, location, and
compass behavior depend on device hardware, permissions, accuracy, and
calibration. The emulator is useful for the demo and UI testing. Foreground
sensors and camera resources are released when the app is no longer active.

Nearby-search coordinates go to Supabase. Selecting GUIDE ME sends route
endpoints to `routing.openstreetmap.de`. Camera frames stay on the device. Public
routing has usage limits and may be unavailable; the app must report route
failures rather than replace them with demo geometry. Paths depend on
OpenStreetMap's pedestrian coverage.

Routing is provided by FOSSGIS. Data is © [OpenStreetMap
contributors](https://www.openstreetmap.org/copyright), available under the
[Open Database License](https://opendatacommons.org/licenses/odbl/1-0/).

## Build and check

From the repository root on macOS or Linux:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Verified: the native debug APK build, all **36 unit tests**, Android lint, and
the local PostGIS integration checks pass. The APK signature and permissions
were checked, and the APK includes the supplied public Supabase configuration.

Unit tests use fixtures and mocked responses. They do not establish live
Supabase authentication, email delivery, public routing availability, or physical
GPS/camera/compass behavior. Inspect the Gradle test and lint reports for the
current run before claiming validation. Debug signing is for local testing;
configure production signing before distributing a release.

The retained restaurant migration can be checked in a disposable database:

```sh
bash supabase/tests/run-postgis.sh
```

That check requires Docker and validates the schema, radius boundaries,
coordinate validation, ordering, public read-only restaurant access, private profile RLS, sign-up triggers,
backfills, and setup idempotence. It does not deploy a Supabase project.

## Report design

The discovery, bottom metadata card (**GUIDE ME / CANCEL**), live route-distance
counter, 3D arrow, and dedicated arrival screen follow the supplied DineFinder
FYP report, section 4.2 (pages 57–60). See [report implementation notes](docs/REPORT_ALIGNMENT.md).

## Project layout

- `app/src/main/java/`: Kotlin app, Compose screens, services, sensors, and routing.
- `app/src/main/res/`: Android resources and application styling.
- `app/src/test/`: native unit tests.
- `supabase/setup.sql`: single-paste live schema, private profiles, and supplied food courts.
- `supabase/`: versioned migrations, separate fictional seed data, and database checks.
- `docs/ANDROID_STUDIO.md`: Android Studio setup and testing instructions.
