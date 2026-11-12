package com.rpbusiness.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Shared base for customers, employees and couriers: GeckoLib animated humanoid with
 * door-aware navigation, distance-throttled thinking and synced action animations.
 */
public abstract class NpcEntity extends PathfinderMob implements GeoEntity {
    public static final int ACT_NONE = 0, ACT_CHOP = 1, ACT_COOK = 2, ACT_CARRY = 3, ACT_CLEAN = 4, ACT_CASHIER = 5,
            ACT_EAT = 6, ACT_PAY = 7, ACT_REACH = 8, ACT_TALK = 9, ACT_READ = 10, ACT_SLEEP = 11;

    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OUTFIT = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CHILD = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation[] ACTIONS = {
            null, RawAnimation.begin().thenLoop("chop"), RawAnimation.begin().thenLoop("cook"), RawAnimation.begin().thenLoop("carry"),
            RawAnimation.begin().thenLoop("clean"), RawAnimation.begin().thenLoop("type"), RawAnimation.begin().thenLoop("eat"),
            RawAnimation.begin().thenLoop("pay"), RawAnimation.begin().thenLoop("reach"), RawAnimation.begin().thenLoop("talk"),
            RawAnimation.begin().thenLoop("read")};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected String npcName = "";
    private int thinkCooldown;
    private long lastThink = -1;
    /** Real game ticks elapsed since the previous think() call. */
    protected int thinkDelta;
    // navigation bookkeeping
    private BlockPos navTarget;
    private Vec3 lastProgressPos = Vec3.ZERO;
    private int noProgressTicks, repathTicks;
    protected boolean stuck;

