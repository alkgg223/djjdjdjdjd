package com.arkragnarok.survivor;

import com.arkragnarok.building.DoorState;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.building.PieceType;
import com.arkragnarok.economy.ModEconomy;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.survivor.world.SurvivorWorld;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An autonomous survivor - an independent rival with its own tribe (see {@link SurvivorWorld}): a personality that
 * shapes its decisions, skills that grow with use, a memory of everything it has seen with its own eyes (no x-ray,
 * 150 blocks) and of who attacked or robbed it, a base built from one of the {@link BaseDesign blueprints} in the
 * mod's own materials, its own stations, storage boxes and bed.  It uses only the mod's items.  The planning lives in
 * {@link SurvivorBrain}; this class is the body: inventory, food, doors, sleep, pick-up, fighting back, saving.
 */
public class Survivor extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TASK = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.STRING);
    static final String[] NAMES = {"Rawan", "Omar", "Lina", "Karim", "Sara", "Yusuf", "Mira", "Tarek", "Hana", "Zaid", "Nour", "Adam", "Layla", "Fares",
            "Dina", "Sami", "Rana", "Hadi"};
    static final String[] TRIBES = {"Stone Wolves", "Red Hand", "Ash Walkers", "Iron Fang", "Tide Keepers", "Sun Spears", "Night Owls", "Bone Crown",
            "Green Veil", "Storm Riders", "Deep Roots", "Raptor Claw"};

    public final SimpleContainer inv = new SimpleContainer(36);
    public final Memory memory = new Memory();
    public final Perception perception = new Perception(this);
    final Builder builder = new Builder(this);
    SurvivorBrain brain;                   // created in registerGoals (called from the Mob constructor)
    float food = 20f;
    long lastTick = -1;
    @Nullable
    UUID lastAttacker;
    long lastAttacked = -100000;
    private final List<BlockPos> openedDoors = new ArrayList<>();

    public Survivor(EntityType<? extends Survivor> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SKIN, 0);
        b.define(TASK, "idle");
    }

    // ============================================================================================ the tribe
    /** this survivor's tribe (server side) */
    public SurvivorWorld.Tribe tribe() {
        return SurvivorWorld.get((ServerLevel) level()).tribe(getUUID());
    }

    SurvivorWorld world() {
        return SurvivorWorld.get((ServerLevel) level());
    }

    @Nullable
    public BlockPos base() {
        return level().isClientSide ? null : tribe().base;
    }

    /** how many steps of its blueprint stand */
    public int buildStep() {
        return level().isClientSide ? 0 : Builder.builtCount(tribe());
    }

    public int stage() {
        return level().isClientSide ? 1 : tribe().stage;
    }

    public boolean ownsPosition(BlockPos p) {
        return !level().isClientSide && tribe().owns(p);
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

    /** a new survivor: its look, its name and its own rival tribe */
    public void init(int index) {
        this.entityData.set(SKIN, Math.floorMod(index, 9));
        String name = NAMES[Math.floorMod(index, NAMES.length)];
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
        if (level() instanceof ServerLevel) {
            SurvivorWorld.Tribe t = tribe();
            t.name = name + " of the " + TRIBES[Math.floorMod(index * 7 + getUUID().hashCode(), TRIBES.length)];
            t.personality = Personality.random(getUUID().getLeastSignificantBits() ^ index);
            t.lastPos = blockPosition();
            world().setDirty();
        }
        inv.addItem(new ItemStack(ModItems.MEJOBERRY.get(), 8));
    }

    @Override
    protected void registerGoals() {
        brain = new SurvivorBrain(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return getTarget() != null && brain.canFight(getTarget()) && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getTarget() != null && brain.canFight(getTarget()) && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, brain);
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ============================================================================================ inventory
    public int count(Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    boolean take(Item item, int n) {
        if (count(item) < n) return false;
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item)) continue;
            int k = Math.min(n, st.getCount());
            st.shrink(k);
            n -= k;
        }
        inv.setChanged();
        return true;
    }

    void give(ItemStack st) {
        if (st.isEmpty()) return;
        ItemStack rest = inv.addItem(st);
        if (!rest.isEmpty()) spawnAtLocation(rest);
    }

    int freeSlots() {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) n++;
        return n;
    }

    /** holds the best item of a kind (the same stack as in the pack, so the tool wears while it works) */
    void hold(java.util.function.Predicate<ItemStack> kind) {
        ItemStack best = ItemStack.EMPTY;
        float bestScore = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !kind.test(st)) continue;
            float sc = st.getItem() instanceof net.minecraft.world.item.TieredItem t ? t.getTier().getSpeed() + t.getTier().getAttackDamageBonus() : 0.5f;
            if (sc > bestScore) {
                bestScore = sc;
                best = st;
            }
        }
        if (getMainHandItem() != best) setItemSlot(EquipmentSlot.MAINHAND, best);
    }

    void holdNothing() {
        if (!getMainHandItem().isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    // ============================================================================================ life
    @Override
    public void aiStep() {
        super.aiStep();
        updateSwingTime();
        if (level().isClientSide || !isAlive()) return;
        long now = level().getGameTime();
        SurvivorWorld.Tribe t = tribe();
        if (lastTick > 0 && now - lastTick > 1200) brain.catchUp(t, now - lastTick);      // it was far from everybody
        lastTick = now;
        perception.tick();
        food = Math.max(0, food - (isSleeping() ? 0.0012f : 0.0025f));
        if (food <= 0 && tickCount % 80 == 0) hurt(damageSources().starve(), 1.0f);
        if (food > 12 && getHealth() < getMaxHealth() && tickCount % (isSleeping() ? 20 : 60) == 0) heal(1.0f);
        if (isSleeping()) {
            long d = level().getDayTime() % 24000;
            if ((d >= 23300 || d < 12000) || food < 3) stopSleeping();
            return;
        }
        if (tickCount % 20 == 0 && isInWall()) unstick();
        if (tickCount % 4 == 0) doors(t);
        if (tickCount % 10 == 0) pickUp();
        if (tickCount % 200 == 0) {
            t.lastPos = blockPosition();
            t.lastSim = now;
            world().setDirty();
        }
    }

    /** opens its own (and its ally's) doors when it walks through them and shuts them behind itself */
    private void doors(SurvivorWorld.Tribe t) {
        Level level = level();
        BlockPos me = blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(me.offset(-1, 0, -1), me.offset(1, 1, 1))) {
            BlockState st = level.getBlockState(q);
            if (!(st.getBlock() instanceof PieceBlock pb) || st.getValue(PieceBlock.PIECE) != PieceType.DOORFRAME || !PieceBlock.isOpening(st)) continue;
            if (st.getValue(PieceBlock.DOOR) != DoorState.CLOSED) continue;
            BlockPos m = PieceBlock.master(q, st);
            if (!mayUse(t, m)) continue;
            pb.toggleDoor(level, m);
            openedDoors.add(m.immutable());
        }
        for (Iterator<BlockPos> it = openedDoors.iterator(); it.hasNext(); ) {
            BlockPos m = it.next();
            if (position().distanceToSqr(Vec3.atBottomCenterOf(m)) < 2.6 * 2.6) continue;
            BlockState st = level.getBlockState(m);
            if (st.getBlock() instanceof PieceBlock pb && st.getValue(PieceBlock.DOOR) == DoorState.OPEN) pb.toggleDoor(level, m);
            it.remove();
        }
    }

    /** got shut in by a block (its own building, a falling block): climbs out to the nearest free space above */
    private void unstick() {
        for (int dy = 1; dy <= 5; dy++) {
            if (level().noCollision(this, getBoundingBox().move(0, dy, 0))) {
                teleportTo(getX(), getY() + dy, getZ());
                return;
            }
        }
    }

    boolean mayUse(SurvivorWorld.Tribe t, BlockPos p) {
        if (t.owns(p)) return true;
        if (t.ally != null) {
            SurvivorWorld.Tribe a = world().find(t.ally);
            return a != null && a.owns(p);
        }
        return false;
    }

    /** picks up the mod's resources lying around it (loot of its kills, what it chopped and mined) */
    private void pickUp() {
        for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.6, 0.6, 1.6))) {
            ItemStack st = e.getItem();
            if (!e.isAlive() || e.hasPickUpDelay() || !Economy.isMod(st) || freeSlots() == 0) continue;
            int before = st.getCount();
            ItemStack rest = inv.addItem(st.copy());
            take(e, before - rest.getCount());
            if (rest.isEmpty()) e.discard();
            else e.setItem(rest);
        }
    }

    /** eats the best of the mod's food it carries (never raw meat, kibble or narcotics) */
    boolean eatSomething() {
        int bestSlot = -1;
        float best = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!edible(st)) continue;
            FoodProperties fp = st.get(DataComponents.FOOD);
            float v = fp.nutrition() + fp.saturation();
            if (v > best) {
                best = v;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) return false;
        ItemStack st = inv.getItem(bestSlot);
        food = Math.min(20, food + best);
        st.shrink(1);
        playSound(SoundEvents.GENERIC_EAT, 0.8f, 1.0f);
        swing(InteractionHand.MAIN_HAND);
        return true;
    }

    static boolean edible(ItemStack st) {
        if (st.get(DataComponents.FOOD) == null || !Economy.isMod(st)) return false;
        if (st.is(ModItems.NARCOBERRY.get()) || st.is(ModItems.NARCOTIC.get()) || st.is(ModEconomy.RAW_MEAT.get()) || st.is(ModItems.RAW_PRIME_MEAT.get())) return false;
        for (var k : ModItems.KIBBLE.values()) if (st.is(k.get())) return false;
        return true;
    }

    public int foodItems() {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (edible(inv.getItem(i))) n += inv.getItem(i).getCount();
        return n;
    }

    // ============================================================================================ being attacked
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean h = super.hurt(source, amount);
        if (h && !level().isClientSide) {
            Entity by = source.getEntity();
            long now = level().getGameTime();
            if (by instanceof ArkCreature c) {
                memory.see(Memory.Kind.THREAT, c.blockPosition(), now, c.getUUID(), c.species().id);
                if (c.isTamed() && c.getOwnerId() != null && !c.getOwnerId().equals(getUUID())) remember(c.getOwnerId(), "dino_attack", -10);
            }
            if (by instanceof Survivor || by instanceof Player) {
                lastAttacker = by.getUUID();
                lastAttacked = now;
                if (now - brain.lastGrudge > 40) remember(by.getUUID(), "attacked", -12);
                brain.lastGrudge = now;
            }
        }
        return h;
    }

    void remember(UUID who, String what, int delta) {
        if (who.equals(getUUID())) return;
        world().event(who, getUUID(), what, delta, level().getGameTime());
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            if (source.getEntity() instanceof Survivor k) k.tribe().kills++;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty()) spawnAtLocation(st.copy());
            }
            inv.clearContent();
            world().remove(getUUID());          // the base stays as ruins anybody may loot
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            SurvivorWorld.Tribe t = tribe();
            Personality p = t.personality();
            String design = t.base == null ? "no base yet" : BaseDesign.get(t.design).describe() + " #" + t.design + ", " + Builder.builtCount(t) + "/"
                    + Builder.plan(t).size() + " built";
            StringBuilder stock = new StringBuilder();
            int k = 0;
            for (Map.Entry<String, Integer> e : t.stock.entrySet()) {
                if (k++ >= 6) break;
                stock.append(e.getKey().replace("arkragnarok:", "")).append(' ').append(e.getValue()).append("  ");
            }
            int rel = t.relation(player.getUUID());
            player.displayClientMessage(Component.literal(getName().getString() + " - " + t.name + " (" + p.temperament().replace('_', ' ') + ")")
                    .withStyle(ChatFormatting.GOLD), false);
            player.displayClientMessage(Component.literal("stage " + t.stage + "/6 " + SurvivorBrain.STAGES[Math.min(5, t.stage - 1)] + " | ")
                    .append(Component.translatable("task.arkragnarok." + task())).append(" | food " + Math.round(food) + " | tames " + t.tames.size())
                    .withStyle(ChatFormatting.AQUA), false);
            player.displayClientMessage(Component.literal("base: " + design).withStyle(ChatFormatting.AQUA), false);
            player.displayClientMessage(Component.literal("stores: " + (stock.length() == 0 ? "-" : stock.toString().trim()) + " | skills: "
                    + t.skills.summary()).withStyle(ChatFormatting.GRAY), false);
            player.displayClientMessage(Component.literal("knows " + memory.count(Memory.Kind.TREE) + " trees, " + memory.count(Memory.Kind.NODE)
                    + " rocks, " + memory.count(Memory.Kind.CAVE) + " caves, " + memory.count(Memory.Kind.BASE) + " bases | thinks of you: "
                    + (rel > 20 ? "friend" : rel < -30 ? "enemy" : "rival") + " (" + rel + ")").withStyle(ChatFormatting.GRAY), false);
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
        tag.putLong("LastTick", lastTick);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SKIN, tag.getInt("Skin"));
        food = tag.contains("Food") ? tag.getFloat("Food") : 20f;
        if (tag.contains("Inv")) inv.fromTag(tag.getList("Inv", 10), level().registryAccess());
        if (tag.contains("Memory")) memory.load(tag.getCompound("Memory"), level().getGameTime());
        lastTick = tag.contains("LastTick") ? tag.getLong("LastTick") : -1;
    }
}
