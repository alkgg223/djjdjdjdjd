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

# tyre screech (touchdown / skid)
tt = t(0.75)
sq = np.sin(2 * np.pi * (1650 + 120 * np.sin(2 * np.pi * 11 * tt)) * tt) * 0.5 + bp_fast(noise(len(tt)), 1800, 5200) * 0.8
write('vehicle/tire_screech', norm(sq * env(len(tt), 0.01, 0.35) * np.exp(-tt * 1.8), 0.22))
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


# =============================================================== 1.2 sound overhaul (all synthesised here: original sounds)
def tone(f, tt, ph=0): return np.sin(2 * np.pi * f * tt + ph)
def sweep(f0, f1, tt, curve=1.0):
    d = tt[-1] if len(tt) else 1; f = f0 + (f1 - f0) * (tt / d) ** curve
    return np.sin(2 * np.pi * np.cumsum(f) / SR)
def click(n_ms, f_hi=6000, decay=200):
    n = int(n_ms / 1000 * SR); tk = np.arange(n) / SR
    return lp_fast(noise(n), f_hi) * np.exp(-tk * decay)
def place(x, y, at):
    i = int(at * SR); n = min(len(y), len(x) - i)
    if n > 0: x[i:i + n] += y[:n]
    return x
# ---- engines
d = 2.25; tt = t(d)
bpf = 88.0  # propeller blade pass
prop = sum(np.sign(tone(bpf * k, tt)) * (0.5 / k) for k in (1, 2, 3)) * 0.4 + sum(tone(bpf * k, tt, k) / k for k in range(1, 6))
whine = 0.25 * tone(1240, tt) + 0.12 * tone(2480, tt) + 0.35 * bp_fast(noise(len(tt)), 600, 5000)
x = 0.7 * lp_fast(prop, 1800) + whine + 0.25 * lp_fast(noise(len(tt)), 250)
write('engine/turboprop', norm(loopify(x), 0.18))
# turbofan: fan "buzz-saw" (many shaft-order tones), high whine, smooth roar
fan = 0
for k in range(1, 18): fan = fan + tone(53.3 * k, tt, k * 0.7) * (0.6 / (1 + 0.15 * k)) * (1 + 0.3 * rng.random())
x = 0.35 * lp_fast(fan, 3000) + 0.22 * tone(2900, tt) + 0.1 * tone(5800, tt) + 1.0 * lp_fast(noise(len(tt)), 700) + 0.45 * bp_fast(noise(len(tt)), 1500, 7000)
write('engine/turbofan', norm(loopify(x), 0.19))
# helicopter turboshaft: high whine + gearbox hum (blade slap is the rotor sound)
x = 0.3 * tone(3150, tt) + 0.15 * tone(4720, tt) + 0.3 * tone(196, tt) + 0.15 * tone(392, tt) + 0.5 * bp_fast(noise(len(tt)), 2500, 9000) + 0.3 * lp_fast(noise(len(tt)), 300)
write('engine/heli_turbine', norm(loopify(x), 0.15))
# rough running overlay (misfires / damaged engine): random coughs and rattles
x = np.zeros(len(tt))
for k in range(26):
    at = rng.random() * (d - 0.2); n = int((0.03 + 0.06 * rng.random()) * SR); tk = np.arange(n) / SR
    x = place(x, lp_fast(noise(n), 900 + 1500 * rng.random()) * np.exp(-tk * 30) * (0.5 + rng.random()), at)
