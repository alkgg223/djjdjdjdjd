package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.client.screen.LaunchScreen;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.net.Net;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Client-side packet handling and shared shot effects. */
public final class ClientNet {
    private ClientNet() {}

    private static final RandomSource R = RandomSource.create();

    public static void shotFx(Net.ShotFx p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity shooter = level.getEntity(p.shooter());
        Vec3 muzzle = p.origin();
        GunType t = p.gun() >= 0 ? GunType.byId(p.gun()) : null;
        if (shooter instanceof LivingEntity le && !(shooter instanceof JeepEntity)) {
            float yaw = le.getYHeadRot(), pitch = le.getXRot();
            Vec3 look = GunLogic.dir(pitch, yaw);
            Vec3 right = GunLogic.dir(0, yaw + 90);
            muzzle = le.getEyePosition().add(look.scale(t != null && t.category == GunType.Category.PISTOL ? 0.75 : 1.15)).add(right.scale(0.3)).add(0, -0.3, 0);
        }
        if (t == null) {
            // jeep turret
            level.playLocalSound(muzzle.x, muzzle.y, muzzle.z, Reg.sound("shot_turret"), SoundSource.HOSTILE, 7f, 0.95f + R.nextFloat() * 0.1f, true);
            level.addParticle(Reg.MUZZLE_FLASH.get(), muzzle.x, muzzle.y, muzzle.z, 0, 0, 0);
            level.addParticle(Reg.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, 0, 0.02, 0);
        } else {
            GunClient.playShot(level, t, muzzle, p.suppressed(), false);
            if (t.kind == GunType.Kind.FLAME) {
                Vec3 dir = p.ends().isEmpty() ? new Vec3(0, 0, 1) : p.end(0).subtract(muzzle).normalize();
                flames(level, muzzle, dir);
                return;
            }
            if (!p.suppressed()) level.addParticle(Reg.MUZZLE_FLASH.get(), muzzle.x, muzzle.y, muzzle.z, 0, 0, 0);
            level.addParticle(Reg.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, 0, 0.015, 0);
            if (t.kind != GunType.Kind.BULLET) return;
        }
        Vec3 me = mc.player != null ? mc.player.getEyePosition() : null;
        for (int i = 0; i < p.ends().size(); i++) {
            Vec3 end = p.end(i);
            byte kind = p.kinds().length > i ? p.kinds()[i] : 0;
            if (i % 2 == 0 || p.ends().size() < 4) tracer(level, muzzle, end);
            BlockState st = kind == 1 ? level.getBlockState(net.minecraft.core.BlockPos.containing(end.subtract(end.subtract(muzzle).normalize().scale(-0.05)))) : null;
            impact(level, end, kind, st, end.subtract(muzzle).normalize());
            // bullet passing by the local player
            if (me != null && shooter != mc.player && i == 0) {
                Vec3 ab = end.subtract(muzzle);
                double len2 = ab.lengthSqr();
                double tt = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, me.subtract(muzzle).dot(ab) / len2));
                double d = muzzle.add(ab.scale(tt)).distanceTo(me);
                if (d < 2.5 && tt > 0.05 && tt < 0.98) {
                    level.playLocalSound(me.x, me.y, me.z, Reg.sound("bullet_whiz"), SoundSource.HOSTILE, 0.9f, 0.9f + R.nextFloat() * 0.3f, false);
                }
            }
        }
    }

    public static void tracer(ClientLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        level.addParticle(Reg.TRACER.get(), from.x, from.y, from.z, d.x, d.y, d.z);
    }

    public static void impact(ClientLevel level, Vec3 at, byte kind, @Nullable BlockState state, Vec3 dir) {
        if (kind == 1) {
            Vec3 back = at.subtract(dir.scale(0.06));
            for (int i = 0; i < 3; i++) {
                level.addParticle(Reg.SPARK.get(), back.x, back.y, back.z, -dir.x * 0.1 + R.nextGaussian() * 0.08, 0.06 + R.nextDouble() * 0.08, -dir.z * 0.1 + R.nextGaussian() * 0.08);
            }
            if (state != null && !state.isAir()) {
                for (int i = 0; i < 4; i++) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), back.x, back.y, back.z, R.nextGaussian() * 0.1, 0.12, R.nextGaussian() * 0.1);
                }
            }
            level.addParticle(Reg.SMOKE.get(), back.x, back.y, back.z, 0, 0.01, 0);
        } else if (kind == 2) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, R.nextGaussian() * 0.1, 0.1, R.nextGaussian() * 0.1);
            }
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, 0, 0.1, 0);
        }
    }

    public static void flames(ClientLevel level, Vec3 from, Vec3 dir) {
        for (int i = 0; i < 9; i++) {
            double s = 0.55 + R.nextDouble() * 0.45;
            level.addParticle(Reg.FLAME.get(), from.x, from.y, from.z,
                    dir.x * s + R.nextGaussian() * 0.05, dir.y * s + R.nextGaussian() * 0.05 + 0.01, dir.z * s + R.nextGaussian() * 0.05);
        }
        if (R.nextInt(3) == 0) level.addParticle(Reg.SMOKE.get(), from.x + dir.x * 4, from.y + dir.y * 4 + 0.5, from.z + dir.z * 4, dir.x * 0.1, 0.08, dir.z * 0.1);
    }

    public static void hitMarker(Net.HitMarker p) {
        GunClient.onHitMarker(p.kill());
    }

    public static void launchStatus(Net.LaunchStatus p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LaunchScreen ls && ls.station().equals(p.station())) {
            ls.update(p);
        } else if (p.open()) {
            mc.setScreen(new LaunchScreen(p));
        }
    }
}
