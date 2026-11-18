package com.vehicleworks.client;

import com.vehicleworks.Config;
import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.registry.ModSounds;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Looping engine, rotor and wind sounds that follow a vehicle; pitch and volume track the engines. */
public final class VehicleSounds {
    private VehicleSounds() {}

    // loop families
    private static final int PISTON = 0, TURBOPROP = 1, JET = 2, ROTOR = 3, ELECTRIC = 4, WIND = 5, RUMBLE = 6, TURBOFAN = 7, HELI = 8,
            AIRFLOW = 9, STARTER_P = 10, STARTER_T = 11, IGNITION = 12, ROUGH = 13, FLAPS = 14, BRAKE = 15, AMBIENT = 16, N = 17;

    private static final class Loop extends AbstractTickableSoundInstance {
        private final VehicleEntity v;
        private final int family;

        Loop(SoundEvent e, VehicleEntity v, int family) {
            super(e, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.v = v;
            this.family = family;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = 0.6f;
            this.x = v.getX();
            this.y = v.getY();
            this.z = v.getZ();
        }

        @Override
        public void tick() {
            if (v.isRemoved()) { stop(); return; }
            x = v.getX(); y = v.getY(); z = v.getZ();
            VehicleTelemetry t = v.telemetry;
            float level = 0, p = 0.6f;
            Minecraft mc = Minecraft.getInstance();
            boolean inside = mc.player != null && (mc.player.getVehicle() == v || com.vehicleworks.vehicle.EntityVehicleCollision.anchoredVehicle(mc.player) == v);
            // thinner air at altitude carries less sound (wind, airflow)
            float density = (float) Math.exp(-Math.max(0, t.altitude) / 9000.0);
            switch (family) {
                case RUMBLE -> {
                    float s = t.groundSpeed;
                    int grounded = 0;
                    for (float c : t.wheelComp) if (c > 0) grounded++;
                    level = t.onGround || grounded > 0 ? Mth.clamp((s - 1.5f) / 45f, 0, 1) : 0;
                    p = 0.55f + Mth.clamp(s / 90f, 0, 0.9f);
                }
                case WIND -> {
                    float s = t.airspeed;
                    level = Mth.clamp((s - 8) / 70f, 0, 1) * 0.8f * density * (inside ? 0.6f : 1f);
                    p = 0.7f + Mth.clamp(s / 220f, 0, 0.8f);
                }
                case AIRFLOW -> {
                    float s = t.airspeed;
                    level = Mth.clamp((s - 40) / 160f, 0, 1) * density * (inside ? 0.7f : 0.5f) * (0.8f + 0.4f * Math.min(1, Math.abs(t.aoa) / 12f));
                    p = 0.8f + Mth.clamp(s / 300f, 0, 0.7f);
                }
                case ROTOR -> {
                    float om = 0;
                    for (float o : t.rotorOmega) om = Math.max(om, Math.abs(o));
                    level = Mth.clamp(om / 32f, 0, 1.2f);
                    p = 0.5f + 0.5f * Mth.clamp(om / 32f, 0, 1.3f);
                }
                case STARTER_P, STARTER_T -> {
                    for (int i = 0; i < t.engineType.length; i++) {
                        if (i >= t.enginePhase.length || (t.enginePhase[i] & 32) == 0) continue;
                        boolean piston = EngineType.byIndex(t.engineType[i]) == EngineType.PISTON;
                        if (piston != (family == STARTER_P)) continue;
                        level = Math.max(level, 0.9f);
                        // the starter slows down under compression, the turbine starter sings higher with N2
                        p = piston ? 0.85f + t.engineSpool[i] * 2f : 0.55f + t.engineSpool[i] * 2.2f;
                    }
                    level *= cockpit();
                }
                case IGNITION -> {
                    boolean ign = t.sw(com.vehicleworks.vehicle.VehicleControls.SW_IGNITION);
                    for (int i = 0; i < t.engineState.length && i < t.enginePhase.length; i++) {
                        int ph = t.enginePhase[i] & 7;
                        if (ign && t.engineState[i] == 1 && (ph == 1 || ph == 2) && EngineType.byIndex(t.engineType[i]).isTurbine()) level = 0.7f;
                    }
                    level *= cockpit() * (inside ? 1f : 0.4f);
                    p = 1f;
                }
                case ROUGH -> {
                    for (int i = 0; i < t.engineState.length; i++) {
                        float dmg = i < t.engineDamage.length ? t.engineDamage[i] : 0;
                        if (t.engineState[i] == 2 && dmg > 0.15f) level = Math.max(level, Mth.clamp((dmg - 0.15f) * 1.6f, 0, 1) * (0.5f + t.engineSpool[i]));
                    }
                    p = 0.8f + (float) Math.random() * 0.3f;
                    level *= engines();
                }
                case FLAPS -> {
                    VehicleClientState cs = (VehicleClientState) v.clientState;
                    float df = cs == null ? 0 : Math.abs(t.flaps - cs.lastFlaps);
                    if (cs != null) cs.lastFlaps = t.flaps;
                    flapHold = df > 1e-4f ? 6 : Math.max(0, flapHold - 1);
                    level = flapHold > 0 ? 0.6f * (inside ? 1f : 0.5f) : 0;
                    p = 1f;
                }
                case BRAKE -> {
                    boolean grounded = false, skid = false;
                    for (float c : t.wheelComp) grounded |= c > 0;
                    for (boolean b : t.wheelSkid) skid |= b;
                    boolean braking = grounded && t.groundSpeed > 2 && (t.parkingBrake || skid || (t.throttle < 0.04f && t.groundSpeed > 6));
                    level = braking ? Mth.clamp(t.groundSpeed / 30f, 0.15f, 0.9f) : 0;
                    p = 0.8f + Mth.clamp(t.groundSpeed / 80f, 0, 0.5f);
                }
                case AMBIENT -> {
                    // avionics fans and air conditioning, heard inside only while the bus is powered
                    level = inside && t.busPowered ? 0.35f * (t.avionicsPowered() ? 1f : 0.5f) * cockpit() : 0;
                    p = 1f;
                }
                default -> {
                    // engines: pitch follows RPM, volume follows RPM and load (thrust / power), start-up and shutdown included
                    for (int i = 0; i < t.engineType.length; i++) {
                        boolean rotorDriven = i < t.enginePhase.length && (t.enginePhase[i] & 8) != 0;
                        if (familyOf(EngineType.byIndex(t.engineType[i]), rotorDriven) != family) continue;
                        float spool = t.engineSpool[i];
                        boolean lit = i < t.enginePhase.length ? (t.enginePhase[i] & 16) != 0 : t.engineState[i] == 2;
                        float load = Mth.clamp(t.throttle, 0, 1);
                        float l = lit ? spool * (0.65f + 0.45f * load) : spool * 0.35f; // a dry-cranked or winding-down engine is just whine
                        if (t.engineState[i] == 4) l *= 0.3f;
                        level = Math.max(level, l);
                        float base = switch (family) { case PISTON -> 0.42f; case TURBOPROP -> 0.5f; case TURBOFAN -> 0.45f; case HELI -> 0.5f; default -> 0.55f; };
                        p = Math.max(p, base + spool * (family == JET ? 0.75f : 0.9f) + (t.engineAb[i] ? 0.1f : 0));
                    }
                    if (family == PISTON && t.rpmFraction > 0) p = Math.max(p, 0.45f + t.rpmFraction * 1.1f);
                    level *= engines();
                }
            }
            float gain = switch (family) { case JET, TURBOFAN -> 3.5f; case WIND, AIRFLOW -> 1.0f; case RUMBLE -> 1.6f; case STARTER_P, STARTER_T, IGNITION, AMBIENT, FLAPS -> 1.0f; case BRAKE -> 1.2f; default -> 2.2f; };
            boolean engineFamily = family <= ELECTRIC || family == TURBOFAN || family == HELI || family == ROUGH;
            float target = level * gain * (engineFamily && inside ? 0.7f : 1f);
            volume += (target - volume) * 0.2f;
            pitch += (Mth.clamp(p, 0.5f, 2f) - pitch) * 0.15f;
            if (volume < 0.003f && level <= 0) volume = 0.003f;
        }

        private static float engines() {
            return Config.CLIENT.engineVolume.get().floatValue();
        }

        private static float cockpit() {
            return Config.CLIENT.cockpitVolume.get().floatValue();
        }
    }

