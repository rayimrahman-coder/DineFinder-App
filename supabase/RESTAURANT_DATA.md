# Supabase is the restaurant data source

The active app flow is:

1. Home → Scan for Restaurant → camera/location permission consent.
2. Native GPS obtains a fresh coordinate fix.
3. `DineFinderApi.nearby` calls this project's Supabase
   `rest/v1/rpc/nearby_restaurants` with radius 1000 metres.
4. The response supplies marker locations and complete supported profile fields.
   Tapping a marker opens those returned Supabase details without a third-party
   place-details request.

There is no Google Places or OpenStreetMap restaurant lookup in this flow and no
local/fictional fallback when Supabase returns no records or an error. Legacy
Places/Room source files remain for now but are not opened by the Home scan
button. No Google API key is required by the active flow.

## Add or edit a restaurant

Run `supabase/setup.sql` once if the schema is missing. Open **Supabase → Table
Editor → public.restaurants**, then insert a row with:

- `name` and `cuisine` (required).
- `latitude` and `longitude` in decimal degrees (required).
- `description`, `address`, `opening_hours`, and `rating` when known.
- `is_halal`: true/false only if the status is known; otherwise NULL. Do not add a
  HALAL tag when status is unknown.

Leave `id` and `created_at` at their generated defaults. Do not edit the generated
`location` column. The schema's rating 0 / price-level 1 defaults are unknown-data
placeholders; the app does not claim these are verified ratings or prices.

The owner-supplied Cafe UMSKAL and Anjung Selera Sungai Pagar entries are in
`setup.sql`. Additional Labuan restaurants must be entered with accurate data.
The app displays only entries within 1 km of the current phone location.

The public app key only reads restaurant records. Use your dashboard SQL/Table
Editor to maintain the data; never enable public insert/update/delete access or
put administrator credentials in the Android app.

Restaurant sourcing and pedestrian routing are separate. The existing Navigate
implementation currently sends route endpoints to the key-free FOSSGIS walking
service; it does not use that service to obtain restaurant profiles.

## Supplied Labuan spreadsheet

Use the [complete profile import instructions](IMPORT_RESTAURANT_PROFILES.md) and
the smaller files in `imports/restaurant_profile_batches/`. They import **328 operational food
outlets only**, with all **65 original spreadsheet fields** stored in
`public.restaurant_source_profiles` and linked to the app's `public.restaurants`.
The remaining 270 source listings are excluded entirely.

Existing records and manual corrections are preserved. Missing restaurant rows
are inserted; empty/unknown supported fields can be filled from the file. Re-running
adds no duplicates. The SQL also repairs missing nearby-search columns such as
`tags`, without resetting restaurant coordinates or altering login records.

The current app displays supported profile fields returned by `nearby_restaurants`.
Additional source fields (such as photo links, review distributions, menu/order
URLs and business metadata) remain stored for dashboard use and future UI support.
No restaurant photo, GPS coordinate or halal label is added to the profile UI.

The earlier `imports/20261023_labuan_outscraper.sql` remains for historical reference;
use the newer import above to retain full profile data and manual corrections.
The import is tested in disposable PostGIS databases, not deployed automatically
to the live project.
