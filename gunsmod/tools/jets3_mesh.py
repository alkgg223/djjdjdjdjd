"""3.0 fighters, on the twin-engine fighter builder of jets2_mesh (model units 0.375 block, front -Z):
F/A-18E Super Hornet, F-15E Strike Eagle, Su-57, Eurofighter Typhoon, Rafale and Gripen. Each is a set of
proportions: the fuselage stations, the leading edge extensions, the nacelles, the wing, the tailplane (or the canards
of the delta-canard fighters) and the fins; the cockpit, gear, stores and details come from the shared builder."""
import jets2_mesh as J2
from jets2_mesh import flanker_like, gear, finish, store_parts, pylon, plume
from meshkit import v3

J2.COCKPIT.update({"fa18": (False, 3, False), "f15e": (False, 3, False), "su57": (False, 3, True), "typhoon": (False, 3, False),
                   "rafale": (True, 3, False), "gripen": (False, 3, False)})


def fighter(name, L, span, wy=5.3, nac_x=2.6, canard=False, fins=None, nac_r=1.3, wing_root=14.0, wing_le=0.0, stores=None, burner_r=0.95):
    kz = L / 45.0
    kx = span / 30.4

    def z(v):
        return v * kz

    half = span / 2.0
    g = dict(seat=(8.0, 6.6, z(-13.2)), cannon=(5.4, 6.0, z(-8.0)), nozzle=(8.0, 4.9, z(22.6)),
             gearNose=(8.0, 3.4, z(-15.0)), gearMain=(8.0, 3.9, z(3.0)),
             tips=[(8 - half, wy - 0.2, z(11.5)), (8 + half, wy - 0.2, z(11.5))],
             pylons=[(8 - half * 0.5, 4.4, z(3.5)), (8 + half * 0.5, 4.4, z(3.5)), (8 - half * 0.7, 4.4, z(6.0)), (8 + half * 0.7, 4.4, z(6.0))],
             flares=[(6.6, 6.2, z(18.0)), (9.4, 6.2, z(18.0))],
             ends=[(8.0, 5.4, z(-22.0)), (8.0, 6.0, z(23.0)), (8 - half - 0.2, wy - 0.2, z(12.5)), (8 + half + 0.2, wy - 0.2, z(12.5)),
                   (8 + (fins or (3.6,))[0] + 0.8, 13.2, z(18.0)), (8.0, 2.3, 0.0)],
             pivotY=2.2, size=(max(3.0, span * 0.375 * 0.32), 2.4))
    centre = [(z(-22.0), 5.4, 0.05, 0.05, 2.0), (z(-20.0), 5.4, 0.7, 0.65, 2.1), (z(-16.0), 5.6, 1.6, 1.4, 2.3), (z(-12.0), 5.8, 2.1, 1.8, 2.4),
              (z(-7.0), 6.0, 2.2, 1.7, 2.4), (z(0.0), 6.1, 2.0, 1.2, 2.4), (z(10.0), 6.1, 1.6, 0.9, 2.2), (z(18.0), 6.0, 1.0, 0.6, 2.0),
              (z(20.0), 6.0, 0.4, 0.3, 2.0)]
    lerx = [(8.0, z(-16.0)), (6.8, z(-14.0)), (8 - nac_x - 1.6, z(-8.0)), (8 - nac_x - 2.0, z(-1.0)), (8 - nac_x - 2.4, z(8.0)),
            (8 - nac_x - 2.0, z(15.0)), (8 - nac_x - 0.6, z(19.0))]
    nac = [(z(-4.0), 4.2, nac_r, nac_r * 0.85, 3.6), (z(2.0), 4.4, nac_r * 1.04, nac_r, 2.8), (z(12.0), 4.7, nac_r, nac_r, 2.2),
           (z(19.8), 4.9, nac_r * 0.92, nac_r * 0.92, 2.0)]
    if canard:
        # delta wing set far back, the canards ahead of it standing in for the tailplane
        wing = (nac_x + 1.6, z(wing_le), half, z(10.5), wing_root * kz, 2.2)
        htail = (nac_x + 1.0, wy + 0.6, z(-13.0), nac_x + 4.4 * kx, z(-10.0), 4.0 * kz, 1.4)
    else:
        wing = (nac_x + 3.0, z(wing_le), half, z(10.5), wing_root * kz, 3.0 * kx)
        htail = (nac_x + 1.6, 5.0, z(15.5), nac_x + 1.6 + 6.4 * kx, z(20.5), 6.5 * kz, 2.5)
    f = fins or (3.6, 6.8, 10.5, 6.6, 6.0, 6, 7.5, 3.0)
    f = (f[0], f[1], z(f[2]), f[3], f[4], f[5], f[6] * kz, f[7] * kz)
    body = flanker_like(name, L, wy, span, g, centre=centre, lerx=lerx, nac_x=nac_x, nac=nac, nozzle_z=z(22.6), nozzle_r=nac_r * 0.86,
                        wing=wing, htail=htail, fins=f, canopy_c=(8.0, 7.3, z(-13.0)))
    for x, y, zz in g["pylons"]:
        pylon(body, x, y, zz, wy)
    spots = [(v3(8 + half * 0.6, wy + 0.25, z(8.0)), (0, 1, 0), (0, 0, -1), 0.8), (v3(8 - half * 0.6, wy + 0.25, z(8.0)), (0, 1, 0), (0, 0, -1), 0.8),
             (v3(8 + f[0] + 0.2, 10.6, z(14.6)), (1, -0.1, 0), (0, 1, 0), 0.6), (v3(8 - f[0] - 0.2, 10.6, z(14.6)), (-1, -0.1, 0), (0, 1, 0), 0.6)]
    gp = gear(g, 4.8, 4.8, max(2.6, nac_x + 0.6))
    nz = [(8 + s * nac_x, 4.9, z(22.6)) for s in (1, -1)] if nac_x > 0.1 else [(8.0, 4.9, z(22.6))]
    return finish(name, g, body, spots, gp, plume(nz, burner_r), store_parts(g, stores or ["amraam", "amraam", "mav", "mav", "aim9", "aim9"]))