    private static int flapHold;

    private static int familyOf(EngineType t, boolean rotorDriven) {
        return switch (t) {
            case PISTON -> PISTON;
            case TURBOPROP -> rotorDriven ? HELI : TURBOPROP;
            case TURBOFAN -> TURBOFAN;
            case TURBOJET -> JET;
            case ELECTRIC -> ELECTRIC;
        };
    }

    private static SoundEvent event(int f) {
        return switch (f) {
            case PISTON -> ModSounds.PISTON_LOOP.get();
            case TURBOPROP -> ModSounds.TURBOPROP_LOOP.get();
            case TURBOFAN -> ModSounds.TURBOFAN_LOOP.get();
            case HELI -> ModSounds.HELI_TURBINE_LOOP.get();
            case JET -> ModSounds.JET_LOOP.get();
            case ROTOR -> ModSounds.ROTOR_LOOP.get();
            case ELECTRIC -> ModSounds.ELECTRIC_LOOP.get();
            case RUMBLE -> ModSounds.RUMBLE.get();
            case AIRFLOW -> ModSounds.AIRFLOW.get();
            case STARTER_P -> ModSounds.STARTER_PISTON.get();
            case STARTER_T -> ModSounds.STARTER_TURBINE.get();
            case IGNITION -> ModSounds.IGNITION.get();
            case ROUGH -> ModSounds.ENGINE_ROUGH.get();
            case FLAPS -> ModSounds.FLAPS.get();
            case BRAKE -> ModSounds.BRAKE.get();
            case AMBIENT -> ModSounds.COCKPIT_AMBIENT.get();
            default -> ModSounds.WIND_LOOP.get();
        };
    }

