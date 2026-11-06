package com.arkragnarok.survivor;

import com.arkragnarok.building.DoorState;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.building.PieceType;
import com.arkragnarok.building.Placement;
import com.arkragnarok.building.Tier;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.entity.ResourceNode;
import com.arkragnarok.registry.ModBuilding;
import com.arkragnarok.registry.ModItems;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * An autonomous survivor.  It knows only what it has seen (see {@link Perception}: line of sight, 150 blocks, no
 * x-ray) and plans like a person would: stay alive (eat, flee big predators, shelter at night), get wood and thatch,
 * pick a safe flat spot near water for a base, build it piece by piece (foundation, walls, doorframe and door,
 * ceiling), make better tools, go into caves it has spotted and mine the rock, metal and crystal inside, then
 * rebuild the base in a better material.
 */
public class Survivor extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TASK = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.STRING);
    static final String[] NAMES = {"Rawan", "Omar", "Lina", "Karim", "Sara", "Yusuf", "Mira", "Tarek", "Hana", "Zaid", "Nour", "Adam"};

    public final SimpleContainer inv = new SimpleContainer(36);
    public final Memory memory = new Memory();
    public final Perception perception = new Perception(this);
    float food = 20f;
    @Nullable
    BlockPos base;
    @Nullable
    Tier baseTier;
    int buildStep;

    public Survivor(EntityType<? extends Survivor> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SKIN, 0);
        b.define(TASK, "idle");
    }

    @Nullable
    public BlockPos base() {
        return base;
    }

    public int buildStep() {
        return buildStep;
    }

    /** inside its own base (5x5 house around the anchor) */
    public boolean ownsPosition(BlockPos p) {
        return base != null && Math.abs(p.getX() - base.getX()) <= 3 && Math.abs(p.getZ() - base.getZ()) <= 3 && p.getY() >= base.getY() - 1
                && p.getY() <= base.getY() + 5;
    }

    public int skin() {
        return this.entityData.get(SKIN);
    }

    public String task() {
        return this.entityData.get(TASK);
    }

    void setTask(String t) {
        if (!t.equals(task())) this.entityData.set(TASK, t);
    }

    public void init(int index) {
        this.entityData.set(SKIN, Math.floorMod(index, 9));
        setCustomName(Component.literal(NAMES[Math.floorMod(index, NAMES.length)]));
        setCustomNameVisible(true);
        inv.addItem(new ItemStack(Items.BREAD, 4));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                LivingEntity t = getTarget();
                return t != null && t.getBbHeight() < 1.6f && super.canUse();     // only fights what it can beat
            }
        });
        goalSelector.addGoal(2, new BrainGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ============================================================================================ inventory helpers
    int count(Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (p.test(inv.getItem(i))) n += inv.getItem(i).getCount();
        return n;
    }

    boolean take(Predicate<ItemStack> p, int n) {
        if (count(p) < n) return false;
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!p.test(st)) continue;
            int k = Math.min(n, st.getCount());
            st.shrink(k);
            n -= k;
        }
        return true;
    }

    void give(ItemStack st) {
        ItemStack rest = inv.addItem(st);
        if (!rest.isEmpty()) spawnAtLocation(rest);
    }

    static boolean isLog(ItemStack s) {
        return s.is(ItemTags.LOGS);
    }

    static boolean isPlank(ItemStack s) {
        return s.is(ItemTags.PLANKS);
    }

    static boolean isLeaves(ItemStack s) {
        return s.is(ItemTags.LEAVES);
    }

    static boolean isStone(ItemStack s) {
        return s.is(Items.COBBLESTONE);
    }

    // ============================================================================================ crafting (in the head)
    /** logs -> planks -> sticks, then the best tools it can make; smelts metal at its base */
    void craft() {
        // stock up for the house it is about to build: lots of planks for wood, sticks for thatch
        int logs = count(Survivor::isLog);
        Tier planned = baseTier != null ? baseTier : (logs * 4 + count(Survivor::isPlank) >= 72 ? Tier.WOOD : Tier.THATCH);
        int plankGoal = planned == Tier.WOOD || planned == Tier.STONE ? 76 : 16;
        int stickGoal = baseTier == null || baseTier == Tier.THATCH ? 48 : 12;
        for (int k = 0; k < 4; k++) {
            if (count(Survivor::isPlank) < plankGoal && count(Survivor::isLog) > 4 && take(Survivor::isLog, 1)) give(new ItemStack(Items.OAK_PLANKS, 4));
            if (count(s -> s.is(Items.STICK)) < stickGoal && count(Survivor::isPlank) >= 2 && take(Survivor::isPlank, 2)) give(new ItemStack(Items.STICK, 4));
        }
        if (count(s -> s.is(Items.TORCH)) < 8 && count(s -> s.is(Items.STICK)) > 4 && count(Survivor::isLog) > 2 && take(s -> s.is(Items.STICK), 1)
                && take(Survivor::isLog, 1)) give(new ItemStack(Items.TORCH, 4));
        if (base != null && count(s -> s.is(Items.RAW_IRON)) >= 1 && count(Survivor::isLog) >= 1 && distanceToSqr(Vec3.atCenterOf(base)) < 400) {
            int n = Math.min(8, count(s -> s.is(Items.RAW_IRON)));
            if (take(s -> s.is(Items.RAW_IRON), n) && take(Survivor::isLog, 1)) give(new ItemStack(Items.IRON_INGOT, n));
        }
        tool(Items.IRON_PICKAXE, Items.IRON_INGOT, 3);
        tool(Items.IRON_AXE, Items.IRON_INGOT, 3);
        tool(Items.STONE_PICKAXE, Items.COBBLESTONE, 3);
        tool(Items.STONE_AXE, Items.COBBLESTONE, 3);
        if (count(s -> s.is(ItemTags.PICKAXES)) == 0 && count(Survivor::isPlank) >= 3 && count(s -> s.is(Items.STICK)) >= 2) {
            take(Survivor::isPlank, 3);
            take(s -> s.is(Items.STICK), 2);
            give(new ItemStack(Items.WOODEN_PICKAXE));
        }
        if (count(s -> s.is(ItemTags.AXES)) == 0 && count(Survivor::isPlank) >= 3 && count(s -> s.is(Items.STICK)) >= 2) {
            take(Survivor::isPlank, 3);
            take(s -> s.is(Items.STICK), 2);
            give(new ItemStack(Items.WOODEN_AXE));
        }
    }

    private void tool(net.minecraft.world.item.Item tool, net.minecraft.world.item.Item mat, int n) {
        if (count(s -> s.is(tool)) > 0) return;
        boolean better = tool == Items.IRON_PICKAXE || tool == Items.IRON_AXE;
        if (!better && count(s -> s.is(tool == Items.STONE_PICKAXE ? Items.IRON_PICKAXE : Items.IRON_AXE)) > 0) return;
        if (count(s -> s.is(mat)) >= n + (better ? 0 : 8) && count(s -> s.is(Items.STICK)) >= 2) {
            take(s -> s.is(mat), n);
            take(s -> s.is(Items.STICK), 2);
            give(new ItemStack(tool));
        }
    }

    void hold(Predicate<ItemStack> kind) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!kind.test(st)) continue;
            float sp = st.getItem() instanceof net.minecraft.world.item.TieredItem t ? t.getTier().getSpeed() : 0;
            if (sp > bestSpeed) {
                bestSpeed = sp;
                best = st;
            }
        }
        // the very same stack as in the pack, so the tool wears out while it works
        if (getMainHandItem() != best) setItemSlot(EquipmentSlot.MAINHAND, best);
    }

    // ============================================================================================ building costs
    /** materials one piece costs in a tier: {logs, planks, sticks, leaves, cobblestone, iron} */
    static int[] cost(Tier t, PieceType p, boolean door) {
        return switch (t) {
            case THATCH -> door ? new int[]{0, 0, 4, 4, 0, 0} : p == PieceType.FOUNDATION ? new int[]{2, 0, 6, 0, 0, 0} : new int[]{0, 0, 6, 6, 0, 0};
            case WOOD -> door ? new int[]{0, 6, 0, 0, 0, 0} : p == PieceType.FOUNDATION ? new int[]{4, 8, 0, 0, 0, 0} : new int[]{0, 10, 0, 0, 0, 0};
            case STONE -> door ? new int[]{0, 6, 0, 0, 0, 2} : p == PieceType.FOUNDATION ? new int[]{0, 4, 0, 0, 24, 0}
                    : p == PieceType.CEILING ? new int[]{0, 4, 0, 0, 16, 0} : new int[]{0, 0, 0, 0, 18, 0};
            default -> door ? new int[]{0, 6, 0, 0, 0, 4} : new int[]{0, 0, 0, 0, 12, 6};
        };
    }

    private static final List<Predicate<ItemStack>> MATS = List.of(Survivor::isLog, Survivor::isPlank, s -> s.is(Items.STICK),
            Survivor::isLeaves, Survivor::isStone, s -> s.is(Items.IRON_INGOT));

    boolean canPay(int[] c, int times) {
        for (int i = 0; i < c.length; i++) if (count(MATS.get(i)) < c[i] * times) return false;
        return true;
    }

    void pay(int[] c) {
        for (int i = 0; i < c.length; i++) take(MATS.get(i), c[i]);
    }

    static final PieceType[] ORDER = {PieceType.FOUNDATION, PieceType.WALL, PieceType.WALL, PieceType.WALL, PieceType.DOORFRAME, null, PieceType.CEILING};

    /** cost of the next piece of the house being built */
    int[] stepCost() {
        if (baseTier == null || buildStep >= ORDER.length) return new int[6];
        PieceType p = ORDER[buildStep];
        return cost(baseTier, p == null ? PieceType.DOORFRAME : p, p == null);
    }

    /** total cost of a house: foundation, 3 walls, doorframe, door, ceiling */
    boolean canAffordHouse(Tier t) {
        int[] sum = new int[6];
        for (PieceType p : new PieceType[]{PieceType.FOUNDATION, PieceType.WALL, PieceType.WALL, PieceType.WALL, PieceType.DOORFRAME, PieceType.CEILING}) {
            int[] c = cost(t, p, false);
            for (int i = 0; i < 6; i++) sum[i] += c[i];
        }
        int[] d = cost(t, PieceType.DOORFRAME, true);
        for (int i = 0; i < 6; i++) sum[i] += d[i];
        return canPay(sum, 1);
    }

    // ============================================================================================ life
    @Override
    public void aiStep() {
        super.aiStep();
        updateSwingTime();
        if (level().isClientSide || !isAlive()) return;
        perception.tick();
        food = Math.max(0, food - 0.0025f);
        if (food <= 0 && tickCount % 80 == 0) hurt(damageSources().starve(), 1.0f);
        if (food > 14 && getHealth() < getMaxHealth() && tickCount % 60 == 0) heal(1.0f);
        if (tickCount % 40 == 0) craft();
    }

    boolean eatSomething() {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            FoodProperties fp = st.get(DataComponents.FOOD);
            if (fp == null || st.is(ModItems.NARCOBERRY.get()) || st.is(ModItems.NARCOTIC.get()) || st.is(Items.ROTTEN_FLESH)) continue;
            if (st.is(Items.BEEF) || st.is(ModItems.RAW_PRIME_MEAT.get())) continue;     // cooks its meat first
            food = Math.min(20, food + fp.nutrition() + fp.saturation());
            st.shrink(1);
            playSound(SoundEvents.GENERIC_EAT, 0.8f, 1.0f);
            swing(InteractionHand.MAIN_HAND);
            return true;
        }
        // cook raw meat over a fire at the base
        if (base != null && distanceToSqr(Vec3.atCenterOf(base)) < 400 && count(Survivor::isLog) > 0) {
            if (take(s -> s.is(Items.BEEF), 1)) {
                take(Survivor::isLog, 1);
                give(new ItemStack(Items.COOKED_BEEF));
                return eatSomething();
            }
        }
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean h = super.hurt(source, amount);
        if (h && source.getEntity() instanceof ArkCreature c && !level().isClientSide) {
            memory.see(Memory.Kind.THREAT, c.blockPosition(), level().getGameTime(), c.getUUID(), c.species().id);
        }
        return h;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            String inv = count(Survivor::isLog) + " logs, " + count(Survivor::isPlank) + " planks, " + count(Survivor::isStone) + " stone, "
                    + count(s -> s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT)) + " metal";
            player.displayClientMessage(Component.translatable("msg.arkragnarok.survivor", getName(), Component.translatable("task.arkragnarok." + task()),
                    Math.round(food), memory.count(Memory.Kind.TREE), memory.count(Memory.Kind.NODE), memory.count(Memory.Kind.CAVE),
                    memory.count(Memory.Kind.THREAT), base == null ? "-" : (baseTier == null ? "?" : baseTier.id), inv)
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", skin());
        tag.putFloat("Food", food);
        tag.put("Inv", inv.createTag(level().registryAccess()));
        tag.put("Memory", memory.save());
        if (base != null) tag.putLong("Base", base.asLong());
        if (baseTier != null) tag.putInt("BaseTier", baseTier.ordinal());
        tag.putInt("BuildStep", buildStep);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SKIN, tag.getInt("Skin"));
        food = tag.contains("Food") ? tag.getFloat("Food") : 20f;
        if (tag.contains("Inv")) inv.fromTag(tag.getList("Inv", 10), level().registryAccess());
        if (tag.contains("Memory")) memory.load(tag.getCompound("Memory"), level().getGameTime());
        base = tag.contains("Base") ? BlockPos.of(tag.getLong("Base")) : null;
        baseTier = tag.contains("BaseTier") ? Tier.values()[tag.getInt("BaseTier")] : null;
        buildStep = tag.getInt("BuildStep");
    }

    // ============================================================================================ the planner
    /**
     * Picks the most important thing to do every two seconds and carries it out: survive first, then shelter,
     * materials, base, tools, caves and metal, upgrades, and exploring to see more of the world.
     */
    class BrainGoal extends Goal {
        private String doing = "idle";
        @Nullable
        private BlockPos target;
        @Nullable
        private Entity targetEntity;
        private int work, think, stuck;
        private Vec3 lastPos = Vec3.ZERO;

        BrainGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null || getTarget().getBbHeight() >= 1.6f;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (--think <= 0) {
                think = 40;
                decide();
            }
            act();
        }

        private void start(String what, @Nullable BlockPos t, @Nullable Entity e) {
            if (!what.equals(doing) || (t != null && !t.equals(target)) || e != targetEntity) work = 0;
            doing = what;
            target = t;
            targetEntity = e;
            setTask(what);
        }

        private boolean night() {
            long d = level().getDayTime() % 24000;
            return d > 12800 && d < 23200;
        }

        private void decide() {
            Level level = level();
            long now = level.getGameTime();
            Vec3 me = position();
            // 1. danger: a big predator seen close by in the last 30 s
            for (Memory.Fact f : memory.all(Memory.Kind.THREAT)) {
                if (now - f.seen() > 600) {
                    memory.forget(f);
                    continue;
                }
                if (f.pos().distToCenterSqr(me) < 26 * 26) {
                    Vec3 away = me.subtract(Vec3.atCenterOf(f.pos())).normalize().scale(28);
                    start("flee", BlockPos.containing(me.add(away)), null);
                    return;
                }
            }
            // 2. hunger
            if (food < 9) {
                if (eatSomething()) return;
                start("forage", null, null);
                return;
            }
            // 3. night: go home and shut the door
            if (night() && base != null && baseTier != null && buildStep >= 6) {
                start("shelter", base.above(), null);
                return;
            }
            // 4. build the base when it can afford it
            // the best house it can actually pay for right now
            Tier want = canAffordHouse(Tier.STONE) ? Tier.STONE : canAffordHouse(Tier.WOOD) ? Tier.WOOD
                    : canAffordHouse(Tier.THATCH) ? Tier.THATCH : null;
            if (base == null && want != null) {
                BlockPos site = chooseSite();
                if (site != null) {
                    base = site;
                    baseTier = want;
                    buildStep = 0;
                }
            }
            if (base != null && buildStep < 7 && baseTier != null && canPay(stepCost(), 1)) {
                start("build", base, null);
                return;
            }
            if (base != null && baseTier != null && buildStep >= 7 && want != null && baseTier.ordinal() < want.ordinal()) {
                demolish();
                baseTier = want;
                buildStep = 0;
                start("build", base, null);
                return;
            }
            // 5. materials: wood first, then stone and metal (nodes it has seen, caves it has spotted)
            boolean needWood = count(Survivor::isLog) < 24 || (count(Survivor::isLeaves) < 24 && (baseTier == null || baseTier == Tier.THATCH));
            boolean needStone = count(Survivor::isStone) < 140;
            boolean needMetal = count(s -> s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT)) < 12;
            if (needWood) {
                Memory.Fact tree = memory.nearest(Memory.Kind.TREE, me, 140);
                if (tree != null) {
                    start("chop", tree.pos(), null);
                    return;
                }
            }
            if (needStone || needMetal) {
                Memory.Fact node = null;
                double bd = 1e18;
                for (Memory.Fact f : memory.all(Memory.Kind.NODE)) {
                    boolean useful = f.detail().equals("metal") ? needMetal : f.detail().equals("rock") ? needStone : f.detail().equals("crystal");
                    if (!useful) continue;
                    double d = f.pos().distToCenterSqr(me) * (f.detail().equals("metal") ? 0.6 : 1.0);
                    if (d < bd) {
                        bd = d;
                        node = f;
                    }
                }
                if (node != null) {
                    Entity e = ((ServerLevel) level).getEntity(node.entity());
                    if (e instanceof ResourceNode rn && !rn.depleted()) {
                        start("mine", rn.blockPosition(), rn);
                        return;
                    }
                    memory.forget(node);
                }
                Memory.Fact cave = memory.nearest(Memory.Kind.CAVE, me, 150);
                if (cave != null && count(s -> s.is(Items.TORCH)) > 0) {
                    start("cave", cave.pos(), null);
                    return;
                }
            }
            if (needWood && memory.count(Memory.Kind.TREE) == 0) {
                start("explore", exploreTarget(), null);
                return;
            }
            start("explore", exploreTarget(), null);
        }

        private BlockPos exploreTarget() {
            if (target != null && doing.equals("explore") && target.distToCenterSqr(position()) > 36) return target;
            double a = getRandom().nextDouble() * Math.PI * 2;
            double r = 40 + getRandom().nextDouble() * 40;
            Vec3 p = position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            if (base != null && p.distanceToSqr(Vec3.atCenterOf(base)) > 160 * 160) p = Vec3.atCenterOf(base).add(Math.cos(a) * 30, 0, Math.sin(a) * 30);
            int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) p.x, (int) p.z);
            return BlockPos.containing(p.x, y, p.z);
        }

        /** flat, dry spot with room for a 5x5 house, near water if it knows any, away from where predators were */
        @Nullable
        private BlockPos chooseSite() {
            Level level = level();
            BlockPos best = null;
            double bestScore = -1e9;
            List<Memory.Fact> flats = new java.util.ArrayList<>(memory.all(Memory.Kind.FLAT));
            flats.add(new Memory.Fact(Memory.Kind.FLAT, blockPosition(), 0, null, ""));
            for (Memory.Fact f : flats) {
                BlockPos c = f.pos();
                if (c.distToCenterSqr(position()) > 90 * 90) continue;
                if (!roomFor(level, c)) continue;
                double score = -Math.sqrt(c.distToCenterSqr(position())) * 0.3;
                Memory.Fact w = memory.nearest(Memory.Kind.WATER, Vec3.atCenterOf(c), 40);
                if (w != null) score += 30 - Math.sqrt(w.pos().distToCenterSqr(Vec3.atCenterOf(c)));
                for (Memory.Fact t : memory.all(Memory.Kind.THREAT)) if (t.pos().distToCenterSqr(Vec3.atCenterOf(c)) < 40 * 40) score -= 40;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            return best;
        }

        private boolean roomFor(Level level, BlockPos c) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos g = c.offset(dx, -1, dz);
                    if (!level.getBlockState(g).isFaceSturdy(level, g, Direction.UP)) return false;
                    if (!level.getFluidState(g.above()).isEmpty()) return false;
                    for (int dy = 0; dy <= 4; dy++) if (!level.getBlockState(c.offset(dx, dy, dz)).canBeReplaced()) return false;
                }
            }
            return true;
        }

        private void demolish() {
            if (base == null) return;
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = 0; dy <= 3; dy++)
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos p = base.offset(dx, dy, dz);
                        if (level().getBlockState(p).getBlock() instanceof PieceBlock) level().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
        }

        // ------------------------------------------------------------------------------------------ doing it
        private boolean walkTo(BlockPos p, double reach, double speed) {
            Vec3 c = Vec3.atBottomCenterOf(p);
            if (position().distanceToSqr(c) <= reach * reach) {
                getNavigation().stop();
                getLookControl().setLookAt(c.x, c.y + 0.5, c.z);
                return true;
            }
            if (getNavigation().isDone() || tickCount % 20 == 0) {
                Vec3 goal = c;
                if (position().distanceTo(c) > 80) goal = position().add(c.subtract(position()).normalize().scale(60));
                if (!getNavigation().moveTo(goal.x, goal.y, goal.z, speed)) stuck++;
            }
            if (tickCount % 40 == 0) {
                if (position().distanceToSqr(lastPos) < 0.5) stuck++;
                else stuck = 0;
                lastPos = position();
            }
            return false;
        }

        private void act() {
            switch (doing) {
                case "flee" -> {
                    if (target != null) walkTo(target, 3, 1.45);
                }
                case "forage" -> forage();
                case "shelter" -> shelter();
                case "chop" -> chop();
                case "mine" -> mine();
                case "cave" -> cave();
                case "build" -> build();
                default -> {
                    if (target != null && walkTo(target, 4, 1.0)) target = null;
                }
            }
            if (stuck > 6) {
                stuck = 0;
                if (doing.equals("chop") && target != null) memory.all(Memory.Kind.TREE).stream()
                        .filter(f -> f.pos().equals(target)).findFirst().ifPresent(memory::forget);
                if (doing.equals("cave") && target != null) memory.all(Memory.Kind.CAVE).stream()
                        .filter(f -> f.pos().equals(target)).findFirst().ifPresent(memory::forget);
                start("explore", exploreTarget(), null);
            }
        }

        private void forage() {
            Level level = level();
            BlockPos me = blockPosition();
            if (target == null || !(level.getBlockState(target).is(Blocks.SHORT_GRASS) || level.getBlockState(target).is(Blocks.TALL_GRASS)
                    || level.getBlockState(target).is(Blocks.SWEET_BERRY_BUSH) || level.getBlockState(target).is(BlockTags.LEAVES))) {
                target = null;
                for (BlockPos p : BlockPos.betweenClosed(me.offset(-10, -3, -10), me.offset(10, 3, 10))) {
                    BlockState st = level.getBlockState(p);
                    if (st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.SHORT_GRASS)) {
                        target = p.immutable();
                        break;
                    }
                }
                if (target == null) {
                    // hunt something small instead
                    for (Entity e : level.getEntities(Survivor.this, getBoundingBox().inflate(20), e -> e instanceof net.minecraft.world.entity.animal.Animal
                            || (e instanceof ArkCreature c && !c.isTamed() && c.getBbHeight() < 1.0f))) {
                        if (perception.visible(e)) {
                            hold(s -> s.is(ItemTags.AXES));
                            setTarget((LivingEntity) e);
                            return;
                        }
                    }
                    start("explore", exploreTarget(), null);
                    return;
                }
            }
            if (walkTo(target, 2.5, 1.0) && ++work > 15) {
                work = 0;
                swing(InteractionHand.MAIN_HAND);
                level.destroyBlock(target, false, Survivor.this);
                if (getRandom().nextFloat() < 0.7f) give(new ItemStack(ModItems.MEJOBERRY.get(), 1 + getRandom().nextInt(3)));
                target = null;
                eatSomething();
            }
        }

        private void shelter() {
            if (base == null) return;
            BlockPos inside = base.above();
            BlockPos door = base.relative(Direction.NORTH, 2).above();
            boolean in = position().distanceToSqr(Vec3.atBottomCenterOf(inside)) < 2.0;
            BlockState ds = level().getBlockState(door);
            if (!in) {
                if (ds.getBlock() instanceof PieceBlock pb && ds.getValue(PieceBlock.DOOR) == DoorState.CLOSED
                        && position().distanceToSqr(Vec3.atCenterOf(door)) < 9) pb.toggleDoor(level(), door);
                walkTo(inside, 1.0, 1.1);
            } else {
                getNavigation().stop();
                if (ds.getBlock() instanceof PieceBlock pb && ds.getValue(PieceBlock.DOOR) == DoorState.OPEN) pb.toggleDoor(level(), door);
            }
        }

        private void chop() {
            Level level = level();
            if (target == null || !level.getBlockState(target).is(BlockTags.LOGS)) {
                if (target != null) memory.all(Memory.Kind.TREE).stream().filter(f -> f.pos().equals(target)).findFirst().ifPresent(memory::forget);
                think = 0;
                return;
            }
            hold(s -> s.is(ItemTags.AXES));
            if (!walkTo(target, 3.2, 1.0)) return;
            float speed = getMainHandItem().is(ItemTags.AXES) ? (getMainHandItem().getItem() instanceof net.minecraft.world.item.TieredItem t
                    ? t.getTier().getSpeed() : 2f) : 1f;
            int need = (int) (40 / speed) + 6;
            if (++work % 6 == 0) swing(InteractionHand.MAIN_HAND);
            level.destroyBlockProgress(getId(), target, (int) (10f * work / need));
            if (work < need) return;
            work = 0;
            level.destroyBlockProgress(getId(), target, -1);
            fellTree(target);
            target = null;
            think = 0;
        }

        /** the whole trunk comes down; leaves give thatch; it replants a sapling */
        private void fellTree(BlockPos start) {
            Level level = level();
            BlockState base = level.getBlockState(start);
            int logs = 0, leaves = 0;
            java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
            java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
            q.add(start);
            while (!q.isEmpty() && logs < 24) {
                BlockPos p = q.poll();
                if (!seen.add(p)) continue;
                BlockState st = level.getBlockState(p);
                if (st.is(BlockTags.LOGS)) {
                    level.destroyBlock(p, false, Survivor.this);
                    give(new ItemStack(st.getBlock().asItem()));
                    logs++;
                    for (Direction d : Direction.values()) q.add(p.relative(d));
                    q.add(p.above().north()); q.add(p.above().south()); q.add(p.above().east()); q.add(p.above().west());
                } else if (st.is(BlockTags.LEAVES) && leaves < 24 && p.distSqr(start) < 64) {
                    level.destroyBlock(p, false, Survivor.this);
                    give(new ItemStack(st.getBlock().asItem()));
                    leaves++;
                    if (getRandom().nextFloat() < 0.08f) give(new ItemStack(Items.APPLE));
                }
            }
            // collect the leaves around the crown too
            for (BlockPos p : BlockPos.betweenClosed(start.offset(-3, 2, -3), start.offset(3, 9, 3))) {
                if (leaves >= 24) break;
                BlockState st = level.getBlockState(p);
                if (st.is(BlockTags.LEAVES)) {
                    level.destroyBlock(p, false, Survivor.this);
                    give(new ItemStack(st.getBlock().asItem()));
                    leaves++;
                }
            }
            BlockState below = level.getBlockState(start.below());
            if (below.is(BlockTags.DIRT) && level.isEmptyBlock(start)) {
                Block sapling = base.is(Blocks.BIRCH_LOG) ? Blocks.BIRCH_SAPLING : base.is(Blocks.SPRUCE_LOG) ? Blocks.SPRUCE_SAPLING
                        : base.is(Blocks.JUNGLE_LOG) ? Blocks.JUNGLE_SAPLING : base.is(Blocks.ACACIA_LOG) ? Blocks.ACACIA_SAPLING : Blocks.OAK_SAPLING;
                level.setBlock(start, sapling.defaultBlockState(), 3);
            }
        }

        private void mine() {
            if (!(targetEntity instanceof ResourceNode n) || n.depleted() || !n.isAlive()) {
                think = 0;
                return;
            }
            hold(s -> s.is(ItemTags.PICKAXES));
            double reach = n.getBbWidth() * 0.5 + 1.8;
            if (!walkTo(n.blockPosition(), reach, 1.0)) return;
            getLookControl().setLookAt(n);
            if (++work >= 14) {
                work = 0;
                swing(InteractionHand.MAIN_HAND);
                float dmg = 2f + (getMainHandItem().getItem() instanceof net.minecraft.world.item.TieredItem t ? t.getTier().getAttackDamageBonus() + 2f : 0f);
                n.harvest(Survivor.this, getMainHandItem(), dmg, Survivor.this::give);
                if (n.depleted()) {
                    memory.all(Memory.Kind.NODE).stream().filter(f -> n.getUUID().equals(f.entity())).findFirst().ifPresent(memory::forget);
                    think = 0;
                }
            }
        }

        private void cave() {
            if (target == null) return;
            if (!walkTo(target, 2.5, 1.0)) return;
            // inside the cave mouth: light it up and look around - the nodes it now sees become mining targets
            BlockPos me = blockPosition();
            if (level().getMaxLocalRawBrightness(me) < 8 && level().isEmptyBlock(me) && level().getBlockState(me.below()).isFaceSturdy(level(), me.below(), Direction.UP)
                    && take(s -> s.is(Items.TORCH), 1)) {
                level().setBlock(me, Blocks.TORCH.defaultBlockState(), 3);
            }
            if (++work > 60) {
                memory.all(Memory.Kind.CAVE).stream().filter(f -> f.pos().equals(target)).findFirst().ifPresent(memory::forget);
                // go deeper along the cave: the next dark opening it has seen from here
                Memory.Fact next = memory.nearest(Memory.Kind.CAVE, position(), 24);
                if (next != null) start("cave", next.pos(), null);
                else think = 0;
            }
        }

        private void build() {
            if (base == null || baseTier == null) return;
            PieceType[] order = ORDER;
            Direction[] wallDir = {null, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.NORTH, null};
            if (buildStep >= order.length) {
                think = 0;
                return;
            }
            PieceType p = order[buildStep];
            BlockPos spot = switch (buildStep) {
                case 0 -> base;
                case 6 -> base.above(3);
                default -> base.relative(wallDir[buildStep], 2).above();
            };
            if (!walkTo(spot, 4.5, 1.0)) return;
            if (++work % 8 == 0) {
                swing(InteractionHand.MAIN_HAND);
                playSound(baseTier.sound.getHitSound(), 0.8f, 1.0f);
            }
            if (work < 50) return;
            work = 0;
            PieceBlock block = ModBuilding.PIECE_BLOCKS.get(baseTier).get();
            boolean door = p == null;
            int[] c = stepCost();
            if (!canPay(c, 1)) {
                think = 0;               // ran out of material: gather more, then carry on with this piece
                return;
            }
            boolean ok;
            if (door) ok = Placement.installDoor(level(), spot, baseTier);
            else {
                Direction f = p.flat() ? Direction.NORTH : wallDir[buildStep];
                ok = Placement.place(level(), block, p, new Placement.Plan(spot, f));
            }
            if (ok) {
                pay(c);
                playSound(baseTier.sound.getPlaceSound(), 1.2f, 0.8f);
                buildStep++;
            } else if (buildStep == 0) {
                base = null;             // the site was taken in the meantime: choose another
            } else {
                buildStep++;             // something is in the way of this piece: skip it
            }
        }
    }
}
