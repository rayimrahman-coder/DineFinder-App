# Set up a personal Google Maps API key

DineFinder uses Google Maps Platform **Places API (New)** to search for restaurants and load their available types and ratings, and **Routes API** for walking directions. Saved Room restaurants remain available without a key or internet. The camera shows matching restaurants within **500 m** using GPS and compass direction. Open **Profile → Help** for the same setup tutorial in the app.

## Before you start

You need a Google account, internet access and an active Google Cloud billing account. Google may ask for payment details during setup. Restaurant searches, ratings and walking routes above the applicable free allowance can incur charges. Review [Places API pricing](https://developers.google.com/maps/documentation/places/web-service/usage-and-billing) and [Routes API pricing](https://developers.google.com/maps/documentation/routes/usage-and-billing) before enabling billing.

## Eight setup steps

1. **Open Google Cloud Console.**\
   Open [Google Cloud Console](https://console.cloud.google.com/) in a browser.\
   Sign in with your Google account.\
   Accept the account terms if prompted.

2. **Create or select a project.**\
   Click the project selector at the top of Cloud Console.\
   Choose **New project** or **Create project**. Enter **DineFinder** as the project name and click **Create**.\
   Select that project, or choose a project you already own. Keep the same project selected for all remaining Cloud steps.

3. **Activate billing.**\
   Open **Menu → Billing**.\
   If you need an account, choose **Manage billing accounts → Create account** and complete Google's payment setup.\
   Return to your project's Billing page, choose **Link a billing account**, and select your active account.\
   If an active account is already linked, continue. Google's [billing status guide](https://docs.cloud.google.com/billing/docs/how-to/verify-billing-enabled) explains how to check it.

4. **Enable Places and Routes.**\
   Open **Menu → APIs & Services → Library**.\
   Search for **Places API (New)**, open it, and click **Enable**. Repeat for **Routes API** in the same project.\
   If the button says **Manage**, that API is already enabled.\
   Direct links: [Places API (New)](https://console.cloud.google.com/apis/library/places.googleapis.com) and [Routes API](https://console.cloud.google.com/apis/library/routes.googleapis.com).

5. **Create a dedicated API key.**\
   Open **APIs & Services → Credentials**.\
   Choose **Create credentials → API key**.\
   Copy the key displayed in the dialog and click **Close**. Keep it private and restrict it before use.

6. **Restrict the key to DineFinder.**\
   On **Credentials**, open the new key.\
   Under **Application restrictions**, choose **Android apps → Add**. Enter the app package name and installed-build signing SHA-1, using **App restriction help** below.\
   Under **API restrictions**, choose **Restrict key** and select **Places API (New)** and **Routes API**.\
   Click **Save**. Keep the app and API restrictions enabled.

7. **Save the key in DineFinder.**\
   Copy your key from **Cloud Console → Credentials**.\
   Open **DineFinder → Profile → Settings → Google Maps API**.\
   Paste only the key text into **API key**, then tap **Save API key**.\
   You should see **API key saved**. This saves the key on your device. Google checks it when you search for restaurants or request a route.

8. **Find a restaurant and test a walking route.**\
   Wait a few minutes for Cloud changes to apply. Keep internet and location on.\
   On Home, tap **Scan for Restaurant** and allow camera and precise location access.\
   Turn toward restaurants within **1 km**. Matching places appear as camera markers when they are in view.\
   Tap a marker to view available details, then tap **Get directions**.\
   A successful request shows **Walking route loaded**. If an error appears, use the troubleshooting table below. Walking routes can omit sidewalks or pedestrian paths; check your surroundings and use safe paths.

## App restriction help

DineFinder's package name is `com.example.dinefinder`. Ask the app provider or developer for the signing SHA-1 of the exact build installed on your phone. Enter the package name and colon-separated SHA-1 in the key's **Android apps** restriction. These details belong to the installed build.

If you build DineFinder yourself, open the project in **Android Studio → Terminal** and run `bash ./gradlew :app:signingReport` (Windows: `gradlew.bat :app:signingReport`). Copy the SHA-1 for the debug or release variant installed on your phone.

For a Play Store build, use **Play Console → App integrity → App signing → App signing key certificate** and its SHA-1. The app-signing certificate can differ from the upload certificate. Debug, release and Play Store builds can use different certificates, so a fingerprint from another build will not match your installed app.

## Usage and costs

Use Cloud Console to monitor Places and Routes requests, configure suitable API quotas and set billing alerts. Restaurant ratings can add charges beyond the initial search. Alerts-only budgets notify you and do not automatically cap spending; see [Google Cloud budget alerts](https://cloud.google.com/billing/docs/how-to/budgets). Removing the key stops new online restaurant and route requests in DineFinder; saved restaurants remain available. Delete or restrict the key in Cloud Console to control its use elsewhere.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Invalid key or permission denied | Saved key text and API restrictions permitting both Places API (New) and Routes API in the same project. Recheck step 6 and App restriction help. Keep restrictions enabled. |
| API disabled or billing unavailable | Both APIs are enabled and billing is active in the same project as the key. Wait briefly after changes. |
| Quota reached | Review project usage and quota limits. Wait for reset or review costs before raising a limit. |
| No camera markers | Check camera and precise location permission, GPS freshness and compass accuracy. Face a restaurant within 1 km. Online search needs internet and a configured key; saved places work without them. |
| No walking route | Check internet and precise location. Walking route coverage varies. The arrow stays hidden until a usable walking route is loaded. |
| Route loaded but no moving pointer | Check fresh GPS, compass calibration and a live camera. ARCore tracking must be active when that backend is used. Unsupported phones or a declined optional AR installation use CameraX and the rotation sensor. This does not require another Cloud API or key. |

## Implementation and deployment notes

Directions API and the older Places API became Legacy on March 1, 2025. Use the current APIs for new projects; see [Legacy products](https://developers.google.com/maps/legacy) and the [Routes migration guide](https://developers.google.com/maps/documentation/routes/migrate-routes). This app uses POST https://routes.googleapis.com/directions/v2:computeRoutes with WALK travel mode, coordinate origin/destination and a restricted response field mask for the route and walking steps. See [Get a route](https://developers.google.com/maps/documentation/routes/compute_route_directions).

Nearby discovery uses Places API (New) searchNearby with a 1 km location circle and a field mask limited to basic display data. The initial search fields use the Nearby Search Pro tier. A lean type/rating request runs when a selected online restaurant needs these fields. Ratings trigger the Place Details Enterprise tier. Profiles do not request photos, addresses, opening hours or websites. Billing follows the highest-priced requested field tier, so adding fields changes cost; see [Nearby Search](https://developers.google.com/maps/documentation/places/web-service/nearby-search), [Place Details](https://developers.google.com/maps/documentation/places/web-service/place-details), and [Places billing](https://developers.google.com/maps/documentation/places/web-service/usage-and-billing).

Google restaurant data stays in memory for the current session and is not inserted into Room as offline data. Place IDs are an exception to general Places storage restrictions. Profiles show Google Maps and any returned place-provider attributions with online type/rating data. See [Places policies](https://developers.google.com/maps/documentation/places/web-service/policies).

Restaurant markers use generic icons. Profiles show type, rating and halal status; unknown values remain unavailable. Discovery continues to use CameraX with GPS/compass markers on a fixed row within 1 km.

Walking navigation keeps step instructions separate from its moving 3D path pointer. The pointer looks up to 6 m ahead along the active, ordered walking segment and stops at corners and provider step boundaries. It follows the path toward the next junction rather than aiming directly at the restaurant. Phone movement changes its screen position, rotation and pitch; a target outside the view produces an edge pointer. Near a turn, the text can name the turn while the pointer still leads to the junction.

Supported phones use ARCore's relative pose with calibrated compass true-north alignment. Google Play Services for AR may offer an optional installation or update; declining it or using an unsupported phone selects CameraX and rotation-sensor guidance. Fresh GPS, a live calibrated camera and reliable heading are required. Paused ARCore tracking hides the pointer until it recovers. This uses local tracking, not ARCore Geospatial or Earth positioning, so the eight key-setup steps and the two enabled APIs remain unchanged. No extra Cloud API, shared key or Geospatial configuration is needed. See [Camera discovery](camera-discovery.md) for model credits, bounded GPS/pose movement and physical-device Logcat verification.

For direct mobile web service requests, Google's [security guidance](https://developers.google.com/maps/api-security-best-practices#secure-client-side-web-service-calls) calls for Android application restrictions and X-Android-Package / X-Android-Cert headers. The certificate header contains the signing certificate SHA-1 as a 40-character hexadecimal string without separators; Cloud Console accepts the corresponding colon-separated fingerprint. The app sends its installed identity rather than a fixed development certificate.

The same guidance requires verifying that incorrect application identifiers are rejected by the endpoint. Automated tests validate request construction; they cannot establish Google's enforcement or billing configuration without a live Cloud project and key. Before distributing a production integration, test the configured key with the correct identity and with incorrect package/certificate headers, and confirm incorrect identities are denied. If the service does not enforce the intended application restriction, use an authenticated secure server proxy and a server-restricted key. Do not solve that failure by distributing an unrestricted key.

Google recommends a secure proxy or native SDK where suitable for a shared production service. This personal-key flow uses each user's own Cloud project and charges. See [Places setup](https://developers.google.com/maps/documentation/places/web-service/get-api-key), [Routes setup](https://developers.google.com/maps/documentation/routes/get-api-key), and [walking-route limitations](https://developers.google.com/maps/documentation/routes/reference/rest/v2/RouteTravelMode) for current requirements.
