-- Run 00_setup.sql first. Run this entire file as ONE query. Safe to repeat.
-- Each data file commits independently; no session state is shared between files.
BEGIN;
CREATE TEMP TABLE dinefinder_complete_workbook ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset($workbook_data$
[{"source_row":590,"discovery_eligible":true,"raw_data":{"query":"seafood restaurant, 87000, Labuan, Labuan, MY","name":"Nara Tomyam Seafood","name_for_emails":"Nara Tomyam Seafood","subtypes":"Bistro","category":null,"type":"Bistro","phone":"+60 12-634 3364","website":null,"address":"SU E1T/SL02, Ground Floor, Wisma Wong Wo Lo, Labuan, Malaysia, 87000","street":"SU E1T/SL02, Ground Floor, Wisma Wong Wo Lo, Labuan, Malaysia","city":"Labuan","county":"None","state":"Labuan Federal Territory","state_code":null,"postal_code":"87000","country":"Malaysia","country_code":"MY","latitude":5.291318599999999,"longitude":115.245894,"h3":"896810cc813ffff","time_zone":"Asia/Kuching","plus_code":null,"area_service":false,"rating":null,"reviews":null,"reviews_link":null,"reviews_tags":null,"reviews_per_score":null,"reviews_per_score_1":null,"reviews_per_score_2":null,"reviews_per_score_3":null,"reviews_per_score_4":null,"reviews_per_score_5":null,"photos_count":2,"photo":"https://lh3.googleusercontent.com/grass-cs/AABkmLcs7p4qSa8LtE4955N-Am3zv7lySLW_zI5JXOwYbk2i2celKIrIPAYw0MWRcF11YQ6SmS0KWjuNy8_by3iEI3Hy-c_ZMVvhTq3159-QpkzGvbwf4px_c3RaOHCx5dvDcR-U6uM-EA=w800-h500-k-no","street_view":"https://lh3.googleusercontent.com/grass-cs/AABkmLcs7p4qSa8LtE4955N-Am3zv7lySLW_zI5JXOwYbk2i2celKIrIPAYw0MWRcF11YQ6SmS0KWjuNy8_by3iEI3Hy-c_ZMVvhTq3159-QpkzGvbwf4px_c3RaOHCx5dvDcR-U6uM-EA=w1600-h1000-k-no","logo":null,"located_in":null,"located_google_id":null,"business_status":"OPERATIONAL","working_hours":null,"working_hours_csv_compatible":null,"other_hours":null,"popular_times":null,"typical_time_spent":null,"range":null,"prices":null,"reservation_links":null,"booking_appointment_link":null,"menu_link":null,"order_links":null,"about":null,"description":null,"posts":null,"verified":false,"owner_id":"None","owner_title":"Nara Tomyam Seafood","owner_link":null,"location_link":"https://www.google.com/maps/place/Nara+Tomyam+Seafood/@5.291318599999999,115.24589399999999,14z/data=!4m8!1m2!2m1!1sNara+Tomyam+Seafood!3m4!1s0x322318fad1cec1b5:0x2ee58c7b987ad7f3!8m2!3d5.291318599999999!4d115.24589399999999","location_reviews_link":"https://www.google.com/maps/place/Nara+Tomyam+Seafood/@5.291318599999999,115.24589399999999,13.99z/data=!4m16!1m7!3m6!1s0x322318fad1cec1b5:0x2ee58c7b987ad7f3!2sNara+Tomyam+Seafood!8m2!3d5.291318599999999!4d115.24589399999999!16s%2Fg%2F11ckvg1dmb!3m7!1s0x322318fad1cec1b5:0x2ee58c7b987ad7f3!8m2!3d5.291318599999999!4d115.24589399999999!9m1!1b1!16s%2Fg%2F11ckvg1dmb?entry=ttu","place_id":"ChIJtcHO0foYIzIR89d6mHuM5S4","google_id":"0x322318fad1cec1b5:0x2ee58c7b987ad7f3","cid":"3379261557878478835","kgmid":"/g/1pzqrspnm","reviews_id":"None"}},{"source_row":591,"discovery_eligible":true,"raw_data":{"query":"seafood restaurant, 87000, Labuan, Labuan, MY","name":"Awana Corner","name_for_emails":"Awana Corner","subtypes":"Seafood restaurant","category":null,"type":"Seafood restaurant","phone":"+60 11-2685 9890","website":null,"address":"ganggarak, Jln Pohon Batu, kampung ganggarak, 87000 Labuan, Wilayah Persekutuan Labuan","street":"ganggarak, Jln Pohon Batu","city":"Labuan","county":"kampung ganggarak","state":"Labuan Federal Territory","state_code":null,"postal_code":"87000","country":"Malaysia","country_code":"MY","latitude":5.363910199999999,"longitude":115.228886,"h3":"896810cedcfffff","time_zone":"Asia/Kuching","plus_code":null,"area_service":false,"rating":3.7,"reviews":7,"reviews_link":"https://search.google.com/local/reviews?placeid=ChIJaYO6rNYXIzIRBb_liuLkQ88&q=*&authuser=0&hl=en&gl=MY","reviews_tags":null,"reviews_per_score":"{\"1\": 1, \"2\": 0, \"3\": 2, \"4\": 1, \"5\": 3}","reviews_per_score_1":1,"reviews_per_score_2":0,"reviews_per_score_3":2,"reviews_per_score_4":1,"reviews_per_score_5":3,"photos_count":14,"photo":"https://lh3.googleusercontent.com/grass-cs/AABkmLfS7ZE7sp-_FCZB5OHAVlA1J7ADsi0ZdacaNJecGVnfr7D8RxaKECy3vY_9zuzh7Ii2DRNVhdGVH46Gf2nQ-Z8y9X25YP1xXym5p2P6TVtTnXzFY9oInYO2QfRUQqvd49GCk5s=w800-h500-k-no","street_view":"https://lh3.googleusercontent.com/grass-cs/AABkmLfS7ZE7sp-_FCZB5OHAVlA1J7ADsi0ZdacaNJecGVnfr7D8RxaKECy3vY_9zuzh7Ii2DRNVhdGVH46Gf2nQ-Z8y9X25YP1xXym5p2P6TVtTnXzFY9oInYO2QfRUQqvd49GCk5s=w1600-h1000-k-no","logo":null,"located_in":null,"located_google_id":null,"business_status":"OPERATIONAL","working_hours":"{\"Tuesday\": [\"5:30pm-12:30am\"], \"Wednesday\": [\"5:30pm-12:30am\"], \"Thursday\": [\"5:30pm-12:30am\"], \"Friday\": [\"5:30pm-12:30am\"], \"Saturday\": [\"5:30pm-12:30am\"], \"Sunday\": [\"5:30pm-12:30am\"], \"Monday\": [\"5:30pm-12:30am\"]}","working_hours_csv_compatible":"Sunday,5:30pm,12:30am|Monday,5:30pm,12:30am|Tuesday,5:30pm,12:30am|Wednesday,5:30pm,12:30am|Thursday,5:30pm,12:30am|Friday,5:30pm,12:30am|Saturday,5:30pm,12:30am","other_hours":null,"popular_times":null,"typical_time_spent":null,"range":null,"prices":null,"reservation_links":null,"booking_appointment_link":null,"menu_link":null,"order_links":null,"about":null,"description":null,"posts":null,"verified":false,"owner_id":"None","owner_title":"Awana Corner","owner_link":null,"location_link":"https://www.google.com/maps/place/Awana+Corner/@5.363910199999999,115.22888599999999,14z/data=!4m8!1m2!2m1!1sAwana+Corner!3m4!1s0x322317d6acba8369:0xcf43e4e28ae5bf05!8m2!3d5.363910199999999!4d115.22888599999999","location_reviews_link":"https://www.google.com/maps/place/Awana+Corner/@5.363910199999999,115.22888599999999,13.99z/data=!4m16!1m7!3m6!1s0x322317d6acba8369:0xcf43e4e28ae5bf05!2sAwana+Corner!8m2!3d5.363910199999999!4d115.22888599999999!16s%2Fg%2F11ckvg1dmb!3m7!1s0x322317d6acba8369:0xcf43e4e28ae5bf05!8m2!3d5.363910199999999!4d115.22888599999999!9m1!1b1!16s%2Fg%2F11ckvg1dmb?entry=ttu","place_id":"ChIJaYO6rNYXIzIRBb_liuLkQ88","google_id":"0x322317d6acba8369:0xcf43e4e28ae5bf05","cid":"14935032450934750981","kgmid":"/g/11krdq_nzc","reviews_id":"-3511711622774800635"}},{"source_row":592,"discovery_eligible":true,"raw_data":{"query":"seafood restaurant, 87000, Labuan, Labuan, MY","name":"Restaurant Sabar Menanti","name_for_emails":"Restaurant Sabar Menanti","subtypes":"Tegal restaurant","category":null,"type":"Tegal restaurant","phone":null,"website":null,"address":"700, 87000, Wilayah Persekutuan Labuan","street":"700","city":"Labuan","county":"Bandar Labuan","state":"Labuan Federal Territory","state_code":null,"postal_code":"87000","country":"Malaysia","country_code":"MY","latitude":5.2776802,"longitude":115.2434984,"h3":"896810cc84bffff","time_zone":"Asia/Kuching","plus_code":null,"area_service":false,"rating":4,"reviews":134,"reviews_link":"https://search.google.com/local/reviews?placeid=ChIJJS5tFNwYIzIRpHt0Mzc66W8&q=*&authuser=0&hl=en&gl=MY","reviews_tags":null,"reviews_per_score":"{\"1\": 10, \"2\": 5, \"3\": 19, \"4\": 37, \"5\": 63}","reviews_per_score_1":10,"reviews_per_score_2":5,"reviews_per_score_3":19,"reviews_per_score_4":37,"reviews_per_score_5":63,"photos_count":102,"photo":"https://lh3.googleusercontent.com/grass-cs/AABkmLfVTKJ076CSir5yzGAG7Gma3aGuynf6Htk4_DBcOcfCne2JRrWBuaYR63fqa1TLBDXRbCJTXwk6XGmEDeLlGLm2ymhrIGXkCBWOIiszgXij7yafe3-QqexSh28xQ7MKTLKJf6To=w800-h500-k-no","street_view":"https://lh3.googleusercontent.com/grass-cs/AABkmLfVTKJ076CSir5yzGAG7Gma3aGuynf6Htk4_DBcOcfCne2JRrWBuaYR63fqa1TLBDXRbCJTXwk6XGmEDeLlGLm2ymhrIGXkCBWOIiszgXij7yafe3-QqexSh28xQ7MKTLKJf6To=w1600-h1000-k-no","logo":null,"located_in":null,"located_google_id":null,"business_status":"OPERATIONAL","working_hours":"{\"Tuesday\": [\"6am-5:30pm\"], \"Wednesday\": [\"6am-5:30pm\"], \"Thursday\": [\"6am-5:30pm\"], \"Friday\": [\"6am-5:30pm\"], \"Saturday\": [\"6am-5:30pm\"], \"Sunday\": [\"6am-5:30pm\"], \"Monday\": [\"6am-5:30pm\"]}","working_hours_csv_compatible":"Sunday,6am,5:30pm|Monday,6am,5:30pm|Tuesday,6am,5:30pm|Wednesday,6am,5:30pm|Thursday,6am,5:30pm|Friday,6am,5:30pm|Saturday,6am,5:30pm","other_hours":null,"popular_times":null,"typical_time_spent":null,"range":null,"prices":null,"reservation_links":null,"booking_appointment_link":null,"menu_link":null,"order_links":null,"about":"{\"Accessibility\": {\"Wheelchair-accessible car park\": false, \"Wheelchair-accessible entrance\": false, \"Wheelchair-accessible seating\": false}}","description":null,"posts":null,"verified":false,"owner_id":"None","owner_title":"Restaurant Sabar Menanti","owner_link":null,"location_link":"https://www.google.com/maps/place/Restaurant+Sabar+Menanti/@5.2776802,115.2434984,14z/data=!4m8!1m2!2m1!1sRestaurant+Sabar+Menanti!3m4!1s0x322318dc146d2e25:0x6fe93a3733747ba4!8m2!3d5.2776802!4d115.2434984","location_reviews_link":"https://www.google.com/maps/place/Restaurant+Sabar+Menanti/@5.2776802,115.2434984,13.99z/data=!4m16!1m7!3m6!1s0x322318dc146d2e25:0x6fe93a3733747ba4!2sRestaurant+Sabar+Menanti!8m2!3d5.2776802!4d115.2434984!16s%2Fg%2F11ckvg1dmb!3m7!1s0x322318dc146d2e25:0x6fe93a3733747ba4!8m2!3d5.2776802!4d115.2434984!9m1!1b1!16s%2Fg%2F11ckvg1dmb?entry=ttu","place_id":"ChIJJS5tFNwYIzIRpHt0Mzc66W8","google_id":"0x322318dc146d2e25:0x6fe93a3733747ba4","cid":"8064040616544467876","kgmid":"/g/11f3wl6dnq","reviews_id":"8064040616544467876"}},{"source_row":596,"discovery_eligible":true,"raw_data":{"query":"restaurant, 87000, Labuan, Labuan, MY","name":"jhonrel","name_for_emails":"Jhonrel","subtypes":"Restaurant","category":null,"type":"Restaurant","phone":null,"website":null,"address":"labuan barangay porok, 3","street":"labuan barangay porok","city":"Labuan","county":"Kampung Patau-Patau 2","state":"Labuan Federal Territory","state_code":null,"postal_code":"3","country":"Malaysia","country_code":"MY","latitude":5.2831456,"longitude":115.230825,"h3":"896810cce07ffff","time_zone":"Asia/Kuching","plus_code":null,"area_service":false,"rating":null,"reviews":null,"reviews_link":null,"reviews_tags":null,"reviews_per_score":null,"reviews_per_score_1":null,"reviews_per_score_2":null,"reviews_per_score_3":null,"reviews_per_score_4":null,"reviews_per_score_5":null,"photos_count":0,"photo":null,"street_view":null,"logo":null,"located_in":null,"located_google_id":null,"business_status":"OPERATIONAL","working_hours":null,"working_hours_csv_compatible":null,"other_hours":null,"popular_times":null,"typical_time_spent":null,"range":null,"prices":null,"reservation_links":null,"booking_appointment_link":null,"menu_link":null,"order_links":null,"about":null,"description":null,"posts":null,"verified":false,"owner_id":"None","owner_title":"jhonrel","owner_link":null,"location_link":"https://www.google.com/maps/place/jhonrel/@5.2831456,115.230825,14z/data=!4m8!1m2!2m1!1sjhonrel!3m4!1s0x322319006a4fa253:0x46959fe389b8138e!8m2!3d5.2831456!4d115.230825","location_reviews_link":"https://www.google.com/maps/place/jhonrel/@5.2831456,115.230825,13.99z/data=!4m16!1m7!3m6!1s0x322319006a4fa253:0x46959fe389b8138e!2sjhonrel!8m2!3d5.2831456!4d115.230825!16s%2Fg%2F11ckvg1dmb!3m7!1s0x322319006a4fa253:0x46959fe389b8138e!8m2!3d5.2831456!4d115.230825!9m1!1b1!16s%2Fg%2F11ckvg1dmb?entry=ttu","place_id":"ChIJU6JPagAZIzIRjhO4ieOflUY","google_id":"0x322319006a4fa253:0x46959fe389b8138e","cid":"5086147153801778062","kgmid":"/g/11yq18vtzj","reviews_id":"None"}}]
$workbook_data$::jsonb) AS x(source_row integer, discovery_eligible boolean, raw_data jsonb);
INSERT INTO public.restaurant_source_profiles (
  source_sha256, source_file, source_sheet, source_row, discovery_eligible,
  "query", "name", "name_for_emails", "subtypes", "category", "type", "phone", "website", "address", "street", "city", "county", "state", "state_code", "postal_code", "country", "country_code", "latitude", "longitude", "h3", "time_zone", "plus_code", "area_service", "rating", "reviews", "reviews_link", "reviews_tags", "reviews_per_score", "reviews_per_score_1", "reviews_per_score_2", "reviews_per_score_3", "reviews_per_score_4", "reviews_per_score_5", "photos_count", "photo", "street_view", "logo", "located_in", "located_google_id", "business_status", "working_hours", "working_hours_csv_compatible", "other_hours", "popular_times", "typical_time_spent", "range", "prices", "reservation_links", "booking_appointment_link", "menu_link", "order_links", "about", "description", "posts", "verified", "owner_id", "owner_title", "owner_link", "location_link", "location_reviews_link", "place_id", "google_id", "cid", "kgmid", "reviews_id", raw_data
)
SELECT '8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21', 'Outscraper-20261021193011s1555.xlsx', 'Generated by Outscraper ©', source_row, discovery_eligible,
  (raw_data->>'query'),
  (raw_data->>'name'),
  (raw_data->>'name_for_emails'),
  (raw_data->>'subtypes'),
  (raw_data->>'category'),
  (raw_data->>'type'),
  (raw_data->>'phone'),
  (raw_data->>'website'),
  (raw_data->>'address'),
  (raw_data->>'street'),
  (raw_data->>'city'),
  (raw_data->>'county'),
  (raw_data->>'state'),
  (raw_data->>'state_code'),
  (raw_data->>'postal_code'),
  (raw_data->>'country'),
  (raw_data->>'country_code'),
  (raw_data->>'latitude')::double precision,
  (raw_data->>'longitude')::double precision,
  (raw_data->>'h3'),
  (raw_data->>'time_zone'),
  (raw_data->>'plus_code'),
  (raw_data->>'area_service')::boolean,
  (raw_data->>'rating')::double precision,
  (raw_data->>'reviews')::bigint,
  (raw_data->>'reviews_link'),
  (raw_data->>'reviews_tags'),
  (raw_data->>'reviews_per_score'),
  (raw_data->>'reviews_per_score_1')::bigint,
  (raw_data->>'reviews_per_score_2')::bigint,
  (raw_data->>'reviews_per_score_3')::bigint,
  (raw_data->>'reviews_per_score_4')::bigint,
  (raw_data->>'reviews_per_score_5')::bigint,
  (raw_data->>'photos_count')::bigint,
  (raw_data->>'photo'),
  (raw_data->>'street_view'),
  (raw_data->>'logo'),
  (raw_data->>'located_in'),
  (raw_data->>'located_google_id'),
  (raw_data->>'business_status'),
  (raw_data->>'working_hours'),
  (raw_data->>'working_hours_csv_compatible'),
  (raw_data->>'other_hours'),
  (raw_data->>'popular_times'),
  (raw_data->>'typical_time_spent'),
  (raw_data->>'range'),
  (raw_data->>'prices'),
  (raw_data->>'reservation_links'),
  (raw_data->>'booking_appointment_link'),
  (raw_data->>'menu_link'),
  (raw_data->>'order_links'),
  (raw_data->>'about'),
  (raw_data->>'description'),
  (raw_data->>'posts'),
  (raw_data->>'verified')::boolean,
  (raw_data->>'owner_id'),
  (raw_data->>'owner_title'),
  (raw_data->>'owner_link'),
  (raw_data->>'location_link'),
  (raw_data->>'location_reviews_link'),
  (raw_data->>'place_id'),
  (raw_data->>'google_id'),
  (raw_data->>'cid'),
  (raw_data->>'kgmid'),
  (raw_data->>'reviews_id'), raw_data
