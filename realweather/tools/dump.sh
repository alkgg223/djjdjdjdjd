#!/bin/bash
# Temporary: check which alpha values vanilla block textures use (the water shader keys on alpha 179).
set +e
RES=$(find "$HOME/.gradle" -name 'minecraft_1.21.1_client.jar' 2>/dev/null | head -1)
mkdir -p /tmp/res && cd /tmp/res && unzip -q -o "$RES" 'assets/minecraft/textures/block/*' 'assets/minecraft/textures/entity/*' 2>/dev/null
pip install -q pillow numpy >/dev/null 2>&1
python3 - <<'PY'
import glob, collections
from PIL import Image
import numpy as np
hist = collections.Counter(); who = collections.defaultdict(set)
for f in glob.glob('/tmp/res/assets/minecraft/textures/block/*.png'):
    a = np.array(Image.open(f).convert('RGBA'))[..., 3].ravel()
    for v in np.unique(a):
        if 0 < v < 255:
            hist[int(v)] += 1; who[int(v)].add(f.split('/')[-1])
for v in range(170, 190):
    print('alpha', v, hist[v], sorted(who[v])[:8])
PY
