-- Run after the Apify import setup. Switches only the new app endpoints.
BEGIN;
DO $$ BEGIN
 IF to_regclass('public.apify_food_outlets') IS NULL THEN
  RAISE EXCEPTION 'Import the Apify food outlets before running this migration';
 END IF;
END $$;
ALTER TABLE public.apify_food_outlets ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.apify_food_outlets FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.apify_nearby_restaurants(
 latitude double precision, longitude double precision, radius_meters double precision DEFAULT 1000
) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = ''
AS $nearby$
DECLARE result jsonb;
BEGIN
 IF latitude IS NULL OR longitude IS NULL OR radius_meters IS NULL
 OR NOT (latitude BETWEEN -90 AND 90) OR NOT (longitude BETWEEN -180 AND 180)
 OR NOT (radius_meters > 0 AND radius_meters <= 1000) THEN
  RAISE EXCEPTION 'Invalid search coordinates or radius' USING ERRCODE='22023';
 END IF;
 SELECT coalesce(jsonb_agg(item.profile ORDER BY item.distance, item.id), '[]'::jsonb)
 INTO result FROM (
  SELECT r.id, d.meters AS distance, jsonb_build_object(
   'id',r.id,'name',r.name,'cuisine',coalesce(nullif(r.category,''),'Restaurant'),
   'description',concat_ws(E'\n',
      CASE WHEN r.business_status='TEMPORARILY_CLOSED' THEN 'Temporarily closed' END,
      nullif(r.source_profile->>'description','')),
   'address',coalesce(r.address,'Address not listed'),
   'latitude',r.latitude,'longitude',r.longitude,'rating',coalesce(r.rating,0),
   'price_level',1,'emoji','🍽️','accent_hex',4280445331,
   'tags',CASE WHEN r.business_status='TEMPORARILY_CLOSED'
     THEN jsonb_build_array('Temporarily closed') ELSE '[]'::jsonb END,
   'opening_hours',coalesce((SELECT string_agg(
      concat_ws(': ',h.value->>'day',h.value->>'hours'),E'\n' ORDER BY h.ordinality)
      FROM jsonb_array_elements(CASE WHEN jsonb_typeof(r.opening_hours)='array'
        THEN r.opening_hours ELSE '[]'::jsonb END) WITH ORDINALITY h(value,ordinality)),
      'Hours unavailable'),
   'distance_meters',d.meters,'is_halal',null
  ) AS profile
  FROM public.apify_food_outlets r
  CROSS JOIN LATERAL (SELECT 6371000.0 * 2 * asin(sqrt(least(1.0,greatest(0.0,
   power(sin(radians(r.latitude-$1)/2),2) + cos(radians($1))*cos(radians(r.latitude))*
   power(sin(radians(r.longitude-$2)/2),2))))) AS meters) d
  WHERE r.business_status IN ('OPERATIONAL','TEMPORARILY_CLOSED') AND d.meters <= $3
  ORDER BY d.meters,r.id LIMIT 200
 ) item;
 RETURN result;
END;
$nearby$;

CREATE OR REPLACE FUNCTION public.apify_restaurant_profile_details(restaurant_id uuid)
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $profile$
 SELECT jsonb_build_object(
  'photo_url',coalesce(nullif(r.photo_url,''),
    (SELECT p.value FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(r.photo_urls)='array'
     THEN r.photo_urls ELSE '[]'::jsonb END) p(value) WHERE p.value LIKE 'https://%' LIMIT 1)),
  'review_count',r.review_count,
  'score_counts',jsonb_strip_nulls(jsonb_build_object(
    '1',r.review_distribution->'oneStar','2',r.review_distribution->'twoStar',
    '3',r.review_distribution->'threeStar','4',r.review_distribution->'fourStar',
    '5',r.review_distribution->'fiveStar')),
  'location_reviews_link',null,
  'reviews',coalesce((SELECT jsonb_agg(v.review ORDER BY v.ordinality) FROM (
    SELECT e.ordinality, jsonb_build_object(
      'id',coalesce(nullif(e.value->>'reviewId',''),r.id::text||':'||e.ordinality::text),
      'author',left(coalesce(nullif(btrim(e.value->>'name'),''),'Diner'),160),
      'rating',CASE WHEN e.value->>'stars' IN ('1','2','3','4','5')
         THEN (e.value->>'stars')::integer ELSE null END,
      'text',left(coalesce(nullif(btrim(e.value->>'text'),''),e.value->>'textTranslated'),5000)
    ) AS review
    FROM jsonb_array_elements(CASE WHEN jsonb_typeof(r.reviews)='array'
     THEN r.reviews ELSE '[]'::jsonb END) WITH ORDINALITY e(value,ordinality)
    WHERE coalesce(nullif(btrim(e.value->>'text'),''),nullif(btrim(e.value->>'textTranslated'),'')) IS NOT NULL
    ORDER BY e.ordinality LIMIT 10
   ) v),'[]'::jsonb)
 ) FROM public.apify_food_outlets r WHERE r.id=$1
 AND r.business_status IN ('OPERATIONAL','TEMPORARILY_CLOSED');
$profile$;
REVOKE ALL ON FUNCTION public.apify_nearby_restaurants(double precision,double precision,double precision) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.apify_restaurant_profile_details(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.apify_nearby_restaurants(double precision,double precision,double precision) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.apify_restaurant_profile_details(uuid) TO anon, authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
