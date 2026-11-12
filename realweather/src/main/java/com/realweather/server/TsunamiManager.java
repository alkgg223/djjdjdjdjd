package com.realweather.server;

import com.realweather.net.TsunamiSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Raises tsunamis (by command, or now and then from the sea near a player), runs them inland and applies them:
 * whatever stands in front of the wave is swept along and lifted, the weaker parts of buildings, trees and plants
 * are torn down in its face, and the land behind it stays under water until it drains back to the sea.
 */
public final class TsunamiManager {
    public static final List<Tsunami> WAVES = new ArrayList<>();
    private static int nextId = 1;
    private static boolean had;

    private TsunamiManager() {}

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        if (d.natural && d.tsunamiNatural && WAVES.isEmpty() && level.getGameTime() % 1200 == 0 && !players.isEmpty() && rng.nextInt(160) == 0) {
            natural(level, players.get(rng.nextInt(players.size())), rng);
        }
        if (WAVES.isEmpty()) {
            if (had) {
                had = false;
                sync(level);
            }
            return;
        }
        had = true;
        Iterator<Tsunami> it = WAVES.iterator();
        while (it.hasNext()) {
            Tsunami w = it.next();
            w.age++;
            if (w.stage == Tsunami.RUNNING) {
                w.front += w.speed();
                if (w.front >= w.run()) {
                    w.stage = Tsunami.DRAINING;
                }
            } else {
                w.drained = Math.min(1F, w.drained + 1F / 900F);
                if (w.drained >= 1F) {
                    it.remove();
                    continue;
                }
            }
            affectEntities(level, w, players);
            if (w.stage == Tsunami.RUNNING && d.tornadoDamage) {
                breakBlocks(level, w, players, rng);
            }
        }
        if (level.getGameTime() % 10 == 0) {
            sync(level);
        }
    }

    /** Starts a tsunami `distance` blocks out from `target`, rolling in towards it. Direction: degrees, or NaN for "from the sea". */
    public static Tsunami spawn(ServerLevel level, Vec3 target, int strength, float bearingDeg, RandomSource rng) {
        float bearing = Float.isNaN(bearingDeg) ? seaBearing(level, target, rng) : (float) Math.toRadians(bearingDeg);
        // `bearing` points from the target out to where the wave comes from.
        float fx = Mth.sin(bearing), fz = -Mth.cos(bearing);
        double dist = 240 + strength * 0.06;
        double ox = target.x + fx * dist;
        double oz = target.z + fz * dist;
        boolean sea = !Float.isNaN(bearingDeg) || hasSeaToward(level, target, bearing);
        float base = sea ? level.getSeaLevel() - 0.2F : level.getHeight(Heightmap.Types.OCEAN_FLOOR, Mth.floor(target.x), Mth.floor(target.z));
        if (!sea) {
            base = Math.max(level.getSeaLevel() - 0.2F, base - 0.5F);
        }
        Tsunami w = new Tsunami(nextId++, ox, oz, -fx, -fz, base, strength, rng.nextLong());
        WAVES.add(w);
        sync(level);
        return w;
    }

    public static void stop(ServerLevel level) {
        for (Tsunami w : WAVES) {
            w.stage = Tsunami.DRAINING;
        }
        sync(level);
    }

    /** The direction (bearing out from the point) with the most ocean, so the wave comes in from the sea. */
    private static float seaBearing(ServerLevel level, Vec3 p, RandomSource rng) {
        float best = rng.nextFloat() * Mth.TWO_PI;
        int bestCount = 0;
        for (int i = 0; i < 16; i++) {
            float b = i * Mth.TWO_PI / 16F;
            int count = 0;
            for (int r = 60; r <= 300; r += 60) {
                BlockPos pos = BlockPos.containing(p.x + Mth.sin(b) * r, level.getSeaLevel(), p.z - Mth.cos(b) * r);
                if (level.isLoaded(pos) && level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = b;
            }
        }
        return best;
    }

    private static boolean hasSeaToward(ServerLevel level, Vec3 p, float b) {
        for (int r = 60; r <= 300; r += 60) {
            BlockPos pos = BlockPos.containing(p.x + Mth.sin(b) * r, level.getSeaLevel(), p.z - Mth.cos(b) * r);
            if (level.isLoaded(pos) && level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
                return true;
            }
        }
        return false;
    }

    /** Rarely, a player near the coast sees the sea rise. */
    private static void natural(ServerLevel level, ServerPlayer p, RandomSource rng) {
        Vec3 pos = p.position();
        float b = seaBearing(level, pos, rng);
        if (!hasSeaToward(level, pos, b) || p.getY() > level.getSeaLevel() + 30) {
            return;
        }
        float r = rng.nextFloat();
        int strength = 20 + Math.round(r * r * 580F) + (rng.nextInt(40) == 0 ? 400 : 0);
        spawn(level, pos, strength, (float) Math.toDegrees(b), rng);
    }

    // ------------------------------------------------------------------------------------ effects
    private static void affectEntities(ServerLevel level, Tsunami w, List<ServerPlayer> players) {
        float h = w.height();
        float crest = w.baseY + h;
        float flood = w.floodY();
        float speed = w.stage == Tsunami.RUNNING ? w.speed() : -0.12F * (1F - w.drained);
        float width = w.width();
        boolean hurt = level.getGameTime() % 10 == 0;
        for (ServerPlayer p : players) {
            double s = w.along(p.getX(), p.getZ());
            if (s > w.front + 120 || Math.abs(w.across(p.getX(), p.getZ())) > width + 100) {
                continue;
            }
            AABB box = new AABB(p.getX() - 96, w.baseY - 6, p.getZ() - 96, p.getX() + 96, crest + 8, p.getZ() + 96);
            for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator()
                    && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                if (e.isPassenger()) {
                    continue;
                }
                double es = w.along(e.getX(), e.getZ());
                if (Math.abs(w.across(e.getX(), e.getZ())) > width || es > w.front + 2.5) {
                    continue;
                }
                boolean face = es > w.front - Math.max(4, h * 0.8) && e.getY() < crest + 1;
                boolean under = es <= w.front && e.getY() < flood;
                if (!face && !under) {
                    continue;
                }
                double k = e instanceof Player ? 0.18 : e instanceof ItemEntity ? 0.4 : e instanceof com.realweather.vehicle.StormVehicle ? 0.12
                        : e instanceof LivingEntity living ? 0.28 / Math.max(1.0, living.getBbWidth() * living.getBbHeight()) : 0.3;
                double push = face ? speed * (1.0 + Math.min(1.5, h / 12.0)) : speed * 0.55;
                Vec3 v = e.getDeltaMovement();
                double vy = v.y;
                double surface = face ? crest - 1.0 : flood - 0.4;
                if (e.getY() < surface) {
                    // Carried up to the surface of the water.
                    vy += (Math.min(0.5, (surface - e.getY()) * 0.08) - Math.max(0, v.y)) * (face ? 0.6 : 0.3);
                }
                e.setDeltaMovement(v.x + (w.dirX * push - v.x) * k, vy, v.z + (w.dirZ * push - v.z) * k);
                e.hurtMarked = true;
                e.clearFire();
                if (hurt && face && h > 5 && e instanceof LivingEntity living) {
                    living.hurt(level.damageSources().drown(), Math.min(8F, (h - 4F) * 0.25F));
                }
            }
        }
    }

    private static void breakBlocks(ServerLevel level, Tsunami w, List<ServerPlayer> players, RandomSource rng) {
        float h = w.height();
        if (h < 1.5F) {
            return;
        }
        float maxHard = Math.min(6F, 0.4F + w.strength * 0.0055F);
        int samples = 6 + w.strength / 25;
        float crest = w.baseY + h;
        for (ServerPlayer p : players) {
            double ps = w.along(p.getX(), p.getZ());
            if (Math.abs(ps - w.front) > 200) {
                continue;
            }
            double pl = w.across(p.getX(), p.getZ());
            for (int i = 0; i < samples; i++) {
                double l = Mth.clamp(pl + (rng.nextDouble() - 0.5) * 300, -w.width(), w.width());
                double s = w.front - rng.nextDouble() * 3;
                int x = Mth.floor(w.ox + w.dirX * s - w.dirZ * l);
                int z = Mth.floor(w.oz + w.dirZ * s + w.dirX * l);
                if (!level.isLoaded(new BlockPos(x, 0, z))) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = Math.min(top, Mth.floor(crest)); y >= Mth.floor(w.baseY) && y > top - 12; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || natural(state)) {
                        continue;
                    }
                    float hard = state.getDestroySpeed(level, pos);
                    if (hard < 0 || hard > maxHard || rng.nextFloat() > 0.75F * (1F - hard / (maxHard + 0.5F)) + 0.15F) {
                        continue;
                    }
                    level.destroyBlock(pos, rng.nextFloat() < 0.04F);
                }
            }
        }
    }

    /** The land itself stays: soil, sand, stone and the like. */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.BEDROCK) || s.is(BlockTags.ICE) || s.is(Blocks.SNOW_BLOCK);
    }

    // ------------------------------------------------------------------------------------ sync
    public static void sync(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, payload());
    }

    public static TsunamiSyncPayload payload() {
        List<TsunamiSyncPayload.Entry> list = new ArrayList<>();
        for (Tsunami w : WAVES) {
            list.add(new TsunamiSyncPayload.Entry(w.id, w.ox, w.oz, w.dirX, w.dirZ, w.baseY, w.strength, w.front, w.stage, w.drained, w.age, w.seed));
        }
        return new TsunamiSyncPayload(list);
    }
}
