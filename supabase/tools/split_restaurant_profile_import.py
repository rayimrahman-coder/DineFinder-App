#!/usr/bin/env python3
"""Split the verified 328-profile SQL into independent SQL Editor-sized transactions."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def split(source, output, max_bytes=175_000):
    sql = source.read_text()
    raw_start, raw_end = '$workbook_data$\n', '\n$workbook_data$::jsonb)'
    food_start, food_end = '$food_outlets$\n', '\n$food_outlets$::jsonb)'
    raw_json = sql.split(raw_start, 1)[1].split(raw_end, 1)[0]
    food_json = sql.split(food_start, 1)[1].split(food_end, 1)[0]
    records = json.loads(raw_json)
    food = json.loads(food_json)
    by_place = {r['google_place_id']: r for r in food}
    if len(records) != 328 or len(by_place) != 328:
        raise ValueError('Expected exactly 328 source and normalized profiles')
    if {r['raw_data']['place_id'] for r in records} != set(by_place):
        raise ValueError('Source and normalized profiles do not match')
    digest = re.search(r'^-- SHA-256: ([a-f0-9]{64})$', sql, re.M).group(1)
    start = sql.index('CREATE TEMP TABLE dinefinder_complete_workbook')
    end = sql.index('DO $verify_import$')
    template = sql[start:end]
    def data_file(batch):
        selected = [by_place[r['raw_data']['place_id']] for r in batch]
        body = template.replace(raw_json, json.dumps(batch, ensure_ascii=False, separators=(',', ':')))
        body = body.replace(food_json, json.dumps(selected, ensure_ascii=False, separators=(',', ':')))
        return ('-- Run 00_setup.sql first. Run this entire file as ONE query. Safe to repeat.\n'
                '-- Each data file commits independently; no session state is shared between files.\nBEGIN;\n' + body +
                "COMMIT;\nSELECT count(*) AS profiles_imported_so_far, count(restaurant_id) AS linked_outlets_so_far\n"
                "FROM public.restaurant_source_profiles WHERE source_sha256='" + digest + "';\n")
    batches = []
    current = []
    for record in records:
        candidate = current + [record]
        if len(data_file(candidate).encode()) > max_bytes:
            if not current:
                raise ValueError('One profile exceeds the selected SQL batch limit')
            batches.append(current)
            current = [record]
        else:
            current = candidate
    if current:
        batches.append(current)
    output.mkdir(parents=True, exist_ok=True)
    setup = sql[:start].replace('-- Run this WHOLE SQL file in Supabase SQL Editor. It is safe to run again.',
        '-- Step 00: run this setup file first, then the numbered data files, then 90_verify.sql.')
    setup = setup.replace('-- Large SQL file: use restaurant_profile_batches for SQL Editor, or a direct database connection.',
        '-- Step 00: run this setup file first, then the numbered data files, then 90_verify.sql.')
    setup += "NOTIFY pgrst, 'reload schema';\nCOMMIT;\n"
    files = [('00_setup.sql', setup)]
    files += [(f'{index:02d}_profiles.sql', data_file(batch)) for index, batch in enumerate(batches, 1)]
    files += [('90_verify.sql', '-- Run AFTER all numbered data files. Expect 328 / 328 / 65.\nBEGIN;\n' + sql[end:])]
    for name, contents in files:
        if len(contents.encode()) > max_bytes:
            raise ValueError(f'{name} exceeds SQL batch limit')
        (output / name).write_text(contents)
    readme = '''# Supabase SQL Editor import — 328 complete food-outlet profiles

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

'''
    readme += '\n'.join(f'- {name} ({len(contents.encode()):,} bytes)' for name, contents in files)
    readme += '''

After setup, “Success. No rows returned” is normal. Each data file shows the
cumulative profile count. If a data file fails, stop and send its exact error.
Previously successful files stay imported. Each file can be retried safely
without duplicates; do not delete existing restaurants or the source-profile table.
If verification reports missing rows, check for a skipped data file.

Existing restaurant IDs, manual coordinates, known ratings and supplied values
are preserved. Full original fields live in restaurant_source_profiles, linked
to restaurants. The raw source table is for dashboard/admin use only. No secret
keys are included. These files do not change Auth users or login records.
'''
    (output / 'README.md').write_text(readme)
    print(json.dumps({'folder': str(output), 'sql_files': len(files), 'data_batches': len(batches),
                      'rows': len(records), 'largest_file_bytes': max(len(s.encode()) for _, s in files)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'supabase/imports/20261027_full_restaurant_profiles.sql')
    parser.add_argument('--output', type=Path, default=ROOT / 'supabase/imports/restaurant_profile_batches')
    args = parser.parse_args()
    split(args.source, args.output)