def fa18():
    return fighter("fa18", 49, 36.0, nac_x=2.0, fins=(3.0, 6.8, 9.0, 6.2, 7.0, 20, 7.0, 3.0), wing_le=-1.0, wing_root=13.0,
                   stores=["amraam", "amraam", "mav", "mav", "aim9", "aim9"])


def f15e():
    return fighter("f15e", 52, 35.0, nac_x=2.4, nac_r=1.45, fins=(3.4, 6.8, 11.0, 7.0, 6.0, 0, 8.0, 3.2), wing_le=-2.0, wing_root=15.0,
                   stores=["amraam", "amraam", "bomb", "bomb", "aim9", "aim9"], burner_r=1.05)


def su57():
    return fighter("su57", 54, 38.0, nac_x=3.6, nac_r=1.4, fins=(4.4, 6.6, 12.0, 5.4, 9.0, 26, 7.0, 2.6), wing_le=-4.0, wing_root=16.0,
                   stores=["amraam", "amraam", "mav", "mav", "r73", "r73"], burner_r=1.05)


def typhoon():
    return fighter("typhoon", 43, 29.0, nac_x=1.2, canard=True, fins=(0.0, 6.6, 9.0, 6.8, 9.0, 0, 7.0, 2.4), wing_le=-3.0, wing_root=17.0,
                   stores=["amraam", "amraam", "bomb", "bomb", "aim9", "aim9"])


def rafale():
    return fighter("rafale", 41, 29.0, nac_x=1.3, canard=True, fins=(0.0, 6.6, 8.5, 6.2, 9.5, 0, 7.0, 2.2), wing_le=-2.0, wing_root=16.0,
                   stores=["amraam", "amraam", "mav", "mav", "aim9", "aim9"])


def gripen():
    return fighter("gripen", 38, 22.4, nac_x=0.0, nac_r=1.35, canard=True, fins=(0.0, 6.6, 8.5, 6.0, 9.0, 0, 6.6, 2.0), wing_le=-1.0, wing_root=15.0,
                   stores=["amraam", "amraam", "bomb", "bomb", "aim9", "aim9"], burner_r=1.0)


JETS3 = {"fa18": fa18, "f15e": f15e, "su57": su57, "typhoon": typhoon, "rafale": rafale, "gripen": gripen}
J2.JETS2.update(JETS3)

if __name__ == "__main__":
    import os
    import meshkit as K
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in JETS3.items():
        parts, g = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()), sorted(parts))
