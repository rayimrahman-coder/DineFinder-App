# Test DineFinder in Android Studio

DineFinder is a native Kotlin/Jetpack Compose Android project. Android Studio's
Android and Kotlin support is sufficient. The public Supabase configuration is
already included. You do not need a Google Maps or routing API key.

## 1. Get the project

Install [Android Studio](https://developer.android.com/studio) and
[Git](https://git-scm.com/downloads).

On Android Studio's welcome screen, select **Get from VCS** or **Clone Repository**.
If a project is already open, select **File → New → Project from Version Control**.
Choose Git and paste:

```text
https://github.com/rayimrahman-coder/DineFinder-App.git
```

Choose a local folder, click **Clone**, and open and trust the project. Open the
repository root containing `settings.gradle.kts`, `gradlew`, and `app`.

If the repository is already on your computer, use **Git → Pull**, or open its
terminal and run:

```sh
git pull --ff-only
```

Then select **File → Sync Project with Gradle Files**.

## 2. Prepare the Android tools

Open **Tools → SDK Manager** and install:

- **SDK Platforms:** Android SDK Platform **36**.
- **SDK Tools:** Android SDK Build-Tools **36.0.0**, Android SDK Platform-Tools,
  and Android SDK Command-line Tools.
- **Android Emulator**, if you want an emulator.

Accept the licenses for the components you install. Choose **JDK 21** under
**Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
On macOS, Settings may be named Preferences. Android Studio's bundled JDK 21
can be used. The project uses Gradle 9.6.0, Android Gradle Plugin 9.4.1, and Kotlin
2.3.20; the wrapper downloads the correct Gradle version automatically.

Let the Gradle sync finish. Initial setup needs internet access to download
Android and Maven dependencies.

## 3. Select a device

For a physical phone, use Android 9/API 28 or newer. Enable Developer options
and USB debugging, connect it by USB, and accept the debugging authorization on
the phone. Select the phone in Android Studio's device toolbar. A real phone is
best for GPS, camera, and compass testing.

For an emulator, open **Tools → Device Manager → Create Device**, choose a Pixel
model, download an API 36 system image, and start it. Live heading availability depends on the emulated sensors; use emulator location
controls to set a position near your stored restaurants.

## 4. Run the app

Select the **app** run configuration and your device, then click **Run ▶**.
Choose **Continue as guest**, or register/sign in with Supabase Auth.

1. Tap **Scan for Restaurant** and accept the location/camera consent.
2. Allow precise location and camera access when Android prompts.
3. Wait for GPS, then view camera markers or the restaurant list.
4. Tap a restaurant to open its Supabase profile.
5. Choose **Navigate** for an OpenStreetMap-based walking route and camera arrows.

Live restaurant data comes only from Supabase within 1 km of the phone. No
Google Places key is required. Empty results are not filled with fictional
or local listings. A real phone is needed to verify camera/compass/AR alignment.

## 5. Create the live database and enable accounts

Open your Supabase project dashboard, choose **SQL Editor → New query**, and
paste the whole [supabase/setup.sql](../supabase/setup.sql) file. Select **Run**.
The file includes the complete restaurant schema, the 1 km search RPC, private
`public.profiles`, Auth profile creation/backfill, and the two supplied HALAL
food courts. The SQL has passed local checks; the live database has not been
created automatically. Its public client key cannot run table-creation SQL.

For your uploaded Labuan restaurant spreadsheet, the complete ready-to-run file is
[supabase/imports/20261023_labuan_outscraper.sql](../supabase/imports/20261023_labuan_outscraper.sql).
It includes setup plus 328 selected food outlets and your two original food courts.
Copy the SQL file, not the CSV, into SQL Editor. Existing matched rows are preserved.
This repository file does not apply itself to your live Supabase database.

Under **Authentication → Providers**, enable **Email** for email/password sign-in
and account creation. Supabase Auth stores and verifies passwords. The public
profile table stores display names and metadata rather than passwords. If email
confirmation is enabled, confirm the account's email before signing in.

The project already includes its public Supabase configuration in
`supabase.public.properties`. You do not need to create a local key file or
request a Google Maps or routing key. Apply `supabase/setup.sql` first if the
project database has not been created.

Only when connecting another Supabase project, copy `supabase.properties.example`
to ignored `supabase.properties` and enter both public values. Nonblank local
values override the bundled configuration; blank values are ignored. Never
use an admin or service-role key in the client.

Sync with Gradle, stop the app, and run it again after changing properties.
Sign in or continue as guest, select **Scan for Restaurant**, and grant
foreground location and camera permissions. Choose precise location when Android
offers it. Discovery searches within 1 km of the real GPS location; Navigate
requests walking directions through the public FOSSGIS pedestrian service.
Live discovery and routing require internet.

The two supplied records are:

- **Cafe UMSKAL** — 5.280285448386224, 115.17640486697412 — HALAL.
- **Anjung Selera Sungai Pagar** — 5.28381416215476, 115.17329350236913 — HALAL.

Their names, coordinates, and HALAL flags were supplied by the project owner;
ratings and opening hours are not verified. When the phone is outside the 1 km
radius of every stored restaurant, an empty result is expected. For more Labuan
locations, apply the supplied spreadsheet SQL import. Live discovery does not
fall back to fictional data.

To check accounts, create an account through the app and look for it under
**Authentication → Users** in the dashboard. Its matching `public.profiles` row
is created by the sign-up trigger. Guests do not create account/profile rows.
Existing Auth accounts receive profiles when setup runs. Native sign-in sessions
stay only in memory; sign in again after a process restart or expired session.

## 6. Build an APK or run checks

Use Android Studio's terminal in the project root. On macOS or Linux:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`. It is signed for
local development. Unit-test reports are under `app/build/reports/tests/`, and
lint reports are under `app/build/reports/`.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Git is missing | Install Git, restart Android Studio, and clone again. |
| Gradle cannot find Java | Set Gradle JDK to JDK 21 in Android Studio settings. |
| Android SDK is missing | Install SDK 36 and the listed tools through SDK Manager, then sync again. |
| Device is missing | Start the emulator or authorize the phone's USB debugging prompt. |
| Old demo/key settings appear | Pull the latest main branch, sync Gradle, and reinstall the app from Android Studio. |
| No nearby restaurants | Run `supabase/setup.sql` in the SQL editor, then check GPS, internet, and the food courts being within 1 km. |
| Search reports missing `description` column | Run `supabase/migrations/202610230002_repair_restaurant_profile_columns.sql` in Supabase SQL Editor. Existing rows are preserved; no app reinstall is needed. |
| Sign-in or sign-up fails | Apply setup SQL, enable the email/password provider, and check email confirmation and credentials. |
| Camera was denied | Allow camera access in the phone's app settings and retry. |
| No heading or markers | Test on a compass-equipped phone and follow the app's sensor guidance. |
| Walking route fails | Check internet and retry later; service availability and pedestrian-map coverage vary. |

Use a physical phone to validate live alignment and permissions. Camera guidance
uses approximate GPS/compass bearings and does not detect obstacles or safe
crossings.

## Diagnose an empty restaurant search

On the scan screen, tap **Checks → Copy checks**. The checklist automatically
updates permissions, location-service availability, GPS freshness/accuracy,
internet validation, Supabase configuration, the database request, nearby
matches, compass and camera-preview streaming. PASS means an observed success;
WAIT means not yet confirmed, FAIL identifies a failed requirement/request, and
CHECK means something needs attention (including zero restaurant results).
The copied report includes phone coordinates, location accuracy/precise
permission, the actual search centre, radius, result count and a safe error message. It never
copies the app key or account tokens. Share these details when reporting an
empty search; enabling precise location is especially relevant when reported
accuracy is hundreds of metres or more.

Version 0.4.3 includes the HTTP response status and safe PostgreSQL/PostgREST
error code for a failed nearby request. HTTP 401 points to rejected public
connection settings, 403/42501 to denied permissions, PGRST203 to conflicting
function overloads, and 42804/42702/42883 to a database-function issue. Server
response bodies, keys and SQL internals are not copied. Configuration PASS
checks syntax/presence only; the separate search check verifies access.
GPS fix age is included explicitly. A checklist clock update no longer makes a
newly arrived position briefly appear stale.

An unsuccessful request now shows a search failure rather than claiming no
restaurants exist. No-results messages are shown only after a successful search.
To check database rows, the RPC and guest access independently of the phone, run
[supabase/diagnose_nearby.sql](../supabase/diagnose_nearby.sql) in SQL Editor.
This is read-only. It compares normal and public-role searches at the supplied
Cafe UMSKAL position; both food courts should be returned if their saved
coordinates, RPC and read policies match the project setup.

## Local 3D AR navigation without a Google API key

On a compatible phone, selecting Navigate starts local ARCore guidance. Accept
the Google Play Services for AR installation prompt if shown. This is a device
runtime, not a Google Maps API account or key. Slowly point the phone toward a
well-lit ground surface; the 3D arrow appears once a horizontal plane is tracked.
GPS and compass readings are still required to orient the arrow toward the route.

Use **Use compass camera** to return to the ordinary camera view. Unsupported
phones or a declined AR runtime installation fall back automatically. Try a
real route, background/resume the app, turn the phone, and confirm that stale GPS
or heading pauses directional guidance. Automated screen tests do not validate
the physical ARCore runtime.

The arrival screen checks proximity to the restaurant coordinates, not just the
routing service's snapped path endpoint. GPS proximity is approximate and does
not verify an exact entrance. New AR rendering and installation behavior require
validation on a compatible physical phone.
