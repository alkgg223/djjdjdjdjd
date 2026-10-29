"""Mod list logo: a storm over the sea with rain and a lightning bolt."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'realweather_logo.png')
W, H = 512, 256
rng = np.random.default_rng(7)
y = np.linspace(0, 1, H)[:, None]
sky = np.zeros((H, W, 3))
sky[...] = (np.array([28, 32, 42]) * (1 - y) + np.array([70, 78, 92]) * y)[:, None, :][:, 0, :][:, None, :]
# Cloud texture.
n = np.fft.ifft2(np.fft.fft2(rng.standard_normal((H, W))) * np.exp(-((np.fft.fftfreq(H)[:, None] * H) ** 2 + (np.fft.fftfreq(W)[None, :] * W / 2) ** 2) / 60)).real
n = (n - n.min()) / (n.max() - n.min())
sky += (n[..., None] - 0.5) * 50 * (1 - y[..., None]) * 1.4
img = Image.fromarray(np.clip(sky, 0, 255).astype('uint8'), 'RGB').convert('RGBA')
# Sea.
d = ImageDraw.Draw(img)
for i in range(70):
    yy = 200 + i * 0.8
    c = int(40 + i * 0.6)
    d.line([(0, yy), (W, yy)], fill=(c - 10, c + 5, c + 25, 255))
# Lightning bolt with glow.
glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
g = ImageDraw.Draw(glow)
pts = [(360, 0), (352, 40), (366, 62), (340, 110), (356, 128), (330, 200)]
g.line(pts, fill=(190, 210, 255, 255), width=10)
glow = glow.filter(ImageFilter.GaussianBlur(9))
img.alpha_composite(glow)
d = ImageDraw.Draw(img)
d.line(pts, fill=(245, 248, 255, 255), width=3)
d.line([(352, 40), (330, 70), (322, 96)], fill=(220, 230, 255, 255), width=2)
# Rain.
rain = Image.new('RGBA', (W, H), (0, 0, 0, 0))
r = ImageDraw.Draw(rain)
for _ in range(260):
    x0 = rng.uniform(-40, W)
    y0 = rng.uniform(-40, H)
    ln = rng.uniform(14, 34)
    a = int(rng.uniform(60, 150))
    r.line([(x0, y0), (x0 + ln * 0.28, y0 + ln)], fill=(200, 215, 235, a), width=1)
img.alpha_composite(rain)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf', 50)
text = 'Real Weather'
tw = d.textlength(text, font=font)
shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
ImageDraw.Draw(shadow).text(((W - tw) / 2 + 3, 92 + 3), text, font=font, fill=(0, 0, 0, 200))
img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(3)))
d = ImageDraw.Draw(img)
d.text(((W - tw) / 2, 92), text, font=font, fill=(236, 242, 250, 255))
img.convert('RGB').save(OUT, optimize=True)
print('logo', os.path.getsize(OUT))
