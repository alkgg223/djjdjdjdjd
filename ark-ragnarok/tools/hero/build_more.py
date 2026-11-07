"""Build the 35 additional species into the mod resources and save a preview sheet of all of them."""
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_species
import dinos_more
import view

root = sys.argv[1]
preview = sys.argv[2]
ids = sys.argv[3:] or list(dinos_more.BIPEDS) + list(dinos_more.QUADS) + list(dinos_more.SAUROPODS)
tiles = []
for sid in ids:
    meta, (um, img), sc = build_species.build(sid, root, verbose=False)
    print("built", sid, meta["height"], meta["width"], flush=True)
    tiles.append(view.render(um["v"], um["n"], um["f"], size=(260, 190), yaw=-0.75, pitch=-0.12, tex=img, uv=um["uv"], ground=False))
    Image.fromarray((view.sheet(tiles, 5) * 255).astype(np.uint8)).save(preview)
import subprocess
subprocess.run([sys.executable, os.path.join(HERE, "compact_assets.py"), root], check=True)
print("all done")
