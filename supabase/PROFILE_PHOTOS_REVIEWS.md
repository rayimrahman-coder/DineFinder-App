# Profile photos and reviews (Android 0.6.1)

## Enable in Supabase

1. Complete the 328-profile import using the numbered SQL files described in
   [the import guide](IMPORT_RESTAURANT_PROFILES.md).
2. In SQL Editor, run the whole small file
   [202610280001_profile_photos_reviews.sql](migrations/202610280001_profile_photos_reviews.sql).
3. Pull the latest Android code, sync Gradle, install/run version 0.6.1, scan and
   open a restaurant profile. Photos and reviews load separately from discovery.

The migration is safe to repeat. It does not delete source profiles or restaurant
records and does not alter Auth users, login tables, coordinates or routing.
It has been tested locally; it is not automatically applied to the live project.

## What is displayed

- A restaurant photo using the HTTPS `photo` URL imported into
  `restaurant_source_profiles`. Its `restaurant_id` must link to `restaurants.id`.
- An optional `restaurants.photo_url` override takes priority. Use a publicly
  accessible HTTPS image URL, such as a public Supabase Storage object URL.
- The exported review total and 1–5-star breakdown, where present.
- Up to ten newest published written reviews stored in `restaurant_reviews`.

The uploaded spreadsheet contains counts and star breakdowns, **not individual
written review text**. No written reviews are fabricated. The app explains when
written reviews are unavailable even if the exported aggregate count is nonzero.
A missing photo gets a small placeholder; a failed image gets a retry action.
Images can expire or become unavailable at the original host. Image URLs come
from Supabase, while image bytes load from their HTTPS host without app keys.
No Google Places API key or Google review-scraping request is used.

The app retains the cuisine/rating cards, saved state, fixed walking navigation,
and camera/voice navigation. Halal labels and GPS coordinates remain hidden.

## Add actual written reviews

In the dashboard's `restaurant_reviews` table, insert a real review with:

- `restaurant_id`: the existing restaurant UUID.
- `author_name`: the public display name to show with the review.
- `rating`: 1–5 when known, otherwise NULL.
- `review_text`: the actual review text (1–5,000 nonblank characters).
- `reviewed_at`: the real review time when known.
- `published`: true only when the review should be publicly displayed.

Leave `id` at its generated default. This release adds viewing, not an in-app
review submission flow. Drafts stay private. Do not put account email addresses,
passwords or other private account information into public author names/text.

## Public endpoint and missing data

`restaurant_profile_details(restaurant_id uuid)` returns only a photo URL,
review count, score counts and published review display fields. Full source
profiles and raw review tables retain no public table access. The function uses
a fixed empty search path and grants execution to `anon` and `authenticated`.
No new public write permissions are granted.

Before the optional source import is installed, the endpoint still works with a
manual photo override and published written reviews. When the endpoint is absent,
or the network fails, the app keeps the basic restaurant profile and navigation
available and provides **Retry photos and reviews**. Reopen the profile after
changing data in the dashboard to load the latest details.

For a known restaurant, check the endpoint in SQL Editor:

```sql
SELECT public.restaurant_profile_details(id)
FROM public.restaurants
ORDER BY name
LIMIT 1;
```

Run local database checks with `bash supabase/tests/run-profile-media.sh`.
Actual remote image availability and mobile network behavior need phone testing.
