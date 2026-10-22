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

`imports/20261023_labuan_outscraper.sql` is a complete SQL Editor import (including
setup). From 598 spreadsheet listings, it selects 328 operational food outlets,
excludes 251 entries not clearly food-service businesses and 19 closed food
outlets. Restaurants, cafes, food courts, hawkers, bakeries and dessert/drink
outlets are included. Source status and details are not independently verified.
The two owner-supplied food courts are retained. On an empty project the total
is 330. Re-running the import adds no duplicate records.

Optional phone/website/review-count/source columns are stored in Supabase. The
current app displays the core profile fields returned by `nearby_restaurants`;
these additional contact/source columns are available for future UI support.
Imported halal status stays unknown. Required price-level defaults are
placeholders, not verified prices. SQL was tested twice in a disposable PostGIS
database; it has not been automatically deployed to your live project.
