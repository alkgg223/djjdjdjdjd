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

    private static final int PISTON = 0, TURBINE = 1, JET = 2, ROTOR = 3, ELECTRIC = 4, WIND = 5, RUMBLE = 6;

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
            if (family == RUMBLE) {
                float s = t.groundSpeed;
                int grounded = 0;
                for (float c : t.wheelComp) if (c > 0) grounded++;
                level = t.onGround || grounded > 0 ? Mth.clamp((s - 1.5f) / 45f, 0, 1) : 0;
                p = 0.55f + Mth.clamp(s / 90f, 0, 0.9f);
            } else if (family == WIND) {
                float s = t.airspeed;
                level = Mth.clamp((s - 8) / 70f, 0, 1) * 0.8f;
                p = 0.7f + Mth.clamp(s / 220f, 0, 0.8f);
            } else if (family == ROTOR) {
                float om = 0;
                for (float o : t.rotorOmega) om = Math.max(om, Math.abs(o));
                level = Mth.clamp(om / 32f, 0, 1.2f);
                p = 0.5f + 0.5f * Mth.clamp(om / 32f, 0, 1.3f);
            } else {
                for (int i = 0; i < t.engineType.length; i++) {
                    if (familyOf(EngineType.byIndex(t.engineType[i])) != family) continue;
                    float spool = t.engineSpool[i];
                    if (t.engineState[i] == 0 || t.engineState[i] == 4) spool *= 0.5f;
                    level = Math.max(level, spool);
                    p = Math.max(p, (family == PISTON ? 0.45f : 0.55f) + spool * (family == JET ? 0.75f : 0.9f) + (t.engineAb[i] ? 0.1f : 0));
                }
                if (family == PISTON && t.rpmFraction > 0) p = Math.max(p, 0.45f + t.rpmFraction * 1.1f);
            }
            boolean inside = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() == v;
            float target = level * (family == JET ? 3.5f : (family == WIND ? 1.0f : (family == RUMBLE ? 1.6f : 2.2f))) * Config.CLIENT.engineVolume.get().floatValue() * (inside ? 0.8f : 1f);
            volume += (target - volume) * 0.2f;
            pitch += (p - pitch) * 0.15f;
            if (volume < 0.003f && level <= 0) volume = 0.003f;
        }
    }

    private static int familyOf(EngineType t) {
        return switch (t) {
            case PISTON -> PISTON;
            case TURBOPROP, TURBOFAN -> TURBINE;
            case TURBOJET -> JET;
            case ELECTRIC -> ELECTRIC;
        };
    }

    @SuppressWarnings("unchecked")
    public static void tick(VehicleEntity v, VehicleClientState s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || v.distanceToSqr(mc.player) > 200 * 200) { stop(s); return; }
        List<Loop> loops = (List<Loop>) s.sounds;
        boolean[] want = new boolean[7];
        VehicleTelemetry t = v.telemetry;
        for (int i = 0; i < t.engineType.length; i++) want[familyOf(EngineType.byIndex(t.engineType[i]))] = true;
        if (t.rotorOmega.length > 0) want[ROTOR] = true;
        want[WIND] = t.airspeed > 10;
        want[RUMBLE] = t.wheelKeys.length > 0 && t.groundSpeed > 1.5f;
        if (loops == null) {
            loops = new ArrayList<>();
            s.sounds = loops;
        }
        loops.removeIf(l -> l.isStopped() || !mc.getSoundManager().isActive(l) && l.v.tickCount % 40 == 0);
        for (int f = 0; f < 7; f++) {
            if (!want[f]) continue;
            int ff = f;
            if (loops.stream().anyMatch(l -> l.family == ff)) continue;
            SoundEvent e = switch (f) {
                case PISTON -> ModSounds.PISTON_LOOP.get();
                case TURBINE -> ModSounds.TURBINE_LOOP.get();
                case JET -> ModSounds.JET_LOOP.get();
                case ROTOR -> ModSounds.ROTOR_LOOP.get();
                case ELECTRIC -> ModSounds.ELECTRIC_LOOP.get();
                case RUMBLE -> ModSounds.RUMBLE.get();
                default -> ModSounds.WIND_LOOP.get();
            };
            Loop l = new Loop(e, v, f);
            loops.add(l);
            mc.getSoundManager().play(l);
        }
        // touchdown / hard landing
        if (t.wheelKeys.length > 0) {
            if (!t.onGround) s.lastVs = t.verticalSpeed;
            else if (!s.wasGround) {
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
            s.wasGround = t.onGround;
        }
    }

    @SuppressWarnings("unchecked")
    public static void stop(VehicleClientState s) {
        if (s.sounds instanceof List<?> l) {
            for (Object o : l) if (o instanceof Loop loop) Minecraft.getInstance().getSoundManager().stop(loop);
            ((List<Object>) l).clear();
        }
    }
}
