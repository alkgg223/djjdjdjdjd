"""Procedurally synthesised sounds (mono OGG Vorbis), with seamless loops for engines."""
import numpy as np, subprocess, os, json, tempfile, wave
SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'vehicleworks')
rng = np.random.default_rng(7)

def noise(n): return rng.standard_normal(n)

def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR); y = np.empty_like(x); s = 0.0
    for i in range(len(x)): s = (1 - a) * x[i] + a * s; y[i] = s
    return y

def lp_fast(x, cutoff, passes=2):
    # FFT brickwall-ish low pass (fast)
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / (1 + (f / cutoff) ** (2 * passes)); return np.fft.irfft(X, len(x))

def bp_fast(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= (1 / (1 + (f / hi) ** 4)) * (1 / (1 + (lo / np.maximum(f, 1)) ** 4)); return np.fft.irfft(X, len(x))

def env(n, a, r):
    e = np.ones(n); ai = int(a * SR); ri = int(r * SR)
    if ai: e[:ai] = np.linspace(0, 1, ai)
    if ri: e[-ri:] *= np.linspace(1, 0, ri) ** 2
    return e

def loopify(x, fade=0.25):
    f = int(fade * SR); head = x[:f].copy(); tail = x[-f:]
    w = np.linspace(0, 1, f)
    out = x[f:].copy(); out[-f:] = tail * (1 - w) + head * w
    return out

def norm(x, rms=0.18, peak=0.95):
    x = x - np.mean(x); r = np.sqrt(np.mean(x ** 2)) + 1e-9; x = x * (rms / r)
    p = np.max(np.abs(x)); 
    if p > peak: x *= peak / p
    return x

def write(name, x):
    path = os.path.join(OUT, 'sounds', name + '.ogg'); os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf: tmp = tf.name
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(tmp, 'wb') as wf: wf.setnchannels(1); wf.setsampwidth(2); wf.setframerate(SR); wf.writeframes(data.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-ac', '1', '-map_metadata', '-1', '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.remove(tmp)

t = lambda d: np.arange(int(d * SR)) / SR
# piston: firing pulses at 45 Hz with harmonics, exhaust noise bursts
d = 2.25; tt = t(d)
fire = 45.0
pulses = (np.sin(2 * np.pi * fire * tt) > 0.6).astype(float)
body = sum(np.sin(2 * np.pi * fire * k * tt + k) / k for k in range(1, 8))
x = 0.6 * body + 0.9 * lp_fast(noise(len(tt)) * pulses, 900) + 0.15 * lp_fast(noise(len(tt)), 300)
write('engine/piston', norm(loopify(x)))
# turbine whine: tonal 1.6k/2.4k + hiss
d = 2.25; tt = t(d)
x = 0.35 * np.sin(2 * np.pi * 1600 * tt) + 0.2 * np.sin(2 * np.pi * 2410 * tt) + 0.6 * bp_fast(noise(len(tt)), 300, 6000) + 0.4 * lp_fast(noise(len(tt)), 200)
write('engine/turbine', norm(loopify(x), 0.16))
# jet roar: brown-ish broadband with crackle
d = 2.25; tt = t(d)
x = 1.2 * lp_fast(noise(len(tt)), 500) + 0.5 * bp_fast(noise(len(tt)), 800, 5000) + 0.25 * np.sin(2 * np.pi * 70 * tt)
crack = (rng.random(len(tt)) > 0.9993) * rng.standard_normal(len(tt)) * 4
x += lp_fast(crack, 3000)
write('engine/jet', norm(loopify(x), 0.2))
# rotor: blade passage thump ~ 11 Hz
d = 2.25; tt = t(d)
bp = 11.0
th = np.exp(-((tt * bp) % 1.0) * 9)
x = lp_fast(noise(len(tt)), 260) * th * 2.5 + 0.4 * np.sin(2 * np.pi * 55 * tt) * th + 0.2 * lp_fast(noise(len(tt)), 900)
write('engine/rotor', norm(loopify(x), 0.2))
# electric hum
d = 2.0; tt = t(d)
x = 0.5 * np.sin(2 * np.pi * 120 * tt) + 0.3 * np.sin(2 * np.pi * 240 * tt) + 0.25 * np.sin(2 * np.pi * 3100 * tt) + 0.08 * bp_fast(noise(len(tt)), 1000, 4000)
write('engine/electric', norm(loopify(x), 0.12))
# starter: rising whine
d = 2.2; tt = t(d)
f = 200 + 1500 * (tt / d) ** 1.5
x = (0.5 * np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.4 * bp_fast(noise(len(tt)), 400, 3000)) * env(len(tt), 0.05, 0.4)
write('engine/start', norm(x, 0.17))
# wind
d = 3.0; tt = t(d)
mod = 0.7 + 0.3 * np.sin(2 * np.pi * 0.5 * tt)
x = bp_fast(noise(len(tt)), 200, 2200) * mod
write('vehicle/wind', norm(loopify(x, 0.5), 0.15))
# stall warning beep
tt = t(0.22); x = np.sign(np.sin(2 * np.pi * 900 * tt)) * 0.5 * env(len(tt), 0.005, 0.03)
write('vehicle/stall_warning', norm(lp_fast(x, 4000), 0.2))
# gear motor
tt = t(1.2); f = 300 + 80 * np.sin(2 * np.pi * 3 * tt)
x = (0.5 * np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * bp_fast(noise(len(tt)), 200, 2000)) * env(len(tt), 0.05, 0.2)
x[-int(0.1 * SR):] += lp_fast(noise(int(0.1 * SR)), 700) * np.exp(-np.linspace(0, 8, int(0.1 * SR)))
write('vehicle/gear', norm(x, 0.15))
# assemble: clunk + whoosh
tt = t(0.8)
x = lp_fast(noise(len(tt)), 400) * np.exp(-tt * 18) * 2 + bp_fast(noise(len(tt)), 500, 4000) * np.exp(-((tt - 0.35) / 0.15) ** 2)
write('vehicle/assemble', norm(x, 0.18))
# machine gun shot
tt = t(0.12)
x = (lp_fast(noise(len(tt)), 2500) * np.exp(-tt * 55) + 0.6 * np.sin(2 * np.pi * 90 * tt) * np.exp(-tt * 40))
write('weapon/machine_gun', norm(x, 0.25))
# cannon boom
tt = t(1.0)
x = lp_fast(noise(len(tt)), 350) * np.exp(-tt * 6) * 1.5 + np.sin(2 * np.pi * 45 * tt) * np.exp(-tt * 5) + lp_fast(noise(len(tt)), 3000) * np.exp(-tt * 60)
write('weapon/cannon', norm(x, 0.25))
# missile launch whoosh
tt = t(1.4)
x = bp_fast(noise(len(tt)), 300, 3500) * env(len(tt), 0.02, 0.9) * (1 + 0.5 * np.sin(2 * np.pi * 23 * tt)) + lp_fast(noise(len(tt)), 3000) * np.exp(-tt * 30)
write('weapon/missile', norm(x, 0.2))
# bomb release clunk
tt = t(0.5); x = lp_fast(noise(len(tt)), 600) * np.exp(-tt * 25) + 0.3 * np.sin(2 * np.pi * 180 * tt) * np.exp(-tt * 20)
write('weapon/bomb', norm(x, 0.2))
# reload clack
tt = t(0.45); x = np.zeros(len(tt))
for s in (0.02, 0.22):
    i = int(s * SR); n = int(0.06 * SR); x[i:i + n] += lp_fast(noise(n), 3000) * np.exp(-np.linspace(0, 10, n))
write('weapon/reload', norm(x, 0.18))
# arm switch
tt = t(0.12); x = lp_fast(noise(len(tt)), 5000) * np.exp(-tt * 80) + 0.4 * np.sin(2 * np.pi * 1800 * tt) * np.exp(-tt * 60)
write('weapon/arm', norm(x, 0.18))
# ui click
tt = t(0.06); x = np.sin(2 * np.pi * 2400 * tt) * np.exp(-tt * 120)
write('ui/click', norm(x, 0.15))

# ---- cockpit alarms (6 different sounds for 12 warnings)
# stick shaker (stall): low rattle
tt = t(0.8); x = np.sign(np.sin(2 * np.pi * 28 * tt)) * lp_fast(noise(len(tt)), 900) * 0.8 + 0.3 * np.sin(2 * np.pi * 56 * tt)
write('alarm/stall', norm(x * env(len(tt), 0.01, 0.05), 0.24))
# clacker (overspeed): fast mechanical clicks
tt = t(1.0); x = np.zeros(len(tt))
for k in range(12):
    i0 = int(k / 12 * SR); n = int(0.03 * SR); x[i0:i0 + n] += lp_fast(noise(n), 6000) * np.exp(-np.arange(n) / SR * 160)
write('alarm/overspeed', norm(x, 0.22))
# single chime (caution: low fuel, low oil pressure)
tt = t(1.0); x = (np.sin(2 * np.pi * 880 * tt) + 0.5 * np.sin(2 * np.pi * 1320 * tt) + 0.25 * np.sin(2 * np.pi * 2640 * tt)) * np.exp(-tt * 3.5)
write('alarm/caution', norm(x * env(len(tt), 0.004, 0.05), 0.2))
# fire bell (engine failure / fire): rapid bell strikes
tt = t(1.0); x = np.zeros(len(tt))
for k in range(16):
    i0 = int(k / 16 * SR); n = len(tt) - i0; tk = np.arange(n) / SR
    x[i0:] += (np.sin(2 * np.pi * 1050 * tk) + 0.6 * np.sin(2 * np.pi * 2730 * tk)) * np.exp(-tk * 25)
write('alarm/fire', norm(x, 0.22))
# whoop (ground proximity: gear, terrain, pull up, low altitude)
tt = t(0.9); f = np.where(tt < 0.55, 380 + 900 * (tt / 0.55) ** 1.5, 0)
x = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * (tt < 0.55) * 0.6
write('alarm/whoop', norm(lp_fast(x, 3500) * env(len(tt), 0.02, 0.3), 0.22))
# triple chime (systems: hydraulic, electrical)
tt = t(0.9); x = np.zeros(len(tt))
for k in range(3):
    i0 = int(k * 0.22 * SR); n = int(0.18 * SR); tk = np.arange(n) / SR
    x[i0:i0 + n] += (np.sin(2 * np.pi * 1250 * tk) + 0.4 * np.sin(2 * np.pi * 2500 * tk)) * np.exp(-tk * 12)
write('alarm/warning', norm(x, 0.2))

# ---- ground roll rumble (loop), touchdown thump, hard landing crash
d = 2.0; tt = t(d)
x = lp_fast(noise(len(tt)), 180) * 1.0 + 0.35 * bp_fast(noise(len(tt)), 300, 1200) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * tt) ** 2)
write('vehicle/rumble', norm(loopify(x, 0.4), 0.2))
tt = t(0.7)
thump = np.sin(2 * np.pi * (55 + 40 * np.exp(-tt * 20)) * tt) * np.exp(-tt * 9)
chirp = bp_fast(noise(len(tt)), 1500, 5000) * np.exp(-tt * 14) * 0.5
write('vehicle/touchdown', norm(thump + chirp, 0.22))
tt = t(1.4)
boom = np.sin(2 * np.pi * (40 + 70 * np.exp(-tt * 15)) * tt) * np.exp(-tt * 4)
crunch = bp_fast(noise(len(tt)), 600, 6000) * np.exp(-tt * 5) * 0.9
rattle = np.zeros(len(tt))
for k in range(18):
    i0 = int((0.05 + k * 0.06 + 0.02 * np.sin(k * 7)) * SR); n = int(0.04 * SR)
    if i0 + n < len(tt): rattle[i0:i0 + n] += bp_fast(noise(n), 2000, 7000) * np.exp(-np.arange(n) / SR * 90) * (1 - k / 18)
