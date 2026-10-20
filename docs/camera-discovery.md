# Camera discovery and walking guidance

DineFinder remains a native Kotlin Android app. A CameraX rear-camera preview
provides the live view. GPS and device orientation place restaurant markers over
that view; no ARCore session or Google Play Services for AR is required.

## Discover a restaurant

1. On Home, tap **Scan for Restaurant** and allow camera and precise location.
2. Turn on location and face nearby restaurants. With a fresh, sufficiently
   accurate location and reliable compass, markers appear for matching places
   within **1 km** that fall inside the camera's horizontal field of view.
3. Tap a marker to open its profile. The app shows its type, rating and halal status
   with a **Get directions** action.

The scanner observes saved Room records, including Cafe UMSKAL and Anjung Selera
Sungai Pagar. These places work without an API key or internet. A configured key
with **Places API (New)** enabled adds nearby online results; online search,
ratings require internet. Results are held for the current session
rather than saved to the offline restaurant database.

Markers use generic restaurant icons and stay on one fixed row halfway down the
camera view. Turning the phone moves markers horizontally according to each
restaurant's bearing. Tilting or rolling the phone does not move them up or down.
Direction comes from the horizontal component of the rear camera's optical axis;
the marker height does not represent restaurant elevation or an entrance.
The camera image is a background for these geographic overlays and does not
identify a restaurant's facade or logo. Restaurants beyond 1 km, behind the
camera, or outside the horizontal view are hidden. Pointing almost straight up or
down hides markers because the camera has no reliable horizontal direction.
Stale or unreliable camera, location or orientation input hides markers until the
data recovers.

The visibility boundary includes places exactly 1 km away. The scanner accepts
location fixes up to 30 seconds old with reported accuracy up to 50 m, reliable
rotation-vector samples up to 2 seconds old, and calibrated camera frames up to
1 second old. Horizontal placement and clipping use live Camera2 intrinsics or
focal pixels derived from the actual lens and sensor dimensions. CameraX's preview
transform accounts for rotation, stream crop and zoom; vertical projection is
ignored to keep the marker row steady. When calibration is missing or invalid,
the preview can remain visible while geographic markers stay hidden.

## Restaurant profiles

The profile shows the restaurant name, type, rating and halal status, plus a
**Get directions** button. Saved information takes precedence; Google ratings
are loaded when needed for a selected online place. Missing ratings or halal
information are explicitly unavailable. Google's place category does not
establish halal certification. Google content retains Google Maps and any
returned provider attribution.

## Walking guidance

**Get directions** uses **Routes API** to request a walking route and step instructions.
The main instruction and shaded 3D arrow show the current walking action. On a
straight approach to a right turn, the main instruction reads **Continue straight
for 63 m** and the arrow points forward. A separate **Then in 63 m: Turn right**
line describes the upcoming turn. Within 8 m along the route and within 8 m of
the junction, the main instruction changes to **At the junction: Turn right**
and the arrow turns right. Follow the path to that junction before turning.
On entering the outgoing step, the text changes to **Now: Turn right**. The turn
cue remains visible until progress along that step confirms completion; normal
GPS gaps or small position errors do not immediately consume it. Then the arrow
points forward again. A first location update well past a junction resumes the
outgoing path instead of replaying the completed turn. Text, model and caption
update together from the same guidance result.

The arrow stays fixed on screen and does not rotate with the compass. It is not
anchored to the ground or a physical road. Curved or unverified route segments,
unknown maneuvers and routes without usable step instructions show neutral
follow-path text without a directional arrow.

Arrows require a loaded, usable walking route, fresh GPS and a live camera.
Loading, route failures and off-route positions hide them. Failures offer the
relevant retry or Settings action.

The arrow uses the bundled **Direction Arrow** GLB by
[Alihan](https://sketchfab.com/Dare0), from
[Sketchfab](https://sketchfab.com/3d-models/direction-arrow-6ef46718c7b242e39fcad7f27ee858a5),
licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The file is unchanged; the app applies centering, scaling, rotation and lighting
when drawing its mesh. **Profile → Help → Model credits** provides these credits
and links in the app. No ARCore session or extra model-rendering dependency is
required.

Walking directions can omit sidewalks or pedestrian paths. Use safe paths and
check your surroundings. Straight-line restaurant distance is not the remaining
walking-route length. Missing or stale location removes the current-action arrow
until the app can locate the user on the route again.

## Phone support and field verification

Google's [ARCore supported-device table](https://developers.google.com/ar/devices)
lists **POCO X8 Pro** with Depth API support. The checked table did not list the
exact **POCO X8 Pro Max** model. The CameraX implementation avoids using this
certification as a requirement; it does not prove this app has been tested on
either model.

Test the installed build on the user's physical phone for camera startup and
resume, portrait/landscape preview crop, GPS accuracy near buildings, compass
calibration, marker alignment when turning, and progression at walking turns.
Camera properties and sensor quality differ between devices. Geographic points
have no restaurant elevation or building outline, so positioning cannot promise
pixel alignment with a physical entrance. Automated tests cover calculations and
state handling; physical camera/sensor and live Google authorization checks remain
device tests.

## Google data and cost

Set up both APIs using [the key tutorial](google-maps-api-key.md). Nearby Search
requests only the display fields needed for markers. Profile requests include
the rating and required type/source fields. Ratings use the Place Details
Enterprise tier; photos, hours and website data are not requested by the profile.
Review project usage and quotas before wider use.
See [Places billing](https://developers.google.com/maps/documentation/places/web-service/usage-and-billing)
and [Routes billing](https://developers.google.com/maps/documentation/routes/usage-and-billing).

Google content is not added to Room as a permanent cache. Supplied provider
credits remain visible. Google's
[Places policies](https://developers.google.com/maps/documentation/places/web-service/policies)
describe storage exceptions and attribution requirements.
