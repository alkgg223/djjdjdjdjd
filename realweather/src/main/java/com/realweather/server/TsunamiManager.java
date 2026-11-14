package com.realweather.server;

import com.realweather.net.TsunamiSyncPayload;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
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
 * are torn down in its face, anyone caught under the water runs out of air, cars have their windows smashed in, and
 * the land behind it is left under real water (filled in around the players) until it drains away at the end of
 * the tsunami's five minutes.
 */
public final class TsunamiManager {
    public static final List<Tsunami> WAVES = new ArrayList<>();
    private static int nextId = 1;
    private static boolean had;
    /** Columns already filled with flood water, per tsunami. */
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<LongOpenHashSet> FILLED = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    /** Real water blocks placed per tick at most, and per tsunami in all. */
    private static final int PLACE_BUDGET = 700, MAX_BLOCKS = 150000, FILL_RADIUS = 36;

    private TsunamiManager() {}

    /** A new world (or the server stopping): tsunamis don't carry over; their water is drained by the saved list. */
    public static void reset() {
        WAVES.clear();
        FILLED.clear();
        had = false;
    }

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        if (d.natural && d.tsunamiNatural && WAVES.isEmpty() && level.getGameTime() % 1200 == 0 && !players.isEmpty() && rng.nextInt(160) == 0) {
            natural(level, players.get(rng.nextInt(players.size())), rng);
        }
        if (level.getGameTime() % 40 == 0) {
            // Water left over from tsunamis whose land wasn't loaded when they ended (or from before a restart).
            LongArrayList orphans = d.tsunamiWater.get(0);
            if (orphans != null && !orphans.isEmpty()) {
                drain(level, d, orphans, Float.NaN, null);
            }
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
        int budget = PLACE_BUDGET;
        while (it.hasNext()) {
            Tsunami w = it.next();
            w.age++;
            if (w.stage == Tsunami.RUNNING) {
                w.front += w.speed();
                if (w.front >= w.run() || w.stopping) {
                    w.stage = Tsunami.DRAINING;
                }
            }
            if (w.draining()) {
                w.drained = Math.min(1F, w.drained + 1F / Tsunami.DRAIN_TICKS);
            }
            LongArrayList water = waterOf(d, w.id);
            if (w.drained >= 1F) {
                // Whatever water is left in unloaded land goes on the list to drain when it loads again.
                drain(level, d, water, Float.NaN, w);
                if (!water.isEmpty()) {
                    waterOf(d, 0).addAll(water);
                }
                d.tsunamiWater.remove(w.id);
                FILLED.remove(w.id);
                d.setDirty();
                it.remove();
                continue;
            }
            affectEntities(level, w, players);
            if (w.stage == Tsunami.RUNNING && d.tornadoDamage) {
                breakBlocks(level, w, players, rng);
            }
            if (d.tsunamiFlood && !w.draining() && budget > 0) {
                budget = fill(level, d, w, water, players, budget);
            }
            if (w.draining() && level.getGameTime() % 10 == 0) {
                drain(level, d, water, w.drained, w);
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

    private static LongArrayList waterOf(WeatherData d, int id) {
        LongArrayList l = d.tsunamiWater.get(id);
        if (l == null) {
            l = new LongArrayList();
            d.tsunamiWater.put(id, l);
        }
        return l;
    }

    private static LongOpenHashSet filledOf(int id) {
        LongOpenHashSet s = FILLED.get(id);
        if (s == null) {
            s = new LongOpenHashSet();
            FILLED.put(id, s);
        }
        return s;
    }

    /** Ends every tsunami at once and takes all of their water away (what isn't loaded drains when it loads). */
    public static void clearNow(ServerLevel level) {
        WeatherData d = WeatherData.get(level);
        for (Tsunami w : WAVES) {
            LongArrayList water = d.tsunamiWater.remove(w.id);
            if (water != null) {
                drain(level, d, water, Float.NaN, null);
                if (!water.isEmpty()) {
                    waterOf(d, 0).addAll(water);
                }
            }
        }
        WAVES.clear();
        FILLED.clear();
        d.setDirty();
        sync(level);
    }

    public static void stop(ServerLevel level) {
        for (Tsunami w : WAVES) {
            w.stage = Tsunami.DRAINING;
            w.stopping = true;
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
        boolean running = w.stage == Tsunami.RUNNING;
        float speed = running ? w.speed() : -0.05F * (1F - w.drained);
        float width = w.width();
        boolean hurt = level.getGameTime() % 10 == 0;
        for (ServerPlayer p : players) {
            double s = w.along(p.getX(), p.getZ());
            if (s > w.front + 120 || Math.abs(w.across(p.getX(), p.getZ())) > width + 100) {
                continue;
            }
            float g0 = w.ground(level, p.getX(), p.getZ());
            AABB box = new AABB(p.getX() - 96, g0 - 40, p.getZ() - 96, p.getX() + 96, g0 + h + 40, p.getZ() + 96);
            for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator()
                    && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                if (e.isPassenger()) {
                    continue;
                }
                double es = w.along(e.getX(), e.getZ());
                if (Math.abs(w.across(e.getX(), e.getZ())) > width || es > w.front + 2.5) {
                    continue;
                }
                float g = w.ground(level, e.getX(), e.getZ());
                float crest = g + h;
                float flood = w.floodSurface(level, e.getX(), e.getZ());
                boolean face = running && h > 0.3F && es > w.front - Math.max(4, h * 0.8) && e.getY() < crest + 1 && e.getY() > g - 6;
                boolean under = !Float.isNaN(flood) && e.getY() < flood && e.getY() > flood - 30;
                if (!face && !under) {
                    continue;
                }
                double k = e instanceof Player ? 0.18 : e instanceof ItemEntity ? 0.4 : e instanceof com.realweather.vehicle.StormVehicle ? 0.12
                        : e instanceof LivingEntity living ? 0.28 / Math.max(1.0, living.getBbWidth() * living.getBbHeight()) : 0.3;
                double push = face ? speed * (1.0 + Math.min(1.5, h / 12.0)) : speed * 0.55;
                Vec3 v = e.getDeltaMovement();
                double vy = v.y;
                // Living things are churned along inside the wave; debris and cars bob up to its top. In the flood
                // behind it, real water does its own floating and sinking; elsewhere the water lifts things to the top.
                double surface = face ? (e instanceof LivingEntity && !(e instanceof com.realweather.vehicle.StormVehicle) ? crest - Math.max(1.5, h * 0.45)
                        : crest - 1.0) : flood - 0.4;
                boolean lift = face || (!e.isInWater() && !(e instanceof com.realweather.vehicle.StormVehicle));
                if (lift && e.getY() < surface) {
                    vy += (Math.min(0.5, (surface - e.getY()) * 0.08) - Math.max(0, v.y)) * (face ? 0.6 : 0.3);
                } else if (face && e.getY() > surface + 1 && e instanceof LivingEntity) {
                    // Dragged back under.
                    vy += (-0.25 - Math.min(0, v.y)) * 0.4;
                }
                e.setDeltaMovement(v.x + (w.dirX * push - v.x) * k, vy, v.z + (w.dirZ * push - v.z) * k);
                e.hurtMarked = true;
                e.clearFire();
                if (e instanceof com.realweather.vehicle.StormVehicle car) {
                    // The wall of water smashes the windows in and floods the cabin.
                    if (face && h > 1.2F) {
                        car.smashGlass();
                    }
                    car.soak((float) ((face ? crest : flood) - car.getY()));
                    continue;
                }
                if (hurt && face && h > 5 && e instanceof LivingEntity living) {
                    living.hurt(level.damageSources().drown(), Math.min(8F, (h - 4F) * 0.25F));
                }
                // Under the water: the air runs out as it would in the sea.
                double eye = e.getEyeY();
                if (e instanceof LivingEntity living) {
                    if (face && eye < crest - 0.3) {
                        // Tumbling in the wave: the air goes fast.
                        suffocate(level, living, 4);
                    } else if (under && eye < flood) {
                        suffocate(level, living, 1);
                    }
                }
            }
        }
    }

    /** One tick without air for something under the tsunami's water (net of the air it would get back on land). */
    public static void suffocate(ServerLevel level, LivingEntity e) {
        suffocate(level, e, 1);
    }

    /** As {@link #suffocate(ServerLevel, LivingEntity)}, losing {@code rate} air a tick (1 is the rate in the sea). */
    public static void suffocate(ServerLevel level, LivingEntity e, int rate) {
        if (e.canBreatheUnderwater() || (e instanceof Player p && p.getAbilities().invulnerable)) {
            return;
        }
        if (e.isEyeInFluid(FluidTags.WATER)) {
            // Real water already takes one a tick and gives none back.
            rate -= 1;
            if (rate <= 0) {
                return;
            }
            e.setAirSupply(e.getAirSupply() + 4);
        }
        int air = e.getAirSupply() - 4 - rate;
        if (air <= -20) {
            air = 0;
            e.hurt(level.damageSources().drown(), 2F);
        }
        e.setAirSupply(air);
    }

    private static void breakBlocks(ServerLevel level, Tsunami w, List<ServerPlayer> players, RandomSource rng) {
        float h = w.height();
        if (h < 1.5F) {
            return;
        }
        float maxHard = Math.min(6F, 0.4F + w.strength * 0.0055F);
        int samples = 6 + w.strength / 25;
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
                float g = w.ground(level, x + 0.5, z + 0.5);
                float crest = g + h;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = Math.min(top, Mth.floor(crest)); y >= Mth.floor(g) - 1 && y > top - 12; y--) {
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

    // ------------------------------------------------------------------------------------ the flood
    /**
     * Fills the flooded land around each player with real water (up to the flood surface, nearest the player first),
     * a limited number of blocks per tick. Returns what is left of the budget.
     */
    private static int fill(ServerLevel level, WeatherData d, Tsunami w, LongArrayList water, List<ServerPlayer> players, int budget) {
        if (water.size() >= MAX_BLOCKS) {
            return budget;
        }
        LongOpenHashSet done = filledOf(w.id);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState waterState = Blocks.WATER.defaultBlockState();
        int before = water.size();
        for (ServerPlayer p : players) {
            if (p.isSpectator()) {
                continue;
            }
            double ps = w.along(p.getX(), p.getZ());
            if (ps > w.front + FILL_RADIUS || Math.abs(w.across(p.getX(), p.getZ())) > w.width() + FILL_RADIUS) {
                continue;
            }
            int px = p.getBlockX(), pz = p.getBlockZ();
            // Square rings outward from the player, nearest first.
            for (int r = 0; r <= FILL_RADIUS && budget > 0; r++) {
                int cells = r == 0 ? 1 : 8 * r;
                for (int c = 0; c < cells && budget > 0; c++) {
                    {
                        int dx, dz;
                        if (r == 0) {
                            dx = 0;
                            dz = 0;
                        } else {
                            int side = c / (2 * r), t = c % (2 * r) - r;
                            dx = side == 0 ? t : side == 1 ? r : side == 2 ? -t : -r;
                            dz = side == 0 ? -r : side == 1 ? t : side == 2 ? r : -t;
                        }
                        if (dx * dx + dz * dz > FILL_RADIUS * FILL_RADIUS) {
                            continue;
                        }
                        int x = px + dx, z = pz + dz;
                        long col = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                        if (done.contains(col)) {
                            continue;
                        }
                        double s = w.along(x + 0.5, z + 0.5);
                        if (s > w.front - 2) {
                            // The wave hasn't got there yet.
                            continue;
                        }
                        done.add(col);
                        float surface = w.floodSurface(level, x + 0.5, z + 0.5);
                        if (Float.isNaN(surface)) {
                            continue;
                        }
                        int y0 = TsunamiGround.top(level, x, z);
                        for (int y = y0; y + 0.5F < surface && y < y0 + 24; y++) {
                            BlockState st = level.getBlockState(m.set(x, y, z));
                            boolean flowing = st.getFluidState().is(Fluids.FLOWING_WATER);
                            if (!(st.isAir() || flowing || (st.canBeReplaced() && st.getFluidState().isEmpty()))) {
                                continue;
                            }
                            level.setBlock(m, waterState, 2);
                            water.add(m.asLong());
                            budget--;
                        }
                    }
                }
            }
        }
        if (water.size() != before) {
            d.setDirty();
        }
        return budget;
    }

    /**
     * Takes the flood water away: everything above the falling surface (by {@code drained}), or all of it (NaN).
     * Water in land that isn't loaded stays on the list for later.
     */
    private static void drain(ServerLevel level, WeatherData d, LongArrayList water, float drained, Tsunami w) {
        if (water.isEmpty()) {
            return;
        }
        boolean all = Float.isNaN(drained);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        int kept = 0;
        for (int i = 0; i < water.size(); i++) {
            long pos = water.getLong(i);
            m.set(pos);
            boolean remove;
            if (!level.isLoaded(m)) {
                remove = false;
            } else if (all || w == null) {
                remove = true;
            } else {
                float g = w.ground(level, m.getX() + 0.5, m.getZ() + 0.5);
                float level0 = g + w.floodDepth(w.along(m.getX() + 0.5, m.getZ() + 0.5)) * (1F - drained);
                remove = m.getY() + 0.5F >= level0;
            }
            if (remove && removed < 6000) {
                if (level.getBlockState(m).is(Blocks.WATER)) {
                    level.setBlock(m, air, 3);
                }
                removed++;
                // Water that spread (or turned into new sources) beside the flood goes with it; never the sea.
                int seaTop = level.getSeaLevel();
                if ((all || w == null) && m.getY() >= seaTop) {
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos n = m.relative(dir);
                        if (level.isLoaded(n) && level.getBlockState(n).is(Blocks.WATER) && !level.getBiome(n).is(BiomeTags.IS_OCEAN)
                                && !level.getBiome(n).is(BiomeTags.IS_RIVER)) {
                            level.setBlock(n, air, 3);
                            removed++;
                        }
                    }
                }
            } else {
                water.set(kept++, pos);
            }
        }
        water.size(kept);
        if (removed > 0) {
            d.setDirty();
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
