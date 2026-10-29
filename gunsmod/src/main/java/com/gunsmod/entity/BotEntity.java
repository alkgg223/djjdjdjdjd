package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.block.ArenaBlockEntity;
import com.gunsmod.block.SpawnerBlockEntity;
import com.gunsmod.entity.ai.BotArtillery;
import com.gunsmod.entity.ai.BotAvoidGrenadeGoal;
import com.gunsmod.entity.ai.BotCombatGoal;
import com.gunsmod.entity.ai.BotGlanceGoal;
import com.gunsmod.entity.ai.BotPatrolGoal;
import com.gunsmod.entity.ai.BotReloadGoal;
import com.gunsmod.entity.ai.BotTargetGoal;
import com.gunsmod.entity.ai.BotVehicleGoal;
import com.gunsmod.entity.ai.Sight;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.DogTagItem;
import com.gunsmod.item.GunItem;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.RemoteItem;
import com.gunsmod.menu.BotMenu;
import com.gunsmod.world.BattleMap;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A soldier of the green or the red team. */
public class BotEntity extends PathfinderMob {
    private static final EntityDataAccessor<Byte> DATA_TEAM = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SKIN = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(BotEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Movement speeds as navigation multipliers of {@link #BASE_SPEED}. A mob moves at about 43 * speed^2 blocks/s, so
     * walk = 3.9 b/s, jog = 4.7 b/s and run = 5.6 b/s, the same as a sprinting player. Soldiers never use the vanilla
     * sprint flag, which would add another 30%.
     */
    public static final double BASE_SPEED = 0.3, SPEED_WALK = 1.0, SPEED_JOG = 1.1, SPEED_RUN = 1.2;
    private static final String[] CALLSIGNS = {"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Ghost", "Hunter", "Viper", "Wolf", "Eagle", "Cobra"};

    @Nullable public BlockPos home;
    @Nullable public BlockPos spawnerPos;
    @Nullable public BlockPos arenaPos;
    /** the orchard battlefield this soldier was deployed on: it marches on the enemy base along the roads */
    @Nullable public BattleMap battle;
    /** which way: -1 left flank track, 0 main track, 1 right flank track */
    public int lane;
    /** how far along the way it has got (index into {@link BattleMap#route}) */
    public int routeIdx = -1;
    /**
     * A base's missile operator: stays by its launch stations ({@link #OPERATOR_LEASH} blocks), keeps them loaded from
     * a rack that never runs out and fires at enemies anywhere within 500 blocks (see {@link BotArtillery}).
     */
    public boolean operator;
    public static final double OPERATOR_LEASH = 20;
    /** anti-vehicle mines it carries (laid somewhere along its way) */
    public int mines = 1;
    public boolean ignorePlayers;
    public int grenades = 2;
    public int smokes = 1;
    public long lastHurtTime = -1000;
    @Nullable public LivingEntity lastAttacker;
    @Nullable public Vec3 lastKnownEnemyPos;
    @Nullable private Vec3 heardAt;
    private int heardTicks;
    public int vehicleCooldown;
    /** missiles this soldier carries to reload the launch stations of its remote (off hand) */
    public final SimpleContainer missiles = new SimpleContainer(4);
    public final BotArtillery artillery = new BotArtillery(this);

    private int fireCooldown;
    private int burstLeft;
    private int reloadLeft;
    private float spinUp;

    public BotEntity(EntityType<? extends BotEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
        for (EquipmentSlot s : EquipmentSlot.values()) this.setDropChance(s, 0.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TEAM, (byte) 0);
        builder.define(DATA_SKIN, (byte) 0);
        builder.define(DATA_AIMING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BotAvoidGrenadeGoal(this));
        this.goalSelector.addGoal(2, new BotVehicleGoal(this));
        this.goalSelector.addGoal(3, new BotCombatGoal(this));
        this.goalSelector.addGoal(4, new BotReloadGoal(this));
        this.goalSelector.addGoal(6, new BotPatrolGoal(this));
        this.goalSelector.addGoal(8, new BotGlanceGoal(this));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new BotTargetGoal(this));
    }

    // ------------------------------------------------------------------------------------ team / config
    public BotTeam getBotTeam() {
        return BotTeam.byId(this.entityData.get(DATA_TEAM));
    }

    public void setTeam(BotTeam team) {
        this.entityData.set(DATA_TEAM, (byte) team.ordinal());
    }

    public int getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkin(int skin) {
        this.entityData.set(DATA_SKIN, (byte) (skin & 3));
    }

    public boolean isAiming() {
        return this.entityData.get(DATA_AIMING);
    }

    public void setAiming(boolean aiming) {
        if (isAiming() != aiming) this.entityData.set(DATA_AIMING, aiming);
    }

    /** configure a fresh soldier with a team, a random face, a random weapon and random attachments */
    public void setup(BotTeam team, RandomSource r, @Nullable GunType forced) {
        setTeam(team);
        setSkin(r.nextInt(4));
        GunType[] all = GunType.values();
        GunType g = forced != null ? forced : all[r.nextInt(all.length)];
        ItemStack gun = GunItem.create(g);
        gun.set(Reg.ATTACHMENTS.get(), randomAttachments(g, r));
        this.setItemSlot(EquipmentSlot.MAINHAND, gun);
        String name = CALLSIGNS[r.nextInt(CALLSIGNS.length)] + "-" + (1 + r.nextInt(9));
        this.setCustomName(Component.translatable("gunsmod.team." + team.name().toLowerCase()).append(" " + name).withStyle(team.chat));
        this.setCustomNameVisible(false);
        this.home = this.blockPosition();
    }

    /** every slot the gun supports gets a random attachment of that slot, or stays empty */
    public static GunAttachments randomAttachments(GunType g, RandomSource r) {
        GunAttachments att = GunAttachments.NONE;
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            if (!g.supports(slot) || r.nextFloat() < 0.4f) continue;
            java.util.List<Attachment> options = new java.util.ArrayList<>();
            for (Attachment a : Attachment.values()) if (a.slot == slot) options.add(a);
            if (!options.isEmpty()) att = att.with(slot, options.get(r.nextInt(options.size())));
        }
        return att;
    }

