-- Read-only checks. Paste this whole file into Supabase SQL Editor and Run.
-- This does not change tables, data, policies or permissions.
BEGIN READ ONLY;
SELECT count(*) AS total_restaurant_records FROM public.restaurants;

SELECT name, latitude, longitude
FROM public.restaurants
WHERE name ILIKE '%UMSKAL%' OR name ILIKE '%Anjung Selera Sungai Pagar%';

-- At the owner-supplied Cafe UMSKAL coordinate, both owner-supplied food courts
-- should be in range if those records and coordinates were set up correctly.
SELECT name, latitude, longitude, round(distance_meters::numeric) AS metres_from_cafe_umskal
FROM public.nearby_restaurants(5.280285448386224, 115.17640486697412, 1000);

-- Repeat as the same public role used by a guest phone client. If the previous
-- query returns restaurants and this one does not, investigate RLS/read access.
SET LOCAL ROLE anon;
SELECT name, latitude, longitude, round(distance_meters::numeric) AS public_metres_from_cafe_umskal
FROM public.nearby_restaurants(5.280285448386224, 115.17640486697412, 1000);
ROLLBACK;
