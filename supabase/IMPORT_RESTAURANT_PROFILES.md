# Import the 328 complete restaurant profiles

Use [20261027_full_restaurant_profiles.sql](imports/20261027_full_restaurant_profiles.sql).
It imports **only the 328 previously reviewed operational food outlets** from
`Outscraper-20261021193011s1555.xlsx`. The other 270 listings are excluded entirely.
All 65 spreadsheet fields are preserved for every selected outlet, including
blank values, contact details, business categories, coordinates, ratings, review
counts/breakdowns, hours, photo/menu/order links and source/business metadata.
Source details reflect the supplied export; they are not independently verified.

## Run in your Supabase project

The full SQL file is about 1.3 MB and exceeds the Supabase SQL Editor query-size
limit. Use the smaller files in [restaurant_profile_batches](imports/restaurant_profile_batches/README.md).
Each file is below 175,000 bytes (about 171 KiB).

1. Open Supabase project **oueafqgadqggyinpmyoi → SQL Editor → New query**.
2. Paste all of `00_setup.sql` and click **Run**. “Success. No rows returned” is normal.
3. Run `01_profiles.sql`, then `02_profiles.sql`, continuing through `09_profiles.sql`,
   **one file per query**. Replace the editor contents or open a new query each time.
   Do not concatenate the files back into a single large query.
4. Run `90_verify.sql` last. Its final result should show:

   | source_listings | linked_food_outlets | original_columns_per_listing |
   | --- | --- | --- |
   | 328 | 328 | 65 |

5. In Table Editor, inspect `public.restaurant_source_profiles` for all 65 original
   columns plus source metadata and the complete `raw_data` JSON object.
   Its `restaurant_id` links each profile to `public.restaurants`.
6. In DineFinder, swipe up the scan panel and tap **Refresh location and search**.
   The app still shows only restaurants within 1 km of the phone's GPS position.
   No Android rebuild is needed for this data import.

If a batch fails, stop and share that file's name and exact error. Successful
previous batches stay imported. Retry a failed batch safely after fixing its
error; do not delete existing tables or rows. Verification catches skipped files.

The original large SQL remains available for users with a direct PostgreSQL
connection; use a local `psql` client with your normal database connection settings.
It is not the recommended SQL Editor path.

The app's public key cannot run this import. Execute it with the dashboard's
normal database owner access; never put an administrator key into the app or chat.
This file has been tested locally; it has not been applied to the live project.

## Existing records and repeat imports

The import matches the previous import's UUID, then Google place ID, then an
identical name within 50 metres. It fills empty/unknown supported profile fields
and inserts missing restaurants. Existing IDs, names, coordinates, known ratings,
addresses and other supplied values are retained. In particular, it does not
reset manually corrected Anjung Selera Sungai Pagar coordinates.

Source profiles retain the workbook's original values, even where a manually
maintained restaurant record differs. Each original source row is keyed by the
workbook SHA-256, sheet and Excel row number, so running this same file again
creates no duplicate source profiles or restaurants. No listings, Auth users or
login tables are deleted. Missing fields required by nearby search, such as
`tags`, are repaired automatically.

Full source profiles are for dashboard/admin use. They do not grant the public
app access to the complete raw export or any new write permissions. The app's
existing nearby-search response and profile design remain unchanged: cuisine,
rating, address and supported details display normally; GPS coordinates, halal
status and restaurant pictures remain hidden in the profile UI.

## Verify after running

```sql
SELECT count(*) AS imported_profiles,
       count(restaurant_id) AS linked_restaurants
FROM public.restaurant_source_profiles
WHERE source_sha256 = '8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21';

SELECT r.name, r.cuisine, r.rating, r.opening_hours,
       s.phone, s.website, s.menu_link, s.photo
FROM public.restaurant_source_profiles s
JOIN public.restaurants r ON r.id = s.restaurant_id
ORDER BY r.name
LIMIT 20;
```

The total in `public.restaurants` can be larger than 328 because existing and
owner-supplied restaurants are preserved.

## Reproduce and test locally

The generator requires Python and `openpyxl`. It validates the supplied workbook
layout and reuses the existing reviewed selection in the older SQL import.

```sh
python3 supabase/tools/build_restaurant_profile_import.py path/to/Outscraper-20261021193011s1555.xlsx
python3 supabase/tools/split_restaurant_profile_import.py
DINEFINDER_IMPORT_BATCHES=1 bash supabase/tests/run-full-profile-import.sh path/to/Outscraper-20261021193011s1555.xlsx
```

Tests run in a disposable PostGIS container. They compare every selected original
cell with the stored JSON, import twice, reproduce missing table columns, preserve
manual coordinates/ratings, check matching to an existing UUID, restrict raw
profile access, and exercise public-role 1 km searches. They never connect to the
live project.