    @SuppressWarnings("unchecked")
    public static void tick(VehicleEntity v, VehicleClientState s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || v.distanceToSqr(mc.player) > 200 * 200) { stop(s); return; }
        List<Loop> loops = (List<Loop>) s.sounds;
        boolean[] want = new boolean[N];
        VehicleTelemetry t = v.telemetry;
        boolean anyStarter = false, anyDamaged = false;
        for (int i = 0; i < t.engineType.length; i++) {
            boolean rotorDriven = i < t.enginePhase.length && (t.enginePhase[i] & 8) != 0;
            want[familyOf(EngineType.byIndex(t.engineType[i]), rotorDriven)] = true;
            if (i < t.enginePhase.length && (t.enginePhase[i] & 32) != 0) want[EngineType.byIndex(t.engineType[i]) == EngineType.PISTON ? STARTER_P : STARTER_T] = true;
            if (t.engineState[i] == 1) want[IGNITION] = true;
            if (i < t.engineDamage.length && t.engineDamage[i] > 0.15f) anyDamaged = true;
        }
        want[ROUGH] = anyDamaged;
        if (t.rotorOmega.length > 0) want[ROTOR] = true;
        want[WIND] = t.airspeed > 10;
        want[AIRFLOW] = t.airspeed > 40;
        want[RUMBLE] = t.wheelKeys.length > 0 && t.groundSpeed > 1.5f;
        want[BRAKE] = t.wheelKeys.length > 0 && t.groundSpeed > 2;
        want[FLAPS] = true;
        want[AMBIENT] = t.busPowered && mc.player.distanceToSqr(v) < 30 * 30;
        if (loops == null) {
            loops = new ArrayList<>();
            s.sounds = loops;
        }
        loops.removeIf(l -> l.isStopped() || !mc.getSoundManager().isActive(l) && l.v.tickCount % 40 == 0);
        for (int f = 0; f < N; f++) {
            if (!want[f]) continue;
            int ff = f;
            if (loops.stream().anyMatch(l -> l.family == ff)) continue;
            Loop l = new Loop(event(f), v, f);
            loops.add(l);
            mc.getSoundManager().play(l);
        }
        transitions(v, s, t);
        // touchdown / hard landing
        if (t.wheelKeys.length > 0) {
            boolean grounded = false;
            for (float c : t.wheelComp) if (c > 0) { grounded = true; break; }
            boolean skid = false;
            for (boolean b : t.wheelSkid) skid |= b;
            if (skid && grounded && t.groundSpeed > 6 && v.tickCount % 9 == 0)
                mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.TIRE_SCREECH.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 0.9f + mc.level.random.nextFloat() * 0.2f, false);
            if (!grounded) s.lastVs = t.verticalSpeed;
            else if (!s.wasGround && t.groundSpeed > 8) {
                // tyres spin up on touchdown: screech
                mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.TIRE_SCREECH.get(), net.minecraft.sounds.SoundSource.NEUTRAL,
                        Math.min(2.5f, 0.6f + t.groundSpeed / 40f), 0.85f + mc.level.random.nextFloat() * 0.2f, false);
            }
            if (grounded && !s.wasGround) {
                float impact = -s.lastVs;
                boolean inside = mc.player.getVehicle() == v;
                if (impact > 4.5f) {
                    float k = Mth.clamp((impact - 4.5f) / 6f, 0.15f, 1f);
                    mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.HARD_LANDING.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f + 2.5f * k, 0.9f - 0.2f * k, false);
                    if (inside) {
                        GroundFx.kick(0.4f + 0.6f * k);
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.msg.hard_landing", String.format("%.1f", impact)), true);
                    }
                } else if (impact > 1.0f) {
                    mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.TOUCHDOWN.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f + impact * 0.25f, 1f, false);
                    if (inside) GroundFx.kick(0.06f + impact * 0.04f);
                }
            }
            s.wasGround = grounded;
        }
    }

    /** one-shot sounds on engine state changes: piston catching, turbine light-off, spool down */
    private static void transitions(VehicleEntity v, VehicleClientState s, VehicleTelemetry t) {
        Minecraft mc = Minecraft.getInstance();
        int n = t.engineState.length;
        if (s.lastEngineState == null || s.lastEngineState.length != n) {
            s.lastEngineState = t.engineState.clone();
            s.lastEnginePhase = t.enginePhase.clone();
            return;
        }
        float vol = Config.CLIENT.engineVolume.get().floatValue();
        for (int i = 0; i < n && i < t.enginePhase.length && i < s.lastEnginePhase.length; i++) {
            int ph = t.enginePhase[i] & 7, was = s.lastEnginePhase[i] & 7;
            EngineType et = EngineType.byIndex(t.engineType[i]);
            if (et.isTurbine() && ph == 2 && was == 1)
                mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.LIGHT_OFF.get(), SoundSource.NEUTRAL, 1.6f * vol, 1f, false);
            if (et == EngineType.PISTON && ph == 3 && was == 1)
                mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.PISTON_CATCH.get(), SoundSource.NEUTRAL, 1.6f * vol, 1f, false);
            if (et.isTurbine() && s.lastEngineState[i] == 2 && (t.engineState[i] == 3 || t.engineState[i] == 4))
                mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), ModSounds.SPOOL_DOWN.get(), SoundSource.NEUTRAL, 1.4f * vol, 1f, false);
        }
        s.lastEngineState = t.engineState.clone();
        s.lastEnginePhase = t.enginePhase.clone();
    }

    @SuppressWarnings("unchecked")
    public static void stop(VehicleClientState s) {
        if (s.sounds instanceof List<?> l) {
            for (Object o : l) if (o instanceof Loop loop) Minecraft.getInstance().getSoundManager().stop(loop);
            ((List<Object>) l).clear();
        }
    }
}
