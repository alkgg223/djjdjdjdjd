"""35 more real dinosaurs on the same body plans as dinos.py (theropod, quadruped, sauropod), each with its real
length, its own proportions, skull, crests / horns / sails / plates, colours and skin pattern."""

BIPEDS = {
    "velociraptor": dict(name="Velociraptor", length_m=2.0, geo=dict(arm_pose="fold",
        skull_k=(0.55, 0.62, 1.25), head_scale=1.0, eye_size=1.2, torso=(0.52, 0.56, 0.78), neck_len=1.35, neck_thick=0.45,
        neck_raise=0.32, arm_scale=2.6, arm_thick=0.45, fingers=3, hand_claw=1.6, leg_len=1.18, leg_thick=0.5, leg_x=0.6,
        toes="raptor", toe_thick=0.6, tooth_size=0.7, tail_len=1.35, tail_thick=0.4, feathers="full",
        features=("wing_feathers", "tail_fan"))),
    "coelophysis": dict(name="Coelophysis", length_m=3.0, geo=dict(arm_pose="fold",
        skull_k=(0.5, 0.56, 1.3), head_scale=0.9, eye_size=1.2, torso=(0.5, 0.52, 0.85), neck_len=1.6, neck_thick=0.4,
        neck_raise=0.35, arm_scale=2.0, arm_thick=0.42, fingers=3, leg_len=1.25, leg_thick=0.45, leg_x=0.6, toe_thick=0.55,
        tooth_size=0.6, tail_len=1.55, tail_thick=0.38)),
    "ceratosaurus": dict(name="Ceratosaurus", length_m=6.5, geo=dict(
        skull_k=(0.8, 0.92, 1.1), head_scale=1.05, features=("lacrimal_crests", "horns", "back_ridge"), torso=(0.75, 0.82, 0.98),
        neck_len=1.1, neck_thick=0.78, arm_scale=0.9, fingers=3, leg_len=1.02, leg_thick=0.8, tail_len=1.15, tail_thick=0.85,
        tooth_size=1.25)),
    "albertosaurus": dict(name="Albertosaurus", length_m=9.0, geo=dict(
        skull_k=(0.84, 0.9, 1.08), head_scale=1.1, torso=(0.82, 0.86, 1.0), neck_len=1.05, arm_scale=0.95, leg_len=1.12,
        leg_thick=0.82, tail_len=1.08, tail_thick=0.85, features=("lacrimal_crests",))),
    "daspletosaurus": dict(name="Daspletosaurus", length_m=9.0, geo=dict(
        skull_k=(0.95, 1.0, 1.0), head_scale=1.2, torso=(0.95, 0.95, 0.98), neck_thick=1.05, arm_scale=1.05, leg_len=1.0,
        leg_thick=0.95, tail_len=1.0, tail_thick=0.95, features=("horns",))),
    "tarbosaurus": dict(name="Tarbosaurus", length_m=10.0, geo=dict(
        skull_k=(0.86, 0.92, 1.12), head_scale=1.15, torso=(0.9, 0.92, 1.02), arm_scale=0.75, leg_len=1.05, leg_thick=0.9,
        tail_len=1.08, tail_thick=0.9)),
    "acrocanthosaurus": dict(name="Acrocanthosaurus", length_m=11.5, geo=dict(
        skull_k=(0.8, 0.88, 1.15), head_scale=1.1, features=("back_ridge", "lacrimal_crests"), torso=(0.88, 1.0, 1.06),
        neck_len=1.1, arm_scale=1.6, fingers=3, hand_claw=1.5, leg_len=1.0, leg_thick=0.9, tail_len=1.12, tail_thick=0.9)),
    "carcharodontosaurus": dict(name="Carcharodontosaurus", length_m=12.5, geo=dict(
        skull_k=(0.8, 0.94, 1.22), head_scale=1.28, torso=(0.98, 1.0, 1.08), arm_scale=1.2, fingers=3, leg_len=1.02,
        leg_thick=0.95, tail_len=1.12, tail_thick=0.92, tooth_size=1.3, features=("back_ridge",))),
    "torvosaurus": dict(name="Torvosaurus", length_m=10.0, geo=dict(
        skull_k=(0.86, 0.95, 1.12), head_scale=1.12, torso=(0.95, 0.95, 1.0), arm_scale=1.4, arm_thick=1.1, fingers=3,
        hand_claw=1.9, leg_len=0.95, leg_thick=1.0, tail_thick=0.95, tooth_size=1.2)),
    "suchomimus": dict(name="Suchomimus", length_m=11.0, geo=dict(
        skull="croc", head_scale=1.0, features=("back_ridge",), torso=(0.8, 0.88, 1.08), neck_len=1.3, neck_thick=0.72,
        neck_raise=0.12, arm_scale=2.4, arm_thick=0.72, fingers=3, hand_claw=2.3, leg_len=0.95, leg_thick=0.8, tail_len=1.15,
        tail_thick=0.75)),
    "irritator": dict(name="Irritator", length_m=7.0, geo=dict(
        skull="croc", head_scale=0.92, features=("head_crest",), torso=(0.72, 0.78, 1.0), neck_len=1.3, neck_thick=0.66,
        neck_raise=0.15, arm_scale=2.2, arm_thick=0.66, fingers=3, hand_claw=2.0, leg_len=1.0, leg_thick=0.72, tail_len=1.1,
        tail_thick=0.68)),
    "cryolophosaurus": dict(name="Cryolophosaurus", length_m=6.5, geo=dict(arm_pose="fold",
        skull_k=(0.66, 0.76, 1.12), head_scale=0.95, features=("head_crest",), torso=(0.7, 0.75, 0.95), neck_len=1.25,
        neck_thick=0.62, neck_raise=0.18, arm_scale=1.9, arm_thick=0.58, fingers=3, leg_len=1.1, leg_thick=0.66, leg_x=0.75,
        tail_len=1.2, tail_thick=0.62)),
    "majungasaurus": dict(name="Majungasaurus", length_m=6.5, geo=dict(
        skull_k=(0.92, 0.98, 0.78), head_scale=0.98, features=("horns",), torso=(0.85, 0.85, 0.95), neck_len=0.95,
        neck_thick=0.95, arm_scale=0.55, leg_len=0.9, leg_thick=0.88, tail_len=1.05, tail_thick=0.85)),
    "concavenator": dict(name="Concavenator", length_m=6.0, geo=dict(arm_pose="fold",
        skull_k=(0.68, 0.78, 1.12), head_scale=0.95, features=("back_ridge", "quill_crest"), torso=(0.7, 0.78, 0.95),
        neck_len=1.2, neck_thick=0.62, neck_raise=0.18, arm_scale=1.9, arm_thick=0.58, fingers=3, leg_len=1.1, leg_thick=0.66,
        leg_x=0.75, tail_len=1.2, tail_thick=0.62)),
    "herrerasaurus": dict(name="Herrerasaurus", length_m=4.5, geo=dict(arm_pose="fold",
        skull_k=(0.62, 0.72, 1.15), head_scale=1.0, torso=(0.62, 0.66, 0.88), neck_len=1.25, neck_thick=0.55, neck_raise=0.25,
        arm_scale=2.3, arm_thick=0.52, fingers=3, hand_claw=1.6, leg_len=1.15, leg_thick=0.6, leg_x=0.68, tail_len=1.3,
        tail_thick=0.5)),
    "ornithomimus": dict(name="Ornithomimus", length_m=3.8, geo=dict(arm_pose="fold",
        skull="beak", beak_len=0.9, head_scale=0.52, teeth=False, eye_size=1.1, torso=(0.6, 0.62, 0.78), neck_len=2.0,
        neck_thick=0.4, neck_raise=0.4, arm_scale=2.0, arm_thick=0.42, fingers=3, leg_len=1.5, leg_thick=0.52, leg_x=0.6,
        tail_len=1.15, tail_thick=0.4, feathers="partial", features=("wing_feathers",))),
    "deinocheirus": dict(name="Deinocheirus", length_m=11.0, geo=dict(arm_pose="fold",
        skull="beak", beak_len=1.2, head_scale=0.75, teeth=False, features=("sail",), torso=(0.95, 1.12, 1.0), belly=1.15,
        neck_len=1.8, neck_thick=0.6, neck_raise=0.35, arm_scale=3.4, arm_thick=0.75, fingers=3, hand_claw=3.0, leg_len=1.0,
        leg_thick=0.95, tail_len=0.7, tail_thick=0.8, feathers="partial")),
    "citipati": dict(name="Citipati", length_m=2.9, geo=dict(arm_pose="fold",
        skull="beak", beak_len=0.5, head_scale=0.85, teeth=False, features=("head_crest", "wing_feathers", "tail_fan"),
        torso=(0.62, 0.68, 0.76), neck_len=1.45, neck_thick=0.55, neck_raise=0.38, arm_scale=2.7, arm_thick=0.5, fingers=3,
        leg_len=1.2, leg_thick=0.56, leg_x=0.7, toe_thick=0.65, tail_len=0.85, tail_thick=0.55, feathers="full")),
    "hypsilophodon": dict(name="Hypsilophodon", length_m=1.8, geo=dict(arm_pose="fold",
        skull="beak", beak_len=0.35, head_scale=0.95, teeth=False, eye_size=1.4, torso=(0.6, 0.66, 0.8), neck_len=1.2,
        neck_thick=0.52, neck_raise=0.3, arm_scale=1.8, arm_thick=0.45, fingers=3, leg_len=1.3, leg_thick=0.55, leg_x=0.65,
        toe_thick=0.6, tail_len=1.4, tail_thick=0.45)),
}

