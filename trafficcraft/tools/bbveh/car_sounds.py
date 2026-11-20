"""Per-car engine, start-up and backfire sounds (synthesised; each car's engine layout and exhaust gives it its own voice).

usage: python3 car_sounds.py   -> writes assets/trafficcraft/sounds/engine/<id>_*.ogg, backfire/<id>_*.ogg and updates sounds.json
"""
import os, sys, json, copy
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'vehgen'))
import sounds as S

RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'trafficcraft')

V8X = [1.0, 0.55, 1.0, 0.72, 0.92, 0.5, 1.0, 0.68]          # cross-plane V8 burble
FLAT8 = [1.0, 0.95] * 4                                     # flat-plane V8 (F40) - even, raspy
BOXER4 = [1.0, 0.62, 0.98, 0.7]                             # unequal-length boxer rumble
V12 = [1.0, 0.97, 0.99, 0.96, 1.0, 0.98] * 2

# id: (base, overrides, pitch, backfire pitch, backfire style)
PROFILES = {
    'toyota_supra_mk4': ('i6_twinturbo', {}, 1.0, 1.0, 'turbo'),
    'nissan_gtr_r35': ('i6_twinturbo', dict(pattern=[1.0, 0.9, 0.97, 0.88, 1.0, 0.92], turbo=3700, noise=0.15), 0.94, 0.95, 'turbo'),
    'bmw_m3_2026': ('i6_twinturbo', dict(turbo=3400, noise=0.14, lp=8000), 1.06, 1.05, 'turbo'),
    'ford_mustang_1964': ('v8_sport', dict(idle=700, noise=0.22, lp=5000), 0.86, 0.85, 'v8'),
    'chevrolet_camaro_1966': ('v8_sport', dict(idle=760, noise=0.2, lp=5400), 0.9, 0.88, 'v8'),
    'dodge_challenger_1969': ('v8_sport', dict(idle=680, noise=0.24, lp=4600, gain=1.0), 0.76, 0.78, 'v8'),
    'chevrolet_corvette_1953': ('i6_twinturbo', dict(idle=600, drive=2800, turbo=0, noise=0.18, lp=3500), 0.8, 0.9, 'old'),
    'porsche_911_1963': ('i6_twinturbo', dict(pattern=[1.0, 0.8, 0.98, 0.82, 1.0, 0.8], idle=900, drive=4200, turbo=0, noise=0.18, lp=7500), 1.15, 1.1, 'old'),
    'bmw_m3_1985': ('i4_turbo', dict(idle=950, drive=4800, turbo=0, noise=0.14, lp=8500), 1.1, 1.1, 'na'),
    'mercedes_sclass_1972': ('v8_sport', dict(pattern=[1.0, 0.8, 0.98, 0.85, 0.96, 0.8, 1.0, 0.84], noise=0.08, gain=0.75, lp=3800), 0.95, 0.9, 'quiet'),
    'audi_rs6_2002': ('v8_sport', dict(turbo=3000, noise=0.17, lp=6000), 0.9, 0.92, 'turbo'),
    'honda_civic_1972': ('i4_petrol', dict(idle=850, noise=0.12), 1.25, 1.25, 'small'),
    'honda_accord_1976': ('i4_petrol', dict(idle=820), 1.1, 1.2, 'small'),
    'toyota_corolla_1966': ('i4_petrol', dict(idle=900, noise=0.14), 1.3, 1.25, 'small'),
    'toyota_camry_1982': ('i4_petrol', dict(noise=0.08, gain=0.8), 1.05, 1.15, 'quiet'),
    'mazda_mx5_1989': ('i4_turbo', dict(idle=900, drive=4200, turbo=0, lp=7500), 1.15, 1.15, 'na'),
    'subaru_wrx_sti_1992': ('i4_turbo', dict(pattern=BOXER4, turbo=2900, noise=0.16), 0.95, 1.0, 'rally'),
    'mitsubishi_lancer_evo_1992': ('i4_turbo', dict(turbo=3100, noise=0.15), 1.05, 1.05, 'rally'),
    'volkswagen_golf_gti_1974': ('i4_petrol', dict(idle=880, drive=3200, noise=0.13), 1.15, 1.2, 'na'),
    'jeep_wrangler_1986': ('v6_cruiser', dict(noise=0.16, lp=3800), 0.85, 0.9, 'old'),
    'land_rover_range_rover_1970': ('v8_sport', dict(noise=0.14, gain=0.85, lp=4500, idle=700), 0.95, 0.92, 'v8'),
    'hyundai_elantra_1990': ('i4_petrol', dict(noise=0.1), 1.12, 1.2, 'quiet'),
    'kia_stinger_2017': ('v6_cruiser', dict(turbo=3200, noise=0.14, lp=6000, idle=700, drive=3000), 1.0, 1.0, 'turbo'),
    'ferrari_f40_1987': ('v8_sport', dict(pattern=FLAT8, turbo=3400, noise=0.22, lp=8000, idle=1000, drive=4200), 1.2, 1.1, 'race'),
    'lamborghini_aventador_2011': ('i6_twinturbo', dict(cyl=12, pattern=V12, idle=1000, drive=5000, turbo=0, noise=0.18, lp=10000, gain=1.0), 1.35, 0.95, 'race'),
    'tesla_model_s_2012': None,     # electric: inverter whine instead of combustion
}


