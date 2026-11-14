package com.realweather.server;

import com.realweather.ModContent;
import com.realweather.net.QuakeSyncPayload;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Earthquakes: started by command, or now and then on their own. While the ground shakes, everything loose is jolted,
 * buildings crack and come down according to what they are built of and how hard it shakes there (glass and loose
 * rubble walls first, timber frames hold out longer, stone and concrete longest), the ground splits open along the
 * quake's cracks with their edges crumbling in, slopes slide, big quakes are followed by aftershocks, and a great one
 * near the sea sends a tsunami.
 */
public final class QuakeManager {
    public static final List<Earthquake> QUAKES = new ArrayList<>();
    private static final List<float[]> PENDING = new ArrayList<>();
    private static int nextId = 1;
    private static boolean had;
    /** Blocks whose support just went: they are checked first next tick. */
    private static final LongArrayFIFOQueue UNSUPPORTED = new LongArrayFIFOQueue();
    /** Per quake, how far each crack point has been torn open (0 none, 1 opened, 2 opened to full width). */
    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<byte[][]> OPENED = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private static final Reference2FloatOpenHashMap<Block> RESIST = new Reference2FloatOpenHashMap<>();

    private QuakeManager() {}

    public static void reset() {
        QUAKES.clear();
        PENDING.clear();
        OPENED.clear();
        UNSUPPORTED.clear();
        had = false;
    }

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        long t = level.getGameTime();
        // Natural quakes: rare, nearly always small.
        if (d.natural && d.quakeNatural && t % 1200 == 0 && !players.isEmpty() && QUAKES.isEmpty() && rng.nextInt(240) == 0) {
            float r = rng.nextFloat();
            float m = 2F + 5.5F * r * r * r + (rng.nextInt(60) == 0 ? 2.5F : 0F);
            ServerPlayer p = players.get(rng.nextInt(players.size()));
            double a = rng.nextDouble() * Math.PI * 2, dist = 40 + rng.nextDouble() * 220;
            spawn(level, p.getX() + Math.cos(a) * dist, p.getZ() + Math.sin(a) * dist, m, rng, true);
        }
        // Aftershocks and tsunamis waiting their turn: {ticks left, kind (0 quake, 1 tsunami), x, z, magnitude or strength}.
        Iterator<float[]> pi = PENDING.iterator();
        while (pi.hasNext()) {
            float[] e = pi.next();
            if (--e[0] > 0) {
                continue;
            }
            pi.remove();
            if (e[1] == 0F) {
                spawn(level, e[2], e[3], e[4], rng, false);
            } else if (!players.isEmpty()) {
                ServerPlayer p = nearest(players, e[2], e[3]);
                TsunamiManager.spawn(level, p.position(), Math.round(e[4]), Float.NaN, rng);
            }
        }
        if (QUAKES.isEmpty()) {
            if (had) {
                had = false;
                sync(level);
            }
            return;
        }
        had = true;
        Iterator<Earthquake> it = QUAKES.iterator();
        int budget = 900;
        while (it.hasNext()) {
            Earthquake q = it.next();
            q.age++;
            if (q.age > q.duration + Earthquake.AFTER) {
                it.remove();
                OPENED.remove(q.id);
                continue;
            }
            if (!q.shaking()) {
                continue;
            }
            jolt(level, q, players, rng);
            if (d.quakeDamage) {
                budget = openCracks(level, q, budget, rng);
                budget = collapse(level, q, players, budget, rng);
            }
        }
        if (d.quakeDamage) {
            supports(level, budget, rng);
        }
        if (t % 20 == 0) {
            sync(level);
        }
    }

    /** Starts an earthquake of magnitude `m` with its epicentre at (x, z). */
    public static Earthquake spawn(ServerLevel level, double x, double z, float m, RandomSource rng, boolean main) {
        Earthquake q = new Earthquake(nextId++, x, z, m, rng.nextLong(), rng.nextFloat() * Mth.TWO_PI);
        QUAKES.add(q);
        if (main && m >= 5F) {
            // Aftershocks over the next minutes, a magnitude or two smaller, along the same fault.
            int n = 1 + rng.nextInt(m >= 7F ? 3 : 2);
            for (int i = 0; i < n; i++) {
                float along = (rng.nextFloat() - 0.5F) * Math.min(150F, 9F * (float) Math.pow(1.62, m - 3.5));
                PENDING.add(new float[]{q.duration + 400 + rng.nextInt(2400), 0F, (float) (x + Mth.cos(q.fault) * along),
                        (float) (z + Mth.sin(q.fault) * along), m - 1.2F - rng.nextFloat() * 1.3F});
            }
        }
        if (m >= 7.5F && nearSea(level, x, z)) {
            // The sea floor heaves: a tsunami arrives a little after the shaking.
            PENDING.add(new float[]{q.duration * 0.6F + 300, 1F, (float) x, (float) z, Mth.clamp((m - 7F) * 330F, 120F, 1000F)});
        }
        // Everyone who feels it is told how strong it was.
        for (ServerPlayer p : level.players()) {
            float i = q.peakIntensity(Math.sqrt(p.distanceToSqr(x, p.getY(), z)));
            if (i >= 2F) {
                p.displayClientMessage(Component.translatable("realweather.quake.felt", String.format("%.1f", q.magnitude)), true);
            }
        }
        sync(level);
        return q;
    }

    public static void stop(ServerLevel level) {
        for (Earthquake q : QUAKES) {
            q.age = Math.max(q.age, q.duration);
        }
        PENDING.removeIf(e -> e[1] == 0F);
        sync(level);
    }

    private static boolean nearSea(ServerLevel level, double x, double z) {
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12F;
            for (int r = 40; r <= 280; r += 60) {
                BlockPos p = BlockPos.containing(x + Mth.cos(a) * r, level.getSeaLevel(), z + Mth.sin(a) * r);
                if (level.isLoaded(p) && level.getBiome(p).is(BiomeTags.IS_OCEAN)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ServerPlayer nearest(List<ServerPlayer> players, double x, double z) {
        ServerPlayer best = players.get(0);
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.distanceToSqr(x, p.getY(), z);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------ shaking
    /** Loose things and animals are thrown about; people stagger. */
    private static void jolt(ServerLevel level, Earthquake q, List<ServerPlayer> players, RandomSource rng) {
        if (level.getGameTime() % 3 != 0) {
            return;
        }
        for (ServerPlayer p : players) {
            float i = q.intensityAt(p.getX(), p.getZ());
            if (i < 4.5F) {
                continue;
            }
            float k = Math.min(1F, (i - 4.5F) / 6F);
            AABB box = p.getBoundingBox().inflate(40, 12, 40);
            for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && e.onGround() && !e.isPassenger())) {
                if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
                    continue;
                }
                double s = (e instanceof Player ? 0.05 : 0.11) * k;
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(v.x + (rng.nextDouble() - 0.5) * s, v.y + rng.nextDouble() * s * 0.4, v.z + (rng.nextDouble() - 0.5) * s);
                e.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------------------------ the ground splits
    /** Tears the ground open where the cracks have widened enough. */
    private static int openCracks(ServerLevel level, Earthquake q, int budget, RandomSource rng) {
        List<Earthquake.Crack> cracks = q.cracks();
        byte[][] done = OPENED.get(q.id);
        if (done == null) {
            done = new byte[cracks.size()][];
            for (int c = 0; c < cracks.size(); c++) {
                done[c] = new byte[cracks.get(c).n];
            }
            OPENED.put(q.id, done);
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int c = 0; c < cracks.size() && budget > 0; c++) {
            Earthquake.Crack cr = cracks.get(c);
            if (cr.width[0] < Earthquake.OPEN_WIDTH && cr.width[cr.n / 2] < Earthquake.OPEN_WIDTH) {
                continue;
            }
            for (int i = 0; i < cr.n && budget > 0; i++) {
                if (done[c][i] >= 2 || cr.width[i] < Earthquake.OPEN_WIDTH) {
                    continue;
                }
                float w = q.widthNow(cr, i);
                byte stage = w >= cr.width[i] * 0.98F ? (byte) 2 : w >= Earthquake.OPEN_WIDTH ? (byte) 1 : (byte) 0;
                if (stage <= done[c][i]) {
                    continue;
                }
                if (!level.isLoaded(m.set(cr.xs[i], 0, cr.zs[i]))) {
                    continue;
                }
                done[c][i] = stage;
                budget -= split(level, q, cr.xs[i], cr.zs[i], w, rng);
            }
        }
        return budget;
    }

    /** Opens the ground at one point of a crack: a fissure `w` wide and deep in proportion, its edges falling in. */
    private static int split(ServerLevel level, Earthquake q, float cx, float cz, float w, RandomSource rng) {
        int used = 0;
        float r = w * 0.5F;
        float depth = 1.5F + w * 2.6F + rng.nextFloat() * 1.5F;
        int reach = Mth.ceil(r + 1.4F);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int bx = Mth.floor(cx) + dx, bz = Mth.floor(cz) + dz;
                double ddx = bx + 0.5 - cx, ddz = bz + 0.5 - cz;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist > r + 1.4F) {
                    continue;
                }
                int top = TsunamiGround.top(level, bx, bz) - 1;
                if (dist <= r + 0.35F) {
                    // Inside the fissure: the ground drops away, deepest along the middle.
                    int dd = Mth.ceil(depth * (1F - (float) (dist / (r + 0.6F)) * 0.5F));
                    for (int y = top + 3; y > top - dd; y--) {
                        BlockState s = level.getBlockState(m.set(bx, y, bz));
                        if (s.isAir() || s.is(Blocks.BEDROCK) || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
                            continue;
                        }
                        if (y > top && !isTerrain(s)) {
                            // Something standing on the crack falls in.
                            fall(level, m.immutable(), s, rng);
                        } else {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                        }
                        used++;
                    }
                    if (rng.nextInt(3) == 0) {
                        BlockState s = level.getBlockState(m.set(bx, top - 1, bz));
                        if (!s.isAir()) {
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), bx + 0.5, top + 1.1, bz + 0.5, 6, 0.4, 0.2, 0.4, 0.05);
                        }
                    }
                } else if (rng.nextFloat() < 0.38F) {
                    // The rim: it crumbles and slumps into the crack.
                    BlockState s = level.getBlockState(m.set(bx, top, bz));
                    if (!s.isAir() && isTerrain(s) && s.getFluidState().isEmpty() && !s.is(Blocks.BEDROCK)) {
                        BlockState rubble = s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) ? Blocks.DIRT.defaultBlockState() : s;
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                        if (rng.nextBoolean()) {
                            FallingBlockEntity f = FallingBlockEntity.fall(level, m.immutable(), rubble);
                            f.setDeltaMovement((cx - bx - 0.5) * 0.08, 0.05, (cz - bz - 0.5) * 0.08);
                            f.dropItem = false;
                        }
                        used++;
                    }
                }
            }
        }
        if (used > 0 && rng.nextInt(4) == 0) {
            level.playSound(null, cx, TsunamiGround.top(level, Mth.floor(cx), Mth.floor(cz)), cz, ModContent.QUAKE_CRACK.get(), SoundSource.BLOCKS,
                    2.5F, 0.8F + rng.nextFloat() * 0.4F);
        }
        return Math.max(1, used);
    }

    // ------------------------------------------------------------------------------------ buildings come down
    private static int collapse(ServerLevel level, Earthquake q, List<ServerPlayer> players, int budget, RandomSource rng) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (ServerPlayer p : players) {
            float ip = q.intensityAt(p.getX(), p.getZ());
            if (ip < 4.8F || budget <= 0) {
                continue;
            }
            int samples = Math.min(140, Math.round((ip - 4.5F) * 16F));
            int fell = 0;
            for (int s = 0; s < samples && budget > 0; s++) {
                int x = p.getBlockX() + rng.nextInt(97) - 48;
                int z = p.getBlockZ() + rng.nextInt(97) - 48;
                if (!level.isLoaded(m.set(x, 0, z))) {
                    continue;
                }
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int ground = groundBelow(level, x, z, topY);
                int y = ground + rng.nextInt(Math.max(1, topY - ground + 2)) - 1;
                BlockState st = level.getBlockState(m.set(x, y, z));
                if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
                    continue;
                }
                float local = q.intensityAt(x + 0.5, z + 0.5);
                if (isTerrain(st)) {
                    // Steep ground slides.
                    if (local > 7.5F && slope(level, m) && rng.nextFloat() < (local - 7.5F) * 0.12F) {
                        fall(level, m.immutable(), st, rng);
                        budget--;
                    }
                    continue;
                }
                float res = resistance(st, level, m);
                BlockState below = level.getBlockState(m.set(x, y - 1, z));
                m.set(x, y, z);
                if (below.isAir() || below.canBeReplaced()) {
                    local += 1.6F;
                }
                float p0 = Mth.clamp((local - res) / 2F, 0F, 1F);
                if (p0 <= 0F || rng.nextFloat() > (float) Math.pow(p0, 1.5) * 0.7F) {
                    continue;
                }
                breakOrFall(level, m.immutable(), st, rng);
                budget--;
                fell++;
            }
            if (fell >= 3 && rng.nextInt(4) == 0) {
                level.playSound(null, p.getX() + rng.nextGaussian() * 12, p.getY(), p.getZ() + rng.nextGaussian() * 12, ModContent.QUAKE_COLLAPSE.get(),
                        SoundSource.BLOCKS, 1.6F, 0.85F + rng.nextFloat() * 0.3F);
            }
        }
        return budget;
    }

    /** Blocks that lost what held them up come down after it. */
    private static void supports(ServerLevel level, int budget, RandomSource rng) {
        int n = Math.min(UNSUPPORTED.size(), Math.max(0, Math.min(budget, 120)));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            long pos = UNSUPPORTED.dequeueLong();
            m.set(pos);
            if (!level.isLoaded(m)) {
                continue;
            }
            BlockState st = level.getBlockState(m);
            if (st.isAir() || isTerrain(st) || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
                continue;
            }
            BlockState below = level.getBlockState(m.below());
            if (!(below.isAir() || below.canBeReplaced())) {
                continue;
            }
            // Held by its neighbours a little: beams and roofs sag before they go.
            int held = 0;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (!level.getBlockState(m.relative(dir)).isAir()) {
                    held++;
                }
            }
            if (rng.nextFloat() < 0.9F - held * 0.17F) {
                breakOrFall(level, m.immutable(), st, rng);
            } else {
                UNSUPPORTED.enqueue(pos);
            }
        }
        while (UNSUPPORTED.size() > 4000) {
            UNSUPPORTED.dequeueLong();
        }
    }

    private static void breakOrFall(ServerLevel level, BlockPos pos, BlockState st, RandomSource rng) {
        if (fragile(st)) {
            // Glass shatters, lamps and pots smash.
            level.destroyBlock(pos, false);
        } else if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || st.hasProperty(BlockStateProperties.BED_PART)
                || st.is(BlockTags.LEAVES) || !st.getFluidState().isEmpty()) {
            level.destroyBlock(pos, true);
        } else {
            BlockState below = level.getBlockState(pos.below());
            if (below.isAir() || below.canBeReplaced()) {
                fall(level, pos, st, rng);
            } else {
                // Still standing on something: the block is shaken loose out of the wall and tumbles down outside it,
                // or crumbles where it is.
                net.minecraft.core.Direction out = null;
                int start = rng.nextInt(4);
                for (int i = 0; i < 4; i++) {
                    net.minecraft.core.Direction dir = net.minecraft.core.Direction.from2DDataValue((start + i) % 4);
                    if (level.getBlockState(pos.relative(dir)).isAir()) {
                        out = dir;
                        break;
                    }
                }
                if (out == null || rng.nextInt(4) == 0) {
                    level.destroyBlock(pos, rng.nextInt(5) == 0);
                } else {
                    if (st.hasProperty(BlockStateProperties.WATERLOGGED)) {
                        st = st.setValue(BlockStateProperties.WATERLOGGED, false);
                    }
                    FallingBlockEntity f = FallingBlockEntity.fall(level, pos, st);
                    f.setPos(pos.getX() + 0.5 + out.getStepX() * 0.55, pos.getY() + 0.05, pos.getZ() + 0.5 + out.getStepZ() * 0.55);
                    f.setDeltaMovement(out.getStepX() * (0.18 + rng.nextDouble() * 0.15), 0.12 + rng.nextDouble() * 0.1,
                            out.getStepZ() * (0.18 + rng.nextDouble() * 0.15));
                    f.dropItem = rng.nextInt(3) == 0;
                    f.setHurtsEntities(1.5F, 20);
                }
            }
        }
        UNSUPPORTED.enqueue(pos.above().asLong());
    }

    /** First block of the land itself under a column (below houses, walls and towers). */
    private static int groundBelow(ServerLevel level, int x, int z, int topY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = topY; y > topY - 48 && y > level.getMinBuildHeight(); y--) {
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (!s.isAir() && isTerrain(s) && !s.canBeReplaced() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) {
                return y + 1;
            }
        }
        return topY;
    }

    private static void fall(ServerLevel level, BlockPos pos, BlockState st, RandomSource rng) {
        if (st.hasProperty(BlockStateProperties.WATERLOGGED)) {
            st = st.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        FallingBlockEntity f = FallingBlockEntity.fall(level, pos, st);
        f.setDeltaMovement((rng.nextDouble() - 0.5) * 0.18, rng.nextDouble() * 0.05, (rng.nextDouble() - 0.5) * 0.18);
        f.dropItem = rng.nextInt(3) == 0;
        f.setHurtsEntities(1.5F, 20);
    }

    private static boolean slope(ServerLevel level, BlockPos.MutableBlockPos m) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos side = m.relative(dir);
            if (level.getBlockState(side).isAir() && level.getBlockState(side.below()).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean fragile(BlockState s) {
        return s.is(BlockTags.IMPERMEABLE) || s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock && !s.is(Blocks.IRON_BARS)
                || s.getBlock() instanceof net.minecraft.world.level.block.TorchBlock || s.getBlock() instanceof net.minecraft.world.level.block.LanternBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock || s.is(Blocks.GLOWSTONE) || s.is(Blocks.SEA_LANTERN)
                || s.is(BlockTags.ICE) || s.getBlock() instanceof net.minecraft.world.level.block.CandleBlock;
    }

    /**
     * How much shaking (intensity) a block takes before it gives way. Unreinforced masonry (cobble, rubble, mud) fails
     * first, then brick, timber frames bend a long time, dressed stone and concrete hold out longest, metal and
     * obsidian practically never.
     */
    static float resistance(BlockState s, ServerLevel level, BlockPos pos) {
        Block b = s.getBlock();
        if (RESIST.containsKey(b)) {
            return RESIST.getFloat(b);
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        float r;
        if (fragile(s)) {
            r = 5.0F;
        } else if (s.is(BlockTags.LEAVES)) {
            r = 99F;
        } else if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.BEDROCK) || s.is(Blocks.REINFORCED_DEEPSLATE) || id.contains("netherite")) {
            r = 99F;
        } else if (id.endsWith("_block") && (id.startsWith("iron") || id.startsWith("gold") || id.startsWith("diamond") || id.startsWith("emerald")
                || id.contains("copper"))) {
            r = 11F;
        } else if (s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS)) {
            r = 7.4F;
        } else if (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS)
                || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WOODEN_TRAPDOORS)) {
            r = 8.3F;
        } else if (id.contains("cobble") || id.contains("mossy") || id.startsWith("mud") || id.contains("packed_mud") || id.contains("gravel")) {
            r = 6.5F;
        } else if (id.contains("brick") && !id.contains("stone") && !id.contains("deepslate") && !id.contains("nether") && !id.contains("end_")) {
            r = 7.2F;
        } else if (id.contains("terracotta") || id.contains("sandstone")) {
            r = 7.1F;
        } else if (id.contains("concrete") && !id.contains("powder")) {
            r = 9.6F;
        } else if (id.contains("deepslate") || id.contains("blackstone") || id.contains("end_stone") || id.contains("quartz") || id.contains("prismarine")
                || id.contains("purpur")) {
            r = 8.8F;
        } else if (id.contains("stone_brick") || id.startsWith("polished") || id.startsWith("smooth") || id.startsWith("chiseled") || id.startsWith("cut_")) {
            r = 8.0F;
        } else {
            float h = s.getDestroySpeed(level, pos);
            r = h < 0 ? 99F : Mth.clamp(5.4F + 1.6F * (float) Math.log1p(h), 5F, 11F);
        }
        RESIST.put(b, r);
        return r;
    }

    /** The land itself: it only cracks and slides, it doesn't come down like a wall. */
    static boolean isTerrain(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BEDROCK)
                || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(Blocks.GRASS_BLOCK)
                || s.is(Blocks.SNOW) || s.getBlock() instanceof CoverBlock || s.canBeReplaced() && s.getFluidState().isEmpty() && !s.isAir()
                || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) && naturalLog(s);
    }

    private static boolean naturalLog(BlockState s) {
        // Tree trunks stand upright; logs laid on their side are part of a building.
        return !s.hasProperty(BlockStateProperties.AXIS) || s.getValue(BlockStateProperties.AXIS) == net.minecraft.core.Direction.Axis.Y;
    }

    // ------------------------------------------------------------------------------------ sync
    public static void sync(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, payload());
    }

    public static QuakeSyncPayload payload() {
        List<QuakeSyncPayload.Entry> list = new ArrayList<>();
        for (Earthquake q : QUAKES) {
            list.add(new QuakeSyncPayload.Entry(q.id, q.x, q.z, q.magnitude, q.seed, q.fault, q.age));
        }
        return new QuakeSyncPayload(list);
    }
}