QUADS = {
    "edmontosaurus": dict(name="Edmontosaurus", length_m=12.0, geo=dict(
        skull="iguanodon", torso=(0.85, 0.95, 1.12), front_len=1.05, front_thick=0.7, hind_len=1.55, hind_thick=0.92, neck_len=1.45,
        neck_thick=0.8, neck_raise=0.22, head_scale=1.25, tail_len=1.25, tail_thick=0.85, tail_lift=0.2, fingers=True)),
    "maiasaura": dict(name="Maiasaura", length_m=9.0, geo=dict(
        skull="iguanodon", torso=(0.8, 0.9, 1.02), front_len=1.05, front_thick=0.68, hind_len=1.5, hind_thick=0.88, neck_len=1.4,
        neck_thick=0.75, neck_raise=0.25, head_scale=1.1, tail_len=1.2, tail_thick=0.82, tail_lift=0.2, fingers=True)),
    "psittacosaurus": dict(name="Psittacosaurus", length_m=2.0, geo=dict(
        skull="moschops", torso=(0.7, 0.75, 0.85), front_len=0.9, front_thick=0.65, hind_len=1.3, hind_thick=0.8, neck_len=0.9,
        neck_thick=0.8, head_scale=0.95, tail_len=1.0, tail_thick=0.72, tail_lift=0.15, pitch=-0.1, fingers=True, teeth=False)),
    "styracosaurus": dict(name="Styracosaurus", length_m=5.5, geo=dict(
        skull="pachyrhino", torso=(0.85, 0.9, 0.92), head_scale=0.95, horn_len=1.4, frill=1.15,
        features=("frill_spikes", "frill_rim"))),
    "centrosaurus": dict(name="Centrosaurus", length_m=6.0, geo=dict(
        skull="trike", torso=(0.88, 0.9, 0.92), head_scale=0.92, horn_len=0.6, frill=0.9, features=("frill_spikes",))),
    "chasmosaurus": dict(name="Chasmosaurus", length_m=5.0, geo=dict(
        skull="trike", torso=(0.82, 0.85, 0.88), head_scale=0.95, horn_len=0.55, frill=1.45, features=("frill_rim",))),
    "protoceratops": dict(name="Protoceratops", length_m=2.0, geo=dict(
        skull="pachyrhino", torso=(0.75, 0.8, 0.85), head_scale=1.05, horn_len=0.15, frill=0.85, front_len=0.85, hind_len=0.95,
        tail_len=0.8, features=("frill_rim",))),
    "kentrosaurus": dict(name="Kentrosaurus", length_m=4.5, geo=dict(
        skull="stego", torso=(0.75, 0.92, 0.95), front_len=0.8, front_thick=0.8, hind_len=1.3, hind_thick=0.9, neck_len=1.15,
        neck_thick=0.5, neck_raise=-0.2, head_scale=0.8, tail_len=1.3, tail_thick=0.75, tail_lift=0.25, pitch=-0.15,
        features=("plates", "thagomizer", "side_spikes"))),
    "euoplocephalus": dict(name="Euoplocephalus", length_m=6.0, geo=dict(
        skull="anky", torso=(1.1, 0.75, 0.95), front_len=0.68, hind_len=0.74, front_thick=1.0, hind_thick=1.0, leg_x=1.2,
        neck_len=0.8, neck_thick=1.0, neck_raise=-0.12, tail_len=1.05, tail_thick=0.8,
        features=("osteoderms", "tail_club"))),
    "nodosaurus": dict(name="Nodosaurus", length_m=6.0, geo=dict(
        skull="anky", torso=(1.05, 0.8, 1.02), front_len=0.7, hind_len=0.78, front_thick=1.0, hind_thick=1.0, leg_x=1.15,
        neck_len=0.85, neck_thick=0.95, neck_raise=-0.1, tail_len=1.1, tail_thick=0.85, features=("osteoderms", "side_spikes"))),
}

