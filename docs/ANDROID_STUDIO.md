# Test DineFinder in Android Studio

You can run the guest demo immediately. Connecting real restaurant data and
optional accounts requires your Supabase project's public configuration. Walking
routes require no paid map or routing API key.

## 1. Install the tools

Install [Android Studio](https://developer.android.com/studio),
[Git](https://git-scm.com/downloads), and Flutter **3.44.0** from the
[Flutter SDK archive](https://docs.flutter.dev/install/archive). Extract Flutter
to a folder you can write to, then add its `bin` folder to your computer's PATH.

In Android Studio, open **Settings → Plugins → Marketplace**, install the
**Flutter** plugin, accept installation of the Dart plugin, and restart the IDE.
On macOS, Settings may be named Preferences.

Open **Settings → Languages & Frameworks → Flutter** and select your extracted
Flutter SDK folder. In a terminal, `flutter --version` should report Flutter
3.44.0 and Dart 3.12.0.

## 2. Get the code from GitHub

On Android Studio's welcome screen, select **Get from VCS** or **Clone Repository**.
If a project is open, use **File → New → Project from Version Control**.

Choose Git, enter this URL, select a local folder, and click **Clone**:

```text
https://github.com/rayimrahman-coder/DineFinder-App.git
```

Open and trust the project. Select the root folder containing `pubspec.yaml` and
`lib/main.dart` so Android Studio can load the Flutter project.

If you already cloned the project, open its root folder and get the latest code
with **Git → Pull**, or run this from Android Studio's terminal:

```sh
git pull --ff-only
```

## 3. Prepare Android's SDK

Open **Tools → SDK Manager**. Install the following components:

- **SDK Platforms:** Android SDK Platform **36**.
- **SDK Tools:** Android SDK Build-Tools **36.0.0**, Android SDK Platform-Tools,
  and Android SDK Command-line Tools.
- **SDK Tools → Show Package Details → NDK (Side by side):**
  **28.2.13676358**.
- **Android Emulator** if you want to use an emulator.

Use Android Studio's bundled **JDK 21** for Gradle. Its selection is under
**Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
The project uses Gradle 9.1.0 and Android Gradle Plugin 9.0.1.

Open the IDE's terminal in the project root and run:

```sh
flutter doctor -v
flutter doctor --android-licenses
flutter pub get --enforce-lockfile
```

Read and accept the Android licenses, then resolve Android toolchain errors
reported by `flutter doctor`. Development on Windows or Linux can ignore a
missing Xcode toolchain when testing Android.

## 4. Choose your device

For a physical phone, use Android 7.0/API 24 or newer. Enable Developer options
and USB debugging in the phone's settings, connect it by USB, and accept the
phone's debugging authorization prompt. A physical phone is best for camera,
GPS, and compass tests.

For an emulator, open **Tools → Device Manager → Create Device**, choose a Pixel
model, download an API 36 system image, and start the emulator. The demo can be
tested without GPS or camera hardware; live heading support depends on the
emulated sensors.

Check that Flutter can see your selected device:

```sh
flutter devices
```

## 5. Run and test the guest demo

Select the device in Android Studio's toolbar. Open `lib/main.dart` and click
the green **Run ▶** button. You can also run `flutter run` in the terminal.

On the welcome screen, choose **Continue as guest**. Without Supabase
configuration, the app uses its labeled offline demo:

1. Open Home and select **Scan for Restaurant**.
2. Tap a restaurant marker to open the restaurant profile.
3. Select **Navigate** to open a simulated journey.
4. Adjust the demo heading control to turn the camera view.
5. Move the Walk slider to simulate walking and arrival.
6. Open Profile to find Settings, Help, and Log out.

Demo restaurant records and paths are fictional. They are suitable for testing
the screens and interactions, not for real walking directions.

## 6. Connect live restaurant data

Follow [the Supabase instructions](../supabase/README.md) to apply the restaurant
migration and add nearby restaurant records. For optional account creation and
sign-in, enable Supabase's email/password provider. Guests can read public
restaurant data without an account.

Copy `.env.example.json` to `env.local.json` in the project root and fill in:

```json
{
  "SUPABASE_URL": "https://YOUR_PROJECT.supabase.co",
  "SUPABASE_PUBLISHABLE_KEY": "YOUR_PUBLIC_PUBLISHABLE_KEY"
}
```

Use the public publishable client key, never a service-role key or database
password. No Google Maps or routing-provider key is needed. `env.local.json` is
ignored by Git; Flutter build defines still become visible in the compiled app.

Open **Run → Edit Configurations**, select the Flutter configuration for
`lib/main.dart`, and set **Additional run args** to:

```text
--dart-define-from-file=env.local.json
```

Stop and restart the app so the build-time configuration takes effect. Select
guest access or sign in, then tap **Scan for Restaurant**. Accept location and
camera permissions. The app searches within 1 km of the phone's actual location;
Navigate requests pedestrian directions from the public FOSSGIS routing service.
Live discovery and routing need internet access.

The sample seed data is near Kuala Lumpur. If your phone is elsewhere, add real
nearby restaurant records or use the demo to test the sample screens. If a
configured project has no restaurants within the radius, an empty result is
expected.

## If something does not work

| Symptom | What to check |
| --- | --- |
| `flutter` command is missing | Add Flutter's `bin` folder to PATH and reopen Android Studio. |
| Device is missing | Run `flutter devices`; authorize USB debugging or start the emulator. |
| Android licenses or tools are missing | Install Command-line Tools in SDK Manager, then rerun the doctor and license commands. |
| The app stays in Demo | Verify `env.local.json`, the run configuration flag, and perform a full restart. |
| No nearby places | Check GPS permission, location services, internet, and restaurant coordinates within 1 km. |
| Sign-in or account creation fails | Check the email/password provider and email confirmation settings in Supabase. |
| Camera permission was denied | Allow camera access in the phone's app settings and retry the scan. |
| No directional markers | Use a phone with a compass, check location/heading availability, and follow the displayed sensor guidance. |
| Walking route fails | Check internet and retry later; the public service and local pedestrian-map coverage can vary. |

Camera guidance uses approximate GPS/compass bearings and does not detect
obstacles or safe crossings. Validate it on a physical phone before relying on
its alignment.
