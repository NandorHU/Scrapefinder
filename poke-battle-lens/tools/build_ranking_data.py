#!/usr/bin/env python3
"""Build conservative move-ranking metadata from the existing pinned PokeAPI CSVs."""
import csv, io, json, pathlib, urllib.request, concurrent.futures, argparse

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = json.loads((ROOT / 'tools/data-source.json').read_text())
FILES = ['pokemon', 'pokemon_stats', 'pokemon_stats_past', 'moves', 'move_meta']
# Reviewed regular, single-turn damage effects. Conditional/variable damage,
# multi-hit, recoil, charging, recharge, alternate-stat and unknown effects are
# deliberately omitted. A roster containing one of those gets no recommendation.
REGULAR = {1,3,4,5,6,7,18,21,32,35,37,69,70,71,72,73,74,77,79,104,106,
           126,130,139,140,141,147,148,150,151,153,183,187,198,203,205,
           208,219,224,230,258,261,272,274,275,276,277,288,290,296,297,
           304,305,315,330,331,334,342,346,349,358,359,372,375,379,
           381,385,386,393,394,396,397,402,405,406,422,445,469,478,481,487}
NEVER_MISS = {18,79,381}

def main():
    args = argparse.ArgumentParser()
    args.add_argument('--csv-dir', type=pathlib.Path)
    local = args.parse_args().csv_dir
    def fetch(name):
        if local:
            text = (local / (name + '.csv')).read_text()
        else:
            url = 'https://raw.githubusercontent.com/PokeAPI/pokeapi/' + SOURCE['commit'] + '/data/v2/csv/' + name + '.csv'
            with urllib.request.urlopen(url, timeout=90) as response:
                text = response.read().decode()
        return name, list(csv.DictReader(io.StringIO(text)))
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        tables = dict(pool.map(fetch, FILES))
    names = {int(r['id']): r['identifier'] for r in tables['pokemon']}
    stats, past = {}, {}
    for r in tables['pokemon_stats']:
        stat = int(r['stat_id'])
        if 1 <= stat <= 6:
            stats.setdefault(int(r['pokemon_id']), [0] * 6)[stat - 1] = int(r['base_stat'])
    for r in tables['pokemon_stats_past']:
        past.setdefault(int(r['pokemon_id']), {}).setdefault(r['generation_id'], {})[r['stat_id']] = int(r['base_stat'])
    mons = {names[pid]: {'stats': values, 'past': past.get(pid, {})}
            for pid, values in sorted(stats.items()) if all(values)}
    meta = {int(r['move_id']): r for r in tables['move_meta']}
    safe, certain = [], []
    for r in tables['moves']:
        effect = int(r['effect_id'] or 0)
        m = meta.get(int(r['id']))
        if (effect not in REGULAR or not m or int(r['damage_class_id']) == 1
                or int(r['power'] or 0) <= 0 or int(r['type_id']) > 18
                or any(m[k] for k in ['min_hits','max_hits','min_turns','max_turns'])
                or int(m['drain']) < 0 or int(m['crit_rate']) > 0
                or (not r['accuracy'] and effect not in NEVER_MISS)):
            continue
        safe.append(r['identifier'])
        if effect in NEVER_MISS:
            certain.append(r['identifier'])
    output = {'source': 'PokeAPI CSV / BSD-3-Clause', 'commit': SOURCE['commit'],
              'pokemon': mons, 'safe_moves': safe, 'never_miss': certain}
    (ROOT / 'app/src/main/assets/ranking.json').write_text(json.dumps(output, separators=(',', ':')), encoding='utf-8')
    print(f'Ranking data: {len(mons)} Pokémon forms, {len(safe)} regular damage moves')

if __name__ == '__main__':
    main()