SAUROPODS = {
    "brachiosaurus": dict(name="Brachiosaurus", length_m=22.0, geo=dict(
        torso=(0.9, 1.05, 0.92), neck_len=1.35, neck_thick=0.85, neck_raise=0.45, head_scale=1.05, snout=0.9, leg_len=1.35,
        leg_thick=0.9, tail_len=0.75, tail_thick=0.85)),
    "argentinosaurus": dict(name="Argentinosaurus", length_m=33.0, geo=dict(
        torso=(1.15, 1.15, 1.05), neck_len=1.05, neck_thick=1.0, neck_raise=0.1, head_scale=0.9, leg_len=1.1, leg_thick=1.15,
        tail_len=0.95, tail_thick=1.05)),
    "apatosaurus": dict(name="Apatosaurus", length_m=21.0, geo=dict(
        torso=(1.05, 1.0, 1.0), neck_len=0.95, neck_thick=1.25, neck_raise=-0.1, head_scale=1.0, leg_len=0.95, leg_thick=1.1,
        tail_len=1.15, tail_thick=0.95)),
    "camarasaurus": dict(name="Camarasaurus", length_m=15.0, geo=dict(
        torso=(1.0, 1.05, 0.92), neck_len=0.75, neck_thick=1.05, neck_raise=0.15, head_scale=1.25, snout=0.75, leg_len=1.05,
        leg_thick=1.0, tail_len=0.85, tail_thick=0.95)),
    "mamenchisaurus": dict(name="Mamenchisaurus", length_m=26.0, geo=dict(
        torso=(0.85, 0.9, 0.95), neck_len=1.75, neck_thick=0.7, neck_raise=0.05, head_scale=0.9, leg_len=1.0, leg_thick=0.85,
        tail_len=1.0, tail_thick=0.8)),
    "dreadnoughtus": dict(name="Dreadnoughtus", length_m=26.0, geo=dict(
        torso=(1.12, 1.1, 1.0), neck_len=1.05, neck_thick=1.05, neck_raise=0.2, head_scale=0.95, leg_len=1.12, leg_thick=1.1,
        tail_len=1.05, tail_thick=1.0)),
}

