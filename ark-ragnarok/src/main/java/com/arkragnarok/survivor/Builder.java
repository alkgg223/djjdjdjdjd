package com.arkragnarok.survivor;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.building.DoorState;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.building.PieceType;
import com.arkragnarok.building.Placement;
import com.arkragnarok.building.Tier;
import com.arkragnarok.economy.ModEconomy;
import com.arkragnarok.economy.Recipes;
import com.arkragnarok.economy.Station;
import com.arkragnarok.economy.StationBlock;
import com.arkragnarok.registry.ModBuilding;
import com.arkragnarok.survivor.world.SurvivorWorld;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Turns a tribe's blueprint into a real base, one step at a time: levels the plot (digging out bumps, filling holes
 * and laying a rammed-earth footing under the walls), places every structure piece in the best material it can pay
 * for, hangs doors, sets up furniture and crop plots, notices pieces that were destroyed so they get rebuilt, and
 * upgrades old pieces to a better material once the tribe can afford it.
 */
final class Builder {
    static final int SKIPPED = 99;

    /** one thing to build: plan index, where, which way, in which tier, what it costs */
    record Job(int index, BaseDesign.Step step, BlockPos pos, Direction facing, @Nullable Tier tier, List<Recipes.Ing> cost, boolean upgrade) {}

    private record Op(BlockPos pos, boolean fill) {}

    private final Survivor s;
    private final Map<Integer, Long> retry = new HashMap<>();
    private int scanCursor, progress, groundFor = -1;
    private ArrayDeque<Op> ground = new ArrayDeque<>();

    Builder(Survivor s) {
        this.s = s;
    }

    // ============================================================================================ geometry
    static BaseDesign design(SurvivorWorld.Tribe t) {
        return BaseDesign.get(t.design);
    }

    static BlockPos at(SurvivorWorld.Tribe t, BaseDesign.Step st) {
        return t.base.offset(BaseDesign.rotate(st.offset(), t.rotation));
    }

    static Direction facing(SurvivorWorld.Tribe t, BaseDesign.Step st) {
        if (st.type() != null && st.type().flat()) return Direction.NORTH;
        return BaseDesign.rotate(st.facing(), t.rotation);
    }

    static void settle(SurvivorWorld.Tribe t, BlockPos anchor, int design, int rot) {
        t.base = anchor.immutable();
        t.design = design;
        t.rotation = rot & 3;
        BaseDesign d = BaseDesign.get(design);
        t.built = new int[d.plan().size()];
        t.footprint = d.bounds(t.rotation);
    }

    static List<BaseDesign.Step> plan(SurvivorWorld.Tribe t) {
        List<BaseDesign.Step> p = design(t).plan();
        if (t.built.length != p.size()) t.built = Arrays.copyOf(t.built, p.size());
        return p;
    }

    static int builtCount(SurvivorWorld.Tribe t) {
        int n = 0;
        for (int b : t.built) if (b > 0 && b != SKIPPED) n++;
        return n;
    }

    /** share of a phase that stands (skipped steps count as done) */
    static float phaseDone(SurvivorWorld.Tribe t, int phase) {
        if (t.base == null) return 0f;
        List<BaseDesign.Step> p = plan(t);
        int all = 0, done = 0;
        for (int i = 0; i < p.size(); i++) {
            if (p.get(i).phase() != phase) continue;
            all++;
            if (t.built[i] != 0) done++;
        }
        return all == 0 ? 1f : done / (float) all;
    }

    /** the material a tribe builds in at each civilisation stage */
    static Tier target(int stage) {
        return switch (stage) {
            case 1 -> Tier.THATCH;
            case 2, 3 -> Tier.WOOD;
            case 4 -> Tier.STONE;
            case 5 -> Tier.IRON;
            default -> Tier.STEEL;
        };
    }

    static int maxPhase(int stage) {
        return Math.max(1, Math.min(5, stage));
    }

