"""Render previews of generated JSON models. usage: python3 preview.py out.png model.json [model2.json ...]"""
import sys
import os

from modelkit import load_json_model, render_quads, tex_loader, contact_sheet

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")


def views(paths, size=(640, 360), angles=((0, 0), (35, 22), (-140, 18), (90, 8)), scale=None):
    look = tex_loader(RES)
    quads = []
    for p in paths:
        quads += load_json_model(p, look)
    return [render_quads(quads, yaw=a, pitch=b, size=size, scale=scale) for a, b in angles]


if __name__ == "__main__":
    out = sys.argv[1]
    ims = views(sys.argv[2:])
    contact_sheet(ims, cols=2).save(out)
    print("saved", out)
