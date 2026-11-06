package com.arkragnarok.entity;

import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.registry.ModSounds;
import com.arkragnarok.species.Diet;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.Temper;
import com.arkragnarok.taming.Taming;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * One class for all 27 creatures; behaviour, stats, size and animations come from the entity type's {@link Species}.
 * Wild level 1-150 scales health, damage and torpor like ARK (health +20 % of base per point, damage +5 % per point).
 */
public class ArkCreature extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FORCED_ANIM = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TORPOR = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> UNCONSCIOUS = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AFFINITY = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EFFECTIVENESS = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> ORDER = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HP_SCALE = SynchedEntityData.defineId(ArkCreature.class, EntityDataSerializers.FLOAT);

    /** orders of a tamed creature (owner: sneak + right click with an empty hand) */
    public static final int FOLLOW = 0, STAY = 1, WANDER = 2;

    /** index order of {@link #setForcedAnim} */
    public static final String[] FORCED = {"idle", "walk", "run", "sprint", "eat", "sleep", "death"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Species species;
    private final RawAnimation idle, walk, run, sprint, eat, sleep, death;
    private int roarCooldown = 200;
    private int hpPoints, meleePoints;
    private boolean hadTarget;
    // taming
    private final SimpleContainer tamingFood = new SimpleContainer(12);
    private float pendingTorpor;
    private int eatTimer;
    private int passiveCooldown;
    @Nullable
    private UUID tamer;
    /** client: smoothed body yaw velocity, used to swing the tail when turning */
    public float tailLag;
    public float tailLagO;
    /** client: 1 = head follows the look target, fades to 0 while an attack / roar / hurt animation plays */
    public float lookWeight = 1f;

    public ArkCreature(EntityType<? extends ArkCreature> type, Level level) {
        super(type, level);
        this.species = ModEntities.speciesOf(type);
        this.idle = RawAnimation.begin().thenLoop(species.anim("idle"));
        this.walk = RawAnimation.begin().thenLoop(species.anim("walk"));
        this.run = RawAnimation.begin().thenLoop(species.anim("run"));
        this.sprint = RawAnimation.begin().thenLoop(species.anim("sprint"));
        this.eat = RawAnimation.begin().thenLoop(species.anim("eat"));
        this.sleep = RawAnimation.begin().thenLoop(species.anim("sleep"));
        this.death = RawAnimation.begin().thenPlayAndHold(species.anim("death"));
        this.xpReward = (int) (5 + species.health / 25);
    }

    public Species species() {
        return species;
    }

    public static AttributeSupplier.Builder createAttributes(Species s) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, s.health * Species.HEALTH_FACTOR)
                .add(Attributes.MOVEMENT_SPEED, s.walkSpeed)
                .add(Attributes.ATTACK_DAMAGE, s.damage * Species.DAMAGE_FACTOR)
                .add(Attributes.ATTACK_KNOCKBACK, s.big() ? 1.5 : 0.4)
                .add(Attributes.FOLLOW_RANGE, s.big() ? 48.0 : 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, Mth.clamp(s.width / 3.0, 0.0, 1.0))
                .add(Attributes.ARMOR, s.armor)
                .add(Attributes.STEP_HEIGHT, Math.max(0.6, Math.min(2.5, s.hip * 0.45)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SADDLED, false);
        builder.define(FORCED_ANIM, -1);
        builder.define(LEVEL, 1);
        builder.define(TORPOR, 0f);
        builder.define(UNCONSCIOUS, false);
        builder.define(AFFINITY, 0f);
        builder.define(EFFECTIVENESS, 1f);
        builder.define(OWNER, Optional.empty());
        builder.define(ORDER, FOLLOW);
        builder.define(HP_SCALE, 1f);
    }

    @Override
    protected void registerGoals() {
        Species s = ModEntities.speciesOf(getType());
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (s.temper == Temper.SKITTISH) {
            this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 14f, 1.2, s.runFactor));
        }
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, s.runFactor * 0.75, true));
        this.goalSelector.addGoal(4, new FollowOwnerGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return (!isTamed() || getOrder() == WANDER) && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        HurtByTargetGoal hurt = new HurtByTargetGoal(this);
        if (s.pack) hurt.setAlertOthers();
        this.targetSelector.addGoal(1, hurt);
        if (s.temper == Temper.AGGRESSIVE && s.diet != Diet.HERBIVORE) {
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 30, true, false,
                    e -> !isTamed() && e.distanceToSqr(this) < (s.big() ? 900 : 400)));
            this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Animal.class, 60, true, false,
                    e -> !isTamed() && e.getBbWidth() < getBbWidth() * 1.2f));
            this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, com.arkragnarok.survivor.Survivor.class, 40, true, false,
                    e -> !isTamed() && e.distanceToSqr(this) < (s.big() ? 900 : 400)));
            this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, ArkCreature.class, 80, true, false,
                    e -> !isTamed() && e instanceof ArkCreature c && !c.isTamed() && c.species.diet == Diet.HERBIVORE
                            && c.getBbWidth() < getBbWidth()));
        }
        if (s.temper == Temper.TERRITORIAL) {
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
                    e -> !isTamed() && e.distanceToSqr(this) < 36 + getBbWidth() * getBbWidth()));
        }
    }

    // ------------------------------------------------------------------------------------------------- levels
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        // ARK-like wild level distribution 1..150, low levels are more common
        double r = getRandom().nextDouble();
        int lvl = 1 + (int) Math.floor(Math.pow(r, 1.6) * 149.0);
        setLevel(lvl);
        return out;
    }

    public int getLevel() {
        return this.entityData.get(LEVEL);
    }

    public void setLevel(int level) {
        level = Mth.clamp(level, 1, 450);
        this.entityData.set(LEVEL, level);
        hpPoints = 0;
        meleePoints = 0;
        addPoints(level - 1);
        applyStats();
    }

    private void addPoints(int points) {
        for (int i = 0; i < points; i++) {
            int k = getRandom().nextInt(5);
            if (k <= 1) hpPoints++;
            else if (k == 2) meleePoints++;
        }
    }

    private void applyStats() {
        Species s = species;
        double hp = s.health * Species.HEALTH_FACTOR * (1 + 0.2 * hpPoints);
        double dmg = s.damage * Species.DAMAGE_FACTOR * (1 + 0.05 * meleePoints);
        // Minecraft caps max health at 1024: above that, health is kept in scaled units and damage is scaled down
        float scale = (float) Math.max(1.0, hp / 1024.0);
        this.entityData.set(HP_SCALE, scale);
        var maxHp = getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(hp / scale);
        var atk = getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(dmg);
        setHealth((float) (hp / scale));
    }

    /** real health (the health bar of Minecraft holds it divided by {@link #hpScale()}) */
    public float realHealth() {
        return getHealth() * hpScale();
    }

    public float realMaxHealth() {
        return getMaxHealth() * hpScale();
    }

    public float hpScale() {
        return this.entityData.get(HP_SCALE);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount / hpScale());
    }

    // ------------------------------------------------------------------------------------------------- taming
    public float getTorpor() {
        return this.entityData.get(TORPOR);
    }

    public float maxTorpor() {
        return Taming.maxTorpor(species, getLevel());
    }

    public boolean isUnconscious() {
        return this.entityData.get(UNCONSCIOUS);
    }

    public float getAffinity() {
        return this.entityData.get(AFFINITY);
    }

    public float requiredAffinity() {
        return Taming.requiredAffinity(species, getLevel());
    }

    public float getEffectiveness() {
        return this.entityData.get(EFFECTIVENESS);
    }

    public boolean isTamed() {
        return this.entityData.get(OWNER).isPresent();
    }

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    @Nullable
    public Player getOwner() {
        UUID id = getOwnerId();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    public int getOrder() {
        return this.entityData.get(ORDER);
    }

    /** tranquilizer arrows, narcotics, punches: torpor builds up over a few seconds */
    public void addTorpor(float amount, @Nullable Entity by) {
        if (isTamed() || level().isClientSide) return;
        pendingTorpor += amount;
        if (by instanceof Player p) tamer = p.getUUID();
    }

    private void setTorpor(float t) {
        this.entityData.set(TORPOR, Mth.clamp(t, 0f, maxTorpor()));
    }

    private void knockOut() {
        this.entityData.set(UNCONSCIOUS, true);
        setTarget(null);
        getNavigation().stop();
        eatTimer = 0;
        if (getAffinity() <= 0) this.entityData.set(EFFECTIVENESS, 1f);
        playSound(ModSounds.REX_DEATH.get(), getSoundVolume() * 0.6f, getVoicePitch() * 0.8f);
        Player p = tamer != null ? level().getPlayerByUUID(tamer) : null;
        if (p != null) p.displayClientMessage(Component.translatable("msg.arkragnarok.knocked_out", species.name, getLevel())
                .withStyle(ChatFormatting.GOLD), true);
    }

    private void wakeUp() {
        this.entityData.set(UNCONSCIOUS, false);
        this.entityData.set(AFFINITY, 0f);
        this.entityData.set(EFFECTIVENESS, 1f);
        pendingTorpor = 0;
        for (int i = 0; i < tamingFood.getContainerSize(); i++) {
            ItemStack st = tamingFood.removeItemNoUpdate(i);
            if (!st.isEmpty()) spawnAtLocation(st);
        }
    }

    private void tickTorpor() {
        float max = maxTorpor();
        float t = getTorpor();
        if (pendingTorpor > 0) {
            float d = Math.min(pendingTorpor, Math.max(4f, Taming.TRANQ_ARROW / 40f));
            pendingTorpor -= d;
            t += d;
        }
        if (!isUnconscious()) {
            if (t >= max && !isTamed()) {
                setTorpor(max);
                knockOut();
                return;
            }
            if (pendingTorpor <= 0) t -= Taming.awakeDrainPerSecond(species, getLevel()) / 20f;
            setTorpor(t);
            return;
        }
        t -= Taming.sleepDrainPerSecond(species, getLevel()) / 20f;
        // it swallows narcotics from its food when it is about to wake up
        if (t < max * 0.4f) {
            for (int i = 0; i < tamingFood.getContainerSize(); i++) {
                ItemStack st = tamingFood.getItem(i);
                float tv = Taming.torporValue(st);
                if (tv > 0) {
                    st.shrink(1);
                    t += tv;
                    break;
                }
            }
        }
        setTorpor(t);
        if (t <= 0) {
            wakeUp();
            return;
        }
        if (++eatTimer >= Taming.eatIntervalTicks(species)) {
            eatTimer = 0;
            eatBest();
        }
    }

    /** eats the best item it was given (preferred kibble first) */
    private void eatBest() {
        int best = -1;
        float bestValue = 0;
        for (int i = 0; i < tamingFood.getContainerSize(); i++) {
            float v = Taming.foodValue(species, tamingFood.getItem(i));
            if (v > bestValue) {
                bestValue = v;
                best = i;
            }
        }
        if (best < 0) return;
        tamingFood.getItem(best).shrink(1);
        gainAffinity(bestValue);
    }

    private void gainAffinity(float v) {
        float a = getAffinity() + v;
        this.entityData.set(AFFINITY, a);
        playSound(SoundEvents.GENERIC_EAT, 1.0f, getVoicePitch());
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.8, getZ(), 6, getBbWidth() * 0.4, 0.3,
                    getBbWidth() * 0.4, 0.0);
        }
        if (a >= requiredAffinity() && tamer != null) {
            tameBy(tamer, level().getPlayerByUUID(tamer));       // the one who fed it owns it, even if offline now
        }
    }

    public void tame(Player owner) {
        tameBy(owner.getUUID(), owner);
    }

    public void tameBy(UUID ownerId, @Nullable Player owner) {
        this.entityData.set(OWNER, Optional.of(ownerId));
        this.entityData.set(UNCONSCIOUS, false);
        this.entityData.set(ORDER, FOLLOW);
        this.entityData.set(TORPOR, 0f);
        pendingTorpor = 0;
        int bonus = (int) Math.floor(getLevel() * 0.5f * getEffectiveness());
        this.entityData.set(LEVEL, getLevel() + bonus);
        addPoints(bonus);
        applyStats();
        setTarget(null);
        setPersistenceRequired();
        for (int i = 0; i < tamingFood.getContainerSize(); i++) {
            ItemStack st = tamingFood.removeItemNoUpdate(i);
            if (!st.isEmpty()) spawnAtLocation(st);
        }
        playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 12, getBbWidth() * 0.5, 0.5, getBbWidth() * 0.5, 0.1);
        }
        if (owner != null) owner.displayClientMessage(Component.translatable("msg.arkragnarok.tamed", species.name, getLevel(),
                Math.round(getEffectiveness() * 100)).withStyle(ChatFormatting.GREEN), false);
    }

    @Override
    public boolean isImmobile() {
        return isUnconscious() || super.isImmobile();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (isTamed()) {
            UUID o = getOwnerId();
            if (target.getUUID().equals(o)) return false;
            if (target instanceof ArkCreature c && o.equals(c.getOwnerId())) return false;
        }
        return super.canAttack(target);
    }

    /** a tamed creature walks after its owner when ordered to follow */
    private class FollowOwnerGoal extends Goal {
        FollowOwnerGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        private boolean far(Player o) {
            double d = 5.0 + getBbWidth() * 1.5;
            return distanceToSqr(o) > d * d;
        }

        @Override
        public boolean canUse() {
            if (!isTamed() || getOrder() != FOLLOW || isVehicle() || getTarget() != null) return false;
            Player o = getOwner();
            return o != null && !o.isSpectator() && far(o);
        }

        @Override
        public boolean canContinueToUse() {
            Player o = getOwner();
            return o != null && isTamed() && getOrder() == FOLLOW && getTarget() == null && far(o) && !getNavigation().isDone();
        }

        @Override
        public void start() {
            Player o = getOwner();
            if (o != null) getNavigation().moveTo(o, distanceToSqr(o) > 400 ? 1.4 : 1.0);
        }

        @Override
        public void tick() {
            Player o = getOwner();
            if (o != null && tickCount % 10 == 0) getNavigation().moveTo(o, distanceToSqr(o) > 400 ? 1.4 : 1.0);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
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
        tag.putInt("ArkLevel", getLevel());
        tag.putInt("HpPoints", hpPoints);
        tag.putInt("MeleePoints", meleePoints);
        tag.putFloat("Torpor", getTorpor());
        tag.putBoolean("Unconscious", isUnconscious());
        tag.putFloat("Affinity", getAffinity());
        tag.putFloat("Effectiveness", getEffectiveness());
        tag.putInt("Order", getOrder());
        if (getOwnerId() != null) tag.putUUID("Owner", getOwnerId());
        if (tamer != null) tag.putUUID("Tamer", tamer);
        tag.put("TamingFood", tamingFood.createTag(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSaddled(tag.getBoolean("Saddled"));
        if (tag.contains("ArkLevel")) {
            this.entityData.set(LEVEL, tag.getInt("ArkLevel"));
            hpPoints = tag.getInt("HpPoints");
            meleePoints = tag.getInt("MeleePoints");
            float h = getHealth();
            applyStats();
            setHealth(Math.min(h, getMaxHealth()));
        }
        this.entityData.set(TORPOR, tag.getFloat("Torpor"));
        this.entityData.set(UNCONSCIOUS, tag.getBoolean("Unconscious"));
        this.entityData.set(AFFINITY, tag.getFloat("Affinity"));
        this.entityData.set(EFFECTIVENESS, tag.contains("Effectiveness") ? tag.getFloat("Effectiveness") : 1f);
        this.entityData.set(ORDER, tag.getInt("Order"));
        this.entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        tamer = tag.hasUUID("Tamer") ? tag.getUUID("Tamer") : null;
        if (tag.contains("TamingFood")) tamingFood.fromTag(tag.getList("TamingFood", 10), level().registryAccess());
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

    private boolean roars() {
        return species.big() && species.eatsMeat();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || isDeadOrDying()) return;
        tickTorpor();
        if (passiveCooldown > 0) passiveCooldown--;
        if (isUnconscious()) return;
        if (isTamed()) {
            if (getOrder() == STAY && !isVehicle() && getTarget() == null) getNavigation().stop();
            Player o = getOwner();
            if (o != null && tickCount % 10 == 0 && getOrder() != STAY && distanceToSqr(o) < 1600) {
                LivingEntity a = o.getLastHurtByMob();
                LivingEntity b = o.getLastHurtMob();
                if (a != null && a.isAlive() && a != this && canAttack(a)) setTarget(a);
                else if (b != null && b.isAlive() && b != this && canAttack(b) && o.tickCount - o.getLastHurtMobTimestamp() < 200) setTarget(b);
            }
        }
        if (roarCooldown > 0) roarCooldown--;
        boolean hasTarget = getTarget() != null && getTarget().isAlive();
        if (roars() && hasTarget && !hadTarget && roarCooldown <= 0) roar();
        else if (!hasTarget && roarCooldown <= 0 && getRandom().nextInt(2400) == 0) roar();
        hadTarget = hasTarget;
    }

    public void roar() {
        roarCooldown = 400;
        triggerAnim("action", "roar");
        playSound(ModSounds.REX_ROAR.get(), species.big() ? 5.0f : 2.0f, getVoicePitch());
        if (!roars()) return;
        AABB area = getBoundingBox().inflate(16);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.getBbHeight() < getBbHeight() * 0.6f)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2));
        }
    }

    /** melee reach grows with the animal (a Rex bites far ahead of its hitbox) */
    @Override
    protected AABB getAttackBoundingBox() {
        return super.getAttackBoundingBox().inflate(species.reach, 0.5, species.reach);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            String a = species.attack;
            boolean big = getRandom().nextInt(3) == 0;
            triggerAnim("action", big ? "attack" : "bite");
            playSound(ModSounds.REX_BITE.get(), 1.5f, getVoicePitch());
            if (big && (a.equals("tail") || a.equals("stomp") || a.equals("horn"))) {
                // sweeping attacks hit everything around the target too
                float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6f;
                List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                        e -> e != this && e != target && e.isAlive());
                for (LivingEntity e : near) e.hurt(damageSources().mobAttack(this), dmg);
            }
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            if (isUnconscious()) {
                float e = getEffectiveness() - 0.03f - amount / Math.max(1f, getMaxHealth()) * hpScale();
                this.entityData.set(EFFECTIVENESS, Math.max(0f, e));
            } else {
                triggerAnim("action", "hurt");
            }
            // fists and clubs knock out too (ARK punch torpor)
            if (source.getDirectEntity() instanceof Player p && (p.getMainHandItem().isEmpty() || p.getMainHandItem().is(Items.STICK))) {
                addTorpor(amount * 3.5f, p);
            }
        }
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
        boolean owner = isTamed() && player.getUUID().equals(getOwnerId());
        if (isUnconscious() && !stack.isEmpty() && (Taming.foodValue(species, stack) > 0 || Taming.torporValue(stack) > 0)) {
            if (!level().isClientSide) {
                tamer = player.getUUID();
                float tv = Taming.torporValue(stack);
                if (tv > 0 && player.isSecondaryUseActive()) {
                    // sneak + right click: force-feed one narcotic right away
                    setTorpor(getTorpor() + tv);
                    stack.consume(1, player);
                } else {
                    ItemStack rest = tamingFood.addItem(stack.copy());
                    int used = stack.getCount() - rest.getCount();
                    if (!player.getAbilities().instabuild) stack.shrink(used);
                    if (eatTimer < Taming.eatIntervalTicks(species) - 20) eatTimer = Taming.eatIntervalTicks(species) - 20;
                }
                player.displayClientMessage(Component.translatable("msg.arkragnarok.taming", species.name,
                        Math.round(100 * getAffinity() / requiredAffinity()), Math.round(getEffectiveness() * 100)), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!isTamed() && Taming.passive(species) && getTarget() == null && Taming.foodValue(species, stack) > 0) {
            if (!level().isClientSide) {
                if (passiveCooldown > 0) {
                    player.displayClientMessage(Component.translatable("msg.arkragnarok.not_hungry", (passiveCooldown + 19) / 20), true);
                } else {
                    tamer = player.getUUID();
                    float v = Taming.foodValue(species, stack);
                    stack.consume(1, player);
                    passiveCooldown = Taming.eatIntervalTicks(species);
                    gainAffinity(v);
                    if (!isTamed()) player.displayClientMessage(Component.translatable("msg.arkragnarok.taming", species.name,
                            Math.round(100 * getAffinity() / requiredAffinity()), 100), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (owner && stack.isEmpty() && player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                int o = (getOrder() + 1) % 3;
                this.entityData.set(ORDER, o);
                setTarget(null);
                player.displayClientMessage(Component.translatable("msg.arkragnarok.order." + o, species.name), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (owner && !stack.isEmpty() && Taming.foodValue(species, stack) > 0 && getHealth() < getMaxHealth()) {
            if (!level().isClientSide) {
                heal(Taming.foodValue(species, stack) / species.affinity * getMaxHealth());
                stack.consume(1, player);
                playSound(SoundEvents.GENERIC_EAT, 1.0f, getVoicePitch());
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!owner && (stack.is(ModItems.DINO_SADDLE.get()) || (isSaddled() && stack.isEmpty() && !player.isSecondaryUseActive()))) {
            if (!level().isClientSide) player.displayClientMessage(Component.translatable("msg.arkragnarok.not_yours"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (species.saddle && !isSaddled() && stack.is(ModItems.DINO_SADDLE.get())) {
            if (!level().isClientSide) {
                setSaddled(true);
                stack.consume(1, player);
                playSound(SoundEvents.HORSE_SADDLE, 1.0f, 0.8f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isSaddled() && !isVehicle() && !player.isSecondaryUseActive() && stack.isEmpty()) {
            if (!level().isClientSide) {
                setTarget(null);
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.isEmpty() && player.isSecondaryUseActive() && !level().isClientSide) {
            player.displayClientMessage(Component.literal(species.name + "  Lv " + getLevel() + "  " + Math.round(realHealth()) + "/"
                    + Math.round(realMaxHealth()) + " HP  " + Math.round(getAttributeValue(Attributes.ATTACK_DAMAGE)) + " dmg"), true);
            return InteractionResult.SUCCESS;
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
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (player.isSprinting() ? species.runFactor * 0.85f : 1.0f);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double x = this.getX() - Mth.sin(yaw) * species.seatForward;
        double z = this.getZ() + Mth.cos(yaw) * species.seatForward;
        Vec3 att = passenger.getVehicleAttachmentPoint(this);
        callback.accept(passenger, x - att.x, this.getY() + species.seatHeight - att.y, z - att.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isSaddled() && getPassengers().isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- misc
    @Override
    public AABB getBoundingBoxForCulling() {
        double r = species.lengthM * 0.55;
        return getBoundingBox().inflate(r, 1.0, r);
    }

    @Override
    public boolean isPushable() {
        return !species.big();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.REX_GROWL.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.REX_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.REX_DEATH.get();
    }

    /** the voice gets higher the smaller the animal */
    @Override
    public float getVoicePitch() {
        float base = Mth.clamp(1.6f - species.lengthM / 14f, 0.75f, 1.9f);
        return base * (0.95f + getRandom().nextFloat() * 0.1f);
    }

    @Override
    protected float getSoundVolume() {
        return Mth.clamp(species.lengthM / 5f, 0.6f, 3.0f);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (species.big()) playSound(ModSounds.REX_STEP.get(), Mth.clamp(species.lengthM / 10f, 0.4f, 1.6f), 0.9f + getRandom().nextFloat() * 0.2f);
        else super.playStepSound(pos, state);
    }

    // ------------------------------------------------------------------------------------------------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::mainAnimation));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("roar", RawAnimation.begin().thenPlay(species.anim("roar")))
                .triggerableAnim("bite", RawAnimation.begin().thenPlay(species.anim("bite")))
                .triggerableAnim("attack", RawAnimation.begin().thenPlay(species.anim("attack")))
                .triggerableAnim("hurt", RawAnimation.begin().thenPlay(species.anim("hurt"))));
    }

    private RawAnimation forced(int i) {
        return switch (i) {
            case 1 -> walk;
            case 2 -> run;
            case 3 -> sprint;
            case 4 -> eat;
            case 5 -> sleep;
            case 6 -> death;
            default -> idle;
        };
    }

    private PlayState mainAnimation(AnimationState<ArkCreature> state) {
        if (this.isDeadOrDying()) return state.setAndContinue(death);
        if (isUnconscious()) {
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(sleep);
        }
        int f = getForcedAnim();
        if (f >= 0) {
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(forced(f));
        }
        Vec3 v = getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        double walkSpeed = species.walkSpeed * 0.42;      // blocks per tick at walking pace
        if (state.isMoving() || speed > 0.015) {
            if (speed > walkSpeed * 1.65) {
                state.getController().setAnimationSpeed(Mth.clamp(speed / (walkSpeed * 2.0), 0.8, 1.5));
                return state.setAndContinue(sprint);
            }
            if (speed > walkSpeed * 1.2) {
                state.getController().setAnimationSpeed(Mth.clamp(speed / (walkSpeed * 1.4), 0.8, 1.5));
                return state.setAndContinue(run);
            }
            state.getController().setAnimationSpeed(Mth.clamp(speed / walkSpeed, 0.5, 1.6));
            return state.setAndContinue(walk);
        }
        state.getController().setAnimationSpeed(1.0);
        return state.setAndContinue(idle);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
