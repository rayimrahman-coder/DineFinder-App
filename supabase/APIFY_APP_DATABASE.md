# Use the Apify table in DineFinder 0.7.0

The app now reads `public.apify_food_outlets` through two explicit public RPCs:
`apify_nearby_restaurants` and `apify_restaurant_profile_details`. It no longer
uses the old `restaurants` table for discovery or restaurant profile media.
Authentication and user profiles continue to use the existing Supabase project.

## Activate in Supabase

1. Complete your Apify import batches in this same project. Ten supplied batches
   contain 460 records (448 not marked closed, 12 temporarily closed), assuming
   all imports completed and no other table edits were made. These are distinct
   Google place IDs, not a guarantee of distinct physical venues.
2. Open SQL Editor and run all of
   [`migrations/202610290001_apify_app_endpoints.sql`](migrations/202610290001_apify_app_endpoints.sql).
   It is small and safe to repeat. It requires the import table to exist.
3. Check counts:

   ```sql
   select business_status, count(*) from public.apify_food_outlets
   group by business_status;
   ```

4. Test at a known outlet's coordinates (this call reads only):

   ```sql
   select public.apify_nearby_restaurants(latitude, longitude, 1000)
   from public.apify_food_outlets
   order by name limit 1;
   ```

5. Pull, Sync and Run the Android app from Android Studio. Check the version is
   0.7.0. Scan on a phone with precise location and internet enabled. Only records
   within 1 km of the phone are returned. A distant imported restaurant will not
   appear merely because it is in the new table.

The migration leaves the old tables/functions and login accounts intact. It does
not copy old-only restaurants into the new table or synchronize the two tables.
Update names, coordinates, hours and closure status in `apify_food_outlets` going
forward; refresh/re-enter scanning to see changes. In particular, records that
only existed in the old table will not appear in discovery until added here.

## What the app shows

- Both OPERATIONAL and TEMPORARILY_CLOSED listings are discoverable. Temporary
  closure is labelled in the restaurant's About text. Source status is historical
  and may need manual correction.
- The main supplied photo URL, with the first gallery URL as a fallback when
  there is no main URL. Photos remain external URLs; Google can expire or block
  them. This migration does not download or host image bytes in Supabase Storage.
- Review totals and star distribution; up to 10 imported nonempty written reviews
  displayed natively in the profile, attributed to Google Maps. A count alone
  never becomes fabricated comment text. Empty imports show an honest empty state.
- Reviewer display names, ratings and bounded comment text are public. Reviewer
  IDs, profile links, owner metadata and the complete source JSON stay private.
- No Google Maps review WebView is needed for these new endpoints.

The raw table stays administrator-only with RLS enabled and SELECT revoked from
app roles. Do not add broad anonymous table policies just to make the app work.
The SECURITY DEFINER functions use an empty search path and explicit projection.
The search radius is bounded to 1,000 m and a maximum of 200 nearest results.
Walking routes still use the existing key-free FOSSGIS/OSM service.

## Validation

`bash supabase/tests/run-apify-app.sh <directory-of-import-sql-files>` runs against
a disposable local PostgreSQL container. It repeats imports/migrations and checks
radius ordering, temporary closures, private source fields, public RPC access,
review projection, missing IDs and every imported outlet's own-location search.
It never connects to the live Supabase project.
