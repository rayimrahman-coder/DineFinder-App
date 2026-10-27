# Profile photos and in-app reviews (Android 0.6.3)

## Enable in Supabase

1. Complete the source-profile import described in [the import guide](IMPORT_RESTAURANT_PROFILES.md).
2. Run the whole small SQL file
   [202610280002_profile_review_links.sql](migrations/202610280002_profile_review_links.sql)
   in Supabase SQL Editor. It is safe to repeat and also works if the earlier
   photo/review migration has not been run.
3. Pull the Android code, sync Gradle and run version 0.6.3 on the phone.
4. Open a restaurant profile and tap **Read reviews**.

The button loads the exact `location_reviews_link` stored on the restaurant's
latest linked `restaurant_source_profiles` row in an in-app WebView. DineFinder
stays open. Back moves through webpage history, then returns to the restaurant;
Close always returns to the same profile. Android Back follows the same route.
No external Maps app or browser is launched, including for app-launch links on
the page. No Google API key is needed.

This shows a live Google Maps webpage inside DineFinder, not copied review cards
or scraped comments. Google controls the page and may show consent, sign-in or
app prompts, or restrict embedded access. The app cannot guarantee that every
Google review page will render on every phone. Network/page errors offer retry
and return-to-profile controls without launching another app. If fully native
review cards are wanted, actual written review data must be stored in Supabase;
the spreadsheet itself contains aggregate counts, not review comments.

## Photos and review summary

- The photo uses `restaurant_source_profiles.photo`; `restaurants.photo_url`
  is an optional manual override and takes priority. Use a publicly accessible
  HTTPS image, such as a public Supabase Storage object URL.
- The review total and 1–5-star breakdown remain visible where available.
- The former written-review list is replaced by the in-app review viewer. Existing
  `restaurant_reviews` records are preserved; this change does not delete them.
- If no link is stored, the app shows **Review link not available**. Invalid or
  non-HTTPS links are ignored. If the review page cannot load, an error appears
  without closing the profile or disabling navigation.
- Missing photos get a small placeholder; failed image downloads offer retry.
  URLs can expire or become unavailable at their original image host.

Photos and review details load only when a profile opens. Reopen the profile
after changing Supabase data. Halal labels and GPS coordinates remain hidden,
and cuisine, ratings, saved state and walking navigation remain available.

## Endpoint and access

`restaurant_profile_details(restaurant_id uuid)` now also returns
`location_reviews_link`. Its fixed-search-path public function exposes only
selected profile fields; full source tables and unpublished written reviews
remain private. No new public write permissions are granted.

The migration restores the nullable internal `is_halal` column required by the
existing nearby-search function if it was removed. This does not display a halal
label in the restaurant profile. It does not change coordinates, accounts,
source profiles, photo overrides or routing.

The migration has been tested locally, not automatically deployed to the live
project. The Data API secret key cannot execute table/function creation SQL.

```sql
SELECT r.name, public.restaurant_profile_details(r.id)->>'location_reviews_link' AS reviews_link
FROM public.restaurants r
WHERE r.name ILIKE '%HotSpot%';
```

Run `bash supabase/tests/run-profile-media.sh` for disposable database checks.
Actual browser/Maps availability and image hosting need phone testing.


## Embedded viewer checks

The viewer uses HTTPS, JavaScript and DOM storage for the live website. It does
not receive Supabase keys, account tokens, native JavaScript bridges, file access,
camera/microphone permissions or phone geolocation. TLS failures are cancelled,
non-HTTPS/app-launch navigation is blocked, and the WebView pauses with the app
and is destroyed when closed. This feature requires Android System WebView.
Tests verify URL loading, no external intents, navigation history, close/reopen,
page errors/retry, unsafe-link blocking and lifecycle cleanup with a simulated
WebView. Google page rendering and consent behavior need physical-phone testing.

Version 0.6.3 uses the same `location_reviews_link` endpoint as 0.6.2. No additional
SQL is needed if migration 202610280002 has already been run.