FROM dinefinder_complete_workbook
ON CONFLICT (source_sha256, source_sheet, source_row) DO NOTHING;

CREATE TEMP TABLE dinefinder_food_outlets ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset($food_outlets$
[{"id":"c5f34582-da0f-5da5-a4a6-8c75e27516f5","name":"Nara Tomyam Seafood","cuisine":"Bistro","description":"","address":"SU E1T/SL02, Ground Floor, Wisma Wong Wo Lo, Labuan, Malaysia, 87000","latitude":5.291318599999999,"longitude":115.245894,"rating":0,"price_level":1,"opening_hours":"Hours unavailable","tags":[],"is_halal":null,"phone":"+60 12-634 3364","website":null,"review_count":null,"google_place_id":"ChIJtcHO0foYIzIR89d6mHuM5S4","source_url":"https://www.google.com/maps/place/Nara+Tomyam+Seafood/@5.291318599999999,115.24589399999999,14z/data=!4m8!1m2!2m1!1sNara+Tomyam+Seafood!3m4!1s0x322318fad1cec1b5:0x2ee58c7b987ad7f3!8m2!3d5.291318599999999!4d115.24589399999999","source_name":"User-supplied Outscraper export","source_file":"Outscraper-20261021193011s1555.xlsx","business_status":"OPERATIONAL"},{"id":"92e275e7-8cb9-5288-bdfb-fed8bb0d43cd","name":"Awana Corner","cuisine":"Seafood restaurant","description":"","address":"ganggarak, Jln Pohon Batu, kampung ganggarak, 87000 Labuan, Wilayah Persekutuan Labuan","latitude":5.363910199999999,"longitude":115.228886,"rating":3.7,"price_level":1,"opening_hours":"Monday: 5:30pm-12:30am; Tuesday: 5:30pm-12:30am; Wednesday: 5:30pm-12:30am; Thursday: 5:30pm-12:30am; Friday: 5:30pm-12:30am; Saturday: 5:30pm-12:30am; Sunday: 5:30pm-12:30am","tags":[],"is_halal":null,"phone":"+60 11-2685 9890","website":null,"review_count":7,"google_place_id":"ChIJaYO6rNYXIzIRBb_liuLkQ88","source_url":"https://www.google.com/maps/place/Awana+Corner/@5.363910199999999,115.22888599999999,14z/data=!4m8!1m2!2m1!1sAwana+Corner!3m4!1s0x322317d6acba8369:0xcf43e4e28ae5bf05!8m2!3d5.363910199999999!4d115.22888599999999","source_name":"User-supplied Outscraper export","source_file":"Outscraper-20261021193011s1555.xlsx","business_status":"OPERATIONAL"},{"id":"8ae52f9e-cb1e-524b-bc57-071912194f97","name":"Restaurant Sabar Menanti","cuisine":"Tegal restaurant","description":"","address":"700, 87000, Wilayah Persekutuan Labuan","latitude":5.2776802,"longitude":115.2434984,"rating":4.0,"price_level":1,"opening_hours":"Monday: 6am-5:30pm; Tuesday: 6am-5:30pm; Wednesday: 6am-5:30pm; Thursday: 6am-5:30pm; Friday: 6am-5:30pm; Saturday: 6am-5:30pm; Sunday: 6am-5:30pm","tags":[],"is_halal":null,"phone":null,"website":null,"review_count":134,"google_place_id":"ChIJJS5tFNwYIzIRpHt0Mzc66W8","source_url":"https://www.google.com/maps/place/Restaurant+Sabar+Menanti/@5.2776802,115.2434984,14z/data=!4m8!1m2!2m1!1sRestaurant+Sabar+Menanti!3m4!1s0x322318dc146d2e25:0x6fe93a3733747ba4!8m2!3d5.2776802!4d115.2434984","source_name":"User-supplied Outscraper export","source_file":"Outscraper-20261021193011s1555.xlsx","business_status":"OPERATIONAL"},{"id":"03cbd7d1-0c0a-541d-b905-d33727c27e2e","name":"jhonrel","cuisine":"Restaurant","description":"","address":"labuan barangay porok, 3","latitude":5.2831456,"longitude":115.230825,"rating":0,"price_level":1,"opening_hours":"Hours unavailable","tags":[],"is_halal":null,"phone":null,"website":null,"review_count":null,"google_place_id":"ChIJU6JPagAZIzIRjhO4ieOflUY","source_url":"https://www.google.com/maps/place/jhonrel/@5.2831456,115.230825,14z/data=!4m8!1m2!2m1!1sjhonrel!3m4!1s0x322319006a4fa253:0x46959fe389b8138e!8m2!3d5.2831456!4d115.230825","source_name":"User-supplied Outscraper export","source_file":"Outscraper-20261021193011s1555.xlsx","business_status":"OPERATIONAL"}]
$food_outlets$::jsonb) AS x (
  id uuid, name text, cuisine text, description text, address text,
  latitude double precision, longitude double precision, rating double precision,
  price_level integer, opening_hours text, tags text[], is_halal boolean,
  phone text, website text, review_count integer, google_place_id text,
  source_url text, source_name text, source_file text, business_status text
);
-- Match prior import UUID, then Google place ID, then identical name within 50 metres.
-- Coordinates of existing records are never overwritten (including your manual corrections).
DO $merge_profiles$
DECLARE
  i record;
  target_id uuid;