    // ------------------------------------------------------------------------------------ relations
    public boolean isEnemy(LivingEntity e) {
        if (e == null || e == this || !e.isAlive()) return false;
        if (e instanceof BotEntity b) return b.getBotTeam() != this.getBotTeam();
        if (e instanceof Player p) {
            return BotTeam.of(p) != getBotTeam() && !ignorePlayers && !p.isCreative() && !p.isSpectator();
        }
        if (e instanceof JeepEntity j) return j.getBotTeam() != getBotTeam() && !j.getPassengers().isEmpty();
        if (e instanceof VehicleEntity v) return v.getBotTeam() != getBotTeam() && v.isManned();
        return e instanceof Enemy && getBotTeam() == BotTeam.GREEN && !(e instanceof net.minecraft.world.entity.monster.Creeper && distanceToSqr(e) < 9);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return isEnemy(target) && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        if (other instanceof BotEntity b) return b.getBotTeam() == this.getBotTeam();
        if (other instanceof Player p) return BotTeam.of(p) == getBotTeam();
        return super.isAlliedTo(other);
    }

    public boolean canSeeTarget(LivingEntity t) {
        if (!this.getSensing().hasLineOfSight(t)) return false;
        return !GrenadeEntity.smokeBlocks(this.level(), this.getEyePosition(), t.getEyePosition());
    }

    // ------------------------------------------------------------------------------------ weapons
    public ItemStack gun() {
        return this.getMainHandItem();
    }

    @Nullable
    public GunType gunType() {
        return GunItem.typeOf(this.getMainHandItem());
    }

    /** rockets and grenades can hurt armour, bullets can not */
    public boolean hasAntiArmor() {
        GunType t = gunType();
        return t != null && (t.kind == GunType.Kind.ROCKET || t.kind == GunType.Kind.GRENADE);
    }

    public boolean isReloadingGun() {
        return reloadLeft > 0;
    }

    public boolean needsReload() {
        ItemStack g = gun();
        return g.getItem() instanceof GunItem && GunItem.ammo(g) <= 0;
    }

