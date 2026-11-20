package com.trafficcraft.client.sound;

import com.trafficcraft.TCConfig;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Client side vehicle audio: engine loops (idle + drive cross-fade, rpm pitch), siren, indicator clicks, reverse beeper. */
public final class VehicleSounds {
    private static final int MAX_ENGINES = 10;
    private static final Map<Integer, EngineLoop[]> ENGINES = new HashMap<>();
    private static final Map<Integer, Loop> SIRENS = new HashMap<>();
    private static int beepTimer;

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        List<VehicleEntity> near = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof VehicleEntity v && v.distanceToSqr(mc.player) < 56 * 56) near.add(v);
        }
        near.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        int n = 0;
        for (VehicleEntity v : near) {
            boolean run = v.has(VehicleEntity.F_ENGINE) && n < MAX_ENGINES;
            EngineLoop[] loops = ENGINES.get(v.getId());
            if (run) {
                n++;
                if (loops == null || loops[0].isStopped() || loops[1].isStopped()) {
                    EngineLoop idle = new EngineLoop(v, sound(v, "idle"), false);
                    EngineLoop drive = new EngineLoop(v, sound(v, "drive"), true);
                    mc.getSoundManager().play(idle);
                    mc.getSoundManager().play(drive);
                    ENGINES.put(v.getId(), new EngineLoop[]{idle, drive});
                }
            }
            if (v.has(VehicleEntity.F_SIREN) && v.distanceToSqr(mc.player) < 96 * 96) {
                Loop s = SIRENS.get(v.getId());
                if (s == null || s.isStopped()) {
                    s = new Loop(v, TCRegistry.snd("siren.wail"), VehicleEntity.F_SIREN, 2.4f);
                    mc.getSoundManager().play(s);
                    SIRENS.put(v.getId(), s);
                }
            }
        }
        ENGINES.entrySet().removeIf(en -> en.getValue()[0].isStopped() && en.getValue()[1].isStopped());
        SIRENS.entrySet().removeIf(en -> en.getValue().isStopped());
        // reverse beeper for the vehicle we sit in or nearby heavy vehicles
        if (beepTimer > 0) beepTimer--;
        for (VehicleEntity v : near) {
            if (v.spec().beepsWhenReversing() && v.has(VehicleEntity.F_REVERSE) && v.visSpeed() < -0.05f && beepTimer == 0) {
                mc.level.playLocalSound(v.getX(), v.getY() + 1, v.getZ(), TCRegistry.snd("misc.reverse_beep"), SoundSource.NEUTRAL, 0.9f, 1f, false);
                beepTimer = 16;
                break;
            }
        }
    }

    private static SoundEvent sound(VehicleEntity v, String kind) {
        return TCRegistry.sound("engine." + v.getModel() + "." + kind, () -> "engine.sedan." + kind);
    }

    /** One-shot events raised by the vehicle code on the client. */
    public static void event(VehicleEntity v, String ev) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !v.level().isClientSide) return;
        boolean inside = mc.player.getVehicle() == v;
        switch (ev) {
            case "blink_on", "blink_off" -> {
                if (inside) mc.level.playLocalSound(v.getX(), v.getY() + 1, v.getZ(),
                        TCRegistry.snd(ev.equals("blink_on") ? "misc.indicator_on" : "misc.indicator_off"), SoundSource.NEUTRAL, 0.5f, 1f, false);
            }
            case "shift" -> {
                if (inside) mc.level.playLocalSound(v.getX(), v.getY() + 1, v.getZ(), TCRegistry.snd("misc.gear_shift"), SoundSource.NEUTRAL, 0.35f, 1f, false);
            }
            case "crash" -> mc.level.playLocalSound(v.getX(), v.getY() + 0.8, v.getZ(), TCRegistry.snd("misc.crash"), SoundSource.NEUTRAL, 1.0f,
                    0.9f + v.getRandom().nextFloat() * 0.2f, false);
            case "backfire_0", "backfire_1", "backfire_2" -> {
                String k = ev.endsWith("0") ? "backfire.pop" : ev.endsWith("1") ? "backfire.crackle" : "backfire.bang";
                var ex = v.spec().exhausts.isEmpty() ? null : v.spec().exhausts.get(0);
                var off = ex == null ? net.minecraft.world.phys.Vec3.ZERO : VehicleEntity.toWorldOffset(ex[0], ex[1], ex[2], v.getYRot());
                mc.level.playLocalSound(v.getX() + off.x, v.getY() + off.y, v.getZ() + off.z, TCRegistry.snd(k), SoundSource.NEUTRAL,
                        ev.endsWith("2") ? 1.6f : 1.2f, 0.92f + v.getRandom().nextFloat() * 0.16f, false);
            }
            default -> { }
        }
    }

    static class EngineLoop extends AbstractTickableSoundInstance {
        final VehicleEntity v;
        final boolean drive;

        EngineLoop(VehicleEntity v, SoundEvent ev, boolean drive) {
            super(ev, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.v = v;
            this.drive = drive;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = v.getX();
            this.y = v.getY() + 0.6;
            this.z = v.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (v.isRemoved() || !v.has(VehicleEntity.F_ENGINE) || mc.player == null || v.distanceToSqr(mc.player) > 60 * 60) {
                stop();
                return;
            }
            x = v.getX();
            y = v.getY() + 0.6;
            z = v.getZ();
            float rpm = Math.max(1, v.visRpm());
            float idle = v.spec().idleRpm, drv = v.spec().driveRpm;
            float t = Mth.clamp((rpm - idle) / Math.max(1, (drv - idle) * 0.8f), 0, 1);
            float load = v.visLoad();
            float base = (float) (double) TCConfig.ENGINE_VOLUME.get() * (v.spec().isHeavy() ? 1.25f : 1.0f);
            boolean inside = mc.player.getVehicle() == v;
            if (inside) base *= 0.75f;
            if (drive) {
                volume = base * t * (0.55f + 0.45f * load) * 1.05f;
                pitch = Mth.clamp(rpm / drv, 0.5f, 2.0f);
            } else {
                volume = base * (1 - t * 0.85f) * 0.9f;
                pitch = Mth.clamp(rpm / idle, 0.5f, 2.0f);
            }
            volume = Math.max(0.001f, volume);
        }
    }

    static class Loop extends AbstractTickableSoundInstance {
        final VehicleEntity v;
        final int flag;

        Loop(VehicleEntity v, SoundEvent ev, int flag, float vol) {
            super(ev, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.v = v;
            this.flag = flag;
            this.looping = true;
            this.delay = 0;
            this.volume = vol;
            this.x = v.getX();
            this.y = v.getY() + 1;
            this.z = v.getZ();
        }

        @Override
        public void tick() {
            if (v.isRemoved() || !v.has(flag)) {
                stop();
                return;
            }
            x = v.getX();
            y = v.getY() + 1;
            z = v.getZ();
        }
    }
}