    // ============================================================================================ what exists
    /** positions of placed furniture of one kind (still standing) */
    List<BlockPos> furniture(SurvivorWorld.Tribe t, String id) {
        List<BlockPos> out = new ArrayList<>();
        if (t.base == null) return out;
        List<BaseDesign.Step> p = plan(t);
        Block want = ((BlockItem) Economy.furnitureItem(id)).getBlock();
        for (int i = 0; i < p.size(); i++) {
            BaseDesign.Step st = p.get(i);
            if ((st.kind() != BaseDesign.Kind.FURNITURE && st.kind() != BaseDesign.Kind.CROP) || !st.furniture().equals(id) || t.built[i] == 0
                    || t.built[i] == SKIPPED) continue;
            BlockPos pos = at(t, st);
            if (s.level().isLoaded(pos) && s.level().getBlockState(pos).is(want)) out.add(pos);
        }
        return out;
    }

    @Nullable
    BlockPos station(SurvivorWorld.Tribe t, Station st) {
        List<BlockPos> l = furniture(t, Economy.furnitureOf(st));
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : l) {
            double d = p.distToCenterSqr(s.position());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** storage boxes with the room they stand in */
    List<Map.Entry<BlockPos, BaseDesign.Room>> boxes(SurvivorWorld.Tribe t) {
        List<Map.Entry<BlockPos, BaseDesign.Room>> out = new ArrayList<>();
        if (t.base == null) return out;
        List<BaseDesign.Step> p = plan(t);
        for (int i = 0; i < p.size(); i++) {
            BaseDesign.Step st = p.get(i);
            if (st.kind() != BaseDesign.Kind.FURNITURE || !st.furniture().endsWith("storage_box") || t.built[i] == 0 || t.built[i] == SKIPPED) continue;
            BlockPos pos = at(t, st);
            if (s.level().isLoaded(pos) && s.level().getBlockEntity(pos) instanceof net.minecraft.world.Container) {
                out.add(Map.entry(pos, st.room() == null ? BaseDesign.Room.STORAGE : st.room()));
            }
        }
        return out;
    }

    /** inside a finished dinosaur pen (its feed box), if the base has one */
    @Nullable
    BlockPos penSpot(SurvivorWorld.Tribe t) {
        if (t.base == null) return null;
        List<BaseDesign.Step> p = plan(t);
        int ground = -design(t).depth();
        for (int i = 0; i < p.size(); i++) {
            BaseDesign.Step st = p.get(i);
            if (st.kind() == BaseDesign.Kind.FURNITURE && st.room() == BaseDesign.Room.BARN && st.offset().getY() == ground && t.built[i] != 0
                    && t.built[i] != SKIPPED) return at(t, st).above().relative(Direction.NORTH);
        }
        return null;
    }

    @Nullable
    BlockPos bed(SurvivorWorld.Tribe t) {
        List<BlockPos> l = furniture(t, "bed");
        return l.isEmpty() ? null : l.get(0);
    }

    // ============================================================================================ choosing the next step
    @Nullable
    Job next(SurvivorWorld.Tribe t, long now) {
        if (t.base == null) return null;
        List<BaseDesign.Step> p = plan(t);
        int maxPhase = maxPhase(t.stage);
        for (int ph = 1; ph <= maxPhase; ph++) {
            for (int i = 0; i < p.size(); i++) {
                BaseDesign.Step st = p.get(i);
                if (st.phase() != ph || t.built[i] != 0) continue;
                Long r = retry.get(i);
                if (r != null && r > now) continue;
                BlockPos pos = at(t, st);
                if (!s.level().isLoaded(pos)) continue;
                Job j = job(t, i, st, pos);
                if (j != null) return j;
            }
        }
        return null;
    }

    /** an old piece in a weaker material than the tribe now builds with */
    @Nullable
    Job upgrade(SurvivorWorld.Tribe t) {
        if (t.base == null) return null;
        Tier want = target(t.stage);
        List<BaseDesign.Step> p = plan(t);
        for (int i = 0; i < p.size(); i++) {
            BaseDesign.Step st = p.get(i);
            int b = t.built[i];
            if (st.kind() != BaseDesign.Kind.PIECE || b == 0 || b == SKIPPED || b - 1 >= want.ordinal()) continue;
            List<Recipes.Ing> cost = Recipes.pieceCost(want, st.type(), false);
            if (!affordable(t, cost)) return null;
            BlockPos pos = at(t, st);
            if (!s.level().isLoaded(pos)) continue;
            return new Job(i, st, pos, facing(t, st), want, cost, true);
        }
        return null;
    }

    @Nullable
    private Job job(SurvivorWorld.Tribe t, int i, BaseDesign.Step st, BlockPos pos) {
        Level level = s.level();
        Direction f = facing(t, st);
        switch (st.kind()) {
            case GROUND -> {
                return new Job(i, st, pos, f, null, List.of(), false);
            }
            case PIECE -> {
                Tier tier = chooseTier(t, st.type(), false, st.phase());
                return new Job(i, st, pos, f, tier, Recipes.pieceCost(tier, st.type(), false), false);
            }
            case DOOR -> {
                BlockState bs = level.getBlockState(pos);
                if (!(bs.getBlock() instanceof PieceBlock) || bs.getValue(PieceBlock.PIECE) != PieceType.DOORFRAME) {
                    if (frameSkipped(t, st)) t.built[i] = SKIPPED;
                    return null;                                     // the doorframe comes first
                }
                Tier tier = chooseTier(t, PieceType.DOORFRAME, true, st.phase());
                return new Job(i, st, pos, f, tier, Recipes.pieceCost(tier, PieceType.DOORFRAME, true), false);
            }
            default -> {          // FURNITURE, CROP
                BlockPos below = pos.below();
                BlockState b = level.getBlockState(below);
                if (st.kind() == BaseDesign.Kind.FURNITURE && !(b.getBlock() instanceof PieceBlock) && !b.isFaceSturdy(level, below, Direction.UP)) {
                    return null;                                     // the floor comes first
                }
                Item it = Economy.furnitureItem(st.furniture());
                return new Job(i, st, pos, f, null, List.of(new Recipes.Ing(() -> it, 1)), false);
            }
        }
    }

    private boolean frameSkipped(SurvivorWorld.Tribe t, BaseDesign.Step door) {
        List<BaseDesign.Step> p = plan(t);
        for (int k = 0; k < p.size(); k++) {
            BaseDesign.Step st = p.get(k);
            if (st.kind() == BaseDesign.Kind.PIECE && st.type() == PieceType.DOORFRAME && st.offset().equals(door.offset())) return t.built[k] == SKIPPED;
        }
        return true;
    }

    /** best material it can pay for (pack + stores): the stage's material or one below; the first shelter in anything */
    Tier chooseTier(SurvivorWorld.Tribe t, PieceType type, boolean door, int phase) {
        Tier target = target(t.stage);
        Tier[] tiers = Tier.values();
        int lo = phase <= 1 ? 0 : Math.max(0, target.ordinal() - 1);
        for (int k = target.ordinal(); k >= lo; k--) if (affordable(t, Recipes.pieceCost(tiers[k], type, door))) return tiers[k];
        return tiers[lo];
    }

    boolean affordable(SurvivorWorld.Tribe t, List<Recipes.Ing> cost) {
        for (Recipes.Ing g : cost) {
            int have = s.count(g.item().get()) + t.stock.getOrDefault(Economy.key(g.item().get()), 0);
            if (have < g.count()) return false;
        }
        return true;
    }

    /** what is still missing in the pack (it may be in the stores) */
    List<Recipes.Ing> missing(Job j) {
        List<Recipes.Ing> out = new ArrayList<>();
        for (Recipes.Ing g : j.cost()) {
            int have = s.count(g.item().get());
            if (have < g.count()) out.add(new Recipes.Ing(g.item(), g.count() - have));
        }
        return out;
    }

    /** where to stand instead when the survivor is inside the space the job is about to fill (null: it is fine) */
    @Nullable
    BlockPos standAside(Job j) {
        net.minecraft.world.phys.AABB me = s.getBoundingBox();
        List<BlockPos> cells = new ArrayList<>();
        BaseDesign.Step st = j.step();
        if (st.kind() == BaseDesign.Kind.PIECE) cells.addAll(PieceBlock.parts(j.pos(), st.type(), j.facing(), null));
        else if (st.kind() == BaseDesign.Kind.FURNITURE || st.kind() == BaseDesign.Kind.CROP) {
            cells.add(j.pos());
            if (st.furniture().equals("bed")) cells.add(j.pos().relative(j.facing()));
        } else if (st.kind() == BaseDesign.Kind.GROUND && !st.furniture().equals("ramp")) {
            // levelling: stand in the middle of the plot (only its ring and the ground below are filled)
            BlockPos c = j.pos();
            if (!s.level().isEmptyBlock(c) || !s.level().getBlockState(c.below()).isFaceSturdy(s.level(), c.below(), Direction.UP)) return null;
            double dx = s.getX() - (c.getX() + 0.5), dz = s.getZ() - (c.getZ() + 0.5);
            return Math.abs(dx) < 1.2 && Math.abs(dz) < 1.2 && Math.abs(s.getY() - c.getY()) < 1.5 ? null : c;
        }
        boolean inside = false;
        for (BlockPos c : cells) if (me.intersects(new net.minecraft.world.phys.AABB(c))) inside = true;
        if (!inside) return null;
        Direction d = Direction.getNearest(s.getX() - j.pos().getX() - 0.5, 0, s.getZ() - j.pos().getZ() - 0.5);
        if (!d.getAxis().isHorizontal()) d = Direction.NORTH;
        int k = st.type() != null && st.type().flat() ? 3 : 2;
        return j.pos().relative(d, k);
    }

    void delay(Job j, long now, int ticks) {
        retry.put(j.index(), now + ticks);
    }

    // ============================================================================================ doing the work
    /** a step of work on the job; true when the job is over (done, or given up) */
    boolean work(SurvivorWorld.Tribe t, Job j) {
        if (j.step().kind() == BaseDesign.Kind.GROUND) return groundTick(t, j, false);
        progress++;
        if (progress % 8 == 0) {
            s.swing(InteractionHand.MAIN_HAND);
            Tier tier = j.tier() != null ? j.tier() : Tier.WOOD;
            s.level().playSound(null, j.pos(), tier.sound.getHitSound(), SoundSource.BLOCKS, 0.8f, 0.9f + s.getRandom().nextFloat() * 0.2f);
        }
        int need = (int) (workTicks(j) / t.skills.speed(Skills.Skill.BUILDING));
        if (progress < need) return false;
        progress = 0;
        place(t, j);
        return true;
    }

    private static int workTicks(Job j) {
        if (j.tier() == null) return 30;
        return switch (j.tier()) {
            case THATCH -> 30;
            case WOOD -> 40;
            case STONE -> 55;
            case IRON -> 70;
            case STEEL -> 90;
        };
    }

    /** builds at once (when catching up on what it did while nobody was near) */
    boolean instant(SurvivorWorld.Tribe t, Job j) {
        if (j.step().kind() == BaseDesign.Kind.GROUND) {
            groundFor = -1;
            for (int k = 0; k < 400; k++) if (groundTick(t, j, true)) return true;
            return false;
        }
        return place(t, j);
    }

    private boolean place(SurvivorWorld.Tribe t, Job j) {
        Level level = s.level();
        int i = j.index();
        BaseDesign.Step st = j.step();
        long now = level.getGameTime();
        switch (st.kind()) {
            case PIECE -> {
                PieceType type = st.type();
                if (!j.upgrade()) {
                    Tier there = existing(level, j.pos(), type, j.facing());
                    if (there != null) {
                        t.built[i] = there.ordinal() + 1;
                        return true;
                    }
                }
                if (!Recipes.pay(s.inv, j.cost())) {
                    delay(j, now, 100);
                    return false;
                }
                if (j.upgrade()) {
                    Tier old = Tier.values()[Math.max(0, t.built[i] - 1)];
                    level.setBlock(j.pos(), Blocks.AIR.defaultBlockState(), 3);
                    for (Recipes.Ing g : Recipes.pieceCost(old, type, false)) s.give(new ItemStack(g.item().get(), g.count() / 2));
                    if (type == PieceType.DOORFRAME) resetDoor(t, st);
                }
                List<BlockPos> parts = PieceBlock.parts(j.pos(), type, j.facing(), null);
                for (BlockPos q : parts) clearNatural(level, q);
                if (!type.flat()) for (BlockPos q : parts) if (q.getY() == j.pos().getY()) footing(level, q);
                PieceBlock block = ModBuilding.PIECE_BLOCKS.get(j.tier()).get();
                if (Placement.place(level, block, type, new Placement.Plan(j.pos(), j.facing()))) {
                    t.built[i] = j.tier().ordinal() + 1;
                    t.piecesBuilt++;
                    t.skills.gain(Skills.Skill.BUILDING, 3f + j.tier().ordinal(), t.personality());
                    level.playSound(null, j.pos(), j.tier().sound.getPlaceSound(), SoundSource.BLOCKS, 1.2f, 0.8f);
                    return true;
                }
                for (Recipes.Ing g : j.cost()) s.give(new ItemStack(g.item().get(), g.count()));
                t.built[i] = SKIPPED;                            // something it may not remove is in the way
                return false;
            }
            case DOOR -> {
                BlockState bs = level.getBlockState(j.pos());
                if (bs.getBlock() instanceof PieceBlock) {
                    BlockPos m = PieceBlock.master(j.pos(), bs);
                    BlockState ms = level.getBlockState(m);
                    if (ms.getBlock() instanceof PieceBlock && ms.getValue(PieceBlock.DOOR) != DoorState.NONE) {
                        t.built[i] = j.tier().ordinal() + 1;
                        return true;
                    }
                }
                if (!Recipes.pay(s.inv, j.cost())) {
                    delay(j, now, 100);
                    return false;
                }
                if (Placement.installDoor(level, j.pos(), j.tier())) {
                    t.built[i] = j.tier().ordinal() + 1;
                    t.piecesBuilt++;
                    t.skills.gain(Skills.Skill.BUILDING, 2f, t.personality());
                    level.playSound(null, j.pos(), j.tier().close, SoundSource.BLOCKS, 1.0f, 1.0f);
                    return true;
                }
                for (Recipes.Ing g : j.cost()) s.give(new ItemStack(g.item().get(), g.count()));
                delay(j, now, 600);
                return false;
            }
            default -> {              // FURNITURE, CROP
                Item item = Economy.furnitureItem(st.furniture());
                Block want = ((BlockItem) item).getBlock();
                BlockPos pos = j.pos();
                if (level.getBlockState(pos).is(want)) {
                    t.built[i] = 1;
                    return true;
                }
                clearNatural(level, pos);
                if (!level.getBlockState(pos).canBeReplaced()) {
                    t.built[i] = SKIPPED;
                    return false;
                }
                boolean bed = st.furniture().equals("bed");
                BlockPos foot = pos.relative(j.facing());
                if (bed) {
                    clearNatural(level, foot);
                    if (!level.getBlockState(foot).canBeReplaced()) {
                        t.built[i] = SKIPPED;
                        return false;
                    }
                }
                if (st.kind() == BaseDesign.Kind.CROP) footing(level, pos);
                if (!s.take(item, 1)) {
                    delay(j, now, 100);
                    return false;
                }
                if (bed) {
                    BlockState b = want.defaultBlockState().setValue(BedBlock.FACING, j.facing().getOpposite());
                    level.setBlock(foot, b.setValue(BedBlock.PART, BedPart.FOOT), 3);
                    level.setBlock(pos, b.setValue(BedBlock.PART, BedPart.HEAD), 3);
                } else {
                    BlockState b = want.defaultBlockState();
                    if (b.hasProperty(StationBlock.FACING)) b = b.setValue(StationBlock.FACING, j.facing());
                    level.setBlock(pos, b, 3);
                }
                level.playSound(null, pos, want.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
                t.built[i] = 1;
                t.skills.gain(Skills.Skill.BUILDING, 1f, t.personality());
                return true;
            }
        }
    }

    private void resetDoor(SurvivorWorld.Tribe t, BaseDesign.Step frame) {
        List<BaseDesign.Step> p = plan(t);
        for (int k = 0; k < p.size(); k++) {
            BaseDesign.Step st = p.get(k);
            if (st.kind() == BaseDesign.Kind.DOOR && st.offset().equals(frame.offset())) t.built[k] = 0;
        }
    }

    @Nullable
    private static Tier existing(Level level, BlockPos pos, PieceType type, Direction f) {
        BlockState bs = level.getBlockState(pos);
        if (!(bs.getBlock() instanceof PieceBlock pb) || bs.getValue(PieceBlock.PIECE) != type) return null;
        if (!PieceBlock.master(pos, bs).equals(pos)) return null;
        if (!type.flat() && bs.getValue(PieceBlock.FACING).getAxis() != f.getAxis()) return null;
        return pb.tier;
    }

    // ============================================================================================ ground
    /** levels the 5x5 plot of a room or tower (or digs the stairway of an underground base) */
    private boolean groundTick(SurvivorWorld.Tribe t, Job j, boolean instant) {
        if (groundFor != j.index()) {
            ground = planGround(j);
            groundFor = j.index();
            progress = 0;
        }
        if (!instant) {
            if (++progress % 6 != 0) return false;
            s.swing(InteractionHand.MAIN_HAND);
        }
        Level level = s.level();
        for (int k = 0; k < (instant ? 400 : 3) && !ground.isEmpty(); k++) {
            Op op = ground.poll();
            BlockState st = level.getBlockState(op.pos());
            if (op.fill() && s.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(op.pos()))) {
                if (!instant && ground.size() < 200) ground.add(op);     // later, once it has stepped aside
                continue;
            }
            if (op.fill()) {
                if (st.canBeReplaced() || st.isAir()) level.setBlock(op.pos(), ModEconomy.RAMMED_EARTH.get().defaultBlockState(), 3);
            } else {
                clearNatural(level, op.pos());
            }
        }
        if (ground.isEmpty()) {
            t.built[j.index()] = 1;
            groundFor = -1;
            t.skills.gain(Skills.Skill.BUILDING, 1f, t.personality());
            return true;
        }
        return false;
    }

    private ArrayDeque<Op> planGround(Job j) {
        ArrayDeque<Op> q = new ArrayDeque<>();
        Level level = s.level();
        BlockPos c = j.pos();
        if (j.step().furniture().equals("ramp")) {
            Direction dir = j.facing();
            Direction side = dir.getClockWise();
            int depth = Math.max(1, design(s.tribe()).depth());
            for (int i = 0; i < depth + 1; i++) {
                BlockPos mid = c.relative(dir, 1 + i).above(i);
                for (int w = -1; w <= 1; w++) {
                    BlockPos p = mid.relative(side, w);
                    for (int h = 2; h >= 0; h--) if (diggable(level, p.above(h))) q.add(new Op(p.above(h), false));
                    if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) q.add(new Op(p.below(), true));
                }
            }
            return q;
        }
        for (int dy = 3; dy >= 0; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (diggable(level, p)) q.add(new Op(p, false));
                }
            }
        }
        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (dy == 0 && !ring) continue;
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (dy == -2 && !st.isAir() && st.getFluidState().isEmpty()) continue;
                    if (st.canBeReplaced() || st.isAir() || diggable(level, p) && dy == 0) q.add(new Op(p, true));
                }
            }
        }
        return q;
    }

    /** rammed earth under a wall or crop plot where the ground is missing (up to three blocks down) */
    private static void footing(Level level, BlockPos bottom) {
        BlockPos q = bottom.below();
        for (int k = 0; k < 3; k++) {
            BlockState st = level.getBlockState(q);
            if (!(st.canBeReplaced() || st.isAir())) break;
            level.setBlock(q, ModEconomy.RAMMED_EARTH.get().defaultBlockState(), 3);
            q = q.below();
        }
    }

    /** natural blocks (soil, rock, sand, plants, trees, snow) may be dug away; anything built by someone never is */
    static boolean natural(BlockState st) {
        if (st.isAir() || st.getBlock() instanceof PieceBlock) return false;
        if (BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace().equals(ArkRagnarok.MODID)) return false;
        return st.is(BlockTags.DIRT) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.SAND) || st.is(BlockTags.LOGS)
                || st.is(BlockTags.LEAVES) || st.is(BlockTags.REPLACEABLE) || st.is(BlockTags.REPLACEABLE_BY_TREES) || st.is(BlockTags.SNOW)
                || st.is(BlockTags.TERRACOTTA) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SAPLINGS) || st.is(Blocks.GRAVEL) || st.is(Blocks.CLAY)
                || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE) || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.MOSS_BLOCK)
                || st.is(Blocks.MOSS_CARPET) || st.is(Blocks.COBWEB) || st.is(Blocks.ICE) || st.is(Blocks.CALCITE) || st.is(Blocks.TUFF)
                || st.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
    }

    private static boolean diggable(Level level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        return natural(st) && st.getDestroySpeed(level, p) >= 0 && st.getDestroySpeed(level, p) < 30;
    }

    /** digs a natural block out of the way; trees give wood and thatch, rock gives stone and flint */
    void clearNatural(Level level, BlockPos p) {
        if (!diggable(level, p)) return;
        BlockState st = level.getBlockState(p);
        level.destroyBlock(p, false, s);
        var r = s.getRandom();
        if (st.is(BlockTags.LOGS)) {
            s.give(new ItemStack(ModEconomy.WOOD.get(), 2));
            s.give(new ItemStack(ModEconomy.THATCH.get(), 1));
        } else if (st.is(BlockTags.LEAVES)) {
            if (r.nextFloat() < 0.5f) s.give(new ItemStack(ModEconomy.THATCH.get(), 1));
        } else if (st.is(BlockTags.BASE_STONE_OVERWORLD)) {
            if (r.nextFloat() < 0.5f) s.give(new ItemStack(ModEconomy.STONE.get(), 1));
            if (r.nextFloat() < 0.15f) s.give(new ItemStack(ModEconomy.FLINT.get(), 1));
        }
    }

    // ============================================================================================ upkeep
    /** checks a slice of the base; pieces, doors and furniture that are gone are marked for rebuilding */
    int scan(SurvivorWorld.Tribe t) {
        if (t.base == null) return 0;
        List<BaseDesign.Step> p = plan(t);
        if (p.isEmpty()) return 0;
        Level level = s.level();
        int lost = 0;
        for (int k = 0; k < 24; k++) {
            scanCursor = (scanCursor + 1) % p.size();
            int i = scanCursor;
            int b = t.built[i];
            if (b == 0 || b == SKIPPED) continue;
            BaseDesign.Step st = p.get(i);
            BlockPos pos = at(t, st);
            if (!level.isLoaded(pos)) continue;
            BlockState bs = level.getBlockState(pos);
            boolean ok = switch (st.kind()) {
                case GROUND -> true;
                case PIECE -> bs.getBlock() instanceof PieceBlock && bs.getValue(PieceBlock.PIECE) == st.type();
                case DOOR -> {
                    if (!(bs.getBlock() instanceof PieceBlock)) yield false;
                    BlockState ms = level.getBlockState(PieceBlock.master(pos, bs));
                    yield ms.getBlock() instanceof PieceBlock && ms.getValue(PieceBlock.DOOR) != DoorState.NONE;
                }
                default -> bs.is(((BlockItem) Economy.furnitureItem(st.furniture())).getBlock());
            };
            if (!ok) {
                t.built[i] = 0;
                if (st.kind() == BaseDesign.Kind.PIECE || st.kind() == BaseDesign.Kind.DOOR) t.piecesLost++;
                lost++;
            }
        }
        return lost;
    }
}
