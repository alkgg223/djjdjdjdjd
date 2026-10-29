package com.realweather.server;

import com.realweather.RealWeather;
import com.realweather.net.TornadoSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Spawns, moves and applies the tornadoes: winds on entities, damage to the ground, lightning from the storm. */
public final class TornadoManager {
    public static final ResourceKey<DamageType> TORNADO_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RealWeather.id("tornado"));
    private static boolean hadTornadoes;
    /** Blocks torn up since the world started (diagnostics). */
    public static int blocksDestroyed;

    private TornadoManager() {}

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        // Natural tornadoes: rare, only from strong thunderstorms over open land.
        if (d.tornadoNatural && level.isThundering() && WeatherServer.thunder > 0.45F && d.tornadoes.size() < 2 && !players.isEmpty()
                && rng.nextInt(9600) == 0) {
            ServerPlayer p = players.get(rng.nextInt(players.size()));
            int rating = naturalRating(rng);
            Tornado t = spawnApproaching(level, d, p.position(), rating, 110 + rng.nextInt(90), rng);
            if (t != null && !suitable(level, t)) {
                d.tornadoes.remove(t);
            }
        }
        if (d.tornadoes.isEmpty()) {
            if (hadTornadoes) {
                hadTornadoes = false;
                sync(level, d);
            }
            return;
        }
        hadTornadoes = true;
        if (!level.isThundering()) {
            // A tornado comes from a supercell: keep the storm going while one is alive.
            level.setWeatherParameters(0, 3600, true, true);
        }
        Iterator<Tornado> it = d.tornadoes.iterator();
        while (it.hasNext()) {
            Tornado t = it.next();
            if (!t.tickLife()) {
                it.remove();
                d.setDirty();
                continue;
            }
            move(level, t, rng);
            boolean near = false;
            for (ServerPlayer p : players) {
                if (p.distanceToSqr(t.x, p.getY(), t.z) < 1100 * 1100) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                t.dissipate();
            }
            if (!level.isLoaded(BlockPos.containing(t.x, 0, t.z))) {
                continue;
            }
            if (t.contact > 0.25F) {
                affectEntities(level, t);
                if (d.tornadoDamage && t.contact > 0.6F) {
                    damageGround(level, t, rng);
                }
            }
            stormLightning(level, t, rng);
        }
        if (level.getGameTime() % 2 == 0) {
            sync(level, d);
        }
        if (level.getGameTime() % 100 == 0) {
            d.setDirty();
        }
    }

    static int naturalRating(RandomSource rng) {
        float r = rng.nextFloat();
        return r < 0.35F ? 0 : r < 0.65F ? 1 : r < 0.83F ? 2 : r < 0.93F ? 3 : r < 0.98F ? 4 : 5;
    }

    private static boolean suitable(ServerLevel level, Tornado t) {
        BlockPos pos = BlockPos.containing(t.x, t.groundY, t.z);
        if (!level.isLoaded(pos)) {
            return true;
        }
        Holder<Biome> biome = level.getBiome(pos);
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_MOUNTAIN)
                && biome.value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
    }

    /** Spawns a tornado `distance` blocks from `target`, heading roughly towards it. */
    public static Tornado spawnApproaching(ServerLevel level, WeatherData d, Vec3 target, int rating, double distance, RandomSource rng) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double x = target.x + Math.sin(ang) * distance;
        double z = target.z - Math.cos(ang) * distance;
        // Bearing from the tornado to the target, with some spread so it does not always score a direct hit.
        float heading = (float) Math.atan2(target.x - x, -(target.z - z)) + (rng.nextFloat() - 0.5F) * 0.6F;
        return add(level, d, x, z, heading, rating, rng);
    }

    public static Tornado spawnAt(ServerLevel level, WeatherData d, Vec3 pos, int rating, RandomSource rng) {
        return add(level, d, pos.x, pos.z, rng.nextFloat() * (float) Math.PI * 2, rating, rng);
    }

    private static Tornado add(ServerLevel level, WeatherData d, double x, double z, float heading, int rating, RandomSource rng) {
        float ground = groundAt(level, x, z, 64F);
        float speed = 0.12F + rng.nextFloat() * 0.1F + rating * 0.015F;
        int life = (60 + rating * 30 + rng.nextInt(60)) * 20;
        Tornado t = new Tornado(d.nextTornadoId++, x, z, ground, heading, speed, rating, life, rng.nextLong());
        d.tornadoes.add(t);
        d.setDirty();
        if (!level.isThundering()) {
            level.setWeatherParameters(0, life + 2400, true, true);
        }
        sync(level, d);
        return t;
    }

    private static float groundAt(ServerLevel level, double x, double z, float fallback) {
        BlockPos col = BlockPos.containing(x, 0, z);
        if (!level.isLoaded(col)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
    }

    private static void move(ServerLevel level, Tornado t, RandomSource rng) {
        // Gentle meandering along the storm's track.
        t.heading += (float) (Math.sin((t.age + t.seed % 1000) * 0.004) * 0.0025 + (rng.nextFloat() - 0.5F) * 0.002F);
        t.x += Math.sin(t.heading) * t.speed;
        t.z -= Math.cos(t.heading) * t.speed;
        float g = groundAt(level, t.x, t.z, t.groundY);
        t.groundY += (g - t.groundY) * 0.1F;
    }

    private static void affectEntities(ServerLevel level, Tornado t) {
        double rc = Math.max(2.0, t.coreRadius());
        double reach = rc * 4 + 10;
        double vmax = t.wind() * t.contact;
        if (vmax < 0.02) {
            return;
        }
        AABB box = new AABB(t.x - reach, t.groundY - 8, t.z - reach, t.x + reach, t.groundY + 70, t.z + reach);
        List<Entity> list = level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator() && !e.isPassenger()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && !(e instanceof LightningBolt));
        float lift = Tornado.LIFT[t.rating];
        boolean hurt = level.getGameTime() % 10 == 0;
        for (Entity e : list) {
            double dx = e.getX() - t.x;
            double dz = e.getZ() - t.z;
            double r = Math.sqrt(dx * dx + dz * dz) + 0.01;
            if (r > reach) {
                continue;
            }
            boolean exposed = level.canSeeSky(BlockPos.containing(e.getX(), e.getEyeY(), e.getZ()));
            double shelter = exposed ? 1.0 : 0.15;
            // Rankine vortex: solid rotation inside the core, decaying outside.
            double vt = r < rc ? vmax * r / rc : vmax * Math.pow(rc / r, 0.7);
            double inflow = vt * 0.32;
            double tx = dz / r;
            double tz = -dx / r;
            double rx = dx / r;
            double rz = dz / r;
            double height = e.getY() - t.groundY;
            double ux = tx * vt - rx * inflow;
            double uz = tz * vt - rz * inflow;
            double uy = 0;
            if (r < rc * 1.4 && lift > 0) {
                if (height < lift) {
                    uy = vmax * 0.6 * (1 - r / (rc * 1.4));
                } else {
                    // Flung out of the top of the vortex.
                    ux += rx * vmax * 0.8;
                    uz += rz * vmax * 0.8;
                }
            }
            double k = e instanceof Player ? 0.07 : e instanceof ItemEntity ? 0.3 : e instanceof LivingEntity living ? 0.14 / Math.max(1.0, living.getBbWidth() * living.getBbHeight()) : 0.2;
            k *= shelter * (0.4 + 0.6 * t.strength);
            Vec3 v = e.getDeltaMovement();
            double vy = v.y;
            if (uy > 0) {
                vy += (uy - Math.max(0, v.y)) * k * 1.5;
            }
            e.setDeltaMovement(v.x + (ux - v.x) * k, vy, v.z + (uz - v.z) * k);
            e.hurtMarked = true;
            if (hurt && r < rc && exposed && Tornado.DAMAGE[t.rating] > 0 && e instanceof LivingEntity living) {
                living.hurt(damage(level), Tornado.DAMAGE[t.rating] * 0.5F * t.strength);
            }
        }
    }

    private static DamageSource damage(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TORNADO_DAMAGE));
    }

    private static void damageGround(ServerLevel level, Tornado t, RandomSource rng) {
        int samples = Math.round(Tornado.BREAKS[t.rating] * t.strength * t.contact);
        float maxHard = Tornado.HARDNESS[t.rating];
        double rc = t.coreRadius() * 1.2;
        for (int i = 0; i < samples; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(rng.nextDouble()) * rc;
            int x = Mth.floor(t.x + Math.cos(a) * r);
            int z = Mth.floor(t.z + Math.sin(a) * r);
            if (!level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            // Stronger winds closer to the centre strip deeper.
            int depth = 1 + (t.rating >= 3 ? 1 : 0) + (t.rating >= 5 ? 1 : 0);
            for (int k = 0; k < depth; k++) {
                BlockPos pos = new BlockPos(x, top - k, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
                    break;
                }
                float hard = state.getDestroySpeed(level, pos);
                if (hard < 0 || hard > maxHard) {
                    break;
                }
                float chance = 0.7F * (1F - hard / (maxHard + 0.5F)) + 0.15F;
                if (rng.nextFloat() >= chance) {
                    break;
                }
                level.destroyBlock(pos, rng.nextFloat() < 0.03F);
                blocksDestroyed++;
            }
        }
    }

    private static void stormLightning(ServerLevel level, Tornado t, RandomSource rng) {
        if (rng.nextFloat() >= 0.004F + t.rating * 0.003F) {
            return;
        }
        double a = rng.nextDouble() * Math.PI * 2;
        double r = t.coreRadius() * 2 + 25 + rng.nextDouble() * 110;
        BlockPos col = BlockPos.containing(t.x + Math.cos(a) * r, 0, t.z + Math.sin(a) * r);
        if (!level.isLoaded(col)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, col)));
            bolt.setVisualOnly(rng.nextFloat() > 0.15F);
            level.addFreshEntity(bolt);
        }
    }

    public static void sync(ServerLevel level, WeatherData d) {
        PacketDistributor.sendToPlayersInDimension(level, payload(d));
    }

    public static TornadoSyncPayload payload(WeatherData d) {
        List<TornadoSyncPayload.Entry> list = new ArrayList<>();
        for (Tornado t : d.tornadoes) {
            list.add(new TornadoSyncPayload.Entry(t.id, t.x, t.z, t.groundY, t.heading, t.speed, t.rating, t.stage, t.contact, t.strength, t.seed));
        }
        return new TornadoSyncPayload(list);
    }

    /** Nearest tornado to a position, or null. */
    public static Tornado nearest(WeatherData d, double x, double z, double maxDist) {
        Tornado best = null;
        double bestD = maxDist * maxDist;
        for (Tornado t : d.tornadoes) {
            double dd = (t.x - x) * (t.x - x) + (t.z - z) * (t.z - z);
            if (dd < bestD) {
                bestD = dd;
                best = t;
            }
        }
        return best;
    }
}
