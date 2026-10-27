-- Run AFTER all numbered data files. Expect 328 / 328 / 65.
BEGIN;
DO $verify_import$
BEGIN
  IF (SELECT count(*) FROM public.restaurant_source_profiles WHERE source_sha256='8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21')<>328 THEN
    RAISE EXCEPTION 'Import verification failed: expected 328 source profiles';
  END IF;
  IF (SELECT count(*) FROM public.restaurant_source_profiles WHERE source_sha256='8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21' AND discovery_eligible AND restaurant_id IS NOT NULL)<>328 THEN
    RAISE EXCEPTION 'Import verification failed: expected 328 linked food outlets';
  END IF;
END;
$verify_import$;
NOTIFY pgrst, 'reload schema';
COMMIT;
SELECT count(*) AS source_listings,
       count(*) FILTER (WHERE discovery_eligible AND restaurant_id IS NOT NULL) AS linked_food_outlets,
       65 AS original_columns_per_listing
FROM public.restaurant_source_profiles WHERE source_sha256='8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21';
