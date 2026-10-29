package com.gunsmod.entity;

import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.world.Blast;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Base of the tank and the helicopter: team colour, seats, armour and damage model, the crew protection, the home
 * position soldiers patrol around and the link back to the vehicle spawner that keeps it in service.
 * Model space: 1 unit = {@link #UNIT} block, front at -Z, +X = right side, (8, 0, 8) = entity origin.
 */
public abstract class VehicleEntity extends Mob implements BotRideable {
    public static final double UNIT = 0.25;
    private static final EntityDataAccessor<Byte> DATA_TEAM = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);

    @Nullable public BlockPos home;
    @Nullable public BlockPos spawnerPos;
    /** client hook (sounds, exhaust and damage smoke) installed by the client setup */
    public static Consumer<VehicleEntity> clientTick = v -> { };

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.noCulling = true;
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_TEAM, (byte) 0);
    }

    // ------------------------------------------------------------------------------------ kind
    /** the item this vehicle is placed with (dropped when a creative player picks it up) */
    public abstract ItemStack pickItem();

    public abstract int seatCount();

    /** seat point in model units for a passenger (turrets move their seat) */
    protected abstract Vec3 seatModel(int seat);

    /** heavy armour: rifles are no use against it */
    public abstract boolean isArmored();

    /** damage taken from rifle and machine gun bullets, as a fraction */
    public abstract float bulletFactor();

    /** the crew sits behind armour: bullets and blasts hit the vehicle instead of them */
    public boolean protectsCrew() {
        return true;
    }

    /** the vehicle that shields this entity (tank, helicopter, armoured car or jeep), or null when it is exposed */
    @Nullable
    public static Entity shieldOf(@Nullable Entity e) {
        if (e == null) return null;
        Entity v = e.getVehicle();
        if (v instanceof VehicleEntity ve && ve.protectsCrew() && ve.isAlive()) return v;
        if (v instanceof JeepEntity j && j.isAlive()) return v;
        return null;
    }

    // ------------------------------------------------------------------------------------ team
    @Override
    public BotTeam getBotTeam() {
        return BotTeam.byId(this.entityData.get(DATA_TEAM));
    }

    public void setTeam(BotTeam t) {
        this.entityData.set(DATA_TEAM, (byte) t.ordinal());
    }

    public boolean isManned() {
        return getControllingPassenger() != null;
    }

    // ------------------------------------------------------------------------------------ seats
    public int seatOf(Entity e) {
        return this.getPassengers().indexOf(e);
    }

    @Nullable
    public Entity seatEntity(int seat) {
        return seat < this.getPassengers().size() ? this.getPassengers().get(seat) : null;
    }

    @Override
    public boolean seatFree(int seat) {
        return seat >= this.getPassengers().size();
    }

    @Override
    public boolean fitForBots() {
        return this.getHealth() > this.getMaxHealth() * 0.3f;
    }

    @Override
    public int seatForBot(BotEntity bot) {
        return this.getPassengers().isEmpty() ? 0 : -1;
    }

    @Override
    public void board(Entity e, int seat) {
        if (this.level().isClientSide || e.isPassenger() || this.getPassengers().size() >= seatCount()) return;
        e.startRiding(this, true);
        if (e instanceof Player p && this.getPassengers().size() == 1) setTeam(BotTeam.of(p));
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < seatCount();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity d = this.getFirstPassenger();
        return d instanceof LivingEntity le ? le : null;
    }

    public Vec3 seatWorld(int seat) {
        Vec3 s = seatModel(seat);
        return toWorld(s.x, s.y, s.z);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        int seat = Math.max(0, seatOf(passenger));
        Vec3 p = seatWorld(seat);
        // players hang 0.6 below their seat point; soldiers get the same so both sit at the same height
        Vec3 att = passenger instanceof Player ? passenger.getVehicleAttachmentPoint(this) : new Vec3(0, 0.6, 0);
        callback.accept(passenger, p.x - att.x, p.y - att.y, p.z - att.z);
        if (passenger instanceof LivingEntity le && !(passenger instanceof Player)) le.yBodyRot = this.getYRot();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double half = this.getBbWidth() / 2 + 0.8;
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yr), 0, -Mth.sin(yr)), fwd = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        Vec3[] tries = {right.scale(-half), right.scale(half), fwd.scale(-half - 1.5), fwd.scale(half + 1.5)};
        for (Vec3 t : tries) {
            Vec3 w = this.position().add(t);
            BlockPos bp = BlockPos.containing(w);
            for (int dy = 1; dy >= -3; dy--) {
                Vec3 c = new Vec3(w.x, bp.getY() + dy, w.z);
                BlockPos below = BlockPos.containing(c).below();
                if (this.level().noCollision(passenger, passenger.getBoundingBox().move(c.subtract(passenger.position())))
                        && !this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty()) {
                    return c;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ------------------------------------------------------------------------------------ geometry
    /** blocks per model unit (the jet uses bigger units) */
    public double unit() {
        return UNIT;
    }

    /** model units -> world position using the current yaw */
    public Vec3 toWorld(double mx, double my, double mz) {
        double u = unit();
        double right = (mx - 8) * u, up = my * u, fwd = -(mz - 8) * u;
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yr), fz = Mth.cos(yr);
        double rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        return new Vec3(getX() + rx * right + fx * fwd, getY() + up, getZ() + rz * right + fz * fwd);
    }

    public Vec3 forward() {
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
    }

    /** true when any of the model points (model units) is inside a solid block: the long hull / rotor ends */
    protected boolean pointsBlocked(double[][] pts) {
        for (double[] p : pts) {
            Vec3 w = toWorld(p[0], p[1], p[2]);
            BlockPos bp = BlockPos.containing(w);
            var shape = this.level().getBlockState(bp).getCollisionShape(this.level(), bp);
            if (!shape.isEmpty() && shape.bounds().move(bp).inflate(0.01).contains(w)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if ((held.is(Items.IRON_INGOT) || held.is(Items.IRON_BLOCK)) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide) {
                this.heal(held.is(Items.IRON_BLOCK) ? this.getMaxHealth() * 0.4f : this.getMaxHealth() * 0.06f);
                held.consume(1, player);
                this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 1.2f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (player.isPassenger()) return InteractionResult.PASS;
        if (!this.level().isClientSide) {
            if (this.getPassengers().size() < seatCount()) {
                board(player, this.getPassengers().size());
                player.displayClientMessage(Component.translatable(seatName(seatOf(player))), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    protected String seatName0() {
        return "gunsmod.seat.0";
    }

    protected String seatName(int seat) {
        return seat == 0 ? seatName0() : "gunsmod.seat.2";
    }

    // ------------------------------------------------------------------------------------ crew input
    /** a fire / action key from a crew member: 0 attack, 1 use, 2 flares / countermeasures, 3 switch weapon */
    public void trigger(Entity who, int weapon, boolean down) {
    }

    /** this crew member's attack and use keys work the vehicle's weapons (not his own gun) */
    public boolean firesFor(Entity e) {
        return getControllingPassenger() == e;
    }

    // ------------------------------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isInvulnerableTo(source)) return false;
        Entity att = source.getEntity();
        if (att != null && att.getVehicle() == this) return false;
        if (att instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            this.spawnAtLocation(pickItem());
            VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
            this.discard();
            return true;
        }
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= isArmored() ? 0.8f : 1.3f;
        else if (source.is(DamageTypeTags.IS_FIRE)) amount *= 0.15f;
        else if (source.is(GunLogic.BULLET)) amount *= bulletFactor();
        else if (source.getDirectEntity() instanceof LivingEntity) amount *= isArmored() ? 0.02f : 0.2f;   // fists and swords
        this.invulnerableTime = 0;
        boolean r = super.hurt(source, amount);
        this.invulnerableTime = 0;
        // the crew knows where the hit came from and turns that way
        if (r && att instanceof LivingEntity le) {
            for (Entity p : this.getPassengers()) if (p instanceof BotEntity b && b.isEnemy(le)) b.lastKnownEnemyPos = le.position();
        }
        return r;
    }

    /** a bullet from a gun (rifle or machine gun) */
    public void hurtByBullet(DamageSource src, float amount) {
        this.invulnerableTime = 0;
        super.hurt(src, amount * bulletFactor());
        this.invulnerableTime = 0;
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, DamageSource src) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || this.isRemoved()) return;
        super.die(source);
        this.ejectPassengers();
        VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
        // the wreck burns out: big fireball, no damage to the terrain
        Vec3 c = this.position().add(0, this.getBbHeight() * 0.5, 0);
        Blast.explode(this.level(), this, c, isArmored() ? 4.5f : 3.6f, false, Level.ExplosionInteraction.NONE, Blast.FX_MISSILE, isArmored() ? 2.0f : 1.6f);
        this.discard();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    protected void doPush(Entity e) {
        // far too heavy to be pushed around
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && home == null && this.tickCount > 1) home = this.blockPosition();
        super.tick();
        if (this.level().isClientSide) clientTick.accept(this);
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Team", getBotTeam().ordinal());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (spawnerPos != null) tag.put("Spawner", NbtUtils.writeBlockPos(spawnerPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTeam(BotTeam.byId(tag.getInt("Team")));
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        spawnerPos = NbtUtils.readBlockPos(tag, "Spawner").orElse(null);
    }
}
