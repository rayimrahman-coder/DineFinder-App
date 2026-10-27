# Supabase SQL Editor import — 328 complete food-outlet profiles

The original 1.3 MB query exceeds the SQL Editor limit. These smaller files contain
exactly the same 328 outlets and 65 original fields per outlet.
Every SQL file is below 175,000 bytes (about 171 KiB).

1. Extract this folder and open 00_setup.sql in a text editor.
2. Supabase → SQL Editor → New query. Paste the ENTIRE file, then Run.
3. Repeat with each numbered profiles file in the order below, using a fresh query
   or replacing the editor's contents each time. Do NOT concatenate all files.
4. Run 90_verify.sql last. Expect 328 source_listings, 328 linked_food_outlets,
   and 65 original_columns_per_listing.
5. Refresh location and search in DineFinder. No Android rebuild is needed.

Run order:

- 00_setup.sql (9,625 bytes)
- 01_profiles.sql (172,810 bytes)
- 02_profiles.sql (171,323 bytes)
- 03_profiles.sql (174,916 bytes)
- 04_profiles.sql (171,633 bytes)
- 05_profiles.sql (172,080 bytes)
- 06_profiles.sql (171,223 bytes)
- 07_profiles.sql (173,255 bytes)
- 08_profiles.sql (172,671 bytes)
- 09_profiles.sql (21,768 bytes)
- 90_verify.sql (1,018 bytes)

After setup, “Success. No rows returned” is normal. Each data file shows the
cumulative profile count. If a data file fails, stop and send its exact error.
Previously successful files stay imported. Each file can be retried safely
without duplicates; do not delete existing restaurants or the source-profile table.
If verification reports missing rows, check for a skipped data file.

Existing restaurant IDs, manual coordinates, known ratings and supplied values
are preserved. Full original fields live in restaurant_source_profiles, linked
to restaurants. The raw source table is for dashboard/admin use only. No secret
keys are included. These files do not change Auth users or login records.
