import json, os
import vehicle as V

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "gunsmod")


def out(m, name, tex):
    return m.build(os.path.join(A, "textures", "item", "vehicle", tex + ".png"), os.path.join(A, "models", "entity", name),
                   f"gunsmod:item/vehicle/{tex}")


if __name__ == "__main__":
    out(V.jeep(), "jeep", "jeep")
    out(V.turret(), "jeep", "jeep_turret")
    out(V.turret_gun(), "jeep", "jeep_gun")
    w = V.wheel()
    w.build(os.path.join(A, "textures", "item", "vehicle", "jeep_wheel.png"), os.path.join(A, "models", "entity", "jeep"),
            "gunsmod:item/vehicle/jeep_wheel", parts_out={"body": os.path.join(A, "models", "entity", "jeep", "wheel.json")})
    for name, fn in (("helmet", V.helmet), ("vest", V.vest)):
        m = fn()
        m.build(os.path.join(A, "textures", "item", "gear", name + ".png"), os.path.join(A, "models", "entity", "gear"),
                f"gunsmod:item/gear/{name}", parts_out={"body": os.path.join(A, "models", "entity", "gear", name + ".json")})
    print("ok")
