"""Shrink built assets: ARKM v1 -> v2 meshes, palette PNG textures, smaller saddle textures."""
import glob, os, struct, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_rex import write_mesh

root = sys.argv[1] + "/assets/arkragnarok"


def read_v1(path):
    b = open(path, "rb").read()
    o = 0
    assert b[:4] == b"ARKM"
    ver = struct.unpack_from("<i", b, 4)[0]
    if ver != 1:
        return None
    o = 8
    nb = struct.unpack_from("<i", b, o)[0]; o += 4
    bones = []
    for _ in range(nb):
        l = struct.unpack_from("<H", b, o)[0]; o += 2
        bones.append(b[o:o + l].decode()); o += l
    n = struct.unpack_from("<i", b, o)[0]; o += 4
    v = np.frombuffer(b, "<f4", n * 3, o).reshape(n, 3).astype(np.float64) * 16; o += n * 12
    nr = np.frombuffer(b, "<i1", n * 3, o).reshape(n, 3) / 127.0; o += n * 3
    uv = np.frombuffer(b, "<f4", n * 2, o).reshape(n, 2).astype(np.float64); o += n * 8
    bi = np.frombuffer(b, "<u1", n * 4, o).reshape(n, 4).astype(int); o += n * 4
    bw = np.frombuffer(b, "<u1", n * 4, o).reshape(n, 4) / 255.0; o += n * 4
    nt = struct.unpack_from("<i", b, o)[0]; o += 4
    f = np.frombuffer(b, "<i4", nt * 3, o).reshape(nt, 3)
    return bones, dict(v=v, n=nr, uv=uv, bi=bi, bw=bw, f=f)


before = sum(os.path.getsize(p) for p in glob.glob(root + "/arkmesh/*.arkm"))
for p in glob.glob(root + "/arkmesh/*.arkm"):
    r = read_v1(p)
    if r:
        write_mesh(p, r[1], r[0])
after = sum(os.path.getsize(p) for p in glob.glob(root + "/arkmesh/*.arkm"))
print("meshes %.1f MB -> %.1f MB" % (before / 1e6, after / 1e6))
tb = 0
ta = 0
for p in glob.glob(root + "/textures/entity/*/*.png"):
    tb += os.path.getsize(p)
    im = Image.open(p).convert("RGB")
    if "_saddle_" in p:
        w = im.size[0]
        tgt = {0: 512, 1: 256, 2: 128}[int(p[-5])]
        if w > tgt:
            im = im.resize((tgt, tgt), Image.LANCZOS)
    q = im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    q.save(p, optimize=True)
    ta += os.path.getsize(p)
print("textures %.1f MB -> %.1f MB" % (tb / 1e6, ta / 1e6))
