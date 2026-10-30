package com.gunsmod.gun;

import com.gunsmod.Reg;
import com.gunsmod.block.ExplosiveBarrelBlock;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.MineEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.entity.RocketEntity;
import com.gunsmod.item.AmmoItem;
import com.gunsmod.item.GunItem;
import com.gunsmod.net.Net;
import com.gunsmod.world.PlayerTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server side shooting, damage and reloading. The spread maths is shared with the client prediction. */
public final class GunLogic {
    private GunLogic() {}

    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, Reg.id("bullet"));
    public static final ResourceKey<DamageType> FLAMES = ResourceKey.create(Registries.DAMAGE_TYPE, Reg.id("flames"));
    public static final ResourceKey<DamageType> ROADKILL = ResourceKey.create(Registries.DAMAGE_TYPE, Reg.id("roadkill"));

    private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();

    // ------------------------------------------------------------------------------------ shared maths
    public static Vec3 dir(float pitch, float yaw) {
        float f = Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float f1 = Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float f2 = -Mth.cos(-pitch * Mth.DEG_TO_RAD);
        float f3 = Mth.sin(-pitch * Mth.DEG_TO_RAD);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    public static float spread(GunType t, GunAttachments a, boolean aiming, Entity shooter) {
        float s = aiming ? t.adsSpread * a.adsSpreadMul() : t.hipSpread * a.hipSpreadMul();
        Vec3 v = shooter.getDeltaMovement();
        if (v.x * v.x + v.z * v.z > 0.01) s *= 1.35f;
        if (shooter.isCrouching()) s *= 0.75f;
        if (!shooter.onGround() && !shooter.isPassenger()) s *= 1.8f;
        return s;
    }

    /** pellet directions for one trigger pull */
    public static List<Vec3> directions(GunType t, float yaw, float pitch, long seed, float spreadDeg) {
        RandomSource r = RandomSource.create(seed);
        List<Vec3> out = new ArrayList<>(t.pellets);
        for (int i = 0; i < t.pellets; i++) {
            float dy = (float) (r.nextGaussian() * spreadDeg * 0.45);
            float dp = (float) (r.nextGaussian() * spreadDeg * 0.45);
            out.add(dir(pitch + dp, yaw + dy));
        }
        return out;
    }

    public static boolean canHit(Entity shooter, Entity e) {
        if (e == shooter || !e.isAlive() || e.isSpectator() || !e.isPickable()) return false;
        if (e instanceof MineEntity) return false;    // bullets fly over mines
        if (shooter.getVehicle() != null && (e == shooter.getVehicle() || e.getVehicle() == shooter.getVehicle())) return false;
        if (e.getVehicle() == shooter) return false;
        if (VehicleEntity.shieldOf(e) != null) return false;   // the vehicle takes the hit
        return !BotTeam.friendly(shooter, e);
    }

    public record Trace(Vec3 end, byte kind, Entity entity, boolean headshot, BlockPos block) {}

    /** ray cast against blocks then entities */
    public static Trace trace(Level level, Entity shooter, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 blockEnd = bh.getType() == HitResult.Type.MISS ? to : bh.getLocation();
        Entity best = null;
        Vec3 bestHit = null;
        double bestD = Double.MAX_VALUE;
        AABB box = new AABB(from, blockEnd).inflate(1.0);
        for (Entity e : level.getEntities(shooter, box, e -> canHit(shooter, e))) {
            var hit = e.getBoundingBox().inflate(e instanceof JeepEntity || e instanceof VehicleEntity ? 0.0 : 0.12).clip(from, blockEnd);
            if (hit.isPresent()) {
                double d = from.distanceToSqr(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                    bestHit = hit.get();
                }
            }
        }
        if (best != null) {
            boolean head = best instanceof LivingEntity le && !(best instanceof JeepEntity) && !(best instanceof VehicleEntity) && le.getBbHeight() > 1.2f
                    && bestHit.y > le.getEyeY() - 0.28;
            return new Trace(bestHit, (byte) 2, best, head, null);
        }
        if (bh.getType() != HitResult.Type.MISS) return new Trace(blockEnd, (byte) 1, null, false, bh.getBlockPos());
        return new Trace(to, (byte) 0, null, false, null);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key, Entity direct, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), direct, owner);
    }

    // ------------------------------------------------------------------------------------ player entry points
    public static void playerShoot(ServerPlayer player, float yaw, float pitch, long seed, boolean aiming) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || GunItem.reloading(stack) || player.isSpectator()) return;
        if (player.getVehicle() instanceof JeepEntity jeep && jeep.isDriver(player)) return;
        GunType t = gun.type;
        long now = player.level().getGameTime();
        Long last = LAST_SHOT.get(player.getUUID());
        if (last != null && now - last < Math.max(1, t.fireDelay - 1) && now >= last) return;
        int ammo = GunItem.ammo(stack);
        if (ammo <= 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), Reg.sound("dry_fire"), SoundSource.PLAYERS, 0.8f, 1f);
            return;
        }
        LAST_SHOT.put(player.getUUID(), now);
        GunItem.setAmmo(stack, ammo - 1);
        fire(player, stack, t, yaw, pitch, seed, aiming, 1f, player);
    }

    /** fire one trigger pull. {@code except} is excluded from the FX packet (it predicted the effects itself) */
    public static void fire(LivingEntity shooter, ItemStack stack, GunType t, float yaw, float pitch, long seed, boolean aiming,
                            float spreadMul, ServerPlayer except) {
        Level level = shooter.level();
        GunAttachments att = GunItem.attachments(stack);
        Vec3 eye = shooter.getEyePosition();
        boolean suppressed = att.has(Attachment.SUPPRESSOR);
        float spread = spread(t, att, aiming, shooter) * spreadMul;
        List<Vec3> ends = new ArrayList<>();
        List<Byte> kinds = new ArrayList<>();
        switch (t.kind) {
            case BULLET -> {
                for (Vec3 d : directions(t, yaw, pitch, seed, spread)) {
                    Trace tr = trace(level, shooter, eye, d, t.range);
                    ends.add(tr.end);
                    kinds.add(tr.kind);
                    if (tr.entity != null) {
                        float dmg = t.damage * falloff(t, eye.distanceTo(tr.end));
                        if (tr.headshot) dmg *= t.headshotMultiplier();
                        if (suppressed) dmg *= 0.92f;
                        damage(shooter, tr.entity, dmg, tr.headshot, t);
                    } else if (tr.block != null) {
                        impactBlock(level, shooter, tr.block);
                    }
                }
            }
            case ROCKET, GRENADE -> {
                Vec3 d = directions(t, yaw, pitch, seed, spread).get(0);
                RocketEntity r = new RocketEntity(level, shooter, t.kind == GunType.Kind.ROCKET ? 0 : 1);
                Vec3 start = eye.add(d.scale(0.8)).add(0, -0.15, 0);
                r.setPos(start.x, start.y, start.z);
                r.shoot(d.x, d.y, d.z, t.kind == GunType.Kind.ROCKET ? 2.6f : 1.55f, 0f);
                level.addFreshEntity(r);
                ends.add(start.add(d.scale(2)));
                kinds.add((byte) 0);
            }
            case FLAME -> {
                flame(level, shooter, eye, dir(pitch, yaw), t);
                ends.add(eye.add(dir(pitch, yaw).scale(t.range)));
                kinds.add((byte) 0);
            }
        }
        byte[] k = new byte[kinds.size()];
        for (int i = 0; i < k.length; i++) k[i] = kinds.get(i);
        Net.ShotFx fx = new Net.ShotFx(shooter.getId(), t.ordinal(), eye, relative(eye, ends), k, suppressed);
        if (except != null) PacketDistributor.sendToPlayersTrackingEntity(shooter, fx);
        else PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, fx);
        // soldiers hear gunfire
        if (level instanceof ServerLevel sl && !suppressed) BotEntity.alertGunfire(sl, shooter, t.range > 100 ? 64 : 40);
    }

    public static List<Vec3> relative(Vec3 origin, List<Vec3> ends) {
        List<Vec3> l = new ArrayList<>(ends.size());
        for (Vec3 e : ends) l.add(e.subtract(origin));
        return l;
    }

    private static float falloff(GunType t, double dist) {
        double start = t.range * 0.55;
        if (dist <= start) return 1f;
        return (float) Mth.clamp(1.0 - (dist - start) / (t.range - start) * 0.5, 0.5, 1.0);
    }

    public static void damage(LivingEntity shooter, Entity target, float dmg, boolean headshot, GunType t) {
        DamageSource src = source(shooter.level(), BULLET, shooter, shooter);
        boolean wasAlive = target.isAlive();
        if (target instanceof JeepEntity jeep) {
            jeep.hurtByBullet(src, dmg * (t == GunType.M107 ? 2.5f : t.category == GunType.Category.SNIPER ? 1.2f : 0.6f));
        } else if (target instanceof VehicleEntity v) {
            v.hurtByBullet(src, dmg * (t == GunType.M107 ? 3f : 1f));
        } else {
            target.invulnerableTime = 0;
            target.hurt(src, dmg);
            target.invulnerableTime = 0;
        }
        if (shooter instanceof ServerPlayer sp && target instanceof LivingEntity) {
            PacketDistributor.sendToPlayer(sp, new Net.HitMarker(wasAlive && !target.isAlive(), headshot));
        }
        if (target instanceof BotEntity bot) bot.onShotBy(shooter);
    }

    private static void impactBlock(Level level, Entity shooter, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof ExplosiveBarrelBlock barrel) {
            barrel.detonate(level, pos, shooter);
            return;
        }
        if ((st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES)) && st.getDestroySpeed(level, pos) >= 0
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            level.destroyBlock(pos, false, shooter);
        }
    }

    private static void flame(Level level, LivingEntity shooter, Vec3 eye, Vec3 look, GunType t) {
        double range = t.range;
        AABB box = shooter.getBoundingBox().expandTowards(look.scale(range)).inflate(2.5);
        DamageSource src = source(level, FLAMES, shooter, shooter);
        for (Entity e : level.getEntities(shooter, box, e -> canHit(shooter, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + 1 || dist < 0.01) continue;
            double cos = to.normalize().dot(look);
            if (cos < Math.cos(Math.toRadians(16 + 10 / Math.max(1, dist)))) continue;
            BlockHitResult bh = level.clip(new ClipContext(eye, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (bh.getType() != HitResult.Type.MISS) continue;
            if (e instanceof JeepEntity jeep) {
                jeep.hurtByBullet(src, t.damage * 0.5f);
            } else {
                e.invulnerableTime = 0;
                e.hurt(src, t.damage);
                e.invulnerableTime = 0;
                e.igniteForSeconds(6);
            }
            if (e instanceof BotEntity bot) bot.onShotBy(shooter);
        }
        // set the ground on fire at the end of the stream
        if (level.random.nextInt(5) == 0 && (!(shooter instanceof BotEntity) || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) {
            Vec3 to = eye.add(look.scale(range * (0.6 + level.random.nextDouble() * 0.4)));
            BlockHitResult bh = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (bh.getType() == HitResult.Type.BLOCK) {
                BlockPos p = bh.getBlockPos().relative(bh.getDirection());
                if (level.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(level, p, bh.getDirection())) {
                    level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                }
                BlockState hit = level.getBlockState(bh.getBlockPos());
                if (hit.getBlock() instanceof ExplosiveBarrelBlock barrel) barrel.detonate(level, bh.getBlockPos(), shooter);
                if (hit.is(Blocks.TNT)) {
                    hit.onCaughtFire(level, bh.getBlockPos(), bh.getDirection(), shooter);
                    level.removeBlock(bh.getBlockPos(), false);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------ reloading
    public static void startReload(LivingEntity e, ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) return;
        GunType t = gun.type;
        GunAttachments a = GunItem.attachments(stack);
        int mag = a.magSize(t);
        if (GunItem.ammo(stack) >= mag || GunItem.reloading(stack)) return;
        if (e instanceof Player p && !PlayerTeam.freeAmmo(p) && countAmmo(p, t.ammo) <= 0) {
            p.displayClientMessage(Component.translatable("gunsmod.msg.no_ammo", Component.translatable("item.gunsmod." + t.ammo.itemId)), true);
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), Reg.sound("dry_fire"), SoundSource.PLAYERS, 0.8f, 1f);
            return;
        }
        stack.set(Reg.RELOAD.get(), e.level().getGameTime() + a.reloadTicks(t));
        String snd = switch (t) {
            case DOUBLE_BARREL, M79 -> "break_open";
            case PUMP_SHOTGUN -> "shell_insert";
            case REVOLVER -> "break_open";
            default -> "mag_out";
        };
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), Reg.sound(snd), SoundSource.PLAYERS, 0.9f, 1f);
    }

    public static void finishReload(LivingEntity e, ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) return;
        GunType t = gun.type;
        int mag = GunItem.attachments(stack).magSize(t);
        int ammo = GunItem.ammo(stack);
        int need = Math.max(0, mag - ammo);
        int got = need;
        if (e instanceof Player p && !PlayerTeam.freeAmmo(p)) got = takeAmmo(p, t.ammo, need);
        GunItem.setAmmo(stack, ammo + got);
        stack.remove(Reg.RELOAD.get());
        String snd = switch (t.category) {
            case SNIPER -> "bolt";
            case SHOTGUN -> t == GunType.AUTO_SHOTGUN ? "mag_in" : "pump";
            case EXPLOSIVE -> "mag_in";
            default -> "mag_in";
        };
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), Reg.sound(snd), SoundSource.PLAYERS, 0.9f, 1f);
        if (t.category == GunType.Category.RIFLE || t.category == GunType.Category.SMG || t.category == GunType.Category.HEAVY) {
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), Reg.sound("bolt"), SoundSource.PLAYERS, 0.6f, 1.15f);
        }
    }

    public static int countAmmo(Player p, AmmoType type) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof AmmoItem a && a.type == type) n += a.rounds(s);
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (s.getItem() instanceof AmmoItem a && a.type == type) n += a.rounds(s);
        }
        return n;
    }

    public static int takeAmmo(Player p, AmmoType type, int need) {
        int got = 0;
        List<ItemStack> all = new ArrayList<>(p.getInventory().offhand);
        all.addAll(p.getInventory().items);
        for (ItemStack s : all) {
            if (got >= need) break;
            if (!(s.getItem() instanceof AmmoItem a) || a.type != type) continue;
            if (type.isBox()) {
                int left = s.getMaxDamage() - s.getDamageValue();
                int take = Math.min(left, need - got);
                got += take;
                if (take >= left) s.shrink(1);
                else s.setDamageValue(s.getDamageValue() + take);
            } else {
                int take = Math.min(s.getCount(), need - got);
                got += take;
                s.shrink(take);
            }
        }
        return got;
    }
}
