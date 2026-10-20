Saved restaurant information is stored on each device in the Room/SQLite database
`restaurants.db`. It works offline; it does not synchronize across devices.
Camera discovery also requests nearby restaurants from **Google Places API (New)**
when a key and internet are available. Those results and their profile details
stay in memory and are not inserted into Room as offline records.

The `restaurants` table stores the name, cuisine, halal status, rating, opening
hours, price range, address, coordinates, and the displayed distance label.
IDs are generated for new records (`id = 0`) and retained when records are edited.
New databases contain the two supplied places:

| Name | Latitude | Longitude | Category | Halal |
| --- | --- | --- | --- | --- |
| Cafe UMSKAL | 5.280285448386224 | 115.17640486697412 | Food court | Yes |
| Anjung Selera Sungai Pagar | 5.28381416215476 | 115.17329350236913 | Food court | Yes |

Only the supplied fields are populated. Ratings use `0` to indicate unavailable
information; opening hours, prices, addresses, and stored distance labels are
empty. Discovery hides unspecified ratings and opening hours and calculates
distances from the device location.

The version 2 to 3 migration adds missing supplied places once, matching by name
and coordinates to avoid duplicates. Existing records and edits are preserved.
The earlier version 1 to 2 migration still clears legacy records as requested;
upgrading directly from version 1 to 3 clears those records and then adds these
two places. Migration runs when the updated app opens its database. Later edits
and deletions persist, including deleting every record; reopening does not reseed.

The discovery screen observes database changes while visible and combines saved
places with current Google results. Generic restaurant markers appear only
within a **1-kilometer** straight-line radius and inside
the current camera view. The controls panel, restaurant list, cuisine filter, and
halal filter have been removed. A floating back button and compact scan hints
remain available.

Scanning uses a native **CameraX** rear-camera preview, precise location and
reliable device orientation. The location fix must be no more than 30 seconds
old with reported accuracy of 50 m or better. Rotation-vector orientation must
be reliable and no more than 2 seconds old; a calibrated camera frame must be no
more than 1 second old. Missing, stale or unreliable input hides markers.
The sensor rotation is corrected for magnetic declination, and positions are
projected into the current camera viewport, including display rotation and crop.
Markers move as the camera turns and disappear when a place leaves the view or
radius. This camera implementation does not require ARCore certification.
The saved `distanceLabel` field is retained for schema compatibility and is not
used for current distance estimates.

Tapping a visible marker opens a compact profile with the restaurant name, type,
rating and halal status. **Get directions** sends the selected coordinates to
navigation. Saved data takes precedence; selected Google places can load missing
type/rating data with a lean request. Google Maps/provider attribution stays
visible for online content. Missing ratings and halal information stay unknown;
Google categories do not establish halal certification.

Discovery locates places from their coordinates and sensor-based camera direction.
The markers use generic restaurant icons. Distances are straight-line estimates.
The camera does not recognize restaurant buildings. See
[Camera discovery](camera-discovery.md) for device and overlay limitations.

Navigation uses the user's saved Google Maps Routes API key from Settings and
shows step instructions and a moving 3D camera pointer, plus loading and failure
states. The text describes the current action and previews the next turn. The
pointer separately targets up to 6 m ahead along the current ordered walking
segment, stopping at corners and provider step boundaries. It never substitutes
the restaurant's straight-line bearing for the pedestrian path. Near a right
turn, the text can describe that turn while the pointer still leads straight to
the junction. Panning or tilting the phone changes the pointer's screen position,
rotation and pitch; a target outside the view produces an edge pointer.

Navigation uses optional ARCore local pose tracking with an initial calibrated
true-north compass alignment on supported phones. An optional Google Play
Services for AR installation can be declined; unsupported or unavailable ARCore
uses CameraX and the rotation sensor. Short pose movement is GPS-anchored and
limited to 8 m and 5 seconds, rather than accumulating indefinitely. No extra
Google Cloud API or Geospatial key is needed. The scanner keeps its CameraX
camera, 1 km radius and fixed marker row.

The pointer requires a loaded, usable route, fresh GPS, a live calibrated camera
and reliable heading. Missing, failed or off-route guidance, or paused ARCore
tracking, hides it. Valid path geometry can still guide the pointer when a
provider maneuver is unrecognised. It is not anchored to a physical road or
entrance. Network and quota failures retry after a 30-second cooldown, with a
manual **Retry route**
action. Missing or rejected API key configuration offers **Open Settings** and
pauses automatic requests. **Profile > Help** explains API key setup.

To view or edit data during development, run the debug app, open **Scan for
Restaurant**, then use Android Studio **App Inspection > Database Inspector**
to open `restaurants.db` and the `restaurants` table. Repository methods are
suspending functions and should be called from a coroutine:

```kotlin
val repository = RestaurantRepository.getInstance(context)
val restaurant = repository.getRestaurant(1)
if (restaurant != null) {
    repository.save(restaurant.copy(hours = "9:00 AM - 9:00 PM"))
}
repository.delete(2)
```

Use `com.example.dinefinder.data.RestaurantRepository` for these methods.
`save` inserts new records or updates an existing record with the same ID.
`observeRestaurants()` returns a Flow that updates after changes.

The Room schema is checked into `app/schemas`. Future schema changes must increase
the database version and provide a migration that preserves existing information.
The version 1 to 2 migration intentionally clears restaurant records; version 2
to 3 adds the two supplied places. Future migrations should preserve records
unless another reset is explicitly required.
Destructive migration fallback is not enabled.

Run `bash ./gradlew :app:testDebugUnitTest :app:assembleDebug` (`gradlew.bat` on
Windows) to test initial records, one-time clearing and additions on upgrade,
duplicate prevention, CRUD, persistence after reopening, change observation,
distance/filter calculations, the 1-kilometer camera visibility boundary, projection
and input freshness, discovery permission and lifecycle behavior, route request
scheduling, route progression, and navigation destination validation, and to build
the app. Database and activity tests use Robolectric.
Camera rendering, real GPS/compass accuracy, device-specific preview projection,
ARCore pose/heading alignment and Google Places/Routes authorization require
testing on a physical phone. An ARCore-compatible device is optional because
navigation provides a sensor-camera fallback. See [Camera discovery](camera-discovery.md)
for physical-device checks and the `DineFinderAR` Logcat filter; no connected
phone was available for Logcat verification here.
