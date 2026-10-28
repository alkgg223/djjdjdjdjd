"""Generate block models, blockstates, block item models and BER part models."""
import json, os, sys
from blocks import BLOCKS, launcher_erector, missile_model
from display import block_display

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


def build_parts():
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
