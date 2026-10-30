"""Write every smooth vehicle mesh (OBJ + MTL + loader JSON) into the resources, plus the shared material textures.
Run from tools/:  python3 gen_meshes.py"""
import os
import sys
import time

import meshkit as K
import tanks_mesh as TM
import helis_mesh as HM
import cars_mesh as CM
import jet_mesh as JM
import newveh_mesh as NV

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")


def write_all(folder, parts):
    total = 0
    for name, mesh in parts.items():
        K.write_part(mesh, folder, name, RES)
        total += mesh.quad_count()
    print(f"  {folder}: {len(parts)} parts, {total} quads")


def main(only=None):
    t0 = time.time()
    K.make_textures(RES)
    groups = []
    for key, fn in TM.TANKS.items():
        groups.append((key, lambda fn=fn: fn()[0]))
    for key, fn in NV.TANKS2.items():
        groups.append((key, lambda fn=fn: fn()[0]))
    groups.append(("tank_gear", lambda: dict(TM.gear_parts())))
    for key, fn in HM.HELIS.items():
        groups.append((key, lambda fn=fn: fn()[0]))
    for key, fn in CM.CARS.items():
        groups.append((key, lambda fn=fn: fn()[0]))
    for key, fn in NV.CARS2.items():
        groups.append((key, lambda fn=fn: fn()[0]))
    groups.append(("car", lambda: {"wheel": CM.car_wheel()}))
    groups.append(("jeep", CM.jeep))
    groups.append(("jeep_tow", lambda: {"launcher": CM.tow()}))
    for key, fn in JM.JETS.items():
        groups.append((key, lambda fn=fn: fn()[0]))
    for key, make in groups:
        if only and key not in only:
            continue
        folder = "entity/" + ("tank" if key == "tank_gear" else key)
        write_all(folder, make())
    print(f"done in {time.time() - t0:.1f}s")


if __name__ == "__main__":
    main(sys.argv[1:] or None)
