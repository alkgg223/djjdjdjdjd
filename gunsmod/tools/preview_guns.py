import os, sys
from PIL import Image, ImageDraw
from modelkit import load_json_model, render_quads, tex_loader
from guns import GUNS
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
M = os.path.join(RES, "assets", "gunsmod", "models", "gun")
out = sys.argv[1]
names = sys.argv[2:] or list(GUNS)
look = tex_loader(RES)
rows = []
for n in names:
    parts = [p for p in ("body", "mag", "spin", "break") if os.path.exists(os.path.join(M, n, p + ".json"))]
    q = []
    for p in parts:
        q += load_json_model(os.path.join(M, n, p + ".json"), look)
    a = render_quads(q, yaw=0, pitch=0, size=(420, 200))
    b = render_quads(q, yaw=-35, pitch=25, size=(420, 200))
    c = render_quads(q, yaw=150, pitch=15, size=(420, 200))
    row = Image.new("RGB", (1260, 200))
    row.paste(a, (0, 0)); row.paste(b, (420, 0)); row.paste(c, (840, 0))
    ImageDraw.Draw(row).text((6, 4), n, fill=(255, 255, 0))
    rows.append(row)
sheet = Image.new("RGB", (1260, 200 * len(rows)))
for i, r in enumerate(rows):
    sheet.paste(r, (0, 200 * i))
sheet.save(out)
print("ok", out)
