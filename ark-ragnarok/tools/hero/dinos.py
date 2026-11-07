"""The 27 creatures of the mod (only the dinosaurs the design asks for).

geo: sculpt parameters (see theropod.DEFAULTS / quadruped.DEFAULTS).  length_m: real nose-to-tail length; the model is
scaled so that 1 m = 1 block.  Stats are ARK base values; the game converts them (see Species.java).
"""

BIPEDS = {
    "tyrannosaurus": dict(name="Tyrannosaurus", length_m=12.3, geo=dict()),
    "allosaurus": dict(name="Allosaurus", length_m=9.5, geo=dict(
        skull_k=(0.78, 0.86, 1.12), head_scale=1.0, features=("lacrimal_crests",), torso=(0.82, 0.85, 1.0), neck_len=1.1,
        neck_thick=0.8, arm_scale=1.7, fingers=3, hand_claw=1.6, leg_len=1.05, leg_thick=0.8, tail_len=1.1, tail_thick=0.8)),
    "giganotosaurus": dict(name="Giganotosaurus", length_m=13.0, geo=dict(
        skull_k=(0.82, 0.92, 1.18), head_scale=1.25, torso=(1.0, 1.02, 1.08), arm_scale=1.3, fingers=3, tail_len=1.12,
        features=("back_ridge",))),
    "carnotaurus": dict(name="Carnotaurus", length_m=8.0, geo=dict(
        skull_k=(0.95, 1.0, 0.72), head_scale=1.0, features=("horns",), torso=(0.8, 0.82, 0.95), arm_scale=0.6,
        leg_len=1.18, leg_thick=0.78, tail_len=1.1, tail_thick=0.78, neck_len=1.1, neck_thick=0.8)),
    "spinosaurus": dict(name="Spinosaurus", length_m=15.0, geo=dict(
        skull="croc", head_scale=1.15, features=("sail", "tail_fin"), torso=(0.85, 0.92, 1.15), neck_len=1.3, neck_thick=0.75,
        neck_raise=0.15, arm_scale=2.6, arm_thick=0.75, fingers=3, hand_claw=2.0, leg_len=0.8, leg_thick=0.85, tail_len=1.2,
        tail_thick=0.75)),
    "baryonyx": dict(name="Baryonyx", length_m=9.0, geo=dict(
        skull="croc", head_scale=0.95, torso=(0.75, 0.8, 1.0), neck_len=1.25, neck_thick=0.7, neck_raise=0.1, arm_scale=2.3,
        arm_thick=0.7, fingers=3, hand_claw=2.6, leg_len=1.0, leg_thick=0.75, tail_len=1.05, tail_thick=0.7)),
    "megalosaurus": dict(name="Megalosaurus", length_m=7.0, geo=dict(
        skull_k=(0.84, 0.9, 1.08), head_scale=1.05, torso=(0.9, 0.9, 1.0), arm_scale=1.5, fingers=3, hand_claw=1.5,
        leg_len=0.95, leg_thick=0.9, tail_thick=0.9)),
    "therizinosaurus": dict(name="Therizinosaurus", length_m=10.0, geo=dict(
        skull="beak", beak_len=0.7, head_scale=0.62, teeth=False, torso=(1.0, 1.15, 0.9), belly=1.2, neck_len=2.3,
        neck_thick=0.55, neck_raise=0.42, arm_scale=3.0, arm_thick=0.6, fingers=3, hand_claw=5.0, leg_len=1.15,
        leg_thick=0.8, tail_len=0.55, tail_thick=0.8, feathers="full")),
    "utahraptor": dict(name="Utahraptor", length_m=6.0, geo=dict(arm_pose="fold", 
        skull_k=(0.62, 0.72, 1.2), head_scale=0.95, torso=(0.62, 0.66, 0.85), neck_len=1.25, neck_thick=0.55,
        neck_raise=0.25, arm_scale=2.6, arm_thick=0.55, fingers=3, hand_claw=1.8, leg_len=1.1, leg_thick=0.68, leg_x=0.7, tooth_size=0.75,
        toes="raptor", toe_thick=0.7, tail_len=1.25, tail_thick=0.5, feathers="full", features=("quill_crest", "wing_feathers",
                                                                                                         "tail_fan"))),
    "deinonychus": dict(name="Deinonychus", length_m=3.4, geo=dict(arm_pose="fold", 
        skull_k=(0.6, 0.7, 1.15), head_scale=1.0, torso=(0.58, 0.62, 0.8), neck_len=1.25, neck_thick=0.5, neck_raise=0.3,
        arm_scale=2.7, arm_thick=0.52, fingers=3, hand_claw=1.8, leg_len=1.15, leg_thick=0.62, leg_x=0.62, toes="raptor",
        tooth_size=0.75,
        toe_thick=0.65, tail_len=1.3, tail_thick=0.45, feathers="full", features=("wing_feathers", "tail_fan"))),
    "dilophosaurus": dict(name="Dilophosaurus", length_m=7.0, geo=dict(arm_pose="fold", 
        skull_k=(0.6, 0.7, 1.15), head_scale=0.9, features=("double_crest",), torso=(0.65, 0.7, 0.95), neck_len=1.3,
        neck_thick=0.6, neck_raise=0.2, arm_scale=2.0, arm_thick=0.55, fingers=3, leg_len=1.12, leg_thick=0.6, leg_x=0.75,
        tail_len=1.2, tail_thick=0.6)),
    "troodon": dict(name="Troodon", length_m=2.4, geo=dict(arm_pose="fold", 
        skull_k=(0.55, 0.62, 1.05), head_scale=1.1, eye_size=1.6, torso=(0.55, 0.6, 0.8), neck_len=1.35, neck_thick=0.48,
        neck_raise=0.3, arm_scale=2.6, arm_thick=0.45, fingers=3, leg_len=1.2, leg_thick=0.45, leg_x=0.6, toes="raptor",
        toe_thick=0.55, tail_len=1.25, tail_thick=0.42, feathers="full", features=("wing_feathers", "tail_fan"))),
    "compsognathus": dict(name="Compsognathus", length_m=1.0, geo=dict(arm_pose="fold", 
        skull_k=(0.55, 0.62, 1.0), head_scale=1.0, eye_size=1.3, torso=(0.5, 0.55, 0.75), neck_len=1.4, neck_thick=0.45,
        neck_raise=0.3, arm_scale=2.0, arm_thick=0.45, leg_len=1.25, leg_thick=0.45, leg_x=0.6, toe_thick=0.6,
        tail_len=1.5, tail_thick=0.4)),
    "oviraptor": dict(name="Oviraptor", length_m=1.8, geo=dict(arm_pose="fold", 
        skull="beak", beak_len=0.55, head_scale=0.8, teeth=False, features=("head_crest", "wing_feathers", "tail_fan"),
        torso=(0.6, 0.66, 0.75), neck_len=1.35, neck_thick=0.55, neck_raise=0.35, arm_scale=2.6, arm_thick=0.5, fingers=3,
        leg_len=1.15, leg_thick=0.55, leg_x=0.7, toe_thick=0.65, tail_len=0.9, tail_thick=0.55, feathers="full")),
    "gallimimus": dict(name="Gallimimus", length_m=6.0, geo=dict(arm_pose="fold", 
        skull="beak", beak_len=1.1, head_scale=0.55, teeth=False, eye_size=1.0, torso=(0.62, 0.65, 0.8), neck_len=2.2,
        neck_thick=0.42, neck_raise=0.42, arm_scale=2.2, arm_thick=0.45, fingers=3, leg_len=1.45, leg_thick=0.55, leg_x=0.6,
        toe_thick=0.6, tail_len=1.15, tail_thick=0.5, feathers="partial", features=("wing_feathers",))),
    "pachycephalosaurus": dict(name="Pachycephalosaurus", length_m=4.5, geo=dict(
        skull="dome", head_scale=0.9, torso=(0.78, 0.85, 0.85), neck_len=0.9, neck_thick=0.8, arm_scale=1.5, fingers=3,
        leg_len=1.0, leg_thick=0.8, tail_thick=0.8)),
    "yutyrannus": dict(name="Yutyrannus", length_m=9.0, geo=dict(
        skull_k=(0.85, 0.9, 1.05), head_scale=1.05, torso=(0.9, 0.92, 0.95), arm_scale=1.5, fingers=3, leg_len=0.95,
        feathers="full", features=("neck_mane", "tail_fan"))),
    "dodo": dict(name="Dodo", length_m=0.95, builder="dodo", geo=dict()),
}

