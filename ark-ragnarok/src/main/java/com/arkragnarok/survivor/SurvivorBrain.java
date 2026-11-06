package com.arkragnarok.survivor;

import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.economy.ModEconomy;
import com.arkragnarok.economy.Recipes;
import com.arkragnarok.economy.Station;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.entity.ResourceNode;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.species.Diet;
import com.arkragnarok.survivor.world.SurvivorWorld;
import com.arkragnarok.taming.Taming;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The planner of a survivor.  Every couple of seconds (more often the more intelligent it is) it decides what
 * matters most, the way a person playing alone would: stay alive (run from predators it has seen, eat, sleep in its
 * bed or shelter at night), settle on the best site it knows with the design that suits it, then work its way up the
 * civilisation stages - survival, settled, development, expansion, industry, advanced - by building its base step by
 * step.  Whatever a step needs it traces back through the economy: from the stores, crafted in the hands or at its
 * own station, or gathered (trees, plants, rocks, metal, crystal, obsidian, hunting).  It tames dinosaurs with a club
 * and food, raids rivals it has a grudge against, and keeps going while nobody is near (see {@link #catchUp}).
 */
final class SurvivorBrain extends Goal {
    private static final Logger LOG = LogUtils.getLogger();
    static final String[] STAGES = {"survival", "settled", "development", "expansion", "industry", "advanced"};

    private final Survivor s;
    String doing = "idle";
    @Nullable
    private BlockPos target;
    @Nullable
    private Entity targetEntity;
    private int work, think, stuck, taskTicks, gathered;
    private Vec3 lastPos = Vec3.ZERO;
    long lastGrudge = -1000;
    @Nullable
    private Builder.Job job;
    @Nullable
    private Economy.Way craftWay;
    private int craftBatches;
    @Nullable
    private Item fetchItem;
    private int fetchCount;
    @Nullable
    private UUID raidTribe, tameTarget;
    private int raidPhase;
    private final int[] sectors = new int[8];
    private long lastSettleTry = -10000, lastScan, lastRecount;
    private boolean exploreOk;                    // may it go exploring to find a resource it knows no source of

    SurvivorBrain(Survivor s) {
        this.s = s;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (s.isSleeping()) return false;
        LivingEntity t = s.getTarget();
        return t == null || !t.isAlive() || !canFight(t);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        taskTicks++;
        if (--think <= 0) {
            think = 30 + (int) (25 * (1 - s.tribe().personality().intelligence()));
            decide();
        }
        act();
    }

    /** one line about what it is doing (tests and debugging) */
    String debug() {
        String j = job == null ? "-" : job.step().kind() + "/" + job.step().type() + "/" + job.step().furniture() + "@" + job.pos().toShortString()
                + (job.tier() == null ? "" : " " + job.tier().id);
        BlockPos aside = job == null ? null : builder().standAside(job);
        return doing + " job=" + j + " target=" + (target == null ? "-" : target.toShortString()) + " at=" + s.blockPosition().toShortString()
                + " stuck=" + stuck + " ticks=" + taskTicks + " aside=" + (aside == null ? "-" : aside.toShortString()) + " nav="
                + (s.getNavigation().isDone() ? "done" : "moving") + " food=" + Math.round(s.food);
    }

    // ============================================================================================ judging
    /** fights only what it can beat (and the dinosaur it is knocking out for taming) */
    boolean canFight(LivingEntity e) {
        if (!e.isAlive() || s.level().isClientSide) return false;
        SurvivorWorld.Tribe t = s.tribe();
        Personality p = t.personality();
        if (e.getUUID().equals(tameTarget)) return e instanceof ArkCreature c && !c.isUnconscious() && !c.isTamed();
        if (s.getHealth() < s.getMaxHealth() * 0.3f) return false;
        double dmg = s.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (e instanceof ArkCreature c) {
            if (c.isTamed() && s.getUUID().equals(c.getOwnerId())) return false;
            if (c.isUnconscious()) return false;
            if (Perception.dangerous(c) && p.courage() < 0.85f) return false;
            return c.realMaxHealth() <= dmg * (14 + 20 * p.courage());
        }
        if (e instanceof Survivor || e instanceof Player) return s.getHealth() > s.getMaxHealth() * (0.65f - 0.35f * p.courage());
        return true;
    }

    private boolean night(Personality p) {
        long d = s.level().getDayTime() % 24000;
        long dusk = 12300 + (long) ((1 - p.caution()) * 700);
        return d > dusk && d < 23300;
    }

    private void start(String what, @Nullable BlockPos t, @Nullable Entity e) {
        if (!what.equals(doing) || (t != null && !t.equals(target)) || e != targetEntity) {
            work = 0;
            taskTicks = 0;
            stuck = 0;
            gathered = 0;
        }
        doing = what;
        target = t;
        targetEntity = e;
        s.setTask(what);
    }

    private void finish() {
        doing = "idle";
        target = null;
        targetEntity = null;
        job = null;
        craftWay = null;
        fetchItem = null;
        think = 0;
        s.setTask("idle");
    }

    private boolean committed() {
        if (taskTicks > 2400) return false;
        return switch (doing) {
            case "build", "level", "upgrade", "craft", "fetch", "store", "tame", "raid", "chop", "mine", "quarry", "forage", "harvest", "cave", "pen" -> true;
            default -> false;
        };
    }

    // ============================================================================================ deciding
    private void decide() {
        Level level = s.level();
        long now = level.getGameTime();
        SurvivorWorld.Tribe t = s.tribe();
        Personality p = t.personality();
        Vec3 me = s.position();
        advance(t);
        if (t.base != null && now - lastScan > 200 && t.base.distToCenterSqr(me) < 64 * 64) {
            lastScan = now;
            builder().scan(t);
        }
        if (t.base != null && now - lastRecount > 600 && t.base.distToCenterSqr(me) < 64 * 64) {
            lastRecount = now;
            recount(t);
        }
        // 1. danger: a predator it has seen close by, or losing a fight
        for (Memory.Fact f : s.memory.all(Memory.Kind.THREAT)) {
            if (now - f.seen() > 600) {
                s.memory.forget(f);
                continue;
            }
            if (f.pos().distToCenterSqr(me) < p.fleeDistance() * p.fleeDistance()) {
                flee(t, Vec3.atCenterOf(f.pos()));
                return;
            }
        }
        if (now - s.lastAttacked < 200 && s.getHealth() < s.getMaxHealth() * 0.35f && s.lastAttacker != null) {
            Entity a = ((ServerLevel) level).getEntity(s.lastAttacker);
            if (a != null) {
                s.setTarget(null);
                flee(t, a.position());
                return;
            }
        }
        // 2. hunger
        if (s.food < 11) s.eatSomething();
        if (s.food < 7 && getFood(t)) return;
        // 3. night: its bed, or at least inside its shelter with the door shut
        if (night(p) && t.base != null && Builder.phaseDone(t, 1) >= 0.5f && !doing.equals("raid")) {
            BlockPos bed = builder().bed(t);
            if (bed != null && !level.getBlockState(bed).getValue(BedBlock.OCCUPIED)) start("sleep", bed, null);
            else start("shelter", t.base.offset(BaseDesign.rotate(Builder.design(t).shelterOffset(), t.rotation)), null);
            return;
        }
        if (doing.equals("sleep") || doing.equals("shelter")) finish();
        if (doing.equals("flee")) finish();
        // 4. keep at what it is doing
        if (committed()) return;
        // 5. a full pack goes into the stores
        if (s.freeSlots() < 5 && t.base != null && !builder().boxes(t).isEmpty()) {
            BlockPos box = nearestBox(t, null);
            if (box != null) {
                start("store", box, null);
                return;
            }
        }
        // 6. basic tools come first (when it knows where to get what they need)
        exploreOk = false;
        if (needTool(ItemTags.PICKAXES) && resolve(t, ModEconomy.STONE_PICK.get(), 1, 0)) return;
        if (needTool(ItemTags.AXES) && resolve(t, ModEconomy.STONE_HATCHET.get(), 1, 0)) return;
        exploreOk = true;
        // 7. settle: once it carries enough for a shelter (or has looked around long enough)
        if (t.base == null) {
            boolean ready = s.count(ModEconomy.WOOD.get()) >= 18 && s.count(ModEconomy.THATCH.get()) >= 24 && s.count(ModEconomy.FIBER.get()) >= 8;
            if ((ready || s.tickCount > 9000) && now - lastSettleTry > 200) {
                lastSettleTry = now;
                DesignSelector.Choice c = DesignSelector.choose(s, t, s.world());
                if (c != null) {
                    Builder.settle(t, c.anchor(), c.design(), c.rotation());
                    s.world().setDirty();
                    LOG.info("Survivor {} ({}, {}) settles at {}: design #{} {} turned {}x90", s.getName().getString(), t.name, p.temperament(),
                            c.anchor(), c.design(), BaseDesign.get(c.design()).describe(), c.rotation());
                    return;
                }
            }
            exploreOk = true;
            if (!ready) {
                if (resolve(t, ModEconomy.THATCH.get(), 30, 0) || resolve(t, ModEconomy.WOOD.get(), 20, 0) || resolve(t, ModEconomy.FIBER.get(), 10, 0)) return;
            }
            explore(t);
            return;
        }
        // 8. rivals: raid one it has a grudge against (aggressive ones need less reason)
        if (t.stage >= 3 && tryRaid(t, p, now)) return;
        // 9. tame a dinosaur when it is settled and has the means
        exploreOk = false;
        if (t.stage >= 2 && tryTame(t, p)) return;
        // 10. the base: next step of the blueprint (repairs come back here too), else an upgrade
        Builder.Job j = builder().next(t, now);
        if (j == null) j = builder().upgrade(t);
        if (j != null) {
            List<Recipes.Ing> missing = builder().missing(j);
            if (missing.isEmpty()) {
                job = j;
                String what = j.step().kind() == BaseDesign.Kind.GROUND ? "level" : j.upgrade() ? "upgrade" : "build";
                start(what, j.pos(), null);
                return;
            }
            exploreOk = true;
            for (Recipes.Ing g : missing) if (resolve(t, g.item().get(), s.count(g.item().get()) + g.count(), 0)) return;
            builder().delay(j, now, 400);
        }
        // 11. what its stage needs: better tools, weapons, processed materials, food
        exploreOk = false;
        for (Recipes.Ing w : wants(t)) if (resolve(t, w.item().get(), w.count(), 0)) return;
        // 12. stockpile (greedy ones more), or see more of the world
        if (stockpile(t, p)) return;
        explore(t);
    }

    private Builder builder() {
        return s.builder;
    }

    private boolean needTool(net.minecraft.tags.TagKey<Item> tag) {
        for (int i = 0; i < s.inv.getContainerSize(); i++) if (s.inv.getItem(i).is(tag)) return false;
        return true;
    }

    /** the civilisation stages, each reached with real things built and made */
    private void advance(SurvivorWorld.Tribe t) {
        int before = t.stage;
        if (t.base == null) return;
        switch (t.stage) {
            case 1 -> {
                if (Builder.phaseDone(t, 1) >= 0.9f) t.stage = 2;
            }
            case 2 -> {
                if (builder().station(t, Station.SMITHY) != null && builder().station(t, Station.MORTAR) != null
                        && builder().station(t, Station.CAMPFIRE) != null && Builder.phaseDone(t, 2) >= 0.6f) t.stage = 3;
            }
            case 3 -> {
                if (builder().station(t, Station.FORGE) != null && (s.count(ModEconomy.METAL_PICK.get()) > 0 || s.count(ModEconomy.METAL_HATCHET.get()) > 0)) {
                    t.stage = 4;
                }
            }
            case 4 -> {
                if (t.ingots >= 30 && Builder.phaseDone(t, 3) >= 0.6f) t.stage = 5;
            }
            case 5 -> {
                if (builder().station(t, Station.FABRICATOR) != null && t.electronics >= 2) t.stage = 6;
            }
            default -> {
            }
        }
        if (t.stage != before) {
            LOG.info("Survivor {} ({}) reached stage {} ({})", s.getName().getString(), t.name, t.stage, STAGES[t.stage - 1]);
            s.world().setDirty();
        }
    }

    /** what the stage asks for besides the base (stage gates the tools and materials it aims at) */
    private List<Recipes.Ing> wants(SurvivorWorld.Tribe t) {
        List<Recipes.Ing> l = new ArrayList<>();
        if (needTool(ItemTags.SWORDS)) l.add(ing(ModEconomy.WOODEN_CLUB.get(), 1));
        if (s.foodItems() < 10) l.add(ing(ModItems.MEJOBERRY.get(), 16));
        if (s.count(ModEconomy.RAW_MEAT.get()) > 0 && builder().station(t, Station.CAMPFIRE) != null) {
            l.add(ing(ModEconomy.COOKED_MEAT.get(), s.count(ModEconomy.COOKED_MEAT.get()) + s.count(ModEconomy.RAW_MEAT.get())));
        }
        if (t.stage >= 2 && s.count(ModEconomy.STONE_SPEAR.get()) + s.count(ModEconomy.METAL_SWORD.get()) == 0) l.add(ing(ModEconomy.STONE_SPEAR.get(), 1));
        if (t.stage >= 3) {
            l.add(ing(ModEconomy.METAL_PICK.get(), 1));
            l.add(ing(ModEconomy.METAL_HATCHET.get(), 1));
        }
        if (t.stage >= 4) {
            l.add(ing(ModEconomy.METAL_SWORD.get(), 1));
            l.add(ing(ModEconomy.METAL_SICKLE.get(), 1));
            l.add(ing(ModEconomy.METAL_INGOT.get(), 12));
            l.add(ing(ModEconomy.CEMENTING_PASTE.get(), 6));
        }
        if (t.stage >= 5) {
            l.add(ing(ModEconomy.STEEL_INGOT.get(), 6));
            l.add(ing(ModEconomy.POLYMER.get(), 4));
            l.add(ing(ModEconomy.ELECTRONICS.get(), 2));
        }
        return l;
    }

    private static Recipes.Ing ing(Item it, int n) {
        return new Recipes.Ing(() -> it, n);
    }

    // ============================================================================================ the economy
    /**
     * makes sure it gets {@code need} of an item in the pack: from the stores, crafted (in the hands or at its own
     * station, ingredients first), or gathered; starts the first thing to do and returns true, false if it cannot
     */
    private boolean resolve(SurvivorWorld.Tribe t, Item item, int need, int depth) {
        if (depth > 5) return false;
        int have = s.count(item);
        if (have >= need) return false;
        int stored = t.stock.getOrDefault(Economy.key(item), 0);
        if (stored > 0 && t.base != null && t.base.distToCenterSqr(s.position()) < 96 * 96) {
            BlockPos box = nearestBox(t, item);
            if (box != null) {
                fetchItem = item;
                fetchCount = Math.min(stored, need - have);
                start("fetch", box, null);
                return true;
            }
        }
        for (Economy.Way w : Economy.ways(item)) {
            if (w.station() != null && builder().station(t, w.station()) == null) continue;     // it has no such station (yet)
            boolean ok = true;
            for (Recipes.Ing g : w.recipe().ingredients()) {
                if (s.count(g.item().get()) >= g.count()) continue;
                ok = false;
                if (resolve(t, g.item().get(), g.count(), depth + 1)) return true;
            }
            if (ok) {
                craftWay = w;
                craftBatches = Math.max(1, Math.min(8, (need - have + w.recipe().count() - 1) / w.recipe().count()));
                start("craft", w.station() == null ? null : builder().station(t, w.station()), null);
                return true;
            }
        }
        Economy.Source src = Economy.source(item);
        return src != null && gather(t, src, item);
    }

    private boolean gather(SurvivorWorld.Tribe t, Economy.Source src, Item item) {
        Vec3 me = s.position();
        Level level = s.level();
        switch (src) {
            case TREE -> {
                Memory.Fact tree = s.memory.nearest(Memory.Kind.TREE, me, 140);
                if (tree != null) {
                    start("chop", tree.pos(), null);
                    return true;
                }
            }
            case PLANT -> {
                BlockPos ripe = ripeCrop(t);
                if (ripe != null) {
                    start("harvest", ripe, null);
                    return true;
                }
                BlockPos plant = nearestPlant(level, s.blockPosition());
                if (plant != null) {
                    start("forage", plant, null);
                    return true;
                }
            }
            case ROCK, METAL, CRYSTAL, OBSIDIAN -> {
                if (src == Economy.Source.OBSIDIAN && s.count(ModEconomy.METAL_PICK.get()) == 0) return resolve(t, ModEconomy.METAL_PICK.get(), 1, 1);
                ResourceNode n = nearestNode(src);
                if (n != null) {
                    start("mine", n.blockPosition(), n);
                    return true;
                }
                if (src == Economy.Source.ROCK) {
                    Memory.Fact st = s.memory.nearest(Memory.Kind.STONE, me, 90);
                    if (st != null) {
                        start("quarry", st.pos(), null);
                        return true;
                    }
                }
                Memory.Fact cave = s.memory.nearest(Memory.Kind.CAVE, me, 150);
                if (cave != null) {
                    start("cave", cave.pos(), null);
                    return true;
                }
            }
            case HUNT -> {
                LivingEntity prey = nearestPrey(t);
                if (prey != null) {
                    hunt(prey);
                    return true;
                }
            }
        }
        if (!exploreOk) return false;
        explore(t);
        return true;
    }

    @Nullable
    private ResourceNode nearestNode(Economy.Source src) {
        Vec3 me = s.position();
        ResourceNode best = null;
        double bd = 1e18;
        for (Memory.Fact f : s.memory.all(Memory.Kind.NODE)) {
            boolean useful = switch (src) {
                case ROCK -> f.detail().equals("rock") || f.detail().equals("metal");
                case METAL -> f.detail().equals("metal");
                case CRYSTAL -> f.detail().equals("crystal") || f.detail().equals("metal");
                default -> f.detail().equals("obsidian");
            };
            if (!useful) continue;
            double d = f.pos().distToCenterSqr(me) * (f.detail().equals(src == Economy.Source.ROCK ? "rock" : "x") ? 0.7 : 1.0);
            if (d >= bd) continue;
            Entity e = ((ServerLevel) s.level()).getEntity(f.entity());
            if (!(e instanceof ResourceNode rn) || rn.depleted()) {
                s.memory.forget(f);
                continue;
            }
            bd = d;
            best = rn;
        }
        return best;
    }

    @Nullable
    private static BlockPos nearestPlant(Level level, BlockPos me) {
        BlockPos best = null;
        double bd = 1e9;
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-12, -3, -12), me.offset(12, 3, 12))) {
            BlockState st = level.getBlockState(p);
            if (st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN) || st.is(Blocks.LARGE_FERN) || st.is(Blocks.SWEET_BERRY_BUSH)
                    || (st.is(BlockTags.LEAVES) && p.getY() <= me.getY() + 2)) {
                double d = p.distSqr(me);
                if (d < bd) {
                    bd = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    @Nullable
    private BlockPos ripeCrop(SurvivorWorld.Tribe t) {
        for (BlockPos p : builder().furniture(t, "crop_plot")) if (com.arkragnarok.economy.CropPlotBlock.ripe(s.level().getBlockState(p))) return p;
        return null;
    }

    @Nullable
    private LivingEntity nearestPrey(SurvivorWorld.Tribe t) {
        LivingEntity best = null;
        double bd = 1e18;
        for (Entity e : s.level().getEntities(s, s.getBoundingBox().inflate(48, 16, 48), e -> e instanceof Animal || e instanceof ArkCreature)) {
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (e instanceof ArkCreature c && (c.isTamed() || Perception.dangerous(c))) continue;
            if (!canFight(le) || !s.perception.visible(e)) continue;
            double d = e.distanceToSqr(s);
            if (d < bd) {
                bd = d;
                best = le;
            }
        }
        return best;
    }

    private void hunt(LivingEntity prey) {
        s.hold(st -> st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES));
        s.setTarget(prey);
        start("hunt", prey.blockPosition(), prey);
    }

    /** food: what it carries, its crops, cooking raw meat at its campfire, berries, or a hunt */
    private boolean getFood(SurvivorWorld.Tribe t) {
        if (s.count(ModEconomy.RAW_MEAT.get()) > 0 && builder().station(t, Station.CAMPFIRE) != null
                && resolve(t, ModEconomy.COOKED_MEAT.get(), s.count(ModEconomy.COOKED_MEAT.get()) + 1, 0)) return true;
        return resolve(t, ModItems.MEJOBERRY.get(), 10, 0);
    }

    private boolean stockpile(SurvivorWorld.Tribe t, Personality p) {
        int want = (int) (40 + 120 * p.greed());
        Item[] basics = {ModEconomy.WOOD.get(), ModEconomy.THATCH.get(), ModEconomy.FIBER.get(), ModEconomy.STONE.get(), ModEconomy.HIDE.get(),
                ModEconomy.METAL_ORE.get()};
        for (Item it : basics) {
            if (it == ModEconomy.METAL_ORE.get() && t.stage < 3) continue;
            int have = s.count(it) + t.stock.getOrDefault(Economy.key(it), 0);
            int goal = it == ModEconomy.HIDE.get() ? want / 6 : want;
            if (have < goal && resolve(t, it, s.count(it) + Math.min(32, goal - have), 1)) return true;
        }
        return false;
    }

    private void explore(SurvivorWorld.Tribe t) {
        if (doing.equals("explore") && target != null && target.distToCenterSqr(s.position()) > 36) return;
        int sec = 0;
        for (int i = 1; i < 8; i++) if (sectors[i] < sectors[sec] || (sectors[i] == sectors[sec] && s.getRandom().nextBoolean())) sec = i;
        sectors[sec]++;
        double a = (sec + s.getRandom().nextDouble()) * Math.PI / 4;
        double r = 40 + s.getRandom().nextDouble() * 50 * (0.6 + t.personality().ambition());
        Vec3 from = t.base != null ? Vec3.atCenterOf(t.base) : s.position();
        Vec3 p = from.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        int y = s.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) p.x, (int) p.z);
        start("explore", BlockPos.containing(p.x, y, p.z), null);
    }

    private void flee(SurvivorWorld.Tribe t, Vec3 from) {
        Vec3 me = s.position();
        Vec3 away = me.subtract(from).normalize().scale(28);
        if (t.base != null) {
            Vec3 home = Vec3.atCenterOf(t.base);
            if (home.subtract(me).normalize().dot(away.normalize()) > 0.2) away = home.subtract(me);
        }
        start("flee", BlockPos.containing(me.add(away)), null);
    }

    // ============================================================================================ stores
    /** recounts what lies in its storage boxes (the tribe's stock) */
    void recount(SurvivorWorld.Tribe t) {
        t.stock.clear();
        for (Map.Entry<BlockPos, BaseDesign.Room> e : builder().boxes(t)) {
            if (!(s.level().getBlockEntity(e.getKey()) instanceof Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (!st.isEmpty()) t.stock.merge(Economy.key(st.getItem()), st.getCount(), Integer::sum);
            }
        }
        s.world().setDirty();
    }

    /** nearest box (holding the item, when one is given) */
    @Nullable
    private BlockPos nearestBox(SurvivorWorld.Tribe t, @Nullable Item item) {
        BlockPos best = null;
        double bd = 1e18;
        for (Map.Entry<BlockPos, BaseDesign.Room> e : builder().boxes(t)) {
            if (item != null) {
                if (!(s.level().getBlockEntity(e.getKey()) instanceof Container c) || Recipes.count(c, item) == 0) continue;
            }
            double d = e.getKey().distToCenterSqr(s.position());
            if (d < bd) {
                bd = d;
                best = e.getKey();
            }
        }
        return best;
    }

    /** how much of a stack it keeps in the pack */
    private int keep(ItemStack st) {
        if (st.getMaxStackSize() == 1) return st.getCount();
        if (Survivor.edible(st)) return 16;
        if (st.is(ModEconomy.STONE_ARROW.get()) || st.is(ModItems.TRANQ_ARROW.get())) return 32;
        if (job != null) for (Recipes.Ing g : job.cost()) if (st.is(g.item().get())) return 64;
        return s.freeSlots() < 4 ? 0 : 24;
    }

    private void store(SurvivorWorld.Tribe t) {
        if (!(s.level().getBlockEntity(target) instanceof Container box)) {
            finish();
            return;
        }
        BaseDesign.Room room = null;
        for (Map.Entry<BlockPos, BaseDesign.Room> e : builder().boxes(t)) if (e.getKey().equals(target)) room = e.getValue();
        int cat = Economy.roomCategory(room);
        boolean only = builder().boxes(t).size() == 1;
        java.util.Map<Item, Integer> kept = new java.util.HashMap<>();
        for (int i = 0; i < s.inv.getContainerSize(); i++) {
            ItemStack st = s.inv.getItem(i);
            if (st.isEmpty()) continue;
            int k = keep(st) - kept.getOrDefault(st.getItem(), 0);
            int move = st.getCount() - Math.max(0, k);
            kept.merge(st.getItem(), Math.min(st.getCount(), Math.max(0, k)), Integer::sum);
            if (move <= 0) continue;
            if (!only && cat >= 0 && Economy.category(st) != cat && s.freeSlots() >= 4) continue;      // typed storage
            ItemStack part = st.split(move);
            ItemStack rest = insert(box, part);
            if (!rest.isEmpty()) st.grow(rest.getCount());
        }
        s.inv.setChanged();
        box.setChanged();
        s.level().playSound(null, target, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.6f, 1.0f);
        recount(t);
        finish();
    }

    private static ItemStack insert(Container c, ItemStack st) {
        for (int i = 0; i < c.getContainerSize() && !st.isEmpty(); i++) {
            ItemStack in = c.getItem(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, st) && in.getCount() < in.getMaxStackSize()) {
                int k = Math.min(st.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(k);
                st.shrink(k);
            }
        }
        for (int i = 0; i < c.getContainerSize() && !st.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, st.copy());
                st.setCount(0);
            }
        }
        return st;
    }

    private void fetch(SurvivorWorld.Tribe t) {
        if (fetchItem == null || !(s.level().getBlockEntity(target) instanceof Container box)) {
            finish();
            return;
        }
        int left = fetchCount;
        for (int i = 0; i < box.getContainerSize() && left > 0; i++) {
            ItemStack st = box.getItem(i);
            if (!st.is(fetchItem)) continue;
            int k = Math.min(left, st.getCount());
            s.give(st.split(k));
            left -= k;
        }
        box.setChanged();
        s.level().playSound(null, target, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6f, 1.1f);
        recount(t);
        finish();
    }

    // ============================================================================================ rivals and dinosaurs
    private boolean tryRaid(SurvivorWorld.Tribe t, Personality p, long now) {
        if (doing.equals("raid")) return true;
        if (now - t.lastRaid < p.raidInterval() || s.getHealth() < s.getMaxHealth() * 0.7f || s.food < 12 || needTool(ItemTags.SWORDS)) return false;
        SurvivorWorld.Tribe victim = null;
        double bd = 1e18;
        for (SurvivorWorld.Tribe o : s.world().all()) {
            if (o == t || o.base == null || o.id.equals(t.ally)) continue;
            int rel = t.relation(o.id);
            boolean reason = rel <= -30 || (p.aggression() > 0.65f && rel < -5) || (p.greed() > 0.8f && !o.stock.isEmpty() && rel < 0);
            if (!reason) continue;
            double d = o.base.distSqr(t.base);
            if (d > 200 * 200 || d >= bd || !s.level().isLoaded(o.base)) continue;
            bd = d;
            victim = o;
        }
        if (victim == null) return false;
        t.lastRaid = now;
        raidTribe = victim.id;
        raidPhase = 0;
        start("raid", victim.base.offset(BaseDesign.rotate(Builder.design(victim).doorOffset(), victim.rotation)), null);
        LOG.info("Survivor {} ({}) raids {}", s.getName().getString(), t.name, victim.name);
        return true;
    }

    private void raid(SurvivorWorld.Tribe t) {
        SurvivorWorld.Tribe v = raidTribe == null ? null : s.world().find(raidTribe);
        if (v == null || v.base == null || target == null) {
            finish();
            return;
        }
        s.hold(st -> st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES) || st.is(ItemTags.PICKAXES));
        if (raidPhase == 0) {
            // break in: the door (or the wall piece) in the way
            if (!reach(target, 3.0, 4.0)) {
                walkTo(target, 2.2, 1.15);
                return;
            }
            BlockState bs = s.level().getBlockState(target);
            if (!(bs.getBlock() instanceof PieceBlock pb)) {
                raidPhase = 1;
                return;
            }
            if (++work % 10 == 0) {
                s.swing(InteractionHand.MAIN_HAND);
                s.level().playSound(null, target, pb.tier.sound.getHitSound(), SoundSource.BLOCKS, 1.0f, 0.7f);
            }
            float speed = (float) s.getAttributeValue(Attributes.ATTACK_DAMAGE) * t.skills.speed(Skills.Skill.COMBAT);
            if (work < (int) (pb.tier.hardness * 60 / Math.max(1f, speed))) return;
            BlockPos m = PieceBlock.master(target, bs);
            s.level().destroyBlock(m, false, s);
            s.world().event(t.id, v.id, "raided", -25, s.level().getGameTime());
            t.skills.gain(Skills.Skill.COMBAT, 6f, t.personality());
            raidPhase = 1;
            work = 0;
            alarm(v);
            return;
        }
        if (raidPhase == 1) {
            // loot the nearest box of theirs
            BlockPos best = null;
            double bd = 1e18;
            for (Map.Entry<BlockPos, BaseDesign.Room> e : builder().boxes(v)) {
                double d = e.getKey().distToCenterSqr(s.position());
                if (d < bd) {
                    bd = d;
                    best = e.getKey();
                }
            }
            if (best == null) {
                finish();
                return;
            }
            target = best;
            raidPhase = 2;
            return;
        }
        if (!reach(target, 2.5, 3.0)) {
            walkTo(target, 1.8, 1.1);
            if (stuck > 8) finish();
            return;
        }
        if (s.level().getBlockEntity(target) instanceof Container box) {
            int took = 0;
            for (int i = 0; i < box.getContainerSize() && took < 6 && s.freeSlots() > 2; i++) {
                ItemStack st = box.getItem(i);
                if (st.isEmpty()) continue;
                s.give(st.copy());
                box.setItem(i, ItemStack.EMPTY);
                took++;
            }
            box.setChanged();
            if (took > 0) {
                s.world().event(t.id, v.id, "looted", -30, s.level().getGameTime());
                t.raids++;
                v.stock.clear();
                LOG.info("Survivor {} ({}) looted {} stacks from {}", s.getName().getString(), t.name, took, v.name);
            }
        }
        finish();
    }

    /** the victims come running if they are near */
    private void alarm(SurvivorWorld.Tribe v) {
        Entity e = ((ServerLevel) s.level()).getEntity(v.id);
        if (e instanceof Survivor victim && victim.distanceToSqr(s) < 64 * 64 && victim.brain.canFight(s)) {
            victim.setTarget(s);
            v.defended++;
        }
    }

    private boolean tryTame(SurvivorWorld.Tribe t, Personality p) {
        if (doing.equals("tame")) return true;
        t.tames.removeIf(id -> !(((ServerLevel) s.level()).getEntity(id) instanceof ArkCreature c) ? false : !c.isAlive());
        if (t.tames.size() >= 1 + t.stage / 2 || p.courage() + p.ambition() < 0.7f) return false;
        if (s.count(ModEconomy.WOODEN_CLUB.get()) == 0) return resolve(t, ModEconomy.WOODEN_CLUB.get(), 1, 0);
        // from the development stage on it tranquilizes from a distance, like a player: bow and tranq arrows
        if (t.stage >= 3) {
            if (s.count(ModEconomy.PRIMITIVE_BOW.get()) == 0 && resolve(t, ModEconomy.PRIMITIVE_BOW.get(), 1, 0)) return true;
            if (s.count(ModItems.TRANQ_ARROW.get()) < 8 && resolve(t, ModItems.TRANQ_ARROW.get(), 16, 0)) return true;
        }
        boolean ranged = s.count(ModEconomy.PRIMITIVE_BOW.get()) > 0 && s.count(ModItems.TRANQ_ARROW.get()) > 0;
        ArkCreature best = null;
        double bd = 1e18;
        for (Memory.Fact f : s.memory.all(Memory.Kind.DINO)) {
            if (f.entity() == null || f.pos().distToCenterSqr(s.position()) > 70 * 70) continue;
            Entity e = ((ServerLevel) s.level()).getEntity(f.entity());
            if (!(e instanceof ArkCreature c) || !c.isAlive() || c.isTamed() || Perception.dangerous(c) || c.getBbHeight() > (ranged ? 4.5f : 2.6f)) {
                s.memory.forget(f);
                continue;
            }
            double d = c.distanceToSqr(s);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        if (best == null) return false;
        // taming food first: its kibble if it has some, else berries for plant eaters and meat for meat eaters
        boolean meat = best.species().diet != Diet.HERBIVORE;
        Item foodItem = meat ? ModEconomy.RAW_MEAT.get() : ModItems.MEJOBERRY.get();
        if (s.count(Taming.preferredKibble(best.species()).getItem()) == 0 && s.count(foodItem) < 12) return resolve(t, foodItem, 16, 0);
        tameTarget = best.getUUID();
        start("tame", best.blockPosition(), best);
        LOG.info("Survivor {} ({}) goes to tame a {}", s.getName().getString(), t.name, best.species().id);
        return true;
    }

    private void tame(SurvivorWorld.Tribe t) {
        if (!(targetEntity instanceof ArkCreature c) || !c.isAlive()) {
            tameTarget = null;
            finish();
            return;
        }
        if (c.isTamed()) {
            if (s.getUUID().equals(c.getOwnerId())) {
                if (!t.tames.contains(c.getUUID())) t.tames.add(c.getUUID());
                t.tamed++;
                c.setOrder(ArkCreature.FOLLOW);
                t.skills.gain(Skills.Skill.TAMING, 20f, t.personality());
                s.world().setDirty();
                LOG.info("Survivor {} ({}) tamed a {}", s.getName().getString(), t.name, c.species().id);
                tameTarget = null;
                BlockPos pen = builder().penSpot(t);
                if (pen != null) {
                    start("pen", pen, c);                // it follows its new owner home into the pen
                    return;
                }
            }
            tameTarget = null;
            finish();
            return;
        }
        if (!c.isUnconscious() && s.count(ModEconomy.PRIMITIVE_BOW.get()) > 0 && s.count(ModItems.TRANQ_ARROW.get()) > 0) {
            shootTranq(t, c);
            if (taskTicks > 3000) {
                tameTarget = null;
                finish();
            }
            return;
        }
        if (!c.isUnconscious()) {
            s.hold(st -> st.is(ModEconomy.WOODEN_CLUB.get()));
            s.setTarget(c);                       // knock it out with the club (the melee goal does the hitting)
            if (taskTicks > 2400) {
                tameTarget = null;
                s.setTarget(null);
                finish();
            }
            return;
        }
        s.setTarget(null);
        if (!reach(c.blockPosition(), 3.0 + c.getBbWidth() * 0.5, 3.0)) {
            walkTo(c.blockPosition(), 2.0 + c.getBbWidth() * 0.5, 1.0);
            return;
        }
        s.getLookControl().setLookAt(c);
        if (++work % 60 != 0) return;
        // feed: its preferred kibble, else plain food; narcoberries keep it asleep
        ItemStack kib = Taming.preferredKibble(c.species());
        Item give = s.count(kib.getItem()) > 0 ? kib.getItem() : c.species().diet != Diet.HERBIVORE ? ModEconomy.RAW_MEAT.get() : ModItems.MEJOBERRY.get();
        if (c.getTorpor() < c.maxTorpor() * 0.35f && s.count(ModItems.NARCOBERRY.get()) > 0) give = ModItems.NARCOBERRY.get();
        if (s.count(give) == 0) {
            if (!resolve(t, give, 6, 1)) finish();
            return;
        }
        ItemStack st = new ItemStack(give, Math.min(3, s.count(give)));
        int n = st.getCount();
        if (c.feedForTaming(s, st)) {
            s.take(give, n - st.getCount());
            s.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** keeps a safe distance and puts tranquilizer arrows into it until it falls asleep */
    private void shootTranq(SurvivorWorld.Tribe t, ArkCreature c) {
        s.setTarget(null);
        s.hold(st -> st.is(ModEconomy.PRIMITIVE_BOW.get()));
        double d = s.distanceTo(c);
        double want = 10 + c.getBbWidth();
        if (d > want + 6 || !s.perception.visible(c)) {
            walkTo(c.blockPosition(), want, 1.0);
            return;
        }
        if (d < want - 4) {
            Vec3 away = s.position().subtract(c.position()).normalize().scale(6);
            s.getNavigation().moveTo(s.getX() + away.x, s.getY(), s.getZ() + away.z, 1.1);
        } else {
            s.getNavigation().stop();
        }
        s.getLookControl().setLookAt(c, 30f, 30f);
        if (++work % 30 != 0) return;
        Level level = s.level();
        com.arkragnarok.entity.TranqArrow a = new com.arkragnarok.entity.TranqArrow(level, s, new ItemStack(ModItems.TRANQ_ARROW.get()),
                s.getMainHandItem());
        double dx = c.getX() - s.getX(), dz = c.getZ() - s.getZ();
        double dy = c.getY(0.5) - a.getY();
        double h = Math.sqrt(dx * dx + dz * dz);
        float spread = Math.max(0.5f, 5f - t.skills.level(Skills.Skill.COMBAT) * 0.15f);
        a.shoot(dx, dy + h * 0.14, dz, 2.6f, spread);
        level.addFreshEntity(a);
        s.take(ModItems.TRANQ_ARROW.get(), 1);
        s.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0f, 1.0f);
        t.skills.gain(Skills.Skill.COMBAT, 0.5f, t.personality());
    }

    // ============================================================================================ while nobody was near
    /**
     * The world went on while this survivor was far from every player (its area was not loaded): it gathered at the
     * pace of its skills and stage and built what it could pay for.  Applied cheaply in one go when it loads again.
     */
    void catchUp(SurvivorWorld.Tribe t, long gap) {
        int minutes = (int) Math.min(120, gap / 1200);
        if (minutes <= 0) return;
        float k = (1f + 0.03f * t.skills.level(Skills.Skill.GATHERING)) * (0.8f + t.personality().greed() * 0.4f);
        List<ItemStack> got = new ArrayList<>();
        got.add(new ItemStack(ModEconomy.WOOD.get(), (int) (5 * minutes * k)));
        got.add(new ItemStack(ModEconomy.THATCH.get(), (int) (4 * minutes * k)));
        got.add(new ItemStack(ModEconomy.FIBER.get(), (int) (4 * minutes * k)));
        got.add(new ItemStack(ModEconomy.STONE.get(), (int) (3 * minutes * k)));
        got.add(new ItemStack(ModEconomy.FLINT.get(), (int) (1 * minutes * k)));
        got.add(new ItemStack(ModItems.MEJOBERRY.get(), (int) (2 * minutes * k)));
        got.add(new ItemStack(ModEconomy.HIDE.get(), (int) (0.3f * minutes * k)));
        if (t.stage >= 3) got.add(new ItemStack(ModEconomy.METAL_ORE.get(), (int) (2 * minutes * k)));
        if (t.stage >= 4) got.add(new ItemStack(ModEconomy.CRYSTAL.get(), (int) (0.3f * minutes * k)));
        List<Map.Entry<BlockPos, BaseDesign.Room>> boxes = builder().boxes(t);
        for (ItemStack big : got) {
            int left = big.getCount();
            while (left > 0) {
                ItemStack st = new ItemStack(big.getItem(), Math.min(64, left));
                left -= st.getCount();
                for (Map.Entry<BlockPos, BaseDesign.Room> e : boxes) {
                    if (st.isEmpty()) break;
                    if (s.level().getBlockEntity(e.getKey()) instanceof Container c) st = insert(c, st);
                }
                if (!st.isEmpty() && s.freeSlots() > 3) s.inv.addItem(st);
            }
        }
        t.skills.gain(Skills.Skill.GATHERING, minutes * 2f, t.personality());
        recount(t);
        // build what it can now afford, as if it had done it while away
        int built = 0;
        long now = s.level().getGameTime();
        for (int n = 0; n < minutes * 2 && t.base != null; n++) {
            Builder.Job j = builder().next(t, now);
            if (j == null) break;
            List<Recipes.Ing> miss = builder().missing(j);
            for (Recipes.Ing g : miss) {
                BlockPos box = nearestBox(t, g.item().get());
                if (box != null && s.level().getBlockEntity(box) instanceof Container c) {
                    int left = g.count();
                    for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
                        ItemStack st = c.getItem(i);
                        if (!st.is(g.item().get())) continue;
                        int q = Math.min(left, st.getCount());
                        s.give(st.split(q));
                        left -= q;
                    }
                }
                handCraft(g.item().get(), g.count());
            }
            if (!builder().missing(j).isEmpty()) {
                builder().delay(j, now, 600);
                continue;
            }
            if (builder().instant(t, j)) built++;
        }
        s.food = Math.max(s.food, 16f);
        s.setHealth(s.getMaxHealth());
        recount(t);
        s.world().setDirty();
        LOG.info("Survivor {} ({}) was away {} min: gathered and built {} steps", s.getName().getString(), t.name, minutes, built);
    }

    /** crafts in the hands right away if it has the ingredients (offline catch-up) */
    private void handCraft(Item item, int need) {
        for (Economy.Way w : Economy.ways(item)) {
            if (w.station() != null) continue;
            for (int k = 0; k < 4 && s.count(item) < need && Recipes.pay(s.inv, w.recipe().ingredients()); k++) s.give(w.recipe().resultStack());
            return;
        }
    }

    // ============================================================================================ doing it
    private boolean reach(BlockPos p, double horizontal, double vertical) {
        Vec3 c = Vec3.atBottomCenterOf(p);
        double dx = c.x - s.getX(), dz = c.z - s.getZ();
        return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(c.y - s.getY()) <= vertical;
    }

    private boolean walkTo(BlockPos p, double reach, double speed) {
        Vec3 c = Vec3.atBottomCenterOf(p);
        if (s.position().distanceToSqr(c) <= reach * reach) {
            s.getNavigation().stop();
            s.getLookControl().setLookAt(c.x, c.y + 0.5, c.z);
            return true;
        }
        if (s.getNavigation().isDone() || s.tickCount % 20 == 0) {
            Vec3 goal = c;
            if (s.position().distanceTo(c) > 80) goal = s.position().add(c.subtract(s.position()).normalize().scale(60));
            if (!s.getNavigation().moveTo(goal.x, goal.y, goal.z, speed)) stuck++;
        }
        if (s.tickCount % 40 == 0) {
            if (s.position().distanceToSqr(lastPos) < 0.5) stuck++;
            else stuck = Math.max(0, stuck - 1);
            lastPos = s.position();
        }
        return false;
    }

    private void act() {
        SurvivorWorld.Tribe t = s.tribe();
        switch (doing) {
            case "flee" -> {
                if (target != null && walkTo(target, 3, 1.45)) finish();
            }
            case "sleep" -> sleep(t);
            case "shelter" -> {
                if (target != null) walkTo(target, 0.8, 1.1);
            }
            case "forage" -> forage(t);
            case "harvest" -> harvest(t);
            case "chop" -> chop(t);
            case "mine" -> mine(t);
            case "quarry" -> quarry(t);
            case "cave" -> cave();
            case "hunt" -> {
                if (!(targetEntity instanceof LivingEntity le) || !le.isAlive() || taskTicks > 1200) {
                    s.setTarget(null);
                    finish();
                } else if (s.getTarget() != le) {
                    s.setTarget(le);
                }
            }
            case "build", "level", "upgrade" -> build(t);
            case "craft" -> craft(t);
            case "store" -> {
                if (target != null && (reach(target, 2.6, 3.5) || walkTo(target, 2.0, 1.0))) store(t);
            }
            case "fetch" -> {
                if (target != null && (reach(target, 2.6, 3.5) || walkTo(target, 2.0, 1.0))) fetch(t);
            }
            case "raid" -> raid(t);
            case "pen" -> {
                if (target == null || taskTicks > 3000) finish();
                else if (walkTo(target, 1.5, 1.0)) {
                    // its dinosaurs that came along stay in the pen
                    for (Entity e : s.level().getEntities(s, s.getBoundingBox().inflate(10), e -> e instanceof ArkCreature c && c.isTamed()
                            && s.getUUID().equals(c.getOwnerId()))) ((ArkCreature) e).setOrder(ArkCreature.STAY);
                    finish();
                }
            }
            case "tame" -> tame(t);
            default -> {
                if (target != null && walkTo(target, 4, 1.0)) finish();
            }
        }
        if (stuck > 10) {
            stuck = 0;
            if (doing.equals("chop") && target != null) forgetAt(Memory.Kind.TREE, target);
            if (doing.equals("cave") && target != null) forgetAt(Memory.Kind.CAVE, target);
            if (doing.equals("quarry") && target != null) forgetAt(Memory.Kind.STONE, target);
            if (job != null) builder().delay(job, s.level().getGameTime(), 600);
            if (doing.equals("raid")) s.tribe().lastRaid = s.level().getGameTime();
            finish();
            explore(t);
        }
    }

    private void forgetAt(Memory.Kind k, BlockPos p) {
        s.memory.all(k).stream().filter(f -> f.pos().equals(p)).findFirst().ifPresent(s.memory::forget);
    }

    private void sleep(SurvivorWorld.Tribe t) {
        if (target == null || !night(t.personality())) {
            finish();
            return;
        }
        BlockState bs = s.level().getBlockState(target);
        if (!(bs.getBlock() instanceof BedBlock)) {
            finish();
            return;
        }
        if (reach(target, 1.8, 1.5) || walkTo(target, 1.5, 1.0)) {
            s.holdNothing();
            s.getNavigation().stop();
            s.startSleeping(target);
        }
    }

    private void build(SurvivorWorld.Tribe t) {
        if (job == null || t.base == null) {
            finish();
            return;
        }
        BlockPos p = job.pos();
        boolean close = reach(p, 4.5, 6.0) || (stuck > 4 && reach(p, 7.0, 7.0));
        if (!close) {
            walkTo(p, 3.0, 1.0);
            return;
        }
        BlockPos away = builder().standAside(job);
        if (away != null) {
            walkTo(away, 0.6, 1.0);                // never build a piece into its own feet
            return;
        }
        s.getNavigation().stop();
        s.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        if (job.step().kind() == BaseDesign.Kind.GROUND) s.hold(st -> st.is(ItemTags.SHOVELS) || st.is(ItemTags.PICKAXES));
        else s.hold(st -> st.is(ItemTags.AXES));
        if (builder().work(t, job)) {
            s.world().setDirty();
            finish();
        }
    }

    private void craft(SurvivorWorld.Tribe t) {
        Economy.Way w = craftWay;
        if (w == null) {
            finish();
            return;
        }
        if (w.station() != null) {
            BlockPos st = builder().station(t, w.station());
            if (st == null) {
                finish();
                return;
            }
            target = st;
            if (!reach(st, 2.6, 3.5) && !walkTo(st, 2.0, 1.0)) return;
            s.getLookControl().setLookAt(st.getX() + 0.5, st.getY() + 0.5, st.getZ() + 0.5);
        }
        int need = (int) ((w.station() == null ? 20 : 40) / t.skills.speed(Skills.Skill.CRAFTING));
        if (++work % 10 == 0) s.swing(InteractionHand.MAIN_HAND);
        if (work < need) return;
        work = 0;
        if (!Recipes.pay(s.inv, w.recipe().ingredients())) {
            finish();
            return;
        }
        ItemStack out = w.recipe().resultStack();
        Item it = out.getItem();
        if (it == ModEconomy.METAL_INGOT.get()) t.ingots += out.getCount();
        if (it == ModEconomy.ELECTRONICS.get()) t.electronics += out.getCount();
        t.crafted++;
        s.give(out);
        t.skills.gain(Skills.Skill.CRAFTING, 2f, t.personality());
        if (w.station() != null) s.level().playSound(null, target, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35f, 1.3f);
        if (--craftBatches <= 0) finish();
    }

    private void chop(SurvivorWorld.Tribe t) {
        Level level = s.level();
        if (target == null || !level.getBlockState(target).is(BlockTags.LOGS)) {
            if (target != null) forgetAt(Memory.Kind.TREE, target);
            finish();
            return;
        }
        s.hold(st -> st.is(ItemTags.AXES));
        if (!reach(target, 3.0, 4.0) && !walkTo(target, 2.6, 1.0)) return;
        s.getNavigation().stop();
        boolean axe = s.getMainHandItem().is(ItemTags.AXES);
        float speed = (axe ? (s.getMainHandItem().getItem() instanceof net.minecraft.world.item.TieredItem ti ? ti.getTier().getSpeed() : 2f) : 1f)
                * t.skills.speed(Skills.Skill.GATHERING);
        int need = (int) (40 / speed) + 6;
        if (++work % 6 == 0) s.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(s.getId(), target, (int) (10f * work / need));
        if (work < need) return;
        work = 0;
        level.destroyBlockProgress(s.getId(), target, -1);
        fellTree(t, target, axe);
        finish();
    }

    /** the whole trunk comes down: wood (more with a hatchet) and thatch (more by hand), the crown gives thatch; a sapling is planted */
    private void fellTree(SurvivorWorld.Tribe t, BlockPos start, boolean axe) {
        Level level = s.level();
        BlockState base = level.getBlockState(start);
        int logs = 0, leaves = 0;
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(start);
        var r = s.getRandom();
        while (!q.isEmpty() && logs < 24) {
            BlockPos p = q.poll();
            if (!seen.add(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!st.is(BlockTags.LOGS) || s.ownsPosition(p)) continue;
            level.destroyBlock(p, false, s);
            s.give(new ItemStack(ModEconomy.WOOD.get(), (axe ? 2 : 1) + r.nextInt(2)));
            s.give(new ItemStack(ModEconomy.THATCH.get(), (axe ? 1 : 2) + r.nextInt(2)));
            logs++;
            for (Direction d : Direction.values()) q.add(p.relative(d));
            q.add(p.above().north());
            q.add(p.above().south());
            q.add(p.above().east());
            q.add(p.above().west());
        }
        for (BlockPos p : BlockPos.betweenClosed(start.offset(-3, 2, -3), start.offset(3, 10, 3))) {
            if (leaves >= 30) break;
            BlockState st = level.getBlockState(p);
            if (st.is(BlockTags.LEAVES)) {
                level.destroyBlock(p, false, s);
                if (r.nextFloat() < 0.4f) s.give(new ItemStack(ModEconomy.THATCH.get()));
                if (r.nextFloat() < 0.15f) s.give(new ItemStack(ModEconomy.FIBER.get()));
                leaves++;
            }
        }
        t.skills.gain(Skills.Skill.GATHERING, logs * 0.5f, t.personality());
        BlockState below = level.getBlockState(start.below());
        if (below.is(BlockTags.DIRT) && level.isEmptyBlock(start)) {
            Block sapling = base.is(Blocks.BIRCH_LOG) ? Blocks.BIRCH_SAPLING : base.is(Blocks.SPRUCE_LOG) ? Blocks.SPRUCE_SAPLING
                    : base.is(Blocks.JUNGLE_LOG) ? Blocks.JUNGLE_SAPLING : base.is(Blocks.ACACIA_LOG) ? Blocks.ACACIA_SAPLING : Blocks.OAK_SAPLING;
            level.setBlock(start, sapling.defaultBlockState(), 3);
        }
    }

    private void forage(SurvivorWorld.Tribe t) {
        Level level = s.level();
        if (target == null || level.isEmptyBlock(target)) {
            BlockPos next = gathered < 8 ? nearestPlant(level, s.blockPosition()) : null;
            if (next == null) {
                finish();
                return;
            }
            target = next;
        }
        boolean sickle = s.count(ModEconomy.METAL_SICKLE.get()) > 0;
        if (sickle) s.hold(st -> st.is(ModEconomy.METAL_SICKLE.get()));
        else s.holdNothing();
        if (!reach(target, 2.6, 3.0) && !walkTo(target, 2.2, 1.0)) return;
        if (++work < 12) return;
        work = 0;
        s.swing(InteractionHand.MAIN_HAND);
        BlockState st = level.getBlockState(target);
        level.destroyBlock(target, false, s);
        var r = s.getRandom();
        boolean bush = st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.LARGE_FERN);
        int f = (sickle ? 3 : 1) + r.nextInt(2);
        s.give(new ItemStack(ModEconomy.FIBER.get(), f));
        float k = bush ? 1.0f : 0.5f;
        if (r.nextFloat() < 0.7f * k) s.give(new ItemStack(ModItems.MEJOBERRY.get(), 1 + r.nextInt(bush ? 4 : 2)));
        if (r.nextFloat() < 0.35f * k) s.give(new ItemStack(ModItems.NARCOBERRY.get(), 1 + r.nextInt(2)));
        if (st.is(BlockTags.LEAVES) && r.nextFloat() < 0.5f) s.give(new ItemStack(ModEconomy.THATCH.get()));
        t.skills.gain(Skills.Skill.GATHERING, 0.4f, t.personality());
        target = null;
        gathered++;
        if (s.food < 11) s.eatSomething();
    }

    private void harvest(SurvivorWorld.Tribe t) {
        if (target == null) {
            finish();
            return;
        }
        if (!reach(target, 2.6, 3.0) && !walkTo(target, 2.0, 1.0)) return;
        for (ItemStack st : com.arkragnarok.economy.CropPlotBlock.harvest(s.level(), target, s.getRandom())) s.give(st);
        s.swing(InteractionHand.MAIN_HAND);
        BlockPos next = ripeCrop(t);
        if (next != null && gathered++ < 12) target = next;
        else finish();
    }

    private void mine(SurvivorWorld.Tribe t) {
        if (!(targetEntity instanceof ResourceNode n) || n.depleted() || !n.isAlive()) {
            finish();
            return;
        }
        s.hold(st -> st.is(ItemTags.PICKAXES));
        double reach = n.getBbWidth() * 0.5 + 1.8;
        if (!walkTo(n.blockPosition(), reach, 1.0)) return;
        s.getLookControl().setLookAt(n);
        if (++work < (int) (14 / t.skills.speed(Skills.Skill.MINING))) return;
        work = 0;
        s.swing(InteractionHand.MAIN_HAND);
        float dmg = 2f + (s.getMainHandItem().getItem() instanceof net.minecraft.world.item.TieredItem ti ? ti.getTier().getAttackDamageBonus() + 2f : 0f);
        n.harvest(s, s.getMainHandItem(), dmg, s::give);
        t.skills.gain(Skills.Skill.MINING, 0.6f, t.personality());
        if (n.depleted()) {
            s.memory.all(Memory.Kind.NODE).stream().filter(f -> n.getUUID().equals(f.entity())).findFirst().ifPresent(s.memory::forget);
            finish();
        }
    }

    /** breaks exposed rock it has seen: stone and some flint */
    private void quarry(SurvivorWorld.Tribe t) {
        Level level = s.level();
        if (target == null || !level.getBlockState(target).is(BlockTags.BASE_STONE_OVERWORLD) || s.ownsPosition(target)) {
            if (target != null) forgetAt(Memory.Kind.STONE, target);
            if (gathered < 6 && target != null) {
                BlockPos near = null;
                for (BlockPos p : BlockPos.betweenClosed(target.offset(-2, -1, -2), target.offset(2, 1, 2))) {
                    if (level.getBlockState(p).is(BlockTags.BASE_STONE_OVERWORLD) && level.isEmptyBlock(p.above()) && !s.ownsPosition(p)) {
                        near = p.immutable();
                        break;
                    }
                }
                if (near != null) {
                    target = near;
                    return;
                }
            }
            finish();
            return;
        }
        s.hold(st -> st.is(ItemTags.PICKAXES));
        if (!reach(target, 3.0, 3.5) && !walkTo(target, 2.4, 1.0)) return;
        float speed = (s.getMainHandItem().getItem() instanceof net.minecraft.world.item.TieredItem ti ? ti.getTier().getSpeed() : 1f)
                * t.skills.speed(Skills.Skill.MINING);
        int need = (int) (30 / speed) + 8;
        if (++work % 6 == 0) s.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(s.getId(), target, (int) (10f * work / need));
        if (work < need) return;
        work = 0;
        level.destroyBlockProgress(s.getId(), target, -1);
        level.destroyBlock(target, false, s);
        var r = s.getRandom();
        s.give(new ItemStack(ModEconomy.STONE.get(), 1 + (r.nextFloat() < 0.4f ? 1 : 0)));
        if (r.nextFloat() < 0.3f) s.give(new ItemStack(ModEconomy.FLINT.get()));
        t.skills.gain(Skills.Skill.MINING, 0.4f, t.personality());
        gathered++;
        target = null;
    }

    private void cave() {
        if (target == null) {
            finish();
            return;
        }
        if (!walkTo(target, 2.5, 1.0)) return;
        // inside the cave mouth: it looks around, and the nodes it now sees become mining targets
        if (++work > 80) {
            forgetAt(Memory.Kind.CAVE, target);
            Memory.Fact next = s.memory.nearest(Memory.Kind.CAVE, s.position(), 24);
            if (next != null && gathered++ < 4) {
                target = next.pos();
                work = 0;
            } else finish();
        }
    }
}