BEGIN
  FOR i IN SELECT * FROM dinefinder_food_outlets ORDER BY id LOOP
    SELECT r.id INTO target_id
    FROM public.restaurants r
    WHERE r.id=i.id OR r.google_place_id=i.google_place_id OR (
      lower(regexp_replace(btrim(r.name),'\s+',' ','g')) = lower(regexp_replace(btrim(i.name),'\s+',' ','g'))
      AND extensions.st_dwithin(r.location,
        extensions.st_setsrid(extensions.st_makepoint(i.longitude,i.latitude),4326)::extensions.geography,50)
    )
    ORDER BY CASE WHEN r.id=i.id THEN 0 WHEN r.google_place_id=i.google_place_id THEN 1 ELSE 2 END, r.id
    LIMIT 1;
    IF target_id IS NULL THEN
      INSERT INTO public.restaurants (
        id,name,cuisine,description,address,latitude,longitude,rating,price_level,opening_hours,
        tags,is_halal,phone,website,review_count,google_place_id,source_url,source_name,source_file,business_status,imported_at
      ) VALUES (
        i.id,i.name,i.cuisine,i.description,i.address,i.latitude,i.longitude,i.rating,i.price_level,i.opening_hours,
        i.tags,i.is_halal,i.phone,i.website,i.review_count,i.google_place_id,i.source_url,i.source_name,i.source_file,i.business_status,now()
      ) RETURNING id INTO target_id;
    ELSE
      UPDATE public.restaurants r SET
        cuisine=coalesce(nullif(btrim(r.cuisine),''),i.cuisine),
        description=coalesce(nullif(r.description,''),i.description,''),
        address=coalesce(nullif(r.address,''),i.address,''),
        rating=CASE WHEN coalesce(r.rating,0)=0 THEN i.rating ELSE r.rating END,
        opening_hours=CASE WHEN coalesce(r.opening_hours,'') IN ('','Hours unavailable','Check opening hours with the restaurant')
                           THEN i.opening_hours ELSE r.opening_hours END,
        phone=coalesce(nullif(r.phone,''),i.phone),
        website=coalesce(nullif(r.website,''),i.website),
        review_count=coalesce(r.review_count,i.review_count),
        google_place_id=coalesce(nullif(r.google_place_id,''),i.google_place_id),
        source_url=coalesce(nullif(r.source_url,''),i.source_url),
        source_name=coalesce(nullif(r.source_name,''),i.source_name),
        source_file=coalesce(nullif(r.source_file,''),i.source_file),
        business_status=coalesce(nullif(r.business_status,''),i.business_status),
        imported_at=coalesce(r.imported_at,now())
      WHERE r.id=target_id;
    END IF;
    UPDATE public.restaurant_source_profiles SET restaurant_id=target_id
    WHERE source_sha256='8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21' AND "place_id"=i.google_place_id;
  END LOOP;
END;
$merge_profiles$;
COMMIT;
SELECT count(*) AS profiles_imported_so_far, count(restaurant_id) AS linked_outlets_so_far
FROM public.restaurant_source_profiles WHERE source_sha256='8d2cf43c27db94be926972bec414b5a8b31f5e952bc5a552d8218f9454098f21';
