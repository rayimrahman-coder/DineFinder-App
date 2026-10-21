import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('labuan', Path(__file__).parents[1] / 'import_labuan_restaurants.py')
labuan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(labuan)


def node(osm_id=1, **tags):
    return dict(type='node', id=osm_id, lat=5.28, lon=115.17,
                tags={'name': 'Test Cafe', 'amenity': 'cafe', **tags})


class ImportTests(unittest.TestCase):
    def test_named_places_have_stable_ids_and_unknown_halal(self):
        rows, skipped = labuan.listings({'elements': [node(), node()]})
        self.assertEqual(len(rows), 1)
        self.assertEqual(skipped, 0)
        self.assertIsNone(rows[0]['halal'])
        self.assertEqual(rows[0]['id'], labuan.listings({'elements': [node()]})[0][0]['id'])

    def test_way_centers_and_explicit_halal_are_respected(self):
        item = node(**{'diet:halal': 'no'})
        item['type'] = 'way'
        item['center'] = dict(lat=item.pop('lat'), lon=item.pop('lon'))
        rows, _ = labuan.listings({'elements': [item, node(2, **{'diet:halal': 'yes'})]})
        self.assertEqual({row['halal'] for row in rows}, {False, True})

    def test_unnamed_outside_and_closed_amenities_are_skipped(self):
        bad = [node(name=''), node(amenity='disused:restaurant'),
               {**node(), 'lat': 3.14}, {**node(), 'lon': float('nan')},
               {**node(), 'type': 'relation', 'center': None}, {'tags': None}]
        rows, skipped = labuan.listings({'elements': bad})
        self.assertEqual(rows, [])
        self.assertEqual(skipped, len(bad))

    def test_partial_query_is_never_imported(self):
        with self.assertRaises(ValueError):
            labuan.listings({'elements': [node()], 'remark': 'Query timed out'})

    def test_sql_escapes_names_and_preserves_existing_data(self):
        rows, _ = labuan.listings({'elements': [node(name="Cafe O'Brien; --") ]})
        sql = labuan.sql(rows, '2026-10-21T00:00:00+00:00')
        self.assertIn("'Cafe O''Brien; --'", sql)
        self.assertIn('on conflict (id) do nothing', sql)
        self.assertIn('extensions.st_dwithin', sql)
        self.assertIn('set local standard_conforming_strings = on', sql)
        self.assertNotIn('delete from', sql.lower())
        self.assertNotIn('auth.users', sql.lower())
        self.assertNotIn("array['OpenStreetMap', 'HALAL']", sql)


if __name__ == '__main__':
    unittest.main()
