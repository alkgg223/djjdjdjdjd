package com.gunsmod.client.sound;

import com.gunsmod.Reg;
import com.gunsmod.client.particle.BlastParticles;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.VehicleEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Engine loops of the tank (idle diesel + driving roar with track clatter) and the helicopter (rotor slap and
 * turbine), following the vehicle and cross-fading with speed / rotor speed. Also exhaust, rotor wash and damage smoke.
 */
public class VehicleSound extends AbstractTickableSoundInstance {
    private static final int TANK_IDLE = 0, TANK_DRIVE = 1, ROTOR = 2;
    private static final Map<VehicleEntity, VehicleSound[]> ACTIVE = new WeakHashMap<>();
    private static final Map<VehicleEntity, Boolean> RUNNING = new WeakHashMap<>();

    private final VehicleEntity vehicle;
    private final int kind;

    private VehicleSound(VehicleEntity v, int kind) {
        super(Reg.sound(kind == TANK_IDLE ? "tank_idle" : kind == TANK_DRIVE ? "tank_drive" : "heli_rotor"), SoundSource.NEUTRAL,
                SoundInstance.createUnseededRandom());
        this.vehicle = v;
        this.kind = kind;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.x = v.getX();
        this.y = v.getY();
        this.z = v.getZ();
    }

    /** called every client tick for every tank and helicopter */
    public static void tick(VehicleEntity v) {
        if (v instanceof TankEntity t) tank(t);
        else if (v instanceof HelicopterEntity h) heli(h);
        damageSmoke(v);
    }

    private static boolean playing(VehicleSound[] s) {
        if (s == null) return false;
        for (VehicleSound x : s) if (x.isStopped()) return false;
        return true;
    }

