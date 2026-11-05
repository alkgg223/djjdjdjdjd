package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.world.Blast;
import java.util.Comparator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Attack drones. The FPV kamikaze quadcopter is flown by its operator (a soldier, or the player who launched it):
 * low and fast to the target, then it dives into it and its RPG warhead goes off; if the operator dies the link is
 * lost and it falls. The Shahed-136 flies by itself to the spot it was given: a rocket booster throws it up, the
 * two-stroke engine drones on at height, and close to the target it pitches over and dives in.
 * Both show on radar (small), and guns, the C-RAM and missiles can bring them down.
 */
public class DroneEntity extends VehicleEntity implements Aircraft {
    public enum Kind { FPV, SHAHED }

    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final TicketType<ChunkPos> TICKET = TicketType.create("gunsmod_drone", Comparator.comparingLong(ChunkPos::toLong), 30);
    @Nullable private UUID operator;
    @Nullable private Vec3 target;
    private int targetId = -1, life;
    private boolean gone;
    public Vec3 velocity = Vec3.ZERO;
    // client: bank and pitch for the renderer, the rotor / propeller angle
    public float rotor, rotorO, visPitch, prevVisPitch, visRoll, prevVisRoll;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    /** a new drone of a side, flying to a target (an entity to chase, or a point) */
    public static DroneEntity launch(Level level, Kind kind, BotTeam team, Vec3 from, @Nullable Entity chase, @Nullable Vec3 point, @Nullable UUID operator) {
        DroneEntity d = new DroneEntity(Reg.DRONE.get(), level);
        d.entityData.set(DATA_KIND, (byte) kind.ordinal());
        d.setTeam(team);
        d.operator = operator;
        d.targetId = chase == null ? -1 : chase.getId();
        d.target = chase != null ? chase.position() : point;
        d.moveTo(from.x, from.y, from.z, 0, 0);
        d.setHealth(kind == Kind.FPV ? 4f : 18f);
        Vec3 to = d.target == null ? new Vec3(0, 0, 1) : d.target.subtract(from).multiply(1, 0, 1).normalize();
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        d.setYRot(yaw);
        d.velocity = kind == Kind.SHAHED ? to.scale(0.6).add(0, 0.9, 0) : new Vec3(0, 0.35, 0);
        level.addFreshEntity(d);
        level.playSound(null, from.x, from.y, from.z, Reg.sound(kind == Kind.SHAHED ? "shahed_launch" : "ui_confirm"), SoundSource.NEUTRAL,
                kind == Kind.SHAHED ? 6f : 1f, 1f);
        return d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_KIND.equals(key)) this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return fpv() ? EntityDimensions.scalable(0.9f, 0.45f) : EntityDimensions.scalable(2.0f, 0.7f);
    }

    /** the point it flies to (for the operator's screen) */
    @Nullable
    public Vec3 target() {
        return target;
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(this.entityData.get(DATA_KIND), Kind.values().length)];
    }

    public boolean fpv() {
        return kind() == Kind.FPV;
    }

    // ------------------------------------------------------------------------------------ vehicle basics
    @Override
    public ItemStack pickItem() {
        return new ItemStack(fpv() ? Reg.FPV_DRONE.get() : Reg.SHAHED.get());
    }

    @Override
    public int seatCount() {
        return 0;
    }

    @Override
    protected Vec3 seatModel(int seat) {
        return new Vec3(8, 4, 8);
    }

    @Override
    public boolean isArmored() {
        return false;
    }

    @Override
    public float bulletFactor() {
        return 1f;
    }

    @Override
    public boolean protectsCrew() {
        return false;
    }

    @Override
    public boolean fitForBots() {
        return false;
    }

    @Override
    public int seatForBot(BotEntity bot) {
        return -1;
    }

    @Override
    public boolean unmannedThreat() {
        return true;
    }

    @Override
    public boolean isAirborne() {
        return !this.onGround();
    }

    @Override
    public void missileWarning(GuidedMissileEntity m) {
    }

    @Override
    public void radarWarning() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, DamageSource src) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || gone) return false;
        if (source.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            this.discard();
            return true;
        }
        this.setHealth(this.getHealth() - amount);
        if (this.getHealth() <= 0) shotDown();
        return true;
    }

    @Override
    public void hurtByBullet(DamageSource src, float amount) {
        hurt(src, amount);
    }

    @Override
    public void die(DamageSource source) {
        shotDown();
    }

    /** brought down: a small burst where it is (the warhead may go with it) */
    private void shotDown() {
        if (this.isRemoved() || gone) return;
        gone = true;
        Vec3 c = this.position();
        if (this.random.nextInt(3) == 0) Blast.explode(this.level(), this, c, fpv() ? 1.6f : 2.6f, false, Level.ExplosionInteraction.NONE, Blast.FX_SMALL, 0.7f);
        else Blast.fx(this.level(), c, Blast.FX_SMALL, 0.5f);
        this.discard();
    }

    // ------------------------------------------------------------------------------------ flight
    @Override
    public void tick() {
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (this.level().isClientSide) {
            rotorO = rotor;
            rotor += fpv() ? 70f : 55f;
            prevVisPitch = visPitch;
            prevVisRoll = visRoll;
            Vec3 m = this.position().subtract(this.xo, this.yo, this.zo);
            float pitchWant = (float) (Mth.atan2(m.y, m.horizontalDistance()) * Mth.RAD_TO_DEG);
            if (fpv()) pitchWant = (float) -m.horizontalDistance() * 22f + (m.y < -0.3 ? -40f : 0f);
            visPitch += (pitchWant - visPitch) * 0.3f;
            float dyaw = Mth.wrapDegrees(this.getYRot() - this.yRotO);
            visRoll += (Mth.clamp(dyaw * 6f, -45f, 45f) - visRoll) * 0.25f;
            if (!fpv() && life++ < 40) {
                this.level().addParticle(Reg.FLAME.get(), getX(), getY(), getZ(), 0, -0.05, 0);
                this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        life++;
        if (!fpv() && this.level() instanceof ServerLevel sl && this.tickCount % 5 == 0) {
            ChunkPos here = this.chunkPosition();
            sl.getChunkSource().addRegionTicket(TICKET, here, 3, here);
            ChunkPos ahead = new ChunkPos(BlockPos.containing(this.position().add(velocity.scale(24))));
            sl.getChunkSource().addRegionTicket(TICKET, ahead, 3, ahead);
        }
        if (fpv()) fpvFlight();
        else shahedFlight();
        if (this.isRemoved()) return;
        // anything solid on the way, or an enemy close by: the warhead goes off
        Vec3 next = this.position().add(velocity);
        HitResult hr = this.level().clip(new ClipContext(this.position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hr.getType() != HitResult.Type.MISS) {
            detonate(hr.getLocation());
            return;
        }
        for (Entity e : this.level().getEntities(this, this.getBoundingBox().expandTowards(velocity).inflate(fpv() ? 1.2 : 2.0),
                e -> e instanceof LivingEntity le && le.isAlive() && !(e instanceof DroneEntity) && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam())) {
            detonate(e.position().add(0, e.getBbHeight() * 0.5, 0));
            return;
        }
        // whatever it was sent after (a mob, a player's pick): close enough is a hit
        Entity c = chased();
        if (c != null && c.getBoundingBox().inflate(fpv() ? 1.0 : 1.8).intersects(this.getBoundingBox().expandTowards(velocity))) {
            detonate(c.position().add(0, c.getBbHeight() * 0.5, 0));
            return;
        }
        float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90f;
        if (velocity.horizontalDistanceSqr() > 1e-4) this.setYRot(yaw);
        this.setPos(next.x, next.y, next.z);
    }

    @Nullable
    private Entity chased() {
        if (targetId < 0) return null;
        Entity e = this.level().getEntity(targetId);
        if (e == null || !e.isAlive()) {
            targetId = -1;
            return null;
        }
        return e;
    }

    private boolean operatorOk() {
        if (operator == null) return true;
        if (!(this.level() instanceof ServerLevel sl)) return true;
        Entity op = sl.getEntity(operator);
        return op != null && op.isAlive() && op.distanceToSqr(this) < 420 * 420;
    }

    private void fpvFlight() {
        if (!operatorOk() || life > 1500) {
            // the link is gone (or the battery): it drops out of the sky
            velocity = velocity.scale(0.95).add(0, -0.06, 0);
            return;
        }
        Entity t = chased();
        if (t != null) target = t.position().add(0, t.getBbHeight() * 0.5, 0);
        if (target == null) {
            velocity = velocity.scale(0.9);
            return;
        }
        Vec3 to = target.subtract(this.position());
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
        Vec3 want;
        if (life < 20) {
            want = new Vec3(0, 0.5, 0);                                // lift off
        } else if (horiz > 22) {
            // low and fast over the ground to stay out of sight
            double cruise = Math.max(ground + 7, Math.min(target.y + 8, ground + 14));
            Vec3 flat = new Vec3(to.x, 0, to.z).normalize().scale(1.35);
            want = flat.add(0, Mth.clamp((cruise - getY()) * 0.15, -0.5, 0.5), 0);
        } else {
            // terminal dive straight at it, leading a moving target
            Vec3 lead = t != null ? target.add(t.getDeltaMovement().scale(horiz / 1.6)) : target;
            want = lead.subtract(this.position()).normalize().scale(1.6);
        }
        // a quad turns quickly but not instantly
        velocity = velocity.add(want.subtract(velocity).scale(0.22));
    }

    private void shahedFlight() {
        if (target == null || life > 6000) {
            velocity = velocity.scale(0.99).add(0, -0.03, 0);
            return;
        }
        if (life < 40) {
            // rocket booster: climbing away from the launcher
            velocity = velocity.scale(0.98).add(velocity.normalize().scale(0.05));
            return;
        }
        Vec3 to = target.subtract(this.position());
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        Vec3 want;
        if (horiz > 55) {
            int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
            Vec3 ahead = this.position().add(new Vec3(to.x, 0, to.z).normalize().scale(30));
            int g2 = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ahead.x), Mth.floor(ahead.z));
            double cruise = Math.max(ground, g2) + 45;
            Vec3 flat = new Vec3(to.x, 0, to.z).normalize().scale(1.05);
            want = flat.add(0, Mth.clamp((cruise - getY()) * 0.04, -0.25, 0.25), 0);
            // a wide turn: it can not steer hard
            velocity = velocity.add(want.subtract(velocity).scale(0.06));
        } else {
            // pitch over and dive onto the spot
            want = to.normalize().scale(1.5);
            velocity = velocity.add(want.subtract(velocity).scale(0.12));
        }
    }

    private void detonate(Vec3 at) {
        if (this.isRemoved() || gone) return;
        gone = true;
        Level level = this.level();
        if (fpv()) {
            // RPG warhead: deadly to a vehicle it lands on, a burst for soldiers around
            Blast.explode(level, this, at, 3.0f, false, Level.ExplosionInteraction.NONE, Blast.FX_SMALL, 0.9f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3), e -> e instanceof VehicleEntity || e instanceof JeepEntity)) {
                if (e == this) continue;
                e.invulnerableTime = 0;
                e.hurt(this.damageSources().explosion(this, null), 55f);
                e.invulnerableTime = 0;
            }
        } else {
            // 50 kg warhead
            Blast.explode(level, this, at, 5.0f, false, Level.ExplosionInteraction.NONE, Blast.FX_HEAVY, 2.2f);
            Blast.shockwave(level, at, 14, 40f, 6, this, null);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(8), e -> e instanceof VehicleEntity || e instanceof JeepEntity)) {
                if (e == this) continue;
                double d = e.position().distanceTo(at);
                e.invulnerableTime = 0;
                e.hurt(this.damageSources().explosion(this, null), (float) (160 * Math.max(0, 1 - d / 8)));
                e.invulnerableTime = 0;
            }
            level.playSound(null, at.x, at.y, at.z, Reg.sound("explosion_big"), SoundSource.NEUTRAL, 10f, 0.9f);
        }
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < (fpv() ? 160 * 160 : 320 * 320);
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", this.entityData.get(DATA_KIND));
        tag.putInt("Life", life);
        if (operator != null) tag.putUUID("Operator", operator);
        if (target != null) {
            tag.putDouble("TX", target.x);
            tag.putDouble("TY", target.y);
            tag.putDouble("TZ", target.z);
        }
        tag.putDouble("VX", velocity.x);
        tag.putDouble("VY", velocity.y);
        tag.putDouble("VZ", velocity.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_KIND, tag.getByte("Kind"));
        life = tag.getInt("Life");
        operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
        if (tag.contains("TX")) target = new Vec3(tag.getDouble("TX"), tag.getDouble("TY"), tag.getDouble("TZ"));
        velocity = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ"));
    }
}
