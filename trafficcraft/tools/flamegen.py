"""Backfire flame sprite sheet: 4 frames (64x128 each) of an exhaust flame, white-blue core -> orange -> red edge, noisy tongues."""
import numpy as np, os
from PIL import Image
A = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src/main/resources/assets/trafficcraft/textures/vehicle')
rng = np.random.default_rng(7)
W, H, F = 64, 128, 4
out = np.zeros((H, W * F, 4), np.float32)
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
for f in range(F):
    t = 1 - yy / H               # 1 at the base (bottom), 0 at the tip
    x = (xx - W / 2) / (W / 2)
    # noise field (smoothed) for tongues
    n = rng.standard_normal((H // 8 + 2, W // 8 + 2))
    n = np.kron(n, np.ones((8, 8)))[:H, :W]
    from scipy.ndimage import gaussian_filter
    n = gaussian_filter(n, 6) * 1.2
    length = [1.0, 0.85, 0.7, 0.45][f]
    width = (0.18 + 0.82 * np.clip(t, 0, 1) ** 0.75) * (1 + 0.12 * n) * (0.9 + 0.1 * np.sin(yy * 0.25 + f))
    body = np.clip(1 - np.abs(x) / np.maximum(width, 1e-3), 0, 1)
    tipmask = np.clip((t - (1 - length)) / 0.25, 0, 1)
    dens = body ** 1.1 * tipmask * np.clip(1 + 0.25 * n, 0, 2)
    core = np.clip(1 - np.abs(x) / (0.35 * width + 1e-3), 0, 1) * np.clip((t - (1 - length * 0.55)) / 0.2, 0, 1)
    r = np.clip(dens * 1.6, 0, 1)
    g = np.clip(dens * 1.1 - 0.15, 0, 1) * 0.75 + core * 0.25
    b = np.clip(core * 1.2 - 0.2, 0, 1) * 0.9 + dens * 0.05
    a = np.clip(dens * 2.2, 0, 1)
    fade = [1.0, 0.9, 0.7, 0.45][f]
    out[:, f * W:(f + 1) * W, 0] = r * fade
    out[:, f * W:(f + 1) * W, 1] = np.maximum(g, core * 0.9) * fade
    out[:, f * W:(f + 1) * W, 2] = np.maximum(b, core * 0.75) * fade
    out[:, f * W:(f + 1) * W, 3] = a
Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA').save(f'{A}/flame.png')
print('flame.png', out.shape)

bg = Image.new('RGBA', (W * F, H), (10, 10, 14, 255))
im = Image.open(f'{A}/flame.png')
bg.alpha_composite(im)
bg.save('/tmp/claude-0/-home-user-djjdjdjdjd/a4e48ff6-26c0-5818-93fa-87439b755099/scratchpad/flame_dark.png')