def pitched(E, p):
    E = copy.deepcopy(E)
    E['res'] = [(f * p, b * p, a) for f, b, a in E['res']]
    E['starter'] = E['starter'] * (0.9 + 0.2 * p)
    return E


def resample(y, k):
    """Pitch-shift by resampling (k > 1 = higher)."""
    n = int(len(y) / k)
    return np.interp(np.linspace(0, len(y) - 1, n), np.arange(len(y)), y)


def ev(kind):
    SR = S.SR
    if kind == 'start':
        t = np.arange(int(1.2 * SR)) / SR
        y = np.zeros_like(t)
        for f, t0 in ((880, 0.0), (1320, 0.18)):
            m = t >= t0
            tt = t[m] - t0
            y[m] += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 5) * 0.6
        return y
    t = np.arange(int(2.0 * SR)) / SR
    f = 320 if kind == 'idle' else 1150
    y = sum(np.sin(2 * np.pi * f * h * t) / h for h in (1, 2, 3)) * (0.08 if kind == 'idle' else 0.25)
    y += np.random.default_rng(3).standard_normal(len(t)) * (0.01 if kind == 'idle' else 0.04)
    return y


def main():
    sdir = os.path.join(RES, 'sounds')
    sj_path = os.path.join(RES, 'sounds.json')
    sj = json.load(open(sj_path))
    for cid, prof in PROFILES.items():
        if prof is None:
            for k in ('idle', 'drive', 'start'):
                S.write_ogg(ev(k), f"{sdir}/engine/{cid}_{k}.ogg", rms=0.06 if k == 'idle' else 0.11)
                sj[f'engine.{cid}.{k}'] = {'sounds': [{'name': f'trafficcraft:engine/{cid}_{k}'}]}
            print(cid, 'electric')
            continue
        base, over, p, bp, style = prof
        E = dict(S.ENGINES[base])
        E.update(over)
        E = pitched(E, p)
        S.write_ogg(S.engine_loop(E, E['idle'], 2.0, 0.25), f"{sdir}/engine/{cid}_idle.ogg", rms=0.13)
        S.write_ogg(S.engine_loop(E, E['drive'], 2.0, 0.85), f"{sdir}/engine/{cid}_drive.ogg", rms=0.15)
        S.write_ogg(S.startup(E), f"{sdir}/engine/{cid}_start.ogg", rms=0.14)
        for k in ('idle', 'drive', 'start'):
            sj[f'engine.{cid}.{k}'] = {'sounds': [{'name': f'trafficcraft:engine/{cid}_{k}', 'stream': False}]}
        # backfires: every car has its own set; each event picks one of several different variations
        seed0 = sum(map(ord, cid))
        for k in ('pop', 'crackle', 'bang'):
            files = []
            nv = 4 if k != 'bang' else 2
            for v in range(nv):
                y = S.backfire(k, seed0 + v * 13 + len(k))
                if style in ('v8', 'old'):
                    y = resample(y, bp * 0.9)
                elif style in ('race', 'rally'):
                    y = resample(y, bp * 1.1)
                else:
                    y = resample(y, bp)
                S.write_ogg(y, f"{sdir}/backfire/{cid}_{k}{v}.ogg", rms=0.2 if k != 'bang' else 0.26, peak=0.99)
                files.append({'name': f'trafficcraft:backfire/{cid}_{k}{v}'})
            sj[f'backfire.{cid}.{k}'] = {'sounds': files}
        print(cid, base, 'pitch', p)
    # tyre screech for drifting
    SR = S.SR
    t = np.arange(int(1.5 * SR)) / SR
    from scipy import signal
    n = np.random.default_rng(9).standard_normal(len(t))
    b, a = signal.butter(2, [1500 / (SR / 2), 3200 / (SR / 2)], 'band')
    sq = signal.lfilter(b, a, n) * (1 + 0.4 * np.sin(2 * np.pi * 7 * t))
    sq += np.sin(2 * np.pi * (1900 + 120 * np.sin(2 * np.pi * 3 * t)) * t) * 0.3
    S.write_ogg(sq, f"{sdir}/misc/skid.ogg", rms=0.14)
    sj['misc.skid'] = {'sounds': [{'name': 'trafficcraft:misc/skid'}]}
    json.dump(sj, open(sj_path, 'w'), indent=1)
    print(len(sj), 'sound events')


if __name__ == '__main__':
    main()
