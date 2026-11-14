"""Writes GameTest structure templates (gzipped NBT) without external libraries."""
import gzip, struct, os, sys

def tag_string(s):
    b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b

def named(t, name, payload):
    return bytes([t]) + tag_string(name) + payload

def p_int(v): return struct.pack('>i', v)
def p_list(elem_type, items): return bytes([elem_type]) + struct.pack('>i', len(items)) + b''.join(items)
def p_compound(entries): return b''.join(entries) + b'\x00'

TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 3, 8, 9, 10

def structure(size, blocks, palette):
    pal = [p_compound([named(TAG_STRING, 'Name', tag_string(n))]) for n in palette]
    blks = [p_compound([named(TAG_LIST, 'pos', p_list(TAG_INT, [p_int(x), p_int(y), p_int(z)])), named(TAG_INT, 'state', p_int(s))]) for (x, y, z, s) in blocks]
    root = p_compound([
        named(TAG_INT, 'DataVersion', p_int(3955)),
        named(TAG_LIST, 'size', p_list(TAG_INT, [p_int(v) for v in size])),
        named(TAG_LIST, 'palette', p_list(TAG_COMPOUND, pal)),
        named(TAG_LIST, 'blocks', p_list(TAG_COMPOUND, blks)),
        named(TAG_LIST, 'entities', p_list(TAG_COMPOUND, [])),
    ])
    return bytes([TAG_COMPOUND]) + tag_string('') + root

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f: f.write(data)

out = sys.argv[1]
# platform: 24 x 12 x 24, stone floor at y=0, air above (the GameTest world is void)
sx, sy, sz = 24, 12, 24
blocks = []
for x in range(sx):
    for z in range(sz):
        blocks.append((x, 0, z, 1))
        for y in range(1, sy): blocks.append((x, y, z, 0))
write(os.path.join(out, 'platform.nbt'), structure((sx, sy, sz), blocks, ['minecraft:air', 'minecraft:smooth_stone']))
# pool: 24 x 12 x 24 with water y=1..6 inside stone walls
blocks = []
for x in range(sx):
    for z in range(sz):
        for y in range(sy):
            wall = x == 0 or z == 0 or x == sx - 1 or z == sz - 1
            if y == 0: s = 1
            elif y <= 6: s = 1 if wall else 2
            else: s = 0
            blocks.append((x, y, z, s))
write(os.path.join(out, 'pool.nbt'), structure((sx, sy, sz), blocks, ['minecraft:air', 'minecraft:smooth_stone', 'minecraft:water']))
print('ok')
