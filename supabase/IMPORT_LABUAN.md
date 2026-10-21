# Import Labuan food listings

This importer collects **mapped** restaurants, cafes, fast-food outlets and food
courts inside OpenStreetMap's Labuan administrative area (`MY-15`). It does not
claim to contain every operating restaurant. Unmapped and unnamed places are
not imported. Coordinates of ways/relations are map-feature centers, not verified
entrances. Listings and opening hours may be outdated.

## Run from your computer

1. Pull the latest DineFinder GitHub code and open the project terminal.
2. Install Python 3 if needed. No Python packages or API keys are required.
3. Run:

   ```sh
   python tools/import_labuan_restaurants.py
   ```

   On Windows, use `py` instead of `python` if that is your Python command.
   This makes one request to `https://overpass-api.de/api/interpreter` and writes
   `supabase/labuan_restaurants.sql`. If the community service is busy, wait and
   retry later. An empty/partial response does not replace the output file.
4. Review the generated SQL, including names, coordinates and listing count.
5. Open your Supabase project (`oueafqgadqggyinpmyoi`) → **SQL Editor**. If the
   restaurant table is missing, run `supabase/setup.sql` first.
6. Paste the complete generated `supabase/labuan_restaurants.sql` and click Run.
   Check **Table Editor → restaurants**. The SQL finishes by showing the total
   restaurant count.

The public publishable app key cannot run this import. Use your own dashboard
login; do not place administrator keys or database passwords in the Android app
or send them in chat.

## Data handling

- Existing rows, accounts, profile data and permissions are preserved. Stable
  UUIDs make repeated imports safe. The script only adds listings; it does not
  refresh or delete existing records. Review changes to an existing listing
  separately in Table Editor.
- A same-name existing restaurant within 50 metres is skipped, including your
  owner-supplied records when names match. Different OSM names or a larger
  coordinate discrepancy can still produce duplicates; review the result.
- `source_url` identifies the OSM map feature. `source_checked_at` records when
  this SQL was generated, not an on-site verification or the business's update
  time. For an offline export, its collection date may be earlier.
- Halal status is unknown unless an explicit OSM `diet:halal` tag says yes/only
  or no. This is a map contributor's tag, **not certification verification**.
- No ratings, prices, hours or addresses are invented. A zero rating and required
  price-level value of 1 are existing schema placeholders for unknown data.
- OSM source tags and source links are included in the records. Data is ©
  [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), under
  ODbL. Retain attribution when using or redistributing these listings.
- The app still searches only within **1 km of the phone**, regardless of how
  many restaurants are stored across Labuan.

## Offline export

For a previously obtained Overpass JSON export, run:

```sh
python tools/import_labuan_restaurants.py --input labuan.json
```

Only use an export generated with this Labuan-area query (bounding-box validation
alone does not prove that a record belongs to Labuan):

```overpass
[out:json][timeout:90];
area["ISO3166-2"="MY-15"]["boundary"="administrative"]->.labuan;
nwr(area.labuan)["amenity"~"^(restaurant|cafe|fast_food|food_court)$"];
out center tags;
```

Validation tests:

```sh
python -m unittest discover -s tools/tests -v
```

The cloud workspace was unable to fetch Overpass because its outbound proxy
returned 403. No live listings count or live Supabase import has been verified.
