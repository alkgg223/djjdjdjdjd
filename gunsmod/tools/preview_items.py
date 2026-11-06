import os, sys, json
from PIL import Image, ImageDraw
from modelkit import load_json_model, render_quads, tex_loader
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
look = tex_loader(RES)
out = sys.argv[1]; names = sys.argv[2:]
cells = []
for n in names:
    p = n if n.endswith('.json') else os.path.join(RES, "assets", "gunsmod", "models", "item", n + ".json")
    q = load_json_model(p, look)
    im = render_quads(q, yaw=-35, pitch=25, size=(300, 300))
    ImageDraw.Draw(im).text((4, 4), os.path.basename(n), fill=(255, 255, 0))
    cells.append(im)
cols = 5
rows = (len(cells) + cols - 1) // cols
sheet = Image.new("RGB", (300 * cols, 300 * rows))
for i, c in enumerate(cells):
    sheet.paste(c, ((i % cols) * 300, (i // cols) * 300))
sheet.save(out); print("ok")