    private static void tank(TankEntity t) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = t.getControllingPassenger() != null && t.isAlive();
        boolean was = RUNNING.getOrDefault(t, false);
        if (on && !was) t.level().playLocalSound(t.getX(), t.getY(), t.getZ(), Reg.sound("jeep_start"), SoundSource.NEUTRAL, 2.5f, 0.55f, false);
        RUNNING.put(t, on);
        VehicleSound[] s = ACTIVE.get(t);
        if (on && !playing(s)) {
            s = new VehicleSound[]{new VehicleSound(t, TANK_IDLE), new VehicleSound(t, TANK_DRIVE)};
            ACTIVE.put(t, s);
            mc.getSoundManager().play(s[0]);
            mc.getSoundManager().play(s[1]);
        }
        if (!on) return;
        RandomSource r = t.level().random;
        double sp = Math.abs(t.getDisplaySpeed()) / TankEntity.MAX_SPEED;
        // twin exhausts at the rear, black puffs when it pulls away
        if (t.tickCount % 3 == 0) {
            for (double ex : new double[]{5.0, 11.0}) {
                Vec3 p = t.toWorld(ex, 5.0, 21.8);
                t.level().addParticle(sp > 0.2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.04, 0);
            }
        }
        // dust thrown up behind the tracks
        if (sp > 0.3 && t.onGround() && BlastParticles.dustSprites != null && r.nextFloat() < sp * 0.5) {
            for (double tx : new double[]{2.5, 13.5}) {
                Vec3 p = t.toWorld(tx, 0.4, 21.0 + r.nextDouble());
                BlockState below = t.level().getBlockState(BlockPos.containing(p.x, p.y - 0.6, p.z));
                if (below.isAir()) continue;
                int col = below.getMapColor(t.level(), BlockPos.containing(p.x, p.y - 0.6, p.z)).col;
                float cr = ((col >> 16) & 255) / 255f, cg = ((col >> 8) & 255) / 255f, cb = (col & 255) / 255f;
                mc.particleEngine.add(new BlastParticles.DustCloud((net.minecraft.client.multiplayer.ClientLevel) t.level(), p.x, p.y, p.z,
                        (r.nextDouble() - 0.5) * 0.05, 0.03, (r.nextDouble() - 0.5) * 0.05, BlastParticles.dustSprites, 0.7f + r.nextFloat() * 0.4f,
                        40 + r.nextInt(30), Mth.lerp(0.5f, cr, 0.6f), Mth.lerp(0.5f, cg, 0.55f), Mth.lerp(0.5f, cb, 0.48f), 0.02f, 0.001f));
            }
        }
    }

    private static void heli(HelicopterEntity h) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = h.getControllingPassenger() != null && h.isAlive();
        boolean was = RUNNING.getOrDefault(h, false);
        if (on && !was && h.rotor < 0.5f) h.level().playLocalSound(h.getX(), h.getY(), h.getZ(), Reg.sound("heli_start"), SoundSource.NEUTRAL, 4f, 1f, false);
        RUNNING.put(h, on);
        VehicleSound[] s = ACTIVE.get(h);
        if (h.rotor > 0.05f && !playing(s)) {
            s = new VehicleSound[]{new VehicleSound(h, ROTOR)};
            ACTIVE.put(h, s);
            mc.getSoundManager().play(s[0]);
        }
        // rotor wash: dust and spray blown out under the helicopter
        double alt = h.heightAboveGround();
        RandomSource r = h.level().random;
        if (h.rotor > 0.6f && alt < 10 && BlastParticles.dustSprites != null) {
            int n = (int) ((1.0 - alt / 10.0) * 4 * h.rotor);
            BlockPos gp = BlockPos.containing(h.getX(), h.getY() - alt - 0.5, h.getZ());
            BlockState ground = h.level().getBlockState(gp);
            boolean water = !h.level().getFluidState(gp).isEmpty();
            int col = water ? 0xd8e4ec : ground.isAir() ? 0x9a8a6a : ground.getMapColor(h.level(), gp).col;
            float cr = ((col >> 16) & 255) / 255f, cg = ((col >> 8) & 255) / 255f, cb = (col & 255) / 255f;
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 1.5 + r.nextDouble() * 2.5, sp = 0.35 + r.nextDouble() * 0.35;
                mc.particleEngine.add(new BlastParticles.DustCloud((net.minecraft.client.multiplayer.ClientLevel) h.level(),
                        h.getX() + Math.cos(a) * rad, h.getY() - alt + 0.3, h.getZ() + Math.sin(a) * rad, Math.cos(a) * sp, 0.02, Math.sin(a) * sp,
                        BlastParticles.dustSprites, 0.8f + r.nextFloat() * 0.6f, 30 + r.nextInt(25), Mth.lerp(0.4f, cr, 0.7f),
                        Mth.lerp(0.4f, cg, 0.68f), Mth.lerp(0.4f, cb, 0.62f), 0.03f, 0.0f));
            }
        }
        // turbine exhaust heat haze
        if (on && h.tickCount % 4 == 0) {
            for (double ex : new double[]{6.1, 9.9}) {
                Vec3 p = h.toWorld(ex, 7.4, 9.4);
                h.level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.02, 0);
            }
        }
    }

    private static void damageSmoke(VehicleEntity v) {
        float hp = v.getHealth() / v.getMaxHealth();
        if (hp >= 0.5f || v.tickCount % (hp < 0.25f ? 2 : 5) != 0) return;
        Vec3 p = v instanceof TankEntity ? v.toWorld(8, 6.2, 16) : v.toWorld(8, 8.4, 4);
        v.level().addParticle(hp < 0.25f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.07, 0);
        if (hp < 0.2f && v.tickCount % 6 == 0) v.level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.03, 0);
    }

    @Override
    public void tick() {
        boolean alive = vehicle.isAlive() && !vehicle.isRemoved();
        float target;
        if (kind == ROTOR) {
            HelicopterEntity h = (HelicopterEntity) vehicle;
            if (!alive || h.rotor <= 0.02f) {
                this.volume -= 0.1f;
                if (this.volume <= 0.02f) this.stop();
                return;
            }
            target = 6f * h.rotor;
            this.pitch = 0.55f + 0.45f * h.rotor + (float) Math.min(0.12, h.airSpeed * 0.08);
        } else {
            if (!alive || vehicle.getControllingPassenger() == null) {
                this.volume -= 0.1f;
                if (this.volume <= 0.02f) this.stop();
                return;
            }
            double sp = Math.min(1.0, Math.abs(((TankEntity) vehicle).getDisplaySpeed()) / TankEntity.MAX_SPEED);
            target = kind == TANK_DRIVE ? (float) (0.2 + 2.6 * sp) : (float) (2.4 - sp * 1.6);
            this.pitch = kind == TANK_DRIVE ? (float) (0.75 + sp * 0.45) : 1.0f;
        }
        this.x = vehicle.getX();
        this.y = vehicle.getY() + 1;
        this.z = vehicle.getZ();
        this.volume += (target - this.volume) * 0.15f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
