"""Generate item models (attachments, ammo, grenades, missiles, tools)."""
import json, os, sys
from props import ITEMS
from display import item_display, bounds_of

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "gunsmod")

GUI_ROT = {
    "attachment": (20, -35, 0),
    "missile": (0, 0, -45),
    "item": (25, -40, 0),
}


def build(name):
    fn, kind = ITEMS[name]
    m = fn()
    b = bounds_of(m)
    if kind == "missile":
        disp = item_display(b, gui_rot=(0, 0, -45), gui_fill=1.25, hand_rot=(0, 0, 0), tp_rot=(0, 0, 0), hand_fill=0.7)
    elif name in ("green_soldier", "red_soldier"):
        disp = item_display(b, gui_rot=(15, 150, 0), gui_fill=0.95, hand_rot=(0, 150, 0), tp_rot=(10, 150, 0))
    else:
        disp = item_display(b, gui_rot=GUI_ROT[kind], gui_fill=0.95)
    m.build(os.path.join(A, "textures", "item", name + ".png"), os.path.join(A, "models", "item"),
            f"gunsmod:item/{name}", display=disp, parts_out={"body": os.path.join(A, "models", "item", name + ".json")})
    return {"bounds": b, "kind": kind, "elements": len(m.elements)}


if __name__ == "__main__":
    names = sys.argv[1:] or list(ITEMS)
    meta_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "item_meta.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    for n in names:
        meta[n] = build(n)
    json.dump(meta, open(meta_path, "w"), indent=1)
    print(len(names), "items")