    protected NpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation g) {
            g.setCanOpenDoors(true);
            g.setCanPassDoors(true);
        }
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SKIN, 0);
        b.define(OUTFIT, 0);
        b.define(ACTION, 0);
        b.define(CHILD, false);
    }

    public boolean isKid() {
        try { return entityData != null && entityData.get(CHILD); } catch (Exception e) { return false; }
    }
    public void setKid(boolean k) { entityData.set(CHILD, k); refreshDimensions(); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (CHILD.equals(key)) refreshDimensions();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        var d = super.getDefaultDimensions(pose);
        return isKid() ? d.scale(0.62f) : d;
    }

    public int skin() { return entityData.get(SKIN); }
    public void setSkin(int s) { entityData.set(SKIN, s); }
    /** 0 = civilian clothes, otherwise a job uniform index. */
    public int outfit() { return entityData.get(OUTFIT); }
    public void setOutfit(int o) { if (outfit() != o) entityData.set(OUTFIT, o); }
    public int action() { return entityData.get(ACTION); }
    public void setAction(int a) { if (action() != a) entityData.set(ACTION, a); }
    public String npcName() { return npcName; }

    public void setNpcName(String n) {
        npcName = n;
        refreshName("");
    }

    /** Updates the floating name tag with a short status. */
    protected void refreshName(String status) {
        Component c = status.isEmpty() ? Component.literal(npcName) : Component.literal(npcName + " §7" + status);
        if (getCustomName() == null || !getCustomName().getString().equals(c.getString())) setCustomName(c);
    }

    public void hold(ItemStack s) {
        if (!ItemStack.matches(getMainHandItem(), s)) setItemSlot(EquipmentSlot.MAINHAND, s.copy());
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickNavigationWatch();
        if (--thinkCooldown <= 0) {
            Player p = level().getNearestPlayer(this, 40);
            thinkCooldown = p != null ? 2 : 8; // distance-based update rate
            long now = level().getGameTime();
            thinkDelta = lastThink < 0 ? 1 : (int) Math.min(200, now - lastThink);
            lastThink = now;
            think();
        }
    }

    /** Main decision logic, called every 2-8 ticks depending on distance to the player. */
    protected abstract void think();

    // ------------------------------------------------------------------ navigation

    /**
     * Walks toward a position. Returns true once within {@code reach} blocks (horizontal) of its centre.
     * Re-paths periodically and flags {@link #stuck} when no progress is made for a long time.
     */
    public boolean walkTo(BlockPos target, double reach, double speed) {
        Vec3 c = Vec3.atBottomCenterOf(target);
        double dx = getX() - c.x, dz = getZ() - c.z, dy = Math.abs(getY() - c.y);
        if (dx * dx + dz * dz <= reach * reach && dy < 2.2) {
            if (navTarget != null) { getNavigation().stop(); navTarget = null; }
            return true;
        }
        if (isPassenger()) stopRiding();
        if (!target.equals(navTarget) || getNavigation().isDone() || --repathTicks <= 0) {
            if (!target.equals(navTarget)) { noProgressTicks = 0; stuck = false; lastProgressPos = position(); }
            navTarget = target.immutable();
            repathTicks = 40;
            Path path = getNavigation().createPath(target, 0);
            if (path != null) getNavigation().moveTo(path, speed);
        }
        return false;
    }

    private void tickNavigationWatch() {
        if (navTarget == null) return;
        if (position().distanceToSqr(lastProgressPos) > 1.0) {
            lastProgressPos = position();
            noProgressTicks = 0;
            stuck = false;
        } else if (++noProgressTicks > 160) {
            stuck = true;
        }
    }

    public void stopWalking() { getNavigation().stop(); navTarget = null; }
    public boolean isStuck() { return stuck; }
    public void clearStuck() { stuck = false; noProgressTicks = 0; lastProgressPos = position(); }

    public boolean near(BlockPos p, double r) {
        Vec3 c = Vec3.atCenterOf(p);
        double dx = getX() - c.x, dz = getZ() - c.z;
        return dx * dx + dz * dz <= r * r && Math.abs(getY() - p.getY()) < 2.5;
    }

    public void lookAtBlock(BlockPos p) {
        getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 30, 30);
    }

    /** Finds a walkable spot next to a block so the NPC can work at it (handles items on counters). */
    public BlockPos standSpotFor(BlockPos block) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Direction facing = null;
        BlockState bs = level().getBlockState(block);
        if (bs.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING))
            facing = bs.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        for (int dy = 0; dy >= -1; dy--) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = block.relative(d).above(dy);
                if (!walkable(p)) continue;
                double dist = p.distToCenterSqr(position()) + (facing != null && d == facing ? -4 : 0) + (dy != 0 ? 1 : 0);
                if (dist < bestD) { bestD = dist; best = p; }
            }
            if (best != null) return best;
        }
        return block;
    }

    public boolean walkable(BlockPos p) {
        Level l = level();
        BlockState s = l.getBlockState(p);
        if (!s.getCollisionShape(l, p).isEmpty() && !(s.getBlock() instanceof DoorBlock)) return false;
        BlockPos up = p.above();
        if (!l.getBlockState(up).getCollisionShape(l, up).isEmpty() && !(l.getBlockState(up).getBlock() instanceof DoorBlock)) return false;
        BlockPos down = p.below();
        return !l.getBlockState(down).getCollisionShape(l, down).isEmpty();
    }

    // ------------------------------------------------------------------ misc behaviour

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean canPickUpLoot() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("NpcName", npcName);
        t.putInt("Skin", skin());
        t.putInt("Outfit", outfit());
        t.putBoolean("Kid", isKid());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        npcName = t.getString("NpcName");
        setSkin(t.getInt("Skin"));
        setOutfit(t.getInt("Outfit"));
        setKid(t.getBoolean("Kid"));
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> {
            if (isPassenger()) return state.setAndContinue(action() == ACT_SLEEP ? SLEEP : SIT);
            if (state.isMoving()) return state.setAndContinue(WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 3, state -> {
            int a = action();
            if (a <= 0 || a >= ACTIONS.length) return PlayState.STOP;
            return state.setAndContinue(ACTIONS[a]);
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
