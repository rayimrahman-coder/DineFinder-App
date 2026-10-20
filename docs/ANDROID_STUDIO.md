# Test DineFinder in Android Studio

DineFinder is a native Kotlin/Jetpack Compose Android project. Android Studio's
Android and Kotlin support is sufficient. You can test the guest demo before
connecting a Supabase project.

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
can be used. The project uses Gradle 9.1.0, Android Gradle Plugin 9.0.1, and Kotlin
2.3.20; the wrapper downloads the correct Gradle version automatically.

Let the Gradle sync finish. Initial setup needs internet access to download
Android and Maven dependencies.

## 3. Select a device

For a physical phone, use Android 7.0/API 24 or newer. Enable Developer options
and USB debugging, connect it by USB, and accept the debugging authorization on
the phone. Select the phone in Android Studio's device toolbar. A real phone is
best for GPS, camera, and compass testing.

For an emulator, open **Tools → Device Manager → Create Device**, choose a Pixel
model, download an API 36 system image, and start it. The guest demo can run
without physical sensor hardware. Live heading availability depends on the
emulated sensors.

## 4. Run the guest demo

Select the **app** run configuration and your device, then click the green
**Run ▶** button. On the welcome screen, select **Continue as guest**. Blank
Supabase configuration opens the labeled offline demo. If the build is already
configured for Live, choose **Profile → Settings → Use demo mode** to test the
fictional samples while database setup is pending.

Try the main flow:

1. Open Home and tap **Scan for Restaurant**.
2. Tap a restaurant marker to open its profile.
3. Select **Navigate** and explore simulated route progress.
4. Test the demo heading and movement controls.
5. Open Profile and try Settings, Help, and Log out.

Demo restaurant records and paths are fictional. They are for interaction
testing and should not be followed as real walking directions.

## 5. Create the live database and enable accounts

Open your Supabase project dashboard, choose **SQL Editor → New query**, and
paste the whole [supabase/setup.sql](../supabase/setup.sql) file. Select **Run**.
The file includes the complete restaurant schema, the 1 km search RPC, private
`public.profiles`, Auth profile creation/backfill, and the two supplied HALAL
food courts. The SQL has passed local checks; the live database has not been
created automatically. Its public client key cannot run table-creation SQL.

Under **Authentication → Providers**, enable **Email** for email/password sign-in
and account creation. Supabase Auth stores and verifies passwords. The public
profile table stores display names and metadata rather than passwords. If email
confirmation is enabled, confirm the account's email before signing in.

Copy `supabase.properties.example` to root `supabase.properties` if that local
file does not exist, then fill in:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLIC_PUBLISHABLE_KEY
```

The supplied project's URL is `https://oueafqgadqggyinpmyoi.supabase.co`. Keep its
public publishable key in your ignored local properties file. The prepared APK
may already contain this public configuration, but the dashboard SQL still
needs to be applied. Never use a service-role key or database password in the
Android app. No Google Maps or routing-provider key is required.

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
radius, an empty result is expected. The optional fictional Kuala Lumpur seed
and offline demo are separate from these records. Use **Profile → Settings →
Use demo mode** when you want to test the sample screens away from the food courts.

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
| The app opens Demo | Fill both public values in root `supabase.properties`, sync Gradle, rebuild, and turn off Use demo mode. |
| No nearby restaurants | Run `supabase/setup.sql` in the SQL editor, then check GPS, internet, and the food courts being within 1 km. |
| Sign-in or sign-up fails | Apply setup SQL, enable the email/password provider, and check email confirmation and credentials. |
| Camera was denied | Allow camera access in the phone's app settings and retry. |
| No heading or markers | Test on a compass-equipped phone and follow the app's sensor guidance. |
| Walking route fails | Check internet and retry later; service availability and pedestrian-map coverage vary. |

Use a physical phone to validate live alignment and permissions. Camera guidance
uses approximate GPS/compass bearings and does not detect obstacles or safe
crossings.