QUADS = {
    "triceratops": dict(name="Triceratops", length_m=9.0, geo=dict(features=("frill_rim",))),
    "pachyrhinosaurus": dict(name="Pachyrhinosaurus", length_m=6.0, geo=dict(
        skull="pachyrhino", torso=(0.85, 0.9, 0.9), head_scale=0.92, features=("frill_spikes", "frill_rim"))),
    "stegosaurus": dict(name="Stegosaurus", length_m=9.0, geo=dict(
        skull="stego", torso=(0.8, 1.05, 1.0), front_len=0.78, front_thick=0.85, hind_len=1.4, hind_thick=0.95, neck_len=1.2,
        neck_thick=0.5, neck_raise=-0.25, head_scale=0.78, tail_len=1.2, tail_thick=0.85, tail_lift=0.25, pitch=-0.2,
        features=("plates", "thagomizer"))),
    "ankylosaurus": dict(name="Ankylosaurus", length_m=7.0, geo=dict(
        skull="anky", torso=(1.2, 0.72, 1.0), front_len=0.62, hind_len=0.68, front_thick=1.05, hind_thick=1.05, leg_x=1.25,
        neck_len=0.8, neck_thick=1.0, neck_raise=-0.15, tail_len=1.0, tail_thick=0.85, features=("osteoderms", "side_spikes",
                                                                                                      "tail_club"))),
    "moschops": dict(name="Moschops", length_m=2.7, geo=dict(
        skull="moschops", torso=(0.9, 1.0, 0.72), front_len=1.05, hind_len=0.85, neck_len=0.5, neck_thick=1.0, head_scale=0.88,
        tail_len=0.32, tail_thick=0.9, pitch=0.12)),
    "parasaurolophus": dict(name="Parasaurolophus", length_m=9.5, geo=dict(
        skull="hadro", torso=(0.72, 0.85, 1.0), front_len=1.05, front_thick=0.62, hind_len=1.55, hind_thick=0.85, neck_len=1.5,
        neck_thick=0.7, neck_raise=0.35, head_scale=1.1, tail_len=1.25, tail_thick=0.8, tail_lift=0.25, fingers=True)),
    "iguanodon": dict(name="Iguanodon", length_m=10.0, geo=dict(
        skull="iguanodon", torso=(0.8, 0.92, 1.05), front_len=1.15, front_thick=0.72, hind_len=1.45, hind_thick=0.92,
        neck_len=1.35, neck_thick=0.8, neck_raise=0.25, head_scale=1.15, tail_len=1.2, tail_thick=0.85, tail_lift=0.2,
        thumb_spike=True)),
}

