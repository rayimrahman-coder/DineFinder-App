# Implementation against the supplied DineFinder report

The supplied FYP report is used as a product and wireframe reference. Existing
user decisions remain: native Android Kotlin/Compose, Supabase accounts and
restaurant data, a fixed 1 km search, and no Google API key.

| Report reference | Implemented behavior |
| --- | --- |
| §4.2 / Figure 19, p.57 | Home greeting and a prominent Scan for Restaurant button. |
| Figure 20, p.58 | Camera-first discovery, dining markers and distances, plus a list for devices without a compass. |
| Figure 21, pp.58–59 | Bottom metadata sheet with cuisine, halal status, rating, hours, address, price availability, GUIDE ME and CANCEL. |
| Figure 22, p.59 | Live distance counter, walking instructions, and optional locally tracked 3D ground arrow. |
| Figure 23, p.60 | Dedicated arrival confirmation and return to Home. |
| §4.3, pp.64–66 | Supabase Auth/private user profiles and public read-only restaurant profiles. Temporary route state stays in memory. |

## Services and keys

The report mentions Flutter and Firebase; the app retains the user's explicitly
requested native Kotlin and Supabase implementation. Supabase uses the public
client configuration in ignored `supabase.properties`. Passwords remain managed
by Supabase Auth. Restaurant records and existing SQL setup are preserved.

Walking paths come from the FOSSGIS OpenStreetMap pedestrian router without a
routing key. ARCore's **local** motion tracking and horizontal-plane detection
need no Google API key. The app does not enable Geospatial localization, Cloud
Anchors, Google Maps, Places, or Directions APIs. Google Play Services for AR is
required only on devices using the optional local 3D view.

## Practical limits

Camera discovery markers estimate geographic bearings; they do not recognize
restaurant signs or precisely identify storefront entrances. Local SLAM anchors
a visible arrow to a detected ground surface, while its heading still comes from
GPS/compass bearings to the walking route. It does not remove GPS error or infer
safe crossings and obstacles. An unavailable AR runtime or tracking capability
falls back to the ordinary camera compass view; missing planes show initialization
guidance instead of fabricating a ground anchor.

Arrival is based on proximity to the actual restaurant coordinates. Reaching a
path endpoint that was snapped away from the restaurant does not confirm arrival;
the user is told to check the entrance nearby. This remains a GPS proximity
estimate, not proof of reaching an exact doorway.

Unknown ratings, operating hours, prices, and halal status are displayed as
unavailable, never invented. Existing user-supplied HALAL listings remain.
Food ordering, reservations, payment, AI recommendations, review authoring,
commercial restaurant synchronization, and navigation-history storage are outside
the report's stated prototype scope and were not added.

## Validation

Native unit tests cover bearings, AR-space turn orientation, invalid tracking,
halal metadata, path-snapping versus restaurant arrival, routing, authentication,
and existing geospatial behavior. The debug APK and Android lint are checked.
Plane detection, renderer alignment, AR installation, permissions, lifecycle,
and real walking require testing on an ARCore-compatible physical phone. The
report's claims about a user study are not treated as test results for this app.