x += 0.25 * bp_fast(noise(len(tt)), 2000, 6000) * (rng.random(len(tt)) > 0.97)
write('engine/rough', norm(loopify(x, 0.2), 0.16))
# light-off: whoomp + roar building up
tt = t(1.6)
x = tone(38 + 30 * np.exp(-tt * 6), tt) * np.exp(-tt * 3) * 1.4 + lp_fast(noise(len(tt)), 500) * env(len(tt), 0.05, 0.6) * (1 - np.exp(-tt * 4)) + 0.3 * bp_fast(noise(len(tt)), 800, 4000) * np.exp(-tt * 2)
write('engine/light_off', norm(x, 0.2))
# piston catch: coughs, a backfire, then the engine firing faster and faster
tt = t(2.0); x = np.zeros(len(tt))
for at in (0.0, 0.25, 0.42): x = place(x, lp_fast(noise(int(0.08 * SR)), 700) * np.exp(-np.arange(int(0.08 * SR)) / SR * 30) * 1.4, at)
x = place(x, (lp_fast(noise(int(0.12 * SR)), 2500) + tone(70, t(0.12))) * np.exp(-np.arange(int(0.12 * SR)) / SR * 25) * 2.2, 0.55)
tf = t(1.3); fr = 12 + 33 * (tf / 1.3) ** 0.7
pulses = (np.sin(2 * np.pi * np.cumsum(fr) / SR) > 0.55).astype(float)
x = place(x, lp_fast(noise(len(tf)) * pulses, 900) * 1.2 + 0.4 * np.sin(2 * np.pi * np.cumsum(fr) / SR), 0.7)
write('engine/piston_catch', norm(x * env(len(tt), 0.005, 0.2), 0.2))
# piston starter: electric starter whine modulated by compression strokes (rur-rur-rur)
d = 2.0; tt = t(d)
comp = 0.55 + 0.45 * np.sin(2 * np.pi * 4.5 * tt) ** 2
x = (0.45 * tone(310, tt) + 0.25 * tone(620, tt) + 0.5 * bp_fast(noise(len(tt)), 200, 1800)) * comp + 0.4 * lp_fast(noise(len(tt)), 150) * comp
write('engine/starter_piston', norm(loopify(x), 0.16))
# turbine air / electric starter: steady whine with hiss (pitch follows N2 in game)
x = 0.35 * tone(1760, tt) + 0.18 * tone(2640, tt) + 0.5 * bp_fast(noise(len(tt)), 900, 6000) + 0.15 * lp_fast(noise(len(tt)), 300)
write('engine/starter_turbine', norm(loopify(x), 0.15))
# igniters: sharp snaps ~2 per second
x = np.zeros(len(tt))
for k in range(4): x = place(x, click(12, 9000, 380) * 1.5 + 0.3 * tone(3200, t(0.012)) * np.exp(-np.arange(int(0.012 * SR)) / SR * 300), 0.1 + k * 0.5)
write('engine/ignition', norm(x, 0.12, 0.98))
# spool down: whine falling, roar fading
tt = t(4.0)
x = (0.3 * sweep(2600, 300, tt, 0.6) + 0.4 * bp_fast(noise(len(tt)), 400, 4000)) * np.exp(-tt * 0.8) + 0.4 * lp_fast(noise(len(tt)), 400) * np.exp(-tt * 1.4)
write('engine/spool_down', norm(x * env(len(tt), 0.02, 0.5), 0.15))
# ---- cockpit
tt = t(0.12); x = np.zeros(len(tt)); x = place(x, click(6, 9000, 500), 0); x = place(x, click(8, 6000, 350) * 0.8, 0.012)
write('cockpit/toggle', norm(x, 0.16, 0.98))
tt = t(0.35); x = tone(85, tt) * np.exp(-tt * 28) * 1.6 + lp_fast(noise(len(tt)), 900) * np.exp(-tt * 40); x = place(x, click(10, 7000, 300) * 0.9, 0.005)
write('cockpit/relay', norm(x, 0.2, 0.98))
tt = t(0.12); x = lp_fast(noise(len(tt)), 3000) * np.exp(-tt * 120) + 0.3 * tone(900, tt) * np.exp(-tt * 90); x = place(x, click(5, 5000, 400) * 0.6, 0.05)
write('cockpit/button', norm(x, 0.15, 0.98))
tt = t(0.08); x = place(0.25 * tone(2400, tt) * np.exp(-tt * 200), click(5, 8000, 600), 0)
write('cockpit/knob', norm(x, 0.13, 0.98))
tt = t(0.3); x = np.zeros(len(tt)); x = place(x, lp_fast(noise(int(0.04 * SR)), 2500) * np.exp(-np.arange(int(0.04 * SR)) / SR * 90), 0); x = place(x, click(8, 9000, 450), 0.15)
write('cockpit/guard', norm(x, 0.15, 0.98))
tt = t(0.5); x = np.zeros(len(tt))
for k in range(5): x = place(x, click(6, 6000, 400) * 0.6, 0.02 + k * 0.045)
x = place(x, tone(120, t(0.2)) * np.exp(-np.arange(int(0.2 * SR)) / SR * 22) * 1.2, 0.27)
x = place(x, click(20, 3000, 120), 0.27)
write('cockpit/lever', norm(x, 0.17, 0.98))
tt = t(3.2)
fanup = bp_fast(noise(len(tt)), 300, 3000) * (1 - np.exp(-tt * 1.2)) * 0.6 + 0.2 * sweep(150, 900, tt, 0.5) * (1 - np.exp(-tt * 2))
for at, f in ((1.0, 1500), (1.25, 1500), (2.4, 1200), (2.7, 1800)): fanup = place(fanup, 0.4 * tone(f, t(0.09)) * env(int(0.09 * SR), 0.003, 0.03), at)
write('cockpit/avionics', norm(fanup * env(len(tt), 0.3, 0.6), 0.12))
# autopilot disconnect: an original descending three-tone warble, repeated
tt = t(1.6); x = np.zeros(len(tt))
for rep in range(3):
    for k, f in enumerate((1180, 980, 820)): x = place(x, np.sign(tone(f, t(0.1))) * 0.35 * env(int(0.1 * SR), 0.004, 0.02), rep * 0.5 + k * 0.12)
