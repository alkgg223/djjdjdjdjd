"""Build the tank and helicopter part models + atlases."""
import os
import sys

import armor as AR
import armor2 as A2
import armor3 as A3
import jet as J

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
    for name, fn in A2.TANKS.items():
        if name in which or "tanks" in which or not sys.argv[1:]:
            out(fn(), name, name)
    for name, fn in A2.HELIS.items():
        if name in which or "helis" in which or not sys.argv[1:]:
            out(fn(), "heli_" + name, "heli_" + name)
    for name, fn in A3.CARS.items():
        if name in which or "cars" in which or not sys.argv[1:]:
            out(fn(), name, name)
    if "cars" in which or "wheel" in which or not sys.argv[1:]:
        A3.wheel().build(os.path.join(A, "textures", "item", "vehicle", "car_wheel.png"), os.path.join(A, "models", "entity", "car"),
                         "gunsmod:item/vehicle/car_wheel", parts_out={"body": os.path.join(A, "models", "entity", "car", "wheel.json")})
    if "cars" in which or "tow" in which or not sys.argv[1:]:
        out(A3.tow(), "jeep_tow", "jeep_tow")
    for name, fn in J.JETS.items():
        if name in which or "jets" in which or not sys.argv[1:]:
            out(fn(), name, name)
    if "ammo" in which or not sys.argv[1:]:
        out(AR.projectiles(), "vehicle_ammo", "vehicle_ammo")
    print("ok")