SAUROPODS = {
    "brontosaurus": dict(name="Brontosaurus", length_m=22.0, geo=dict()),
    "diplodocus": dict(name="Diplodocus", length_m=26.0, geo=dict(
        torso=(0.82, 0.85, 1.05), neck_len=1.25, neck_thick=0.78, neck_raise=-0.55, head_scale=1.0, snout=1.35, leg_len=0.95,
        leg_thick=0.82, tail_len=1.4, tail_thick=0.72)),
}

import dinos_more as _more
BIPEDS.update(_more.BIPEDS)
QUADS.update(_more.QUADS)
SAUROPODS.update(_more.SAUROPODS)

ALL = {}
for _plan, _d in (("theropod", BIPEDS), ("quadruped", QUADS), ("sauropod", SAUROPODS)):
    for _k, _v in _d.items():
        _v["plan"] = _plan
        ALL[_k] = _v


def builder(sid):
    spec = ALL[sid]
    if spec.get("builder") == "dodo":
        from bird import Dodo
        return Dodo(spec["geo"])
    if spec["plan"] == "theropod":
        from theropod import Theropod
        return Theropod(spec["geo"])
    if spec["plan"] == "quadruped":
        from quadruped import Quadruped
        return Quadruped(spec["geo"])
    from sauropod import Sauropod
    return Sauropod(spec["geo"])