write('cockpit/ap_disconnect', norm(lp_fast(x, 5000), 0.2))
tt = t(0.9); x = (np.sign(tone(560, tt)) * 0.4 + tone(1120, tt) * 0.2) * env(len(tt), 0.01, 0.05)
write('cockpit/gear_horn', norm(lp_fast(x, 4000), 0.2))
tt = t(1.8); x = (0.4 * sweep(400, 1450, tt, 0.4) + 0.3 * bp_fast(noise(len(tt)), 800, 4000)) * env(len(tt), 0.1, 0.4)
write('cockpit/hydraulic', norm(x, 0.12))
d = 3.0; tt = t(d)
x = 0.6 * lp_fast(noise(len(tt)), 600) + 0.15 * tone(120, tt) + 0.08 * tone(240, tt) + 0.2 * bp_fast(noise(len(tt)), 2000, 5000)
write('cockpit/ambient', norm(loopify(x, 0.5), 0.1))
# ---- cabin
tt = t(1.3); x = lp_fast(noise(len(tt)), 500) * env(len(tt), 0.05, 0.3) * 0.8 + 0.2 * bp_fast(noise(len(tt)), 1500, 4000) * env(len(tt), 0.05, 0.3)
x = place(x, click(15, 5000, 200) * 1.5 + 0.4 * tone(160, t(0.015)), 1.15)
write('cabin/door', norm(x, 0.16))
tt = t(0.9); x = (0.4 * tone(780 + 60 * np.sin(2 * np.pi * 3 * tt), tt) + 0.3 * bp_fast(noise(len(tt)), 400, 3000)) * env(len(tt), 0.05, 0.15)
write('cabin/seat_motor', norm(x, 0.1))
tt = t(0.4); x = lp_fast(noise(len(tt)), 2000) * np.exp(-tt * 25) * 0.6; x = place(x, click(10, 6000, 250), 0.25)
write('cabin/tray', norm(x, 0.14, 0.98))
tt = t(1.2); x = place(np.zeros(len(tt)), (tone(880, t(0.6)) + 0.4 * tone(1760, t(0.6))) * np.exp(-np.arange(int(0.6 * SR)) / SR * 5), 0)
x = place(x, (tone(660, t(0.6)) + 0.4 * tone(1320, t(0.6))) * np.exp(-np.arange(int(0.6 * SR)) / SR * 5), 0.35)
write('cabin/ife', norm(x, 0.14))
# ---- environment
d = 2.5; tt = t(d)
x = bp_fast(noise(len(tt)), 1500, 9000) * (0.85 + 0.15 * np.sin(2 * np.pi * 0.8 * tt)) + 0.5 * bp_fast(noise(len(tt)), 300, 1200)
write('vehicle/airflow', norm(loopify(x, 0.5), 0.14))
for name, rev in (('vehicle/gear_extend', False), ('vehicle/gear_retract', True)):
    tt = t(3.2)
    motor = (0.35 * tone(260 + 40 * np.sin(2 * np.pi * 2 * tt), tt) + 0.3 * bp_fast(noise(len(tt)), 300, 2500)) * env(len(tt), 0.15, 0.4)
    x = motor + 0.25 * lp_fast(noise(len(tt)), 300) * env(len(tt), 0.2, 0.5)
    x = place(x, lp_fast(noise(int(0.25 * SR)), 500) * np.exp(-np.arange(int(0.25 * SR)) / SR * 14) * (1.6 if not rev else 1.2) + 0.8 * tone(65, t(0.25)) * np.exp(-np.arange(int(0.25 * SR)) / SR * 12), 2.75 if not rev else 2.9)
    if not rev: x = place(x, bp_fast(noise(int(0.9 * SR)), 400, 2500) * env(int(0.9 * SR), 0.2, 0.4) * 0.6, 0.6)   # airflow as the doors open
    write(name, norm(x, 0.17))
