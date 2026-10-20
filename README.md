# DineFinder

A Flutter app for discovering nearby restaurants, opening their profiles, and
following walking directions through a camera compass. Android, iOS, and web
project files are included.

## Run the demo

Use Flutter **3.44.0** (Dart 3.12.0), then run from the repository root:

```sh
flutter pub get
flutter run -d chrome
```

Or use `flutter run` with an Android/iOS device. Android needs an SDK with the
platform/build tools selected by Flutter; iOS needs macOS, Xcode, and CocoaPods.
The Android minimum SDK is 24 and the iOS deployment target is 15.0.

No credentials are required for Demo mode. Discover six fictional Kuala Lumpur
restaurants, search by name/cuisine, change the discovery radius, open a profile,
and tap **Navigate**. The Camera screen's heading slider simulates turning a
phone. A journey's Walk slider simulates progress and arrival. Sample names,
ratings, locations, illustrations, and route geometry are explicitly labeled as
demo data. They do not represent real restaurants or walkable streets.

## Connect live data

Follow [the backend instructions](supabase/README.md) to create the PostGIS
restaurant schema and deploy the walking-route function. Enable anonymous Auth
sign-ins and configure the routing provider key in the server's secrets.

Copy `.env.example.json` to the ignored `env.local.json` and fill in your
Supabase project URL and **public publishable/anon client key**. Then run:

```sh
flutter run --dart-define-from-file=env.local.json
```

For web, append `-d chrome`. Never put a Supabase service-role key or routing
provider key in a Flutter build. Flutter's build defines are visible in the
distributed client; they are not secret storage. The OpenRouteService key stays
in the Edge Function. Public read access is limited by RLS; restaurant writes
require an authorized backend.

Switch **Demo → Live** and accept foreground location/camera permissions. GPS
coordinates query the indexed `nearby_restaurants` RPC, with a 1 km default
radius. Distances in discovery are geographic distances. Tapping a restaurant
opens its profile; Navigate requests an actual walking route through the
authenticated Supabase function. There is no synthetic fallback for failed live
requests.

The first live route creates an anonymous Supabase user session. GPS coordinates
are sent to Supabase for nearby discovery and to the route provider through the
function for routing. Camera frames stay on the device. Backgrounding the app
releases the camera and pauses sensors; resuming reconnects them.

## Camera guidance and device support

Camera markers use the restaurant bearing relative to a compass heading. The
arrow points toward the next route vertex, with remaining distance, route steps,
off-route recalculation, and arrival detection. This is a **GPS/compass overlay**,
not ARCore/ARKit world anchoring. Buildings and obstacles are not detected.
Alignment depends on GPS accuracy and compass calibration; use street directions
and remain aware of traffic. A physical device is needed to validate these flows.

Web camera and location need HTTPS (or local development). Browser compass
support varies; without a heading, live directional markers and arrows are
withheld rather than showing an invented direction. The interactive demo works
without hardware access. Native devices are the primary live-camera target.

## Checks

```sh
flutter analyze
flutter test
flutter build web --no-web-resources-cdn
node --test supabase/functions/walking-route/route.test.mjs
bash supabase/tests/run-postgis.sh
```

The database check uses a disposable, pinned PostGIS Docker image and validates
the migration, radius filtering, coordinate validation, sorting, and denied
public writes. Route parser tests run under Node 22.18+/24+. Flutter tests cover
geospatial calculations, demo radius filtering, route progress, and the demo UI.

Validation in the cloud environment: Flutter analysis passed; all 18 Dart/widget
tests passed, including search, profile selection, navigation, and simulated
arrival; the release web build passed. The five routing tests, real PostGIS
migration/permission checks, and Gradle wrapper startup also passed. No production
Supabase service, routing provider connection, physical-device behavior, or native
release build has been validated.

Before store distribution, replace the Android development signing configuration
with your production signing setup. The backend deployment guide describes
provider quotas and authentication requirements.

## Project layout

- `lib/app/`: responsive discovery, profiles, camera markers, and journey screens.
- `lib/device/`: foreground GPS, rear camera, compass, permission/lifecycle handling.
- `lib/core/`: restaurant models, repositories, geospatial and route calculations.
- `supabase/`: schema, sample data, authenticated routing function, backend tests.
- `android/`, `ios/`, `web/`: platform projects and permission declarations.
