-- Run this whole small file in Supabase SQL Editor. Safe to repeat.
-- Publishes only profile photos/review information, not the full raw source export.
BEGIN;
ALTER TABLE public.restaurants
  ADD COLUMN IF NOT EXISTS photo_url text,
  ADD COLUMN IF NOT EXISTS review_count integer;

-- The supplied spreadsheet has aggregates, not written review text. No reviews are fabricated.
-- Administrators may add real written reviews here and publish them explicitly.
CREATE TABLE IF NOT EXISTS public.restaurant_reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id uuid NOT NULL REFERENCES public.restaurants(id) ON DELETE CASCADE,
  author_name text NOT NULL DEFAULT 'Diner' CHECK (length(author_name) BETWEEN 1 AND 160),
  rating integer CHECK (rating BETWEEN 1 AND 5),
  review_text text NOT NULL CHECK (length(btrim(review_text)) BETWEEN 1 AND 5000),
  published boolean NOT NULL DEFAULT false,
  reviewed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS restaurant_reviews_profile_idx
  ON public.restaurant_reviews(restaurant_id, reviewed_at DESC);
ALTER TABLE public.restaurant_reviews ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.restaurant_reviews FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.restaurant_profile_details(restaurant_id uuid)
RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = ''
AS $profile$
DECLARE
  restaurant_data jsonb;
  source_data jsonb := '{}'::jsonb;
  written_reviews jsonb;
  written_count integer;
BEGIN
  SELECT to_jsonb(r) INTO restaurant_data FROM public.restaurants r WHERE r.id=$1;
  IF restaurant_data IS NULL THEN RETURN NULL; END IF;
  -- Profile summary still works before the optional source-profile import is installed.
  IF to_regclass('public.restaurant_source_profiles') IS NOT NULL THEN
    SELECT to_jsonb(s) INTO source_data
    FROM public.restaurant_source_profiles s WHERE s.restaurant_id=$1
    ORDER BY s.imported_at DESC, s.source_sha256, s.source_row LIMIT 1;
  END IF;
  SELECT count(*)::integer INTO written_count
    FROM public.restaurant_reviews v WHERE v.restaurant_id=$1 AND v.published;
  SELECT coalesce(jsonb_agg(review.row ORDER BY review.reviewed_at DESC, review.id), '[]'::jsonb)
  INTO written_reviews FROM (
    SELECT v.id,v.reviewed_at,jsonb_build_object(
      'id',v.id,'author',v.author_name,'rating',v.rating,'text',v.review_text) AS row
    FROM public.restaurant_reviews v WHERE v.restaurant_id=$1 AND v.published
    ORDER BY v.reviewed_at DESC,v.id LIMIT 10
  ) review;
  RETURN jsonb_build_object(
    'photo_url',coalesce(nullif(restaurant_data->>'photo_url',''),nullif(source_data->>'photo','')),
    'review_count',greatest(coalesce((source_data->>'reviews')::integer,(restaurant_data->>'review_count')::integer),
                            nullif(written_count,0)),
    'score_counts',jsonb_strip_nulls(jsonb_build_object(
      '1',(source_data->>'reviews_per_score_1')::integer,
      '2',(source_data->>'reviews_per_score_2')::integer,
      '3',(source_data->>'reviews_per_score_3')::integer,
      '4',(source_data->>'reviews_per_score_4')::integer,
      '5',(source_data->>'reviews_per_score_5')::integer)),
    'reviews',written_reviews
  );
END;
$profile$;
REVOKE ALL ON FUNCTION public.restaurant_profile_details(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.restaurant_profile_details(uuid) TO anon, authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
