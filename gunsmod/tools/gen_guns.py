"""Generate gun part models + atlases. usage: python3 gen_guns.py [names...]"""
import json
import os
import sys

from guns import GUNS

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "gunsmod")


def build(name):
    m = GUNS[name]()
    out = m.build(os.path.join(A, "textures", "item", "gun", name + ".png"),
                  os.path.join(A, "models", "gun", name), f"gunsmod:item/gun/{name}")
    xs = [v for e in m.elements for v in (e["from"][0], e["to"][0])]
    ys = [v for e in m.elements for v in (e["from"][1], e["to"][1])]
    zs = [v for e in m.elements for v in (e["from"][2], e["to"][2])]
    info = {"mounts": m.mounts, "bounds": [min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)],
            "parts": sorted(out.keys()), "elements": len(m.elements), "atlas": list(m.atlas.shape[:2])}
    return info


if __name__ == "__main__":
    names = sys.argv[1:] or list(GUNS)
    meta_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gun_meta.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    for n in names:
        meta[n] = build(n)
        print(n, meta[n]["elements"], "elements, atlas", meta[n]["atlas"], "parts", meta[n]["parts"])
    json.dump(meta, open(meta_path, "w"), indent=1)
