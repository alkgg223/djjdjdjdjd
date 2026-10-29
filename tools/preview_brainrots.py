"""Renders the voxel brainrot OBJ models (with their palette and the voxel grid texture) to PNG, optionally with a mutation.
Usage: python3 tools/preview_brainrots.py OUT.png name [name ...]      (from the project root)
Also used by gen_brainrot_icons.py for the guide's item pictures."""
import math, sys
import numpy as np
from PIL import Image

TEX = 'src/main/resources/assets/eggsteal/textures/entity/br/'
_tex = {}


def tex(name):
    if name not in _tex: _tex[name] = np.asarray(Image.open(TEX + name + '.png').convert('RGB'), dtype=np.float32) / 255.0
    return _tex[name]


def load(path):
    V, T, N, groups, pal, g = [], [], [], [], {}, None
    for line in open(path):
        p = line.split()
        if not p: continue
        if p[0] == '#':
            if len(p) >= 4 and p[1] == 'pal': pal[p[2]] = int(p[3], 16)
            continue
        if p[0] == 'v': V.append(tuple(map(float, p[1:5])))
        elif p[0] == 'vt': T.append((float(p[1]), float(p[2])))
        elif p[0] == 'vn': N.append(tuple(map(float, p[1:4])))
        elif p[0] == 'g': g = [p[1], []]; groups.append(g)
        elif p[0] == 'f': g[1].append([int(t.split('/')[0]) - 1 for t in p[1:]])
    return V, T, N, groups, pal


def rgb(h): return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], np.float32) / 255.0


def recolor(base, mut, x, y, t=0.0):
    """Python copy of the mutation colouring in BrainrotRenderer (for pictures)."""
    if mut is None or mut['index'] == 0: return base
    lum = min(1.0, (0.3 * base[0] + 0.59 * base[1] + 0.11 * base[2]) * 1.15)
    sp = mut['special']
    if sp == 1:
        import colorsys
        return np.array(colorsys.hsv_to_rgb((t * 0.01 + y * 0.5 + x * 0.2) % 1, 0.75, 0.55 + 0.45 * lum), np.float32)
    if sp == 3:
        return np.array([0.08 + 0.2 * lum] * 3, np.float32) if x < 0 else np.array([0.75 + 0.25 * lum] * 3, np.float32)
    if sp == 4:
        import colorsys
        return np.array(colorsys.hsv_to_rgb((x * 0.7 + y * 0.4) % 1, 0.35, 0.8 + 0.2 * lum), np.float32)
    d, m, l = rgb(mut['dark']), rgb(mut['mid']), rgb(mut['light'])
    return d + (m - d) * (lum / 0.5) if lum < 0.5 else m + (l - m) * ((lum - 0.5) / 0.5)


def render(path, size=256, yaw=-30, pitch=18, mut=None, bg=(0.25, 0.27, 0.32, 1), out=None):
    V, T, N, groups, pal = load(path)
    P = np.array([v[:3] for v in V], np.float32)
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    x = P[:, 0] * cy + P[:, 2] * sy
    z = -P[:, 0] * sy + P[:, 2] * cy
    y = P[:, 1] * cp - z * sp
    z2 = P[:, 1] * sp + z * cp
    lo, hi = np.array([x.min(), y.min()]), np.array([x.max(), y.max()])
    scale = size * 0.86 / max(hi - lo)
    sx = (x - (lo[0] + hi[0]) / 2) * scale + size / 2
    sy_ = size / 2 - (y - (lo[1] + hi[1]) / 2) * scale
    img = np.zeros((size, size, 4), np.float32)
    img[:] = bg
    zb = np.full((size, size), -1e9, np.float32)
    pattern = tex(mut['pattern']) if mut else tex('vox')
    uscale = 0.25 if mut and mut['pattern'] != 'vox' else 1.0
    light = np.array([0.35, 0.8, 0.5]); light /= np.linalg.norm(light)
    for slot, faces in groups:
        base = rgb(pal.get(slot, 0xff00ff))
        for f in faces:
            idx = f[:4]
            n = np.array(N[idx[0]], np.float32)
            nx = n[0] * cy + n[2] * sy; nz = -n[0] * sy + n[2] * cy
            ny = n[1] * cp - nz * sp
            lam = 0.55 + 0.45 * max(0, nx * light[0] + ny * light[1] + (n[1] * sp + nz * cp) * light[2])
            cx_ = np.mean(P[idx, 0]); cy_ = np.mean(P[idx, 1])
            col = base if slot.startswith('k_') else recolor(base, mut, cx_, cy_)
            shade = V[idx[0]][3] * lam
            uv = np.array([T[i] for i in idx], np.float32) * uscale
            for tri in ((0, 1, 2), (0, 2, 3)):
                ii = [idx[k] for k in tri]
                X, Y, Z = sx[ii], sy_[ii], z2[ii]
                area = (X[1] - X[0]) * (Y[2] - Y[0]) - (X[2] - X[0]) * (Y[1] - Y[0])
                if abs(area) < 1e-6: continue
                x0, x1 = int(max(0, X.min())), int(min(size - 1, X.max())) + 1
                y0, y1 = int(max(0, Y.min())), int(min(size - 1, Y.max())) + 1
                if x0 >= x1 or y0 >= y1: continue
                gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
                w0 = ((X[1] - gx) * (Y[2] - gy) - (X[2] - gx) * (Y[1] - gy)) / area
                w1 = ((X[2] - gx) * (Y[0] - gy) - (X[0] - gx) * (Y[2] - gy)) / area
                w2 = 1 - w0 - w1
                msk = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
                depth = w0 * Z[0] + w1 * Z[1] + w2 * Z[2]
                reg = zb[y0:y1, x0:x1]
                msk &= depth > reg
                if not msk.any(): continue
                uvt = uv[list(tri)]
                uu = w0 * uvt[0, 0] + w1 * uvt[1, 0] + w2 * uvt[2, 0]
                vv = w0 * uvt[0, 1] + w1 * uvt[1, 1] + w2 * uvt[2, 1]
                th, tw = pattern.shape[:2]
                tx = (np.floor(uu * tw) % tw).astype(int); ty = (np.floor(vv * th) % th).astype(int)
                tc = pattern[ty, tx] if not slot.startswith('k_') else tex('vox')[(np.floor(vv * 16) % 16).astype(int), (np.floor(uu * 16) % 16).astype(int)]
                c = np.clip(tc * col * shade, 0, 1)
                region = img[y0:y1, x0:x1]
                region[msk, :3] = c[msk]
                region[msk, 3] = 1
                reg[msk] = depth[msk]
    im = Image.fromarray((img * 255).astype(np.uint8), 'RGBA')
    if out: im.save(out)
    return im


if __name__ == '__main__':
    outp, names = sys.argv[1], sys.argv[2:]
    cols = 4
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * 256, rows * 256))
    for i, n in enumerate(names):
        sheet.paste(render('src/main/resources/assets/eggsteal/models/brainrot/' + n + '.obj'), ((i % cols) * 256, (i // cols) * 256))
    sheet.save(outp)
