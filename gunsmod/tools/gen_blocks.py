"""Generate block models, blockstates, block item models and BER part models."""
import json, os, sys
from blocks import BLOCKS, launcher_erector, missile_model, heavy_launcher, heavy_launcher_erector
from display import block_display, item_display, bounds_of

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "gunsmod")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=1)


def build_block(name):
    fn, facing = BLOCKS[name]
    m = fn()
    m.build(os.path.join(A, "textures", "block", name + ".png"), os.path.join(A, "models", "block"),
            f"gunsmod:block/{name}", display=block_display(),
            parts_out={"body": os.path.join(A, "models", "block", name + ".json")})
    if facing:
        variants = {f"facing={d}": {"model": f"gunsmod:block/{name}", "y": y} for d, y in
                    (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        for v in variants.values():
            if v["y"] == 0:
                del v["y"]
    else:
        variants = {"": {"model": f"gunsmod:block/{name}"}}
    write(os.path.join(A, "blockstates", name + ".json"), {"variants": variants})
    write(os.path.join(A, "models", "item", name + ".json"), {"parent": f"gunsmod:block/{name}"})


def build_heavy():
    """3 x 5 heavy launcher: invisible blocks + BER parts (half scale); the item uses the base model."""
    base = heavy_launcher()
    disp = item_display(bounds_of(base), gui_rot=(30, 225, 0), gui_fill=0.95, hand_rot=(0, 45, 0), tp_rot=(75, 45, 0), ground_fill=0.5)
    base.build(os.path.join(A, "textures", "block", "heavy_launcher.png"), os.path.join(A, "models", "block"),
               "gunsmod:block/heavy_launcher", display=disp,
               parts_out={"body": os.path.join(A, "models", "block", "heavy_launcher_base.json")})
    er = heavy_launcher_erector()
    er.build(os.path.join(A, "textures", "block", "heavy_launcher_erector.png"), os.path.join(A, "models", "block"),
             "gunsmod:block/heavy_launcher_erector",
             parts_out={"body": os.path.join(A, "models", "block", "heavy_launcher_erector.json")})
    for name in ("heavy_launcher", "heavy_launcher_part"):
        write(os.path.join(A, "models", "block", name + ".json"), {"textures": {"particle": "gunsmod:block/heavy_launcher"}})
    write(os.path.join(A, "blockstates", "heavy_launcher.json"),
          {"variants": {f"facing={d}": {"model": "gunsmod:block/heavy_launcher"} for d in ("north", "east", "south", "west")}})
    write(os.path.join(A, "blockstates", "heavy_launcher_part.json"), {"variants": {"": {"model": "gunsmod:block/heavy_launcher_part"}}})
    write(os.path.join(A, "models", "item", "heavy_launcher.json"), {"parent": "gunsmod:block/heavy_launcher_base"})


def build_parts():
    build_heavy()
    e = launcher_erector()
    e.build(os.path.join(A, "textures", "block", "launcher_erector.png"), os.path.join(A, "models", "block"),
            "gunsmod:block/launcher_erector", parts_out={"body": os.path.join(A, "models", "block", "launcher_erector.json")})


if __name__ == "__main__":
    names = sys.argv[1:] or list(BLOCKS)
    for n in names:
        build_block(n)
    if not sys.argv[1:]:
        build_parts()
    print("blocks:", len(names))
