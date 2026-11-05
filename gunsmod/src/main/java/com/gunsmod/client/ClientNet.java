package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.client.screen.LaunchScreen;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
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
        if (shooter instanceof LivingEntity le && !(shooter instanceof JeepEntity) && !(shooter instanceof VehicleEntity)) {
            float yaw = le.getYHeadRot(), pitch = le.getXRot();
            Vec3 look = GunLogic.dir(pitch, yaw);
            Vec3 right = GunLogic.dir(0, yaw + 90);
            muzzle = le.getEyePosition().add(look.scale(t != null && t.category == GunType.Category.PISTOL ? 0.75 : 1.15)).add(right.scale(0.3)).add(0, -0.3, 0);
        }
        if (!p.suppressed()) ClientLights.light(shooter != null ? shooter : (Object) p.shooter(), muzzle, t == null ? 14 : 13, 3);
        if (t == null) {
            // vehicle guns: -1 jeep turret, -2 coaxial machine gun, -3 20 mm chin gun, -4 30 mm cannon, -5 12.7 mm gatling,
            // -6 anti-aircraft twin 30 mm, -7 jet cannon, -8 C-RAM, -9 GAU-22, -10 GSh-30-1, -11 GAU-8,
            // -20 Gepard 35 mm, -21 ZU-23-2, -22 Bofors 40 mm
            int id = p.gun();
            String snd = switch (id) {
                case -2 -> "shot_lmg";
                case -3 -> "heli_gun";
                case -4, -6 -> "cannon_30";
                case -5 -> "shot_minigun";
                case -7, -8 -> "jet_cannon";
                case -9 -> "gun_gau22";
                case -10 -> "gun_gsh30";
                case -11 -> "gun_gau8";
                case -20 -> "aa_35mm";
                case -21 -> "aa_23mm";
                case -22 -> "aa_40mm";
                default -> "shot_turret";
            };
            float pitch = id == -6 ? 1.15f : id == -8 ? 1.3f : 0.95f;
            // the aircraft cannons and the C-RAM fire 30-100 rounds a second: one burst sample covers several ticks
            boolean burst = id > -7 || id <= -20 || level.getGameTime() % 4 == 0;
            if (burst && (id == -2 || id == -5) && GunClient.farShot(level, muzzle, "shot_far_rifle")) burst = false;
            else if (burst && id != -2 && id != -5 && GunClient.farShot(level, muzzle, "shot_far_heavy")) burst = false;
            if (burst) level.playLocalSound(muzzle.x, muzzle.y, muzzle.z, Reg.sound(snd), SoundSource.HOSTILE, id <= -3 ? 10f : 7f, pitch + R.nextFloat() * 0.1f, true);
            float fs = switch (id) {
                case -2 -> 1.1f;
                case -3, -7 -> 1.7f;
                case -4, -6 -> 2.1f;
                case -5 -> 1.0f;
                case -8 -> 1.4f;
                case -9 -> 1.4f;
                case -10 -> 1.8f;
                case -11 -> 2.3f;
                case -20, -22 -> 2.1f;
                case -21 -> 1.5f;
                default -> 1.6f;
            };
            muzzleFire(level, muzzle, fireDir(p, muzzle, shooter), fs, id != -5 && id != -8 || level.getGameTime() % 3 == 0);
            if (id == -8) {
                // C-RAM: a white-hot muzzle and a bright glow round the mount
                ClientLights.light(shooter != null ? shooter : (Object) p.shooter(), muzzle, 15, 2);
                for (int k = 0; k < 2; k++) {
                    level.addParticle(Reg.SPARK.get(), muzzle.x, muzzle.y, muzzle.z, R.nextGaussian() * 0.1, R.nextGaussian() * 0.1, R.nextGaussian() * 0.1);
                }
            }
            boolean airburst = id == -8 || id <= -20;
            if (id <= -8) {
                // explosive shells: a burst where they strike the ground or a target; flak and the C-RAM's rounds also
                // burst in the air at the end of their flight
                float size = id == -11 ? 1.0f : id == -22 || id == -20 ? 0.9f : id == -8 ? 0.45f : 0.7f;
                for (int i = 0; i < p.ends().size(); i++) {
                    byte kind = p.kinds().length > i ? p.kinds()[i] : 0;
                    if (kind == 0 && !airburst) continue;
                    if ((id == -8 || id == -11 || id == -9) && (i > 0 || level.getGameTime() % 2 != 0)) continue;
                    ClientFx.roundBurst(p.end(i), size, kind == 0 || kind == 2 && airburst);
                }
            }
            if (id == -4 || id == -6) {
                // 30 mm high explosive: a bright burst where it hits
                for (int i = 0; i < p.ends().size(); i++) {
                    Vec3 end = p.end(i);
                    if ((p.kinds().length > i ? p.kinds()[i] : 0) == 0 && id == -4) continue;
                    ClientLights.flash(end, 12, 3);
                    level.addParticle(Reg.FLAME.get(), end.x, end.y, end.z, 0, 0.02, 0);
                    level.addParticle(Reg.SMOKE.get(), end.x, end.y + 0.2, end.z, 0, 0.04, 0);
                    for (int k = 0; k < 4; k++) {
                        level.addParticle(Reg.SPARK.get(), end.x, end.y, end.z, R.nextGaussian() * 0.15, 0.1 + R.nextDouble() * 0.15, R.nextGaussian() * 0.15);
                    }
                }
            }
        } else {
            GunClient.playShot(level, t, muzzle, p.suppressed(), false);
            if (t.kind == GunType.Kind.FLAME) {
                Vec3 dir = p.ends().isEmpty() ? new Vec3(0, 0, 1) : p.end(0).subtract(muzzle).normalize();
                flames(level, muzzle, dir);
                return;
            }
            Vec3 fd = fireDir(p, muzzle, shooter);
            boolean ownView = shooter == mc.player && mc.options.getCameraType().isFirstPerson();
            if (p.suppressed()) {
                // a suppressor: no flame, only a thin wisp of gas
                level.addParticle(Reg.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, fd.x * 0.03, 0.012, fd.z * 0.03);
            } else if (!ownView) {
                // the first-person gun draws its own flash; everyone else sees this one
                float fs = switch (t.category) {
                    case PISTOL -> 0.8f;
                    case SMG -> 0.9f;
                    case SHOTGUN -> 1.5f;
                    case SNIPER -> 1.6f;
                    case HEAVY -> 1.4f;
                    default -> 1.1f;
                };
                muzzleFire(level, muzzle, fd, fs, true);
            } else {
                level.addParticle(Reg.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, fd.x * 0.04, 0.015, fd.z * 0.04);
            }
            // brass flying out of the ejection port of soldiers' guns (the player's own gun does that itself)
            if (shooter instanceof LivingEntity le && shooter != mc.player && t.kind == GunType.Kind.BULLET && t.category != GunType.Category.SHOTGUN) {
                Vec3 right = GunLogic.dir(0, le.getYHeadRot() + 90);
                Vec3 port = muzzle.subtract(fd.scale(0.6));
                level.addParticle(Reg.SHELL.get(), port.x, port.y, port.z, right.x * 0.12 + R.nextGaussian() * 0.02, 0.1, right.z * 0.12);
            }
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

    /** a bullet's flight after it went through something or glanced off: the tracer on, the impact at the end */
    public static void bulletPath(Net.BulletPath p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i + 1 < p.points().size(); i += 2) {
            Vec3 from = p.points().get(i), to = p.points().get(i + 1);
            byte kind = i / 2 < p.kinds().length ? p.kinds()[i / 2] : 0;
            tracer(level, from, to);
            Vec3 d = to.subtract(from);
            if (d.lengthSqr() < 1e-6) continue;
            d = d.normalize();
            BlockState st = kind == 1 ? level.getBlockState(net.minecraft.core.BlockPos.containing(to.add(d.scale(0.05)))) : null;
            impact(level, to, kind, st, d);
        }
    }

    /** the direction a shot went: towards its first end point, else where the shooter looks */
    private static Vec3 fireDir(Net.ShotFx p, Vec3 muzzle, @Nullable Entity shooter) {
        if (!p.ends().isEmpty()) {
            Vec3 d = p.end(0).subtract(muzzle);
            if (d.lengthSqr() > 1e-4) return d.normalize();
        }
        return shooter != null ? shooter.getLookAngle() : new Vec3(0, 0, 1);
    }

    /**
     * Fire at the muzzle: the directional flash (size: 1 = rifle), a puff of smoke pushed forward and a few sparks of
     * burning powder; heavy guns throw a bigger cloud.
     */
    public static void muzzleFire(ClientLevel level, Vec3 at, Vec3 dir, float size, boolean smoke) {
        level.addParticle(Reg.MUZZLE_FLASH.get(), at.x, at.y, at.z, dir.x * size, dir.y * size, dir.z * size);
        if (smoke) {
            int n = size > 1.5f ? 3 : 1;
            for (int i = 0; i < n; i++) {
                double sp = 0.03 + R.nextDouble() * 0.05 * size;
                level.addParticle(Reg.SMOKE.get(), at.x + dir.x * 0.2, at.y + dir.y * 0.2, at.z + dir.z * 0.2, dir.x * sp + R.nextGaussian() * 0.01,
                        dir.y * sp + 0.015, dir.z * sp + R.nextGaussian() * 0.01);
            }
        }
        int sparks = R.nextInt(3) + (size > 1.3f ? 2 : 0);
        for (int i = 0; i < sparks; i++) {
            double sp = 0.35 + R.nextDouble() * 0.4;
            level.addParticle(Reg.SPARK.get(), at.x, at.y, at.z, dir.x * sp + R.nextGaussian() * 0.06, dir.y * sp + R.nextGaussian() * 0.06,
                    dir.z * sp + R.nextGaussian() * 0.06);
        }
    }

    public static void tracer(ClientLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        level.addParticle(Reg.TRACER.get(), from.x, from.y, from.z, d.x, d.y, d.z);
    }

    public static void impact(ClientLevel level, Vec3 at, byte kind, @Nullable BlockState state, Vec3 dir) {
        if (kind == 1 && state != null && !state.isAir()) impactSound(level, at, state);
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
            // a round on armour rings and throws sparks; on a soldier it shows the hit
            boolean armour = !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(0.6),
                    e -> e instanceof com.gunsmod.entity.VehicleEntity || e instanceof com.gunsmod.entity.JeepEntity).isEmpty();
            if (armour) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.distanceToSqr(at) < 60 * 60) {
                    level.playLocalSound(at.x, at.y, at.z, Reg.sound("impact_armor"), SoundSource.BLOCKS, 1.2f, 0.85f + R.nextFloat() * 0.3f, false);
                }
                for (int i = 0; i < 6; i++) {
                    level.addParticle(Reg.SPARK.get(), at.x, at.y, at.z, -dir.x * 0.15 + R.nextGaussian() * 0.12, 0.08 + R.nextDouble() * 0.1, -dir.z * 0.15 + R.nextGaussian() * 0.12);
                }
                return;
            }
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, R.nextGaussian() * 0.1, 0.1, R.nextGaussian() * 0.1);
            }
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, 0, 0.1, 0);
        }
    }

    /** the round striking: a sound for the surface, and off hard ones now and then a ricochet whining away */
    private static void impactSound(ClientLevel level, Vec3 at, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(at) > 40 * 40) return;
        net.minecraft.world.level.block.SoundType st = state.getSoundType();
        String name;
        boolean hard = false;
        if (st == net.minecraft.world.level.block.SoundType.METAL || st == net.minecraft.world.level.block.SoundType.ANVIL
                || st == net.minecraft.world.level.block.SoundType.CHAIN || st == net.minecraft.world.level.block.SoundType.LANTERN
                || st == net.minecraft.world.level.block.SoundType.COPPER) {
            name = "impact_metal";
            hard = true;
        } else if (st == net.minecraft.world.level.block.SoundType.WOOD || st == net.minecraft.world.level.block.SoundType.BAMBOO_WOOD
                || st == net.minecraft.world.level.block.SoundType.CHERRY_WOOD || st == net.minecraft.world.level.block.SoundType.NETHER_WOOD) {
            name = "impact_wood";
        } else if (st == net.minecraft.world.level.block.SoundType.STONE || st == net.minecraft.world.level.block.SoundType.DEEPSLATE
                || st == net.minecraft.world.level.block.SoundType.POLISHED_DEEPSLATE || st == net.minecraft.world.level.block.SoundType.DEEPSLATE_BRICKS
                || st == net.minecraft.world.level.block.SoundType.TUFF || st == net.minecraft.world.level.block.SoundType.BASALT) {
            name = "impact_stone";
            hard = true;
        } else if (st == net.minecraft.world.level.block.SoundType.GLASS) {
            name = "impact_glass";
        } else {
            name = "impact_dirt";
        }
        level.playLocalSound(at.x, at.y, at.z, Reg.sound(name), SoundSource.BLOCKS, 1.0f, 0.9f + R.nextFloat() * 0.25f, false);
        if (hard && R.nextInt(6) == 0) level.playLocalSound(at.x, at.y, at.z, Reg.sound("ricochet"), SoundSource.BLOCKS, 1.2f, 0.85f + R.nextFloat() * 0.3f, false);
    }

    public static void flames(ClientLevel level, Vec3 from, Vec3 dir) {
        for (int i = 0; i < 9; i++) {
            double s = 0.55 + R.nextDouble() * 0.45;
            level.addParticle(Reg.FLAME.get(), from.x, from.y, from.z,
                    dir.x * s + R.nextGaussian() * 0.05, dir.y * s + R.nextGaussian() * 0.05 + 0.01, dir.z * s + R.nextGaussian() * 0.05);
        }
        if (R.nextInt(3) == 0) level.addParticle(Reg.SMOKE.get(), from.x + dir.x * 4, from.y + dir.y * 4 + 0.5, from.z + dir.z * 4, dir.x * 0.1, 0.08, dir.z * 0.1);
    }

    public static void battleState(Net.BattleState p) {
        BattleHud.update(p);
    }

    public static void hitMarker(Net.HitMarker p) {
        GunClient.onHitMarker(p.kill());
    }

    public static void launchStatus(Net.LaunchStatus p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LaunchScreen ls && ls.remote() == p.remote()) {
            ls.update(p);
        } else if (p.open()) {
            mc.setScreen(new LaunchScreen(p));
        }
    }

    public static void explosion(Net.ExplosionFx p) {
        ClientFx.blast(p.pos(), p.style(), p.size());
        ClientLights.flash(p.pos().add(0, 1, 0), 15, 6 + (int) (p.size() * 5));
    }

    public static void muzzle(Net.MuzzleFx p) {
        ClientFx.muzzleBlast(p.pos(), p.dir(), p.size());
        ClientLights.light("mz" + p.entity(), p.pos(), 15, 5);
    }
}
