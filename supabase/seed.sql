-- OPTIONAL DEVELOPMENT DATA: every listing, address, rating, and schedule below
-- is fictitious. Do not present these rows as verified real businesses.
insert into public.restaurants
  (id, name, cuisine, description, address, latitude, longitude, rating,
   price_level, emoji, accent_hex, tags, opening_hours)
values
  ('00000000-0000-4000-8000-000000000001', 'Bamboo & Bowl', 'Japanese',
   'A fictitious neighborhood ramen spot for exploring DineFinder.', 'Sample listing · Kuala Lumpur',
   3.1491, 101.7113, 4.8, 2, '🍜', 4294950694, array['Ramen', 'Comfort food'], 'Sample hours: 11:00–22:00'),
  ('00000000-0000-4000-8000-000000000002', 'Ember Kitchen', 'Malaysian',
   'A fictitious kitchen serving local favorites and smoky grilled dishes.', 'Sample listing · Kuala Lumpur',
   3.1457, 101.7120, 4.7, 2, '🍛', 4294935685, array['Local favorites', 'Grill'], 'Sample hours: 11:00–23:00'),
  ('00000000-0000-4000-8000-000000000003', 'Casa Verde', 'Italian',
   'A fictitious Italian dining room with handmade pasta and garden flavors.', 'Sample listing · Kuala Lumpur',
   3.1511, 101.7082, 4.6, 3, '🍝', 4288661961, array['Pasta', 'Vegetarian options'], 'Sample hours: 12:00–22:00'),
  ('00000000-0000-4000-8000-000000000004', 'The Morning Press', 'Café',
   'A fictitious café for a slow coffee, pastries, and a sunny brunch.', 'Sample listing · Kuala Lumpur',
   3.1462, 101.7067, 4.9, 2, '☕', 4292060107, array['Coffee', 'Brunch'], 'Sample hours: 08:00–18:00'),
  ('00000000-0000-4000-8000-000000000005', 'Saffron House', 'Indian',
   'A fictitious restaurant with fragrant curries and freshly baked naan.', 'Sample listing · Kuala Lumpur',
   3.1438, 101.7094, 4.7, 2, '🥘', 4294959404, array['Curry', 'Sharing plates'], 'Sample hours: 11:00–22:30'),
  ('00000000-0000-4000-8000-000000000006', 'Seoul Social', 'Korean',
   'A fictitious Korean kitchen with colorful bowls and small plates.', 'Sample listing · Kuala Lumpur',
   3.1528, 101.7135, 4.5, 2, '🍲', 4291924718, array['Korean bowls', 'Small plates'], 'Sample hours: 12:00–23:00')
on conflict (id) do nothing;
