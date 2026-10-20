# DineFinder

DineFinder is a Flutter app for finding restaurants within **1 km**, opening
their profiles, and following walking directions through a camera compass.
It includes a sign-in screen, guest access, a Home scan screen, and a Profile
screen with Settings, Help, and Log out.

Restaurant data and optional email/password accounts use Supabase. Walking
directions use the public FOSSGIS OpenStreetMap pedestrian routing service.
**No Google Maps, OpenRouteService, or paid routing API key is required.**

## Run the app

Use Flutter **3.44.0** with Dart **3.12.0**. From the folder containing
`pubspec.yaml`, run:

```sh
flutter pub get --enforce-lockfile
flutter run
```

Choose a connected Android phone or an Android emulator. For a web demo, use
`flutter run -d chrome`. The Android minimum SDK is 24; iOS targets 15.0 and
requires macOS, Xcode, and CocoaPods.

See [the Android Studio guide](docs/ANDROID_STUDIO.md) for cloning the project,
installing the SDK tools, selecting a device, and configuring live data.

## Try it without configuration

Select **Continue as guest** on the welcome screen. Without a Supabase connection,
the app opens the clearly labeled demo. Home shows the nearby radar; select
**Scan for Restaurant** to explore sample places and their profiles. Tap
**Navigate** to try the simulated camera directions. The demo heading and Walk
controls simulate turning a phone, moving along a route, and arriving.

The demo works offline after installation. Its six Kuala Lumpur restaurants,
ratings, addresses, schedules, and route geometry are fictional samples. They
are not real business listings or walkable street directions.

## Connect restaurant data and accounts

Follow [the Supabase setup instructions](supabase/README.md) to create the
restaurant table and radius-search function. Optional email/password sign-in
uses Supabase Auth; guests can read public restaurant data without an account.
No anonymous sign-in or routing Edge Function is required.

Copy `.env.example.json` to the ignored file `env.local.json` and supply your
project URL and **public publishable client key**:

```json
{
  "SUPABASE_URL": "https://YOUR_PROJECT.supabase.co",
  "SUPABASE_PUBLISHABLE_KEY": "YOUR_PUBLIC_PUBLISHABLE_KEY"
}
```

Then start the app with:

```sh
flutter run --dart-define-from-file=env.local.json
```

Supabase needs its public client key even for public reads. This is client
configuration, not an admin secret; Flutter build defines are visible in the
distributed app. Never include a service-role key or database password. The
older `SUPABASE_ANON_KEY` define is also accepted for existing configurations.

With a configured project, **Scan for Restaurant** requests foreground GPS and
camera access, searches nearby Supabase records within 1 km, and displays
restaurant markers according to their bearings. A marker opens the restaurant's
profile. **Navigate** requests a real pedestrian route without a routing key.
Live mode needs internet access and restaurant records near the device's actual
location. Fictional seed data near Kuala Lumpur will not appear outside its
geographic search radius.

## Camera guidance, data, and privacy

The camera view is a **GPS/compass overlay**, not ARCore/ARKit world anchoring.
Markers estimate restaurant directions; the navigation arrow points toward the
next route vertex. Walking instructions, remaining distance, off-route
recalculation, and arrival detection accompany the overlay. Buildings, obstacles,
and safe crossings are not detected. Use the written route instructions and stay
aware of your surroundings.

A physical phone is the primary target for live GPS, camera, and compass tests.
Web camera and location require HTTPS or local development; browser heading
support varies. Live directional markers and arrows are withheld when a heading
is unavailable. Backgrounding the app releases the camera and pauses sensors;
resuming reconnects them.

Nearby-search coordinates go to Supabase. Selecting Navigate sends the route's
origin and destination to `routing.openstreetmap.de`. Camera frames remain on
the device. The public pedestrian routing service has usage limits and may be
unavailable; route failures are shown as errors and never replaced with demo
geometry. Routes depend on the pedestrian paths available in OpenStreetMap.

Routing is provided by FOSSGIS. Routing data is © [OpenStreetMap
contributors](https://www.openstreetmap.org/copyright), available under the
[Open Database License](https://opendatacommons.org/licenses/odbl/1-0/).

## Checks

```sh
flutter analyze
flutter test
flutter build web --no-web-resources-cdn
flutter build apk --debug
bash supabase/tests/run-postgis.sh
```

Verified with Flutter 3.44.0: analysis passes, all 39 Flutter tests pass, and the
web release and Android debug APK builds succeed. The APK's signature and
camera/location permissions were checked; unused microphone and shared-storage
permissions are excluded. Flutter tests exercise data parsing, geospatial
calculations, route progress, account lifecycle, and app interactions.
The database check uses a disposable pinned PostGIS Docker
image to check the schema, search radius, coordinate validation, sorting, and
public read-only access. Mocked service tests do not establish live provider
availability, account email delivery, or physical-device behavior.

Native Android/iOS release builds and real GPS/camera/compass behavior require
device validation. Replace Android's development signing configuration with
your production signing setup before store distribution.

## Project layout

- `lib/app/`: sign-in, guest access, Home scan, profiles, camera, and journey screens.
- `lib/device/`: foreground location, rear camera, compass, permissions, and lifecycle.
- `lib/core/`: restaurant data, pedestrian routing, and geospatial calculations.
- `supabase/`: restaurant schema, fictional seed data, and database checks.
- `android/`, `ios/`, `web/`: platform projects and permission declarations.