write('vehicle/hard_landing', norm(boom + crunch + 0.7 * rattle, 0.26, 0.98))

names = {'engine.piston': 'engine/piston', 'engine.turbine': 'engine/turbine', 'engine.jet': 'engine/jet', 'engine.rotor': 'engine/rotor',
         'engine.electric': 'engine/electric', 'engine.start': 'engine/start', 'vehicle.wind': 'vehicle/wind', 'vehicle.stall_warning': 'vehicle/stall_warning',
         'vehicle.gear': 'vehicle/gear', 'vehicle.assemble': 'vehicle/assemble', 'weapon.machine_gun': 'weapon/machine_gun', 'weapon.cannon': 'weapon/cannon',
         'weapon.missile': 'weapon/missile', 'weapon.bomb': 'weapon/bomb', 'weapon.reload': 'weapon/reload', 'weapon.arm': 'weapon/arm', 'ui.click': 'ui/click',
         'alarm.stall': 'alarm/stall', 'alarm.overspeed': 'alarm/overspeed', 'alarm.caution': 'alarm/caution', 'alarm.fire': 'alarm/fire',
         'alarm.whoop': 'alarm/whoop', 'alarm.warning': 'alarm/warning',
         'vehicle.rumble': 'vehicle/rumble', 'vehicle.touchdown': 'vehicle/touchdown', 'vehicle.hard_landing': 'vehicle/hard_landing'}
sj = {k: {"sounds": [{"name": "vehicleworks:" + v, "attenuation_distance": 64 if k.startswith('engine') or k.startswith('weapon') else 16}],
          "subtitle": "subtitles.vehicleworks." + k} for k, v in names.items()}
with open(os.path.join(OUT, 'sounds.json'), 'w') as f: json.dump(sj, f, indent=2)
print('sounds ok')
