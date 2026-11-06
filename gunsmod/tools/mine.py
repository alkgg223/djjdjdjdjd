"""Anti-vehicle mine (TM-62 style): a squat drum with a pressure plate and fuze, lying flat, centred on 8, 0, 8."""
from modelkit import Model


def mine():
    m = Model("mine", density=12.0, atlas_w=256, seed=7)
    b = dict(part="body")
    m.cyl("y", (8, 8), 4.6, 0.0, 2.0, "od_paint", sides=16, **b)       # body
    m.cyl("y", (8, 8), 4.9, 0.5, 1.0, "dark_paint", sides=16, **b)     # rolled rim
    m.cyl("y", (8, 8), 2.4, 2.0, 2.5, "dark_paint", sides=16, **b)     # pressure plate
    m.cyl("y", (8, 8), 1.0, 2.5, 2.9, "steel", sides=8, **b)           # fuze
    m.box([7.7, 2.9, 7.7], [8.3, 3.05, 8.3], "yellow", **b)            # safety pin cap
    m.box([7.4, 0.7, 2.9], [8.6, 1.5, 3.4], "steel", **b)              # carrying lug
    return m