d = 2.0; tt = t(d)
x = (0.35 * tone(520, tt) + 0.2 * tone(1040, tt) + 0.35 * bp_fast(noise(len(tt)), 300, 2500)) * (0.9 + 0.1 * np.sin(2 * np.pi * 6 * tt))
write('vehicle/flaps', norm(loopify(x), 0.1))
tt = t(1.1); x = tone(70, tt) * np.exp(-tt * 10) + lp_fast(noise(len(tt)), 600) * env(len(tt), 0.02, 0.8) * 0.8
write('vehicle/spoiler', norm(x, 0.17))
d = 1.5; tt = t(d)
x = bp_fast(noise(len(tt)), 900, 5000) * 0.7 + 0.3 * tone(1350 + 80 * np.sin(2 * np.pi * 9 * tt), tt) + 0.4 * lp_fast(noise(len(tt)), 300)
write('vehicle/brake', norm(loopify(x, 0.3), 0.15))
names = {'engine.piston': 'engine/piston', 'engine.turbine': 'engine/turbine', 'engine.jet': 'engine/jet', 'engine.rotor': 'engine/rotor',
         'engine.electric': 'engine/electric', 'engine.start': 'engine/start', 'vehicle.wind': 'vehicle/wind', 'vehicle.stall_warning': 'vehicle/stall_warning',
         'vehicle.gear': 'vehicle/gear', 'vehicle.assemble': 'vehicle/assemble', 'weapon.machine_gun': 'weapon/machine_gun', 'weapon.cannon': 'weapon/cannon',
         'weapon.missile': 'weapon/missile', 'weapon.bomb': 'weapon/bomb', 'weapon.reload': 'weapon/reload', 'weapon.arm': 'weapon/arm', 'ui.click': 'ui/click',
         'alarm.stall': 'alarm/stall', 'alarm.overspeed': 'alarm/overspeed', 'alarm.caution': 'alarm/caution', 'alarm.fire': 'alarm/fire',
         'alarm.whoop': 'alarm/whoop', 'alarm.warning': 'alarm/warning',
         'vehicle.rumble': 'vehicle/rumble', 'vehicle.touchdown': 'vehicle/touchdown', 'vehicle.hard_landing': 'vehicle/hard_landing', 'vehicle.tire_screech': 'vehicle/tire_screech'}
for k in ('engine.turboprop', 'engine.turbofan', 'engine.heli_turbine', 'engine.rough', 'engine.light_off', 'engine.piston_catch', 'engine.starter_piston',
          'engine.starter_turbine', 'engine.ignition', 'engine.spool_down', 'cockpit.toggle', 'cockpit.relay', 'cockpit.button', 'cockpit.knob', 'cockpit.guard',
          'cockpit.lever', 'cockpit.avionics', 'cockpit.ap_disconnect', 'cockpit.gear_horn', 'cockpit.hydraulic', 'cockpit.ambient', 'cabin.door', 'cabin.seat_motor',
          'cabin.tray', 'cabin.ife', 'vehicle.airflow', 'vehicle.gear_extend', 'vehicle.gear_retract', 'vehicle.flaps', 'vehicle.spoiler', 'vehicle.brake'):
    names[k] = k.replace('.', '/')
sj = {k: {"sounds": [{"name": "vehicleworks:" + v, "attenuation_distance": 64 if k.startswith('engine') or k.startswith('weapon') else 16}],
          "subtitle": "subtitles.vehicleworks." + k} for k, v in names.items()}
with open(os.path.join(OUT, 'sounds.json'), 'w') as f: json.dump(sj, f, indent=2)
print('sounds ok')