    public void startBotReload() {
        GunType t = gunType();
        if (t == null || reloadLeft > 0) return;
        ItemStack g = gun();
        if (GunItem.ammo(g) >= GunItem.attachments(g).magSize(t)) return;
        reloadLeft = GunItem.attachments(g).reloadTicks(t) + 6;
        playBotSound(Reg.sound(t == GunType.DOUBLE_BARREL || t == GunType.M79 ? "break_open" : "mag_out"), 0.8f, 1f);
    }

    private float accuracyMul() {
        Difficulty d = this.level().getDifficulty();
        float m = d == Difficulty.HARD ? 1.0f : d == Difficulty.NORMAL ? 1.35f : 1.9f;
        return m;
    }

    /** try to fire at the target this tick. returns true when a shot was fired */
    public boolean tryShoot(LivingEntity target, boolean canSee) {
        GunType t = gunType();
        if (t == null || reloadLeft > 0 || fireCooldown > 0 || !canSee) return false;
        ItemStack g = gun();
        if (GunItem.ammo(g) <= 0) {
            startBotReload();
            return false;
        }
        double dist = this.distanceTo(target);
        if (t.kind == GunType.Kind.FLAME && dist > t.range) return false;
        if ((t.kind == GunType.Kind.ROCKET || t.kind == GunType.Kind.GRENADE) && dist < 7) return false;
        if (t == GunType.MINIGUN) {
            spinUp = Math.min(1f, spinUp + 0.08f);
            if (spinUp < 1f) return false;
        }
        Vec3 eye = this.getEyePosition();
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.62, target.getZ());
        // lead moving targets
        Vec3 v = target.getDeltaMovement();
        aim = aim.add(v.x * dist * 0.5, 0, v.z * dist * 0.5);
        if (t.kind == GunType.Kind.GRENADE) aim = aim.add(0, dist * dist * 0.0028, 0);
        Vec3 d = aim.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        // friendly fire check along the line
        AABB box = new AABB(eye, aim).inflate(0.6);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e != target && !isEnemy(e)
                && !(e instanceof JeepEntity) && !(e instanceof VehicleEntity) && e != this.getVehicle())) {
            if (e.getBoundingBox().inflate(0.35).clip(eye, aim).isPresent()) return false;
        }
        boolean aimingDown = t.category == GunType.Category.SNIPER || dist > 22;
        float spreadMul = accuracyMul() * (1f + (float) dist / 80f) * (target.isCrouching() ? 1.15f : 1f);
        if (this.isPassenger()) spreadMul *= 1.4f;
        setAiming(true);
        GunItem.setAmmo(g, GunItem.ammo(g) - 1);
        GunLogic.fire(this, g, t, yaw, pitch, this.random.nextLong(), aimingDown, spreadMul, null);
        this.yHeadRot = yaw;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        // burst control so automatic weapons are not lasers
        fireCooldown = t.fireDelay;
        if (t.mode != GunType.FireMode.SEMI) {
            if (burstLeft <= 0) burstLeft = (t.category == GunType.Category.HEAVY ? 8 : 3) + this.random.nextInt(5);
            if (--burstLeft <= 0) fireCooldown += 8 + this.random.nextInt(14) + (int) (dist / 4);
        } else {
            fireCooldown += (int) (accuracyMul() * 4) + this.random.nextInt(6) + (t.category == GunType.Category.SNIPER ? 25 : 0);
        }
        return true;
    }

    public void throwGrenade(Vec3 at, boolean smoke) {
        if (smoke ? smokes <= 0 : grenades <= 0) return;
        if (smoke) smokes--; else grenades--;
        GrenadeEntity g = new GrenadeEntity(this.level(), this, smoke);
        Vec3 eye = this.getEyePosition();
        Vec3 d = at.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double ticks = Mth.clamp(horiz / 0.75, 12, 40);
        double gravity = 0.05;
        Vec3 vel = new Vec3(d.x / ticks, (d.y + 0.5 * gravity * ticks * ticks) / ticks, d.z / ticks).scale(1.06);
        g.setPos(eye.x, eye.y - 0.2, eye.z);
        g.setDeltaMovement(vel);
        this.level().addFreshEntity(g);
        this.swing(InteractionHand.OFF_HAND);
        this.playBotSound(SoundEvents.SNOWBALL_THROW, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------------------------ events
    public void onShotBy(LivingEntity shooter) {
        this.lastHurtTime = this.level().getGameTime();
        if (isEnemy(shooter)) {
            this.lastAttacker = shooter;
            if (this.getTarget() == null || !this.canSeeTarget(this.getTarget())) this.setTarget(shooter);
        }
    }

    public boolean recentlyHurt(int ticks) {
        return this.level().getGameTime() - lastHurtTime < ticks;
    }

    /**
     * Soldiers within the radius that are enemies of the shooter hear where the shot came from: one that has the
     * shooter in front of its eyes engages, the others turn their heads that way first.
     */
    public static void alertGunfire(ServerLevel level, LivingEntity shooter, double radius) {
        List<BotEntity> bots = level.getEntitiesOfClass(BotEntity.class, shooter.getBoundingBox().inflate(radius), b -> b != shooter && b.isAlive());
        for (BotEntity b : bots) {
            if (b.isEnemy(shooter) && b.getTarget() == null) {
                b.lastKnownEnemyPos = shooter.position();
                if (b.canSeeTarget(shooter) && Sight.soldier(b, shooter)) b.setTarget(shooter);
                else b.hear(shooter.position());
            }
        }
    }

    /**
     * An anti-vehicle mine at its feet: never near its own base, not next to another of the team's mines and no more
     * than ten of them around. On an orchard battlefield that is on a road, where the vehicles drive.
     */
    private void layMine() {
        if (home != null && home.distSqr(this.blockPosition()) < 40 * 40) return;
        if (battle != null && battle.base(getBotTeam()).distanceToSqr(this.position()) < 70 * 70) return;
        List<MineEntity> laid = this.level().getEntitiesOfClass(MineEntity.class, this.getBoundingBox().inflate(160, 32, 160), m -> m.getMineTeam() == getBotTeam());
        if (laid.size() >= 10) return;
        for (MineEntity m : laid) if (m.distanceToSqr(this) < 14 * 14) return;
        this.level().addFreshEntity(new MineEntity(this.level(), getBotTeam(), this, this.position(), this.random.nextFloat() * 360f));
        mines--;
        this.swing(InteractionHand.MAIN_HAND);
        playBotSound(Reg.sound("cover_open"), 0.6f, 0.6f);
    }

    /** a shot or a call heard from there: look that way for a moment */
    public void hear(Vec3 at) {
        heardAt = at;
        heardTicks = 50;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        net.minecraft.world.entity.Entity att = source.getEntity();
        if (att instanceof BotEntity b && b != this && b.getBotTeam() == this.getBotTeam()) return false;
        if (att instanceof Player p && BotTeam.of(p) == getBotTeam() && !p.isCreative() && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) amount *= 0.25f;
        boolean r = super.hurt(source, amount);
        if (r && !this.level().isClientSide) {
            lastHurtTime = this.level().getGameTime();
            if (att instanceof LivingEntity le && isEnemy(le)) {
                lastAttacker = le;
                if (getTarget() == null) setTarget(le);
                // call for help: team mates nearby turn to where the fire comes from
                for (BotEntity b : this.level().getEntitiesOfClass(BotEntity.class, this.getBoundingBox().inflate(24), b -> b != this && b.getBotTeam() == getBotTeam())) {
                    if (b.getTarget() == null && b.isEnemy(le)) {
                        b.lastKnownEnemyPos = le.position();
                        if (b.canSeeTarget(le) && Sight.soldier(b, le)) b.setTarget(le);
                        else b.hear(le.position());
                    }
                }
            }
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide) {
            Containers.dropContents(this.level(), this, missiles);
            if (spawnerPos != null && this.level().getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity s) s.onBotDied(this.getUUID());
            if (arenaPos != null && this.level().getBlockEntity(arenaPos) instanceof ArenaBlockEntity a) a.onBotDied(this);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (fireCooldown > 0) fireCooldown--;
        if (vehicleCooldown > 0) vehicleCooldown--;
        if (mines > 0 && !operator && this.getTarget() == null && !this.isPassenger() && this.onGround() && this.random.nextInt(900) == 0) layMine();
        if (heardTicks > 0 && heardAt != null) {
            heardTicks--;
            if (this.getTarget() == null) this.getLookControl().setLookAt(heardAt.x, heardAt.y + 1.5, heardAt.z, 20f, 20f);
        }
        if (reloadLeft > 0 && --reloadLeft == 0) {
            ItemStack g = gun();
            GunType t = gunType();
            if (t != null) {
                GunItem.setAmmo(g, GunItem.attachments(g).magSize(t));
                playBotSound(Reg.sound(t.category == GunType.Category.SNIPER ? "bolt" : "mag_in"), 0.8f, 1f);
            }
        }
        LivingEntity target = this.getTarget();
        // the crew of a vehicle can not be hit: fight the vehicle instead
        if (target != null && VehicleEntity.shieldOf(target) instanceof LivingEntity v) {
            this.setTarget(isEnemy(v) ? v : null);
            target = this.getTarget();
        }
        if (target != null && (!target.isAlive() || !isEnemy(target) || distanceToSqr(target) > 96 * 96 || target.level() != this.level())) {
            this.setTarget(null);
        }
        if (target == null) {
            setAiming(false);
            spinUp = Math.max(0, spinUp - 0.05f);
            if (this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth()) this.heal(1.0f);
        }
        if (!isReloadingGun() && target == null && needsReload()) startBotReload();
        artillery.tick();
    }

    @Override
    public void aiStep() {
        this.updateSwingTime();
        super.aiStep();
    }

    // crouching hitbox
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        if (pose == Pose.CROUCHING) return EntityDimensions.scalable(0.6f, 1.5f).withEyeHeight(1.27f);
        return super.getDefaultDimensions(pose);
    }

    public void setCrouch(boolean crouch) {
        Pose p = crouch ? Pose.CROUCHING : Pose.STANDING;
        if (this.getPose() != p) this.setPose(p);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ------------------------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean mayEdit = BotTeam.of(player) == getBotTeam() || player.isCreative();
        if (!mayEdit) return InteractionResult.PASS;
        if (held.getItem() instanceof DogTagItem) {
            if (!this.level().isClientSide) DogTagItem.save(held, this, player);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.getItem() instanceof GunItem) {
            if (!this.level().isClientSide) {
                ItemStack old = this.getMainHandItem().copy();
                this.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
                held.shrink(1);
                if (!old.isEmpty() && !player.getAbilities().instabuild) {
                    if (!player.getInventory().add(old)) player.drop(old, false);
                }
                reloadLeft = 0;
                player.displayClientMessage(Component.translatable("gunsmod.msg.gave_weapon", this.getDisplayName()), true);
                this.playBotSound(Reg.sound("bolt"), 0.8f, 1f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.getItem() instanceof RemoteItem) {
            if (!this.level().isClientSide) {
                ItemStack old = this.getOffhandItem().copy();
                this.setItemSlot(EquipmentSlot.OFFHAND, held.copyWithCount(1));
                this.setGuaranteedDrop(EquipmentSlot.OFFHAND);
                held.shrink(1);
                if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
                player.displayClientMessage(Component.translatable("gunsmod.msg.gave_remote", this.getDisplayName(),
                        RemoteItem.links(this.getOffhandItem()).stations().size()), true);
                this.playBotSound(Reg.sound("ui_confirm"), 0.8f, 1f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.getItem() instanceof MissileItem) {
            if (!this.level().isClientSide) {
                ItemStack rest = missiles.addItem(held.copy());
                int given = held.getCount() - rest.getCount();
                if (given > 0) {
                    held.shrink(given);
                    player.displayClientMessage(Component.translatable("gunsmod.msg.gave_missiles", given, this.getDisplayName()), true);
                    this.playBotSound(Reg.sound("mag_in"), 0.8f, 0.6f);
                } else {
                    player.displayClientMessage(Component.translatable("gunsmod.msg.rack_full", this.getDisplayName()), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.getItem() instanceof ArmorItem armor) {
            if (!this.level().isClientSide) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                ItemStack old = this.getItemBySlot(slot).copy();
                this.setItemSlot(slot, held.copyWithCount(1));
                held.shrink(1);
                if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // soldiers take no orders: they patrol on their own; sneak + empty hand opens the gear editor
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new BotMenu(id, inv, this), this.getDisplayName()), buf -> buf.writeVarInt(this.getId()));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------------------------ dog tag loadout
    public CompoundTag saveLoadout() {
        CompoundTag t = new CompoundTag();
        t.putInt("team", getBotTeam().ordinal());
        t.putInt("skin", getSkin());
        t.putInt("grenades", grenades);
        if (this.hasCustomName()) t.putString("name", this.getCustomName().getString());
        var reg = this.registryAccess();
        CompoundTag eq = new CompoundTag();
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = this.getItemBySlot(s);
            if (!st.isEmpty()) eq.put(s.getName(), st.save(reg));
        }
        t.put("equipment", eq);
        return t;
    }

    public void applyLoadout(CompoundTag t) {
        setTeam(BotTeam.byId(t.getInt("team")));
        setSkin(t.getInt("skin"));
        grenades = t.contains("grenades") ? t.getInt("grenades") : 2;
        if (t.contains("name")) {
            this.setCustomName(Component.literal(t.getString("name")).withStyle(getBotTeam().chat));
            this.setCustomNameVisible(false);
        }
        var reg = this.registryAccess();
        CompoundTag eq = t.getCompound("equipment");
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (eq.contains(s.getName())) {
                ItemStack.parse(reg, eq.getCompound(s.getName())).ifPresent(st -> this.setItemSlot(s, st));
            }
        }
        operator = t.getBoolean("operator");
        if (operator) {
            // one of each missile the launch stations take; the operator's rack never empties
            int[] kinds = {MissileItem.HE, MissileItem.CLUSTER, MissileItem.INCENDIARY};
            for (int i = 0; i < kinds.length && i < missiles.getContainerSize(); i++) missiles.setItem(i, new ItemStack(MissileItem.byKind(kinds[i])));
        }
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Team", getBotTeam().ordinal());
        tag.putInt("Skin", getSkin());
        tag.putInt("Grenades", grenades);
        tag.putInt("Smokes", smokes);
        tag.putBoolean("IgnorePlayers", ignorePlayers);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (spawnerPos != null) tag.put("Spawner", NbtUtils.writeBlockPos(spawnerPos));
        if (arenaPos != null) tag.put("Arena", NbtUtils.writeBlockPos(arenaPos));
        if (battle != null) {
            tag.put("Battle", battle.save());
            tag.putInt("Lane", lane);
            tag.putInt("RouteIdx", routeIdx);
        }
        tag.putBoolean("Operator", operator);
        tag.putInt("Mines", mines);
        ListTag rack = new ListTag();
        for (int i = 0; i < missiles.getContainerSize(); i++) {
            ItemStack s = missiles.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putByte("Slot", (byte) i);
            e.put("Item", s.save(this.registryAccess()));
            rack.add(e);
        }
        tag.put("Missiles", rack);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTeam(BotTeam.byId(tag.getInt("Team")));
        setSkin(tag.getInt("Skin"));
        grenades = tag.getInt("Grenades");
        smokes = tag.getInt("Smokes");
        ignorePlayers = tag.getBoolean("IgnorePlayers");
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        spawnerPos = NbtUtils.readBlockPos(tag, "Spawner").orElse(null);
        arenaPos = NbtUtils.readBlockPos(tag, "Arena").orElse(null);
        battle = BattleMap.load(tag.getCompound("Battle"));
        lane = tag.getInt("Lane");
        routeIdx = battle != null ? tag.getInt("RouteIdx") : -1;
        operator = tag.getBoolean("Operator");
        mines = tag.contains("Mines") ? tag.getInt("Mines") : 1;
        missiles.clearContent();
        for (Tag t : tag.getList("Missiles", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            int slot = e.getByte("Slot");
            if (slot >= 0 && slot < missiles.getContainerSize()) {
                ItemStack.parse(this.registryAccess(), e.getCompound("Item")).ifPresent(s -> missiles.setItem(slot, s));
            }
        }
    }

    public void playBotSound(SoundEvent sound, float vol, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, vol, pitch);
    }
}
