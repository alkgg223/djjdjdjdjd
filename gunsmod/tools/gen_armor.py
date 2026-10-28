"""Build the tank and helicopter part models + atlases."""
import os
import sys

import armor as AR

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "gunsmod")


def out(m, folder, tex):
    return m.build(os.path.join(A, "textures", "item", "vehicle", tex + ".png"), os.path.join(A, "models", "entity", folder),
                   f"gunsmod:item/vehicle/{tex}")


if __name__ == "__main__":
    which = sys.argv[1:] or ["tank", "heli", "ammo"]
    if "tank" in which:
        out(AR.tank(), "tank", "tank")
        out(AR.tank_gear(), "tank", "tank_gear")
    if "heli" in which and hasattr(AR, "heli"):
        out(AR.heli(), "heli", "heli")
        out(AR.heli_rotor(), "heli", "heli_rotor")
    if "ammo" in which or not sys.argv[1:]:
        out(AR.projectiles(), "vehicle_ammo", "vehicle_ammo")
    print("ok")
