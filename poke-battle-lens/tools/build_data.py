#!/usr/bin/env python3
"""Build a deterministic offline dataset from PokeAPI CSVs (BSD-3-Clause)."""
import csv, io, json, pathlib, urllib.request, concurrent.futures
SOURCE=json.loads((pathlib.Path(__file__).resolve().parent/'data-source.json').read_text())
BASE='https://raw.githubusercontent.com/PokeAPI/pokeapi/'+SOURCE['commit']+'/data/v2/csv/'
FILES=['pokemon','pokemon_species','pokemon_types','pokemon_types_past','moves','types','type_efficacy','move_changelog','version_groups']
def fetch(name):
    with urllib.request.urlopen(BASE+name+'.csv', timeout=90) as r:
        return name, list(csv.DictReader(io.StringIO(r.read().decode())))
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    tables=dict(pool.map(fetch, FILES))
path=pathlib.Path(__file__).resolve().parents[1]
(path/'tools'/'raw').mkdir(exist_ok=True)
for n, rows in tables.items():
    (path/'tools'/'raw'/(n+'.json')).write_text(json.dumps(rows))
types={int(r['id']):r['identifier'] for r in tables['types'] if int(r['id'])<=18}
pt={}
for r in tables['pokemon_types']:
    pt.setdefault(int(r['pokemon_id']),{})[int(r['slot'])]=int(r['type_id'])
past={}
for r in tables['pokemon_types_past']:
    past.setdefault(int(r['pokemon_id']),{}).setdefault(int(r['generation_id']),{})[int(r['slot'])]=int(r['type_id'])
species={int(r['id']):r['identifier'] for r in tables['pokemon_species']}
aliases={}
mons=[]
for r in tables['pokemon']:
    pid=int(r['id'])
    if pid not in pt: continue
    if r['is_default']=='1': aliases[species[int(r['species_id'])]]=r['identifier']
    mons.append({'name':r['identifier'],'types':[v for k,v in sorted(pt[pid].items())],
        'past':{str(g):[v for k,v in sorted(t.items())] for g,t in past.get(pid,{}).items()}})
versions={int(r['id']):int(r['generation_id']) for r in tables['version_groups']}
history={}
for r in tables['move_changelog']:
    history.setdefault(int(r['move_id']),[]).append({'generation':versions[int(r['changed_in_version_group_id'])], **{k:int(r[k]) for k in ['type_id','power','accuracy','pp'] if r[k]}})
moves=[]
for r in tables['moves']:
    if int(r['type_id'])>18: continue
    moves.append({'name':r['identifier'],'type':int(r['type_id']),'power':int(r['power']) if r['power'] else 0,
        'accuracy':int(r['accuracy']) if r['accuracy'] else 0,'class':int(r['damage_class_id']),
        'history':sorted(history.get(int(r['id']),[]),key=lambda h:h['generation'])})
chart=[[100 for _ in range(18)] for _ in range(18)]
for r in tables['type_efficacy']:
    a,d=int(r['damage_type_id']),int(r['target_type_id'])
    if a<=18 and d<=18: chart[a-1][d-1]=int(r['damage_factor'])
output={'source':'PokeAPI CSV / BSD-3-Clause','types':types,'aliases':aliases,'pokemon':mons,'moves':moves,'chart':chart}
(path/'app/src/main/assets/dex.json').write_text(json.dumps(output,separators=(',',':')),encoding='utf-8')
print(f"Offline database: {len(mons)} forms, {len(moves)} moves")
