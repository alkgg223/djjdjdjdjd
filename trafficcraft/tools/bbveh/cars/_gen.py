"""Body templates for the second wave of cars: real overall dimensions in, profile / plan curves out.
kind: sedan | suv | box (upright off-roader) | pickup | coupe | super (mid-engine) | hatch
Every car file still sets its own lamps, grille, tail lamps, wheels, interior and performance."""


def body(kind, L, W, H, WB, fo, R=0.34, clear=0.2, doors=4, cab=None, bed=None):
    ro = L - WB - fo
    YR, YF = ro, L - fo
    g = clear
    d = dict(L=L, W=W, H=H, YR=YR, YF=YF, R=R, TRACK=round(W * 0.43, 3))
    if kind in ('sedan', 'hatch', 'coupe'):
        hatch = kind == 'hatch'
        C0 = 0.06 if hatch else (0.2 * L if kind == 'sedan' else 0.19 * L)
        C1 = L - (0.31 * L if kind != 'hatch' else 0.27 * L)
        cl = C1 - C0
        bH = 0.635 * H
        d.update(
            bot=[(0, g + 0.15), (0.15, g + 0.07), (0.6, g + 0.03), (1.5, g), (L - 1.3, g), (L - 0.4, g + 0.02), (L, g + 0.07)],
            belt=[(0, bH - 0.07), (0.06, bH - 0.02), (0.4, bH + 0.01), (C0 + 0.3, bH + 0.005), (C0 + cl * 0.6, bH - 0.02), (C1, bH - 0.05),
                  (L - 0.9, 0.57 * H), (L - 0.4, 0.52 * H), (L, 0.47 * H)],
            hw=[(0, 0.44 * W), (0.1, 0.475 * W), (0.6, 0.495 * W), (L / 2, 0.5 * W), (L - 0.6, 0.49 * W), (L - 0.2, 0.455 * W), (L, 0.39 * W)],
            zsh=[(0, 0.45 * H), (L, 0.4 * H)],
            CABIN0=C0, CABIN1=C1,
            roofz=([(C0, bH + 0.005), (C0 + 0.04, 0.9 * H), (C0 + 0.12 * cl, 0.985 * H), (C0 + 0.3 * cl, H)] if hatch else
                   [(C0, bH + 0.005), (C0 + 0.16 * cl, 0.82 * H), (C0 + 0.3 * cl, 0.96 * H), (C0 + 0.45 * cl, H)]) +
                  [(C0 + 0.68 * cl, 0.995 * H), (C0 + 0.8 * cl, 0.93 * H), (C0 + 0.9 * cl, 0.8 * H), (C1, bH - 0.04)],
            roofw=[(C0, 0.41 * W), (C0 + 0.3 * cl, 0.365 * W), (C0 + 0.6 * cl, 0.35 * W), (C1, 0.405 * W)],
            crown=[(0, 0.012), (C0, 0.02), (C1, 0.025), (L - 0.4, 0.03), (L, 0.01)],
            ws=(C0 + 0.8 * cl, C1 - 0.03),
            rear_glass=(C0 + 0.02, C0 + (0.2 if hatch else 0.28) * cl),
            FB_Z=L - 0.32, RB_Z=0.36, hood=(C1 + 0.02, L - 0.1, 0.36 * W), trunk_y=bH - 0.04, hatch_x=0.36 * W if hatch else None,
        )
        if doors == 2:
            d.update(windows=[(C0 + 0.3 * cl, C1 - 0.03)], bpillars=[], doors=[('f', C0 + 0.36 * cl, C1 - 0.03)])
        else:
            b0 = C0 + 0.55 * cl
            d.update(windows=[(C0 + 0.27 * cl, b0), (b0 + 0.07, C1 - 0.03)], bpillars=[(b0, b0 + 0.07)],
                     doors=[('r', C0 + 0.3 * cl, b0 + 0.04), ('f', b0 + 0.04, C1 - 0.03)])
    elif kind in ('suv', 'box', 'pickup'):
        box = kind == 'box'
        bH = (0.56 if not box else 0.55) * H
        if kind == 'pickup':
            C0 = bed[1]
            C1 = L - 0.34 * L
        else:
            C0 = 0.05 if not box else 0.02
            C1 = L - (0.31 * L if not box else 0.29 * L)
        cl = C1 - C0
        rake = 0.0 if box else 0.12
        d.update(
            bot=[(0, g + 0.2), (0.3, g + 0.12), (1.0, g), (L - 1.0, g), (L - 0.3, g + 0.12), (L, g + 0.18)],
            belt=[(0, bH - 0.03), (0.05, bH), (L / 2, bH + 0.005), (C1, bH - 0.01), (L - 0.5, bH - 0.04 - rake * 0.5), (L, bH - 0.08 - rake)],
            hw=[(0, 0.475 * W), (0.08, 0.49 * W), (0.5, 0.5 * W), (L - 0.5, 0.5 * W), (L - 0.1, 0.485 * W), (L, 0.465 * W)],
            zsh=[(0, 0.5 * H), (L, 0.48 * H)],
            CABIN0=C0, CABIN1=C1,
            roofz=[(C0, bH + 0.005), (C0 + (0.02 if box else 0.12), (0.97 if box else 0.92) * H), (C0 + (0.1 if box else 0.3), 0.995 * H),
                   (C0 + 0.5, H), (C1 - (0.55 if box else 0.85), H), (C1 - (0.45 if box else 0.7), 0.99 * H), (C1 - (0.2 if box else 0.32), 0.82 * H),
                   (C1, bH + 0.01)],
            roofw=[(C0, 0.455 * W), (C0 + 0.3, 0.43 * W), (C1 - 0.6, 0.43 * W), (C1, 0.455 * W)],
            crown=[(0, 0.004), (L, 0.012)],
            ws=(C1 - (0.48 if box else 0.78), C1 - 0.02),
            FB_Z=L - 0.28, RB_Z=0.26, hood=(C1 + 0.02, L - 0.06, 0.4 * W), trunk_y=bH,
            tumble=0.02 if box else 0.04, glass_drop=0.06,
        )
        if kind == 'pickup':
            # double cab: rear glass upright behind the rear seats, open load bed behind it
            d.update(rear_glass=(C0 + 0.01, C0 + 0.07))
            b0 = C0 + 0.48 * cl
            d.update(windows=[(C0 + 0.12, b0), (b0 + 0.08, C1 - 0.03)], bpillars=[(b0, b0 + 0.08)],
                     doors=[('r', C0 + 0.1, b0 + 0.04), ('f', b0 + 0.04, C1 - 0.03)], bed=bed)
        else:
            d.update(rear_glass=(C0 + 0.02, C0 + (0.12 if box else 0.3)))
            q1 = C0 + 0.62
            b0 = C0 + 0.48 * cl + 0.25
            d.update(windows=[(C0 + 0.32, q1), (q1 + 0.07, b0), (b0 + 0.08, C1 - 0.03)], bpillars=[(q1, q1 + 0.07), (b0, b0 + 0.08)],
                     doors=[('r', q1 + 0.03, b0 + 0.04), ('f', b0 + 0.04, C1 - 0.03)])
    elif kind == 'super':
        C0 = 0.3 * L
        C1 = L - 0.36 * L
        cl = C1 - C0
        bH = 0.72 * H
        d.update(
            bot=[(0, g + 0.18), (0.2, g + 0.08), (0.6, g + 0.02), (1.4, g), (L - 1.2, g), (L - 0.4, g + 0.01), (L, g + 0.06)],
            belt=[(0, bH - 0.05), (0.08, bH), (0.6, bH + 0.02), (C0, bH + 0.03), (C0 + cl * 0.6, bH - 0.02), (C1, bH - 0.1),
                  (L - 0.8, 0.52 * H), (L - 0.3, 0.45 * H), (L, 0.38 * H)],
            hw=[(0, 0.45 * W), (0.12, 0.485 * W), (0.8, 0.5 * W), (C0 + cl * 0.5, 0.47 * W), (C1, 0.485 * W), (L - 0.5, 0.48 * W), (L - 0.15, 0.44 * W),
                (L, 0.37 * W)],
            zsh=[(0, 0.5 * H), (L, 0.36 * H)],
            CABIN0=C0, CABIN1=C1,
            roofz=[(C0, bH + 0.03), (C0 + 0.2 * cl, 0.9 * H), (C0 + 0.4 * cl, H), (C0 + 0.62 * cl, 0.98 * H), (C0 + 0.82 * cl, 0.84 * H), (C1, bH - 0.08)],
            roofw=[(C0, 0.34 * W), (C0 + 0.4 * cl, 0.31 * W), (C1, 0.37 * W)],
            crown=[(0, 0.01), (C0, 0.02), (C1, 0.02), (L - 0.4, 0.025), (L, 0.006)],
            ws=(C0 + 0.55 * cl, C1 - 0.03), rear_glass=(C0 + 0.02, C0 + 0.3 * cl),
            windows=[(C0 + 0.32 * cl, C1 - 0.03)], bpillars=[], doors=[('f', C0 + 0.3 * cl, C1 - 0.05)],
            FB_Z=L - 0.36, RB_Z=0.4, hood=(C1 + 0.02, L - 0.12, 0.36 * W), trunk_y=bH - 0.02,
        )
    d['bot'] = sorted(d['bot'])
    return d
