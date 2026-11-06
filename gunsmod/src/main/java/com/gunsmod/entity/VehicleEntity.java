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
    /** the damage zones, six bits each (63 = sound): engine, tracks / wheels, turret, main gun, fuel; bit 30 = on fire */
    private static final EntityDataAccessor<Integer> DATA_ZONES = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    public static final int Z_ENGINE = 0, Z_MOBILITY = 1, Z_TURRET = 2, Z_GUN = 3, Z_FUEL = 4, ZONES = 5;
    private static final int INTACT = 0b111111_111111_111111_111111_111111;
    /** where the hit that is being dealt landed (set by the gun and blast code around the hurt call; server thread) */
    @Nullable public static Vec3 hitPoint;
    private int burnTicks;

    @Nullable public BlockPos home;
    @Nullable public BlockPos spawnerPos;
    /** a route set with the route planner: whoever of the soldiers drives follows it (null = none) */
    @Nullable public com.gunsmod.entity.ai.Waypoints waypoints;
    /** convoy: the vehicle ahead in the column and the distance kept to it (null = not in a column) */
    @Nullable public java.util.UUID convoyAhead;
    public float convoyGap = 12f;
    /** convoy: closed up on the vehicle ahead, wait */
    public boolean convoyWait;
    /** client hook (sounds, exhaust and damage smoke) installed by the client setup */
    public static Consumer<VehicleEntity> clientTick = v -> { };
    /** client: true for the local player looking through the vehicle's sight (first person) */
    public static java.util.function.Predicate<Entity> sightView = e -> false;

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
        b.define(DATA_ZONES, INTACT);
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

    /** flies with nobody on board and is still a threat (an attack drone) */
    public boolean unmannedThreat() {
        return false;
    }

    /** somebody on board, or a drone: what radars, air defence and soldiers treat as a live enemy */
    public boolean threat() {
        return !this.getPassengers().isEmpty() || unmannedThreat();
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

    /** where the gunner's eye goes when he looks through the sight in first person; null = his seat */
    @Nullable
    public Vec3 sightEye() {
        return null;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        int seat = Math.max(0, seatOf(passenger));
        if (seat == 0 && this.level().isClientSide && sightView.test(passenger)) {
            // first person: the eye sits at the gunner's sight beside the gun (the vehicle is not drawn then)
            Vec3 eye = sightEye();
            if (eye != null) {
                callback.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
                return;
            }
        }
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
        if (held.getItem() instanceof com.gunsmod.item.RepairKitItem) {
            if (!this.level().isClientSide) com.gunsmod.item.RepairKitItem.use(this, player, held);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if ((held.is(Items.IRON_INGOT) || held.is(Items.IRON_BLOCK)) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide) {
                if (held.is(Items.IRON_BLOCK)) repair();
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

    // ------------------------------------------------------------------------------------ damage zones
    /** a zone's state: 1 sound, 0 wrecked */
    public float zone(int z) {
        return ((this.entityData.get(DATA_ZONES) >> (z * 6)) & 63) / 63f;
    }

    public void setZone(int z, float v) {
        int raw = this.entityData.get(DATA_ZONES);
        int q = Mth.clamp(Math.round(v * 63), 0, 63);
        raw = (raw & ~(63 << (z * 6))) | (q << (z * 6));
        this.entityData.set(DATA_ZONES, raw);
    }

    public boolean isBurning() {
        return (this.entityData.get(DATA_ZONES) & (1 << 30)) != 0;
    }

    private void setBurning(boolean b) {
        int raw = this.entityData.get(DATA_ZONES);
        this.entityData.set(DATA_ZONES, b ? raw | (1 << 30) : raw & ~(1 << 30));
    }

    /** the models left as the hulk when it is destroyed (model path, render scale), the turret last */
    protected java.util.List<String> hulkModels() {
        return java.util.List.of();
    }

    protected float hulkScale() {
        return 4f;
    }

    /** has a turret and a main gun (the zones only matter then) */
    public boolean hasTurret() {
        return false;
    }

    /** top speed and acceleration with the engine and running gear as they are */
    public float engineFactor() {
        float e = zone(Z_ENGINE);
        return e <= 0 ? 0f : 0.35f + 0.65f * e;
    }

    public float mobilityFactor() {
        float m = zone(Z_MOBILITY);
        return m <= 0 ? 0.12f : 0.35f + 0.65f * m;
    }

    /** turret traverse speed (0: jammed) */
    public float turretFactor() {
        float t = zone(Z_TURRET);
        return t <= 0 ? 0f : 0.3f + 0.7f * t;
    }

    /** main gun: reload time multiplier, 0 = it cannot fire */
    public float gunFactor() {
        float g = zone(Z_GUN);
        return g <= 0 ? 0f : 0.35f + 0.65f * g;
    }

    /** which zone a hit at a world point takes: low = running gear, the back = engine (and the tanks), up front = turret / gun */
    private int zoneAt(Vec3 p) {
        Vec3 d = p.subtract(this.position());
        double a = Math.toRadians(this.getYRot());
        double fwd = -d.x * Math.sin(a) + d.z * Math.cos(a);
        double h = this.getBbHeight(), len = Math.max(1.0, this.getBbWidth());
        double up = d.y / Math.max(0.5, h);
        if (up < 0.3) return Z_MOBILITY;
        if (fwd < -len * 0.18) return this.random.nextFloat() < 0.35f ? Z_FUEL : Z_ENGINE;
        if (hasTurret() && up > 0.6) return this.random.nextFloat() < 0.4f ? Z_GUN : Z_TURRET;
        return this.random.nextFloat() < 0.2f ? Z_FUEL : -1;
    }

    private void damageZones(DamageSource source, float amount) {
        Vec3 at = hitPoint;
        if (at == null) {
            Entity src = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
            if (src == null) return;
            // from the attacker's side, at the height it is
            Vec3 c = this.position().add(0, this.getBbHeight() * 0.5, 0);
            Vec3 to = src.position().add(0, src.getBbHeight() * 0.5, 0).subtract(c);
            at = to.lengthSqr() < 1e-4 ? c : c.add(to.normalize().scale(this.getBbWidth() * 0.5));
        }
        boolean blast = source.is(DamageTypeTags.IS_EXPLOSION);
        float share = amount / Math.max(1f, this.getMaxHealth() * 0.3f);
        int z = zoneAt(at);
        if (z >= 0) setZone(z, zone(z) - share);
        // a blast underneath (a mine, a shell at the tracks) wrecks the running gear
        if (blast && at.y < this.getY() + this.getBbHeight() * 0.3) setZone(Z_MOBILITY, zone(Z_MOBILITY) - share * 1.5f);
        if (zone(Z_FUEL) < 0.3f && !isBurning() && this.random.nextFloat() < 0.35f) setBurning(true);
    }

    /** the repair kit: puts a quarter back on the worst zone and some health, true if there was anything to mend */
    public boolean repair() {
        int worst = -1;
        float w = 1f;
        for (int z = 0; z < ZONES; z++) {
            if (!hasTurret() && (z == Z_TURRET || z == Z_GUN)) continue;
            if (zone(z) < w - 1e-3) {
                w = zone(z);
                worst = z;
            }
        }
        boolean did = false;
        if (worst >= 0) {
            setZone(worst, zone(worst) + 0.25f);
            did = true;
        }
        if (this.getHealth() < this.getMaxHealth()) {
            this.heal(this.getMaxHealth() * 0.08f);
            did = true;
        }
        if (isBurning() && zone(Z_FUEL) >= 0.3f) setBurning(false);
        return did;
    }

    /** server tick: a burning fuel system eats the vehicle until it is put out or blows up */
    private void zoneTick() {
        if (!isBurning()) return;
        if (++burnTicks % 20 == 0) {
            this.invulnerableTime = 0;
            super.hurt(this.damageSources().onFire(), Math.max(1f, this.getMaxHealth() * 0.02f));
            this.invulnerableTime = 0;
            if (this.isInWater()) setBurning(false);
        }
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
        amount /= com.gunsmod.Config.vehicleDurability();
        boolean r = super.hurt(source, amount);
        if (r && !source.is(DamageTypeTags.IS_FIRE)) damageZones(source, amount);
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
        float a = amount * bulletFactor() / com.gunsmod.Config.vehicleDurability();
        if (super.hurt(src, a)) damageZones(src, a);
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
        // the burnt-out hulk stays; a tank's turret is sometimes blown clean off
        java.util.List<String> hulk = hulkModels();
        for (int i = 0; i < hulk.size(); i++) {
            boolean turret = hasTurret() && i == hulk.size() - 1 && hulk.size() > 1;
            Vec3 v = turret && this.random.nextFloat() < 0.4f ? new Vec3(this.random.nextGaussian() * 0.15, 0.7 + this.random.nextDouble() * 0.3, this.random.nextGaussian() * 0.15)
                    : Vec3.ZERO;
            this.level().addFreshEntity(com.gunsmod.entity.WreckPieceEntity.hulk(this.level(), hulk.get(i), hulkScale(), this.position(), this.getYRot(), v));
        }
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
        if (!this.level().isClientSide && routeActive()) com.gunsmod.entity.ai.Waypoints.keepLoaded(this);
        if (!this.level().isClientSide && this instanceof Aircraft) com.gunsmod.block.airdef.AirTracker.add(this);
        if (!this.level().isClientSide) zoneTick();
        super.tick();
        if (this.level().isClientSide) clientTick.accept(this);
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Team", getBotTeam().ordinal());
        tag.putInt("Zones", this.entityData.get(DATA_ZONES));
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (spawnerPos != null) tag.put("Spawner", NbtUtils.writeBlockPos(spawnerPos));
        if (waypoints != null) tag.put("Waypoints", waypoints.save());
        if (convoyAhead != null) {
            tag.putUUID("ConvoyAhead", convoyAhead);
            tag.putFloat("ConvoyGap", convoyGap);
        }
    }

    /** column driving: the place behind the vehicle ahead (null = not in a column, or wait when convoyWait is set) */
    @Nullable
    public Vec3 convoyGoal() {
        convoyWait = false;
        if (convoyAhead == null) return null;
        Entity a = com.gunsmod.entity.ai.Convoy.ahead(this, convoyAhead);
        if (a == null) {
            // the vehicle ahead is gone: lead on along the route from here
            convoyAhead = null;
            com.gunsmod.entity.ai.Convoy.rejoin(this, waypoints);
            return null;
        }
        com.gunsmod.entity.ai.Waypoints.keepLoaded(this);
        Vec3 s = com.gunsmod.entity.ai.Convoy.slot(this, a, convoyGap);
        if (s == null) convoyWait = true;
        return s;
    }

    /** a soldier drives and there is route left to follow */
    public boolean routeActive() {
        return waypoints != null && waypoints.active() && getControllingPassenger() instanceof BotEntity;
    }

    /** where a soldier driver goes on the route (null = no route or it is done) */
    @Nullable
    public Vec3 routeGoal(double reach) {
        if (!routeActive()) return null;
        BlockPos last = waypoints.points.get(waypoints.at);
        Vec3 g = waypoints.goal(this, reach);
        if (g == null) home = last;           // the end of the route: patrol round there from now on
        return g;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTeam(BotTeam.byId(tag.getInt("Team")));
        if (tag.contains("Zones")) this.entityData.set(DATA_ZONES, tag.getInt("Zones"));
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        spawnerPos = NbtUtils.readBlockPos(tag, "Spawner").orElse(null);
        waypoints = com.gunsmod.entity.ai.Waypoints.read(tag, "Waypoints");
        convoyAhead = tag.hasUUID("ConvoyAhead") ? tag.getUUID("ConvoyAhead") : null;
        if (tag.contains("ConvoyGap")) convoyGap = tag.getFloat("ConvoyGap");
    }
}
