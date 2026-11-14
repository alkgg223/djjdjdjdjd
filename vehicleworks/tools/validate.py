"""Validates JSON, model/texture references and translation keys used in Java code."""
import json, os, re, glob, sys
R = os.path.join(os.path.dirname(__file__), '..', 'src', 'main')
A = os.path.join(R, 'resources', 'assets', 'vehicleworks')
errors = 0
for p in glob.glob(os.path.join(R, 'resources', '**', '*.json'), recursive=True):
    try: json.load(open(p, encoding='utf-8'))
    except Exception as e: print('BAD JSON', p, e); errors += 1
def tex_exists(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    if ns != 'vehicleworks': return True
    return os.path.exists(os.path.join(A, 'textures', path + '.png'))
def model_exists(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    if ns != 'vehicleworks': return True
    return os.path.exists(os.path.join(A, 'models', path + '.json'))
for p in glob.glob(os.path.join(A, 'models', '**', '*.json'), recursive=True):
    m = json.load(open(p))
    for k, v in m.get('textures', {}).items():
        if not v.startswith('#') and not tex_exists(v): print('missing texture', v, 'in', p); errors += 1
    if 'parent' in m and not model_exists(m['parent']): print('missing parent', m['parent'], p); errors += 1
for p in glob.glob(os.path.join(A, 'blockstates', '*.json')):
    for v in json.load(open(p))['variants'].values():
        if not model_exists(v['model']): print('missing model', v['model'], p); errors += 1
lang = json.load(open(os.path.join(A, 'lang', 'en_us.json'), encoding='utf-8'))
ar = json.load(open(os.path.join(A, 'lang', 'ar_sa.json'), encoding='utf-8'))
src = ''.join(open(p, encoding='utf-8').read() for p in glob.glob(os.path.join(R, 'java', '**', '*.java'), recursive=True))
for k in set(re.findall(r'"(vehicleworks\.[a-z_.0-9]+[a-z_0-9])"', src)):
    if k not in lang: print('missing lang', k); errors += 1
# dynamic keys
comp = re.findall(r'^    [A-Z_]+\("([a-z_]+)"', open(os.path.join(R, 'java', 'com', 'vehicleworks', 'component', 'ComponentType.java')).read(), re.M)
for c in comp:
    for k in ('block.vehicleworks.' + c, 'vehicleworks.tooltip.' + c):
        if k not in lang: print('missing lang', k); errors += 1
for k in re.findall(r'PropertyDef\.(?:number|integer|bool|choice)\("([a-z_]+)"', src):
    if 'vehicleworks.prop.' + k not in lang: print('missing prop', k); errors += 1
for opts in re.findall(r'PropertyDef\.choice\("[a-z_]+", [^,]+, ((?:"[a-z_]+"(?:, )?)+)\)', src):
    for o in re.findall(r'"([a-z_]+)"', opts):
        if 'vehicleworks.opt.' + o not in lang: print('missing opt', o); errors += 1
for k in re.findall(r'key\("([a-z_0-9]+)"', src):
    if 'key.vehicleworks.' + k not in lang: print('missing key', k); errors += 1
for k in lang:
    if k not in ar: print('missing arabic', k); errors += 1
sj = json.load(open(os.path.join(A, 'sounds.json')))
for k, v in sj.items():
    for s in v['sounds']:
        if not os.path.exists(os.path.join(A, 'sounds', s['name'].split(':')[1] + '.ogg')): print('missing sound', s); errors += 1
for k in re.findall(r'reg\("([a-z_.]+)"\)', src):
    if k not in sj: print('sound not in sounds.json', k); errors += 1
print('errors:', errors)
sys.exit(1 if errors else 0)