LOOK = {
    "velociraptor": dict(pal=dict(dorsal="#6a4a2e", flank="#a8804e", belly="#e6d6b0", feather="#7a5434", feather2="#2e2014",
                                  stripe="#2a1a0e", accent="#c8902a"), pattern="stripes", feathers="full"),
    "coelophysis": dict(pal=dict(dorsal="#5a5a3a", flank="#90905a", belly="#e0dcb4", stripe="#30301e", accent="#c06a2a"),
                        pattern="stripes", pattern_scale=0.8),
    "ceratosaurus": dict(pal=dict(dorsal="#3a3a40", flank="#6a6a74", belly="#c8c4b4", stripe="#1e1e24", accent="#c0502a",
                                  horn="#5a4a3a", iris="#e8b030"), pattern="bands"),
    "albertosaurus": dict(pal=dict(dorsal="#5a4630", flank="#94764e", belly="#dccaa0", stripe="#2e2418", accent="#a85a2a"),
                          pattern="stripes"),
    "daspletosaurus": dict(pal=dict(dorsal="#4a2e24", flank="#8a5a42", belly="#d8bc9a", stripe="#24140e", accent="#9a3a2a",
                                    horn="#4a3a2e"), pattern="mottled", scars=True),
    "tarbosaurus": dict(pal=dict(dorsal="#5e5a44", flank="#9a9470", belly="#e0dabc", stripe="#34321e", accent="#b0702e"),
                        pattern="stripes", pattern2="spots"),
    "acrocanthosaurus": dict(pal=dict(dorsal="#4a3a2c", flank="#86684a", belly="#d8c4a0", stripe="#2a1e14", accent="#b84a2a"),
                             pattern="bands"),
    "carcharodontosaurus": dict(pal=dict(dorsal="#6a5a40", flank="#a89068", belly="#e4d6b4", stripe="#3a3020", accent="#a0402a",
                                         iris="#e8c040"), pattern="stripes", pattern_strength=0.5),
    "torvosaurus": dict(pal=dict(dorsal="#3a4434", flank="#66745a", belly="#ccccb0", stripe="#20261c", accent="#8a5a2a"),
                        pattern="mottled"),
    "suchomimus": dict(pal=dict(dorsal="#4a5a5a", flank="#7a908a", belly="#d8dcc8", stripe="#283434", accent="#c06a3a"),
                       pattern="stripes"),
    "irritator": dict(pal=dict(dorsal="#5a4a3a", flank="#90785a", belly="#dcd0b4", stripe="#302418", frill="#c84a2a",
                               accent="#d06a30"), pattern="spots"),
    "cryolophosaurus": dict(pal=dict(dorsal="#4a4a4e", flank="#7e7e86", belly="#dcdad4", stripe="#26262a", frill="#e0b030",
                                     accent="#e0b030"), pattern="stripes"),
    "majungasaurus": dict(pal=dict(dorsal="#6a3a2a", flank="#a8664a", belly="#e0c4a4", stripe="#3a1e14", accent="#c84a2a",
                                   horn="#5a3a2a"), pattern="blotch"),
    "concavenator": dict(pal=dict(dorsal="#3a4a3a", flank="#6a806a", belly="#d4dcc8", stripe="#1e281e", accent="#a8a030"),
                         pattern="stripes"),
    "herrerasaurus": dict(pal=dict(dorsal="#6a5034", flank="#a8845a", belly="#e4d4b0", stripe="#3a2a1a", accent="#b05a2a"),
                          pattern="spots", pattern_scale=0.9),
    "ornithomimus": dict(pal=dict(dorsal="#6a6250", flank="#a89c80", belly="#ece4d0", feather="#8a8068", feather2="#3a3628",
                                  stripe="#3a3428", beak="#5a5040"), pattern="stripes", feathers="partial", round_pupil=True),
    "deinocheirus": dict(pal=dict(dorsal="#5a4a34", flank="#8e7a58", belly="#dcd0b0", feather="#7a6a4e", feather2="#3a3022",
                                  sail="#a85a2a", accent="#c0743a", claw="#3a3228", beak="#4e463a"), pattern="bands",
                         feathers="partial", round_pupil=True),
    "citipati": dict(pal=dict(dorsal="#4a3a3a", flank="#7a6262", belly="#e0d4d0", feather="#6a4e4e", feather2="#2a1e1e",
                              frill="#d04a2a", accent="#3a8ab0", beak="#3a3430"), pattern="stripes", feathers="full", round_pupil=True),
    "hypsilophodon": dict(pal=dict(dorsal="#5a6a3a", flank="#8ea05a", belly="#e4e4bc", stripe="#303a1e", beak="#4a4436"),
                          pattern="stripes", round_pupil=True),
    "edmontosaurus": dict(pal=dict(dorsal="#5a5444", flank="#8e866a", belly="#dcd6bc", stripe="#383426", beak="#5a5244"),
                          pattern="bands", round_pupil=True),
    "maiasaura": dict(pal=dict(dorsal="#6a5a3a", flank="#a08a5a", belly="#e4dcbc", stripe="#40341e", beak="#5c5444",
                               accent="#b07a3a"), pattern="spots", round_pupil=True),
    "psittacosaurus": dict(pal=dict(dorsal="#5a4a3a", flank="#8e7a5e", belly="#e0d4b8", stripe="#342a1e", beak="#4a4034"),
                           pattern="mottled", round_pupil=True),
    "styracosaurus": dict(pal=dict(dorsal="#5a4a3a", flank="#8a765a", belly="#d8caa8", frill="#b0502a", accent="#d07a3a",
                                   horn="#7a6e58", beak="#4c4438"), pattern="bands"),
    "centrosaurus": dict(pal=dict(dorsal="#4e5040", flank="#7e826a", belly="#d4d4b8", frill="#7a5a3a", accent="#a8783a",
                                  horn="#6e6650", beak="#4a4436"), pattern="mottled"),
    "chasmosaurus": dict(pal=dict(dorsal="#5a4a40", flank="#8e7a6a", belly="#dccab4", frill="#c0603a", accent="#3a6a9a",
                                  horn="#7a6a58", beak="#4c4438"), pattern="bands", pattern_scale=1.2),
    "protoceratops": dict(pal=dict(dorsal="#7a6a4a", flank="#b09a72", belly="#e8dcc0", frill="#a07a4a", accent="#b88a4a",
                                   beak="#5a5040"), pattern="spots", round_pupil=True),
    "kentrosaurus": dict(pal=dict(dorsal="#5a5034", flank="#8a8054", belly="#d8d0a8", stripe="#34301e", plate="#7a5a34",
                                  accent="#b86a2a", horn="#6e6252", beak="#585040"), pattern="stripes"),
    "euoplocephalus": dict(pal=dict(dorsal="#4a4434", flank="#787052", belly="#c8bc98", plate="#3a3428", accent="#8a6a3a",
                                    horn="#4c4234", beak="#585040"), pattern="mottled"),
    "nodosaurus": dict(pal=dict(dorsal="#5a3e2a", flank="#8a6446", belly="#d0b48e", plate="#4a3020", accent="#a86a3a",
                                horn="#4c3a2a", beak="#585040"), pattern="blotch"),
    "brachiosaurus": dict(pal=dict(dorsal="#5a6058", flank="#8a9284", belly="#d4d8c8", stripe="#3a3e38"), pattern="mottled",
                          skin="smooth"),
    "argentinosaurus": dict(pal=dict(dorsal="#6a5444", flank="#9a8068", belly="#dccab0", stripe="#44342a"), pattern="bands",
                            pattern_strength=0.3, skin="smooth"),
    "apatosaurus": dict(pal=dict(dorsal="#4a4a40", flank="#7a7a66", belly="#cccab0", stripe="#2e2e26"), pattern="bands",
                        pattern_scale=1.5, skin="smooth"),
    "camarasaurus": dict(pal=dict(dorsal="#5a4e3e", flank="#8a7a60", belly="#d6c8aa", stripe="#3a3226"), pattern="spots",
                         pattern_scale=1.3, skin="smooth"),
    "mamenchisaurus": dict(pal=dict(dorsal="#4e5a44", flank="#7e8c6a", belly="#d4dcbc", stripe="#2e3626"), pattern="chevrons",
                           pattern_scale=1.4, skin="smooth"),
    "dreadnoughtus": dict(pal=dict(dorsal="#5e5650", flank="#90867c", belly="#d8d0c4", stripe="#3e3832"), pattern="mottled",
                          skin="smooth"),
}

