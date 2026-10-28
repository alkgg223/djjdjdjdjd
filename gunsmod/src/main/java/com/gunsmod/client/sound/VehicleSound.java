package com.gunsmod.client.sound;

import com.gunsmod.Reg;
import com.gunsmod.client.particle.BlastParticles;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JetEntity;
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
    private static final int TANK_IDLE = 0, TANK_DRIVE = 1, ROTOR = 2, CAR_IDLE = 3, CAR_DRIVE = 4;
    private static final Map<VehicleEntity, VehicleSound[]> ACTIVE = new WeakHashMap<>();
    private static final Map<VehicleEntity, Boolean> RUNNING = new WeakHashMap<>();

    private final VehicleEntity vehicle;
    private final int kind;

    private VehicleSound(VehicleEntity v, int kind) {
        super(Reg.sound(switch (kind) {
            case TANK_IDLE -> "tank_idle";
            case TANK_DRIVE -> "tank_drive";
            case CAR_IDLE -> "jeep_idle";
            case CAR_DRIVE -> "jeep_drive";
            default -> "heli_rotor";
        }), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
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
        else if (v instanceof ArmoredCarEntity c) car(c);
        else if (v instanceof JetEntity j) JetSound.tick(j);
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
        double sp = Math.abs(t.getDisplaySpeed()) / t.kind.maxSpeed;
        // twin exhausts at the rear, black puffs when it pulls away
        if (t.tickCount % 3 == 0) {
            for (double[] ex : t.kind.geo.exhaust()) {
                Vec3 p = t.toWorld(ex[0], ex[1], ex[2]);
                t.level().addParticle(sp > 0.2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.04, 0);
            }
        }
        // dust thrown up behind the tracks
        if (sp > 0.3 && t.onGround() && BlastParticles.dustSprites != null && r.nextFloat() < sp * 0.5) {
            for (double tx : t.kind.geo.trackX()) {
                Vec3 p = t.toWorld(tx, 0.4, t.kind.geo.sprocket()[1] + 1.0 + r.nextDouble());
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

    private static void car(ArmoredCarEntity c) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = c.getControllingPassenger() != null && c.isAlive();
        boolean was = RUNNING.getOrDefault(c, false);
        if (on && !was) c.level().playLocalSound(c.getX(), c.getY(), c.getZ(), Reg.sound("jeep_start"), SoundSource.NEUTRAL, 2.5f, 0.7f, false);
        RUNNING.put(c, on);
        VehicleSound[] s = ACTIVE.get(c);
        if (on && !playing(s)) {
            s = new VehicleSound[]{new VehicleSound(c, CAR_IDLE), new VehicleSound(c, CAR_DRIVE)};
            ACTIVE.put(c, s);
            mc.getSoundManager().play(s[0]);
            mc.getSoundManager().play(s[1]);
        }
        if (!on) return;
        double sp = Math.abs(c.getDisplaySpeed()) / c.kind.maxSpeed;
        if (c.tickCount % 3 == 0) {
            for (double[] ex : c.geo.exhaust()) {
                Vec3 p = c.toWorld(ex[0], ex[1], ex[2]);
                c.level().addParticle(sp > 0.3 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.05, 0);
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
            double[] hub = h.geo.hub();
            for (double ex : new double[]{-2.0, 2.0}) {
                Vec3 p = h.toWorld(hub[0] + ex, hub[1] - 3.0, hub[2] + 6.0);
                h.level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.02, 0);
            }
        }
    }

    private static void damageSmoke(VehicleEntity v) {
        float hp = v.getHealth() / v.getMaxHealth();
        if (hp >= 0.5f || v.tickCount % (hp < 0.25f ? 2 : 5) != 0) return;
        if (v instanceof JetEntity) return;      // the jet burns its own way when it goes down
        double[] sm = v instanceof TankEntity tk ? tk.kind.geo.smoke() : v instanceof ArmoredCarEntity c ? c.geo.smoke()
                : v instanceof HelicopterEntity h ? new double[]{h.geo.hub()[0], h.geo.hub()[1] - 2.2, h.geo.hub()[2] + 1.5} : new double[]{8, 8.4, 4};
        Vec3 p = v.toWorld(sm[0], sm[1], sm[2]);
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
            if (vehicle instanceof ArmoredCarEntity c) {
                double sp = Math.min(1.0, Math.abs(c.getDisplaySpeed()) / c.kind.maxSpeed);
                target = kind == CAR_DRIVE ? (float) (0.3 + 2.4 * sp) : (float) (2.2 - sp * 1.4);
                this.pitch = kind == CAR_DRIVE ? (float) (0.6 + sp * 0.4) : 0.72f;
            } else {
                double sp = Math.min(1.0, Math.abs(((TankEntity) vehicle).getDisplaySpeed()) / ((TankEntity) vehicle).kind.maxSpeed);
                target = kind == TANK_DRIVE ? (float) (0.2 + 2.6 * sp) : (float) (2.4 - sp * 1.6);
                this.pitch = kind == TANK_DRIVE ? (float) (0.75 + sp * 0.45) : 1.0f;
            }
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