# ------------------------------------------------------------------ looks (colours, patterns, skin)
LOOK = {
    "tyrannosaurus": dict(pattern="stripes", scars=True),
    "triceratops": dict(pal=dict(dorsal="#5a4a30", flank="#8e7550", belly="#d7c39a", stripe="#3a2c1c", frill="#a8582a",
                                 accent="#c06a2c", horn="#7a6e58", beak="#4a4034", iris="#c89030"), pattern="bands", pattern_scale=1.3),
    "stegosaurus": dict(pal=dict(dorsal="#4f5a36", flank="#7f8a55", belly="#d3c99a", stripe="#2f3520", plate="#6a4a2a",
                                 accent="#b8572a", horn="#6e6252", beak="#585040"), pattern="spots", pattern_scale=0.8),
    "brontosaurus": dict(pal=dict(dorsal="#5a5248", flank="#8c8070", belly="#d0c4ae", stripe="#3e3830"), pattern="bands",
                         pattern_strength=0.35, pattern_scale=1.6, skin="smooth"),
    "diplodocus": dict(pal=dict(dorsal="#6a5a3a", flank="#a08858", belly="#e0d2a8", stripe="#4a3a22"), pattern="chevrons",
                       pattern_scale=1.4, skin="smooth"),
    "ankylosaurus": dict(pal=dict(dorsal="#5c4e36", flank="#8a7652", belly="#c9b88e", plate="#4a3e2c", accent="#9a7a4a",
                                  horn="#4c4234", beak="#585040"), pattern="mottled"),
    "parasaurolophus": dict(pal=dict(dorsal="#4a6a5a", flank="#7da08a", belly="#e1dcc0", stripe="#2c4034", frill="#c0542a",
                                     accent="#d06a30", beak="#5a5244"), pattern="stripes", round_pupil=True),
    "iguanodon": dict(pal=dict(dorsal="#5a5a3e", flank="#8f8e64", belly="#dcd6b4", stripe="#3a3a26", beak="#5c5444",
                               horn="#6e665a"), pattern="bands", round_pupil=True),
    "gallimimus": dict(pal=dict(dorsal="#6e5c44", flank="#a8916a", belly="#e8dcc0", feather="#8c7656", feather2="#4a3a28",
                                stripe="#3c3020", beak="#5a4e3c"), pattern="stripes", feathers="partial", round_pupil=True),
    "carnotaurus": dict(pal=dict(dorsal="#5a2e1e", flank="#9a4e2e", belly="#d8b894", stripe="#2e1810", accent="#c0442a",
                                 horn="#4a3a2a", iris="#e8c030"), pattern="stripes", pattern2="spots"),
    "allosaurus": dict(pal=dict(dorsal="#5a4430", flank="#9a7448", belly="#dcc49a", stripe="#2e2216", accent="#b4542a"),
                       pattern="stripes", scars=False),
    "giganotosaurus": dict(pal=dict(dorsal="#3e4036", flank="#6e7058", belly="#c8c0a0", stripe="#22241c", accent="#8a2a20",
                                    iris="#e04020"), pattern="stripes", pattern2="mottled"),
    "spinosaurus": dict(pal=dict(dorsal="#4a4438", flank="#7a6e54", belly="#d0c4a0", stripe="#2a261e", sail="#b04a2a",
                                 accent="#d0703a"), pattern="spots", pattern_scale=1.2),
    "baryonyx": dict(pal=dict(dorsal="#3c5048", flank="#6a8476", belly="#d4d4b8", stripe="#24302a", accent="#a8582c"),
                     pattern="stripes"),
    "megalosaurus": dict(pal=dict(dorsal="#2c2c30", flank="#4e4e58", belly="#a8a49a", stripe="#18181c", accent="#6a2a3a",
                                  iris="#e05a1a"), pattern="stripes"),
    "therizinosaurus": dict(pal=dict(dorsal="#5a4c38", flank="#8a7858", belly="#d8ccb0", feather="#7a6a52", feather2="#3a3024",
                                     accent="#b86a3a", claw="#3a3228", claw_base="#d8ccb4", beak="#4e463a"), pattern="stripes",
                            feathers="full", round_pupil=True),
    "utahraptor": dict(pal=dict(dorsal="#5a3e2c", flank="#9a6e4c", belly="#e0cca8", feather="#6a4a36", feather2="#2a1c14",
                                stripe="#2a1a10", accent="#c0502a"), pattern="stripes", feathers="full"),
    "deinonychus": dict(pal=dict(dorsal="#2a3440", flank="#5a6a7a", belly="#d0d4d8", feather="#3a4a5a", feather2="#121820",
                                 stripe="#10141a", accent="#4a8ac0"), pattern="stripes", feathers="full"),
    "dilophosaurus": dict(pal=dict(dorsal="#4a5a34", flank="#7e9050", belly="#dcd6a8", stripe="#2c3820", frill="#d06a2a",
                                   accent="#e0a030"), pattern="spots"),
    "troodon": dict(pal=dict(dorsal="#4a4a32", flank="#807e56", belly="#d8d4b8", feather="#6a6a4a", feather2="#2a2a1a",
                             accent="#b0a040", iris="#f0d020"), pattern="stripes", feathers="full"),
    "compsognathus": dict(pal=dict(dorsal="#4a5a2e", flank="#7a9048", belly="#dcdca8", stripe="#2a3418", accent="#c0a030"),
                          pattern="stripes"),
    "oviraptor": dict(pal=dict(dorsal="#3a4450", flank="#6a7a8a", belly="#e0e0d8", feather="#5a6a7a", feather2="#222a34",
                               frill="#b4583a", accent="#b4583a", beak="#4a443c"), pattern="stripes", feathers="full",
                      round_pupil=True),
    "pachycephalosaurus": dict(pal=dict(dorsal="#5a4a3a", flank="#8e7a62", belly="#d8c8ac", horn="#6e6050", accent="#b06a3a"),
                               pattern="bands"),
    "pachyrhinosaurus": dict(pal=dict(dorsal="#5a5040", flank="#8a8066", belly="#d4c8aa", frill="#8a5a3a", accent="#c07a3a",
                                      horn="#7a6e58", beak="#4c4438"), pattern="mottled"),
    "moschops": dict(pal=dict(dorsal="#6a5a4a", flank="#9a8872", belly="#d8ccb8", stripe="#4a3e32"), pattern="blotch",
                     skin="smooth", round_pupil=True),
    "yutyrannus": dict(pal=dict(dorsal="#5a4c3a", flank="#9a8a6a", belly="#e4dcc8", feather="#8a7a62", feather2="#3a3226",
                                accent="#a8683a"), pattern="stripes", pattern_strength=0.45, feathers="full"),
    "dodo": dict(pal=dict(dorsal="#6a6570", flank="#8c8792", belly="#c9c2b8", feather="#8e8994", feather2="#56525c",
                          stripe="#4a4650", beak="#ddd3a4", beak2="#b2a66e", lid="#a3a09a", accent="#8e8a94", iris="#d4b468",
                          iris_dark="#8a6a30", leg="#c9a43c", foot="#b08a30", plume="#e6e0d2", plume2="#a8a098", throat="#9c96a0",
                          claw="#3a3228", claw_base="#6a5a3a"),
                 pattern="none", feathers="full", round_pupil=True, mask=0.0, brow=0.0, hood=0.2, lid_mix=0.9, feather_size=0.55,
                 scale_size=0.45),
}