ATTACK = {"velociraptor": "pounce", "coelophysis": "pounce", "ceratosaurus": "shake", "albertosaurus": "shake", "daspletosaurus": "shake",
          "tarbosaurus": "shake", "acrocanthosaurus": "shake", "carcharodontosaurus": "shake", "torvosaurus": "shake",
          "suchomimus": "shake", "irritator": "shake", "cryolophosaurus": "shake", "majungasaurus": "shake", "concavenator": "shake",
          "herrerasaurus": "pounce", "ornithomimus": "peck", "deinocheirus": "slash", "citipati": "peck", "hypsilophodon": "peck",
          "edmontosaurus": "rear", "maiasaura": "rear", "psittacosaurus": "bite", "styracosaurus": "horn", "centrosaurus": "horn",
          "chasmosaurus": "horn", "protoceratops": "bite", "kentrosaurus": "tail", "euoplocephalus": "tail", "nodosaurus": "bite",
          "brachiosaurus": "stomp", "argentinosaurus": "stomp", "apatosaurus": "tail", "camarasaurus": "stomp",
          "mamenchisaurus": "tail", "dreadnoughtus": "stomp"}

RIDEABLE = {"velociraptor", "ceratosaurus", "albertosaurus", "daspletosaurus", "tarbosaurus", "acrocanthosaurus", "carcharodontosaurus",
            "torvosaurus", "suchomimus", "irritator", "cryolophosaurus", "majungasaurus", "concavenator", "herrerasaurus",
            "ornithomimus", "deinocheirus", "edmontosaurus", "maiasaura", "styracosaurus", "centrosaurus", "chasmosaurus",
            "kentrosaurus", "euoplocephalus", "nodosaurus", "brachiosaurus", "argentinosaurus", "apatosaurus", "camarasaurus",
            "mamenchisaurus", "dreadnoughtus"}
