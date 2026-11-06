package com.arkragnarok.entity;

import com.arkragnarok.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The hero Tyrannosaurus.  Server side: AI, combat, saddle and riding; it fires GeckoLib triggered animations
 * (roar, bite, shake attack, hurt) that the client plays on top of the looping locomotion controller.
 */
public class RexEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(RexEntity.class, EntityDataSerializers.BOOLEAN);
    /** showcase / debug: forces a looping animation (-1 = normal behaviour) */
    private static final EntityDataAccessor<Integer> FORCED_ANIM = SynchedEntityData.defineId(RexEntity.class, EntityDataSerializers.INT);

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rex.idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rex.walk");
    public static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.rex.run");
    public static final RawAnimation SPRINT = RawAnimation.begin().thenLoop("animation.rex.sprint");
    public static final RawAnimation EAT = RawAnimation.begin().thenLoop("animation.rex.eat");
    public static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("animation.rex.sleep");
    public static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.rex.death");
    public static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.rex.roar");
    public static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.rex.bite");
    public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.rex.attack");
    public static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.rex.hurt");
    /** index order of {@link #setForcedAnim} */
    public static final RawAnimation[] FORCED = {IDLE, WALK, RUN, SPRINT, EAT, SLEEP, DEATH};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int roarCooldown = 200;
    private boolean hadTarget;
    /** client: smoothed body yaw velocity, used to swing the tail when turning */
    public float tailLag;
    public float tailLagO;

    public RexEntity(EntityType<? extends RexEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 220.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.STEP_HEIGHT, 1.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SADDLED, false);
        builder.define(FORCED_ANIM, -1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 20.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 20, true, false, null));
    }

    // ------------------------------------------------------------------------------------------------- state
    public boolean isSaddled() {
        return this.entityData.get(SADDLED);
    }

    public void setSaddled(boolean saddled) {
        this.entityData.set(SADDLED, saddled);
    }

    public int getForcedAnim() {
        return this.entityData.get(FORCED_ANIM);
    }

    public void setForcedAnim(int index) {
        this.entityData.set(FORCED_ANIM, index);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddled", isSaddled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSaddled(tag.getBoolean("Saddled"));
    }

    // ------------------------------------------------------------------------------------------------- behaviour
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tailLagO = tailLag;
            float yawVel = Mth.wrapDegrees(this.yBodyRot - this.yBodyRotO);
            tailLag += (Mth.clamp(yawVel, -12f, 12f) - tailLag) * 0.18f;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || isDeadOrDying()) return;
        if (roarCooldown > 0) roarCooldown--;
        boolean hasTarget = getTarget() != null && getTarget().isAlive();
        if (hasTarget && !hadTarget && roarCooldown <= 0) roar();
        else if (!hasTarget && roarCooldown <= 0 && getRandom().nextInt(2400) == 0) roar();
        hadTarget = hasTarget;
    }

    public void roar() {
        roarCooldown = 400;
        triggerAnim("action", "roar");
        playSound(SoundEvents.RAVAGER_ROAR, 4.0f, 0.55f);
        AABB area = getBoundingBox().inflate(14);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.getBbHeight() < 2.2f)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("action", getRandom().nextInt(3) == 0 ? "attack" : "bite");
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) triggerAnim("action", "hurt");
        return hurt;
    }

    /** keep the body for the 3 s death animation instead of vanishing after one second */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 70 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isSaddled() && stack.is(ModItems.REX_SADDLE.get())) {
            if (!level().isClientSide) {
                setSaddled(true);
                stack.consume(1, player);
                playSound(SoundEvents.HORSE_SADDLE, 1.0f, 0.8f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isSaddled() && !isVehicle() && !player.isSecondaryUseActive() && stack.isEmpty()) {
            if (!level().isClientSide) player.startRiding(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------------------------------------- riding
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setTarget(null);
        this.setRot(player.getYRot(), player.getXRot() * 0.5f);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float side = player.xxa * 0.4f;
        float fwd = player.zza;
        if (fwd <= 0) fwd *= 0.3f;
        return new Vec3(side, 0, fwd);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (player.isSprinting() ? 1.55f : 1.0f);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        // seat: on the saddle above the shoulders; the model is 0.8 scale, seat top is 69 px above the ground
        double seatY = 69.5 / 16.0 * 0.8;
        double fwd = 7.5 / 16.0 * 0.8;
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double x = this.getX() - Mth.sin(yaw) * fwd;
        double z = this.getZ() + Mth.cos(yaw) * fwd;
        Vec3 att = passenger.getVehicleAttachmentPoint(this);
        callback.accept(passenger, x - att.x, this.getY() + seatY - att.y, z - att.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isSaddled() && getPassengers().isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- misc
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(5.5, 1.0, 5.5);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55f + getRandom().nextFloat() * 0.1f;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 260;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.9f, 0.6f);
    }

    // ------------------------------------------------------------------------------------------------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::mainAnimation));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("roar", ROAR)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("hurt", HURT));
    }

    private PlayState mainAnimation(AnimationState<RexEntity> state) {
        if (this.isDeadOrDying()) return state.setAndContinue(DEATH);
        int forced = getForcedAnim();
        if (forced >= 0 && forced < FORCED.length) {
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(FORCED[forced]);
        }
        Vec3 v = getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        if (state.isMoving() || speed > 0.02) {
            if (speed > 0.30) {
                state.getController().setAnimationSpeed(Mth.clamp(speed / 0.36, 0.8, 1.4));
                return state.setAndContinue(SPRINT);
            }
            if (speed > 0.17) {
                state.getController().setAnimationSpeed(Mth.clamp(speed / 0.23, 0.8, 1.4));
                return state.setAndContinue(RUN);
            }
            state.getController().setAnimationSpeed(Mth.clamp(speed / 0.11, 0.55, 1.5));
            return state.setAndContinue(WALK);
        }
        state.getController().setAnimationSpeed(1.0);
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