# attack animation family
ATTACK = {"tyrannosaurus": "shake", "giganotosaurus": "shake", "yutyrannus": "shake", "allosaurus": "shake", "megalosaurus": "shake",
          "carnotaurus": "shake", "spinosaurus": "shake", "baryonyx": "shake", "utahraptor": "pounce", "deinonychus": "pounce",
          "troodon": "pounce", "therizinosaurus": "slash", "pachycephalosaurus": "headbutt", "dodo": "peck", "oviraptor": "peck",
          "gallimimus": "peck", "compsognathus": "peck", "dilophosaurus": "spit", "triceratops": "horn", "pachyrhinosaurus": "horn",
          "stegosaurus": "tail", "ankylosaurus": "tail", "moschops": "bite", "parasaurolophus": "rear", "iguanodon": "rear",
          "brontosaurus": "stomp", "diplodocus": "tail"}

RIDEABLE = {"tyrannosaurus", "giganotosaurus", "yutyrannus", "allosaurus", "megalosaurus", "carnotaurus", "spinosaurus", "baryonyx",
            "utahraptor", "deinonychus", "therizinosaurus", "pachycephalosaurus", "gallimimus", "triceratops", "pachyrhinosaurus",
            "stegosaurus", "ankylosaurus", "parasaurolophus", "iguanodon", "brontosaurus", "diplodocus", "moschops"}

LOOK.update(_more.LOOK)
ATTACK.update(_more.ATTACK)
RIDEABLE |= _more.RIDEABLE
