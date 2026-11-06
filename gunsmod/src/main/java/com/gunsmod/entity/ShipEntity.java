package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.block.airdef.AirTracker;
import com.gunsmod.net.Net;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A warship. It floats with its waterline at the surface (a submarine dives below it), drives with throttle and rudder
 * (it only turns while it moves), and fights: the main gun aims where its captain looks (or at the soldiers' target),
 * the anti-ship missiles / torpedoes go on the use key, and on their own the missile cells shoot down enemy aircraft and
 * the close-in gun catches whatever gets near. A soldier crew patrols the water and engages what it sees. The carrier's
 * catapult launches a jet: use a jet item on it.
 */
public class ShipEntity extends VehicleEntity {
    private static final EntityDataAccessor<Float> DATA_GUN_YAW = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GUN_PITCH = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DEPTH = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);

    public final ShipType kind;
    public double speed;
    private float throttleIn, rudderIn;
    private boolean fireMain, fireSecond, diveWanted;
    private int gunReload, secondReload, samCooldown, ciwsCooldown, samLeft, asmLeft, torpLeft, rearmIn;
    @Nullable private Vec3 waypoint;
    private int waypointAge;
    /** client: the gun mount's yaw for the renderer */
    public float prevGunYaw;

    public ShipEntity(EntityType<? extends ShipEntity> type, Level level) {
        this(type, level, ShipType.PATROL);
    }

    public ShipEntity(EntityType<? extends ShipEntity> type, Level level, ShipType kind) {
        super(type, level);
        this.kind = kind;
        this.samLeft = kind.sam;
        this.asmLeft = kind.asm;
        this.torpLeft = kind.torpedoes;
    }

    public static AttributeSupplier.Builder createAttributes(ShipType kind) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, kind.health).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_GUN_YAW, 0f);
        b.define(DATA_GUN_PITCH, 0f);
        b.define(DATA_SPEED, 0f);
        b.define(DATA_DEPTH, 0f);
    }

    // ------------------------------------------------------------------------------------ vehicle basics
    @Override
    public ItemStack pickItem() {
        return new ItemStack(Reg.vehicleItem(kind.vehicle(), getBotTeam()));
    }

    @Override
    public int seatCount() {
        return kind.seats.length;
    }

    @Override
    protected Vec3 seatModel(int seat) {
        double[] s = kind.seats[Math.min(seat, kind.seats.length - 1)];
        return new Vec3(s[0], s[1], s[2]);
    }

    @Override
    public boolean isArmored() {
        return kind.health >= 300;
    }

    @Override
    public float bulletFactor() {
        return kind.bulletFactor;
    }

    @Override
    protected String seatName0() {
        return "gunsmod.seat.captain";
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, net.minecraft.world.damagesource.DamageSource src) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 400 * 400;
    }

    public float getGunYaw() {
        return this.entityData.get(DATA_GUN_YAW);
    }

    public float getGunPitch() {
        return this.entityData.get(DATA_GUN_PITCH);
    }

    public float depth() {
        return this.entityData.get(DATA_DEPTH);
    }

    public double getDisplaySpeed() {
        return this.isControlledByLocalInstance() ? speed : this.entityData.get(DATA_SPEED);
    }

    /** the boat is under water (a dived submarine): hidden from radars and from far eyes */
    public boolean submerged() {
        return kind.submarine() && depth() > 0.6f;
    }

    @Override
    protected java.util.List<String> hulkModels() {
        return java.util.List.of("entity/" + kind.model + "/body");
    }

    // ------------------------------------------------------------------------------------ controls
    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        throttleIn = Mth.clamp(player.zza / 0.98f, -1f, 1f);
        rudderIn = Mth.clamp(-player.xxa / 0.98f, -1f, 1f);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(0, 0, 1);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.3f;
    }

    @Override
    public void trigger(Entity who, int weapon, boolean down) {
        if (who != getControllingPassenger()) return;
        if (weapon == 0) fireMain = down;
        else if (weapon == 1) fireSecond = down;
        else if (weapon == 2 && down && kind.submarine()) {
            diveWanted = !diveWanted;
            if (who instanceof Player p) p.displayClientMessage(Component.translatable(diveWanted ? "gunsmod.msg.sub_dive" : "gunsmod.msg.sub_surface"), true);
        }
    }

    // ------------------------------------------------------------------------------------ floating and driving
    /** the top of the water under the ship, or NaN when there is none close below */
    private double surface() {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(getX()), Mth.floor(getY() + kind.waterline * 0.25 + 3), Mth.floor(getZ()));
        for (int i = 0; i < 12; i++) {
            if (this.level().getFluidState(p).is(FluidTags.WATER)) return p.getY() + 1.0 - 0.12;
            p.move(0, -1, 0);
        }
        return Double.NaN;
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) physics();
        this.calculateEntityAnimation(false);
    }

    private void physics() {
        double surf = surface();
        float dive = depth();
        double vy;
        if (Double.isNaN(surf)) {
            // aground: it settles where it is
            vy = this.getDeltaMovement().y - 0.08;
            speed *= 0.8;
        } else {
            double want = surf - kind.waterline * 0.25 - dive * 4.5;
            double bob = Math.sin(this.tickCount * 0.07 + getId()) * 0.03 * (1 - dive);
            vy = Mth.clamp((want - getY()) * 0.12 + bob, -0.25, 0.25);
        }
        double target = throttleIn >= 0 ? throttleIn * kind.maxSpeed : throttleIn * kind.maxSpeed * 0.35;
        speed += Mth.clamp(target - speed, -kind.accel * 1.5, kind.accel);
        if (Math.abs(throttleIn) < 0.05) speed *= 0.985;
        // a ship turns with the water flowing past its rudder
        float turn = rudderIn * kind.turnRate * (float) Mth.clamp(Math.abs(speed) / (kind.maxSpeed * 0.3), 0.0, 1.0) * (speed < 0 ? -1 : 1);
        this.setYRot(this.getYRot() - turn);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        Vec3 f = forward();
        Vec3 move = new Vec3(f.x * speed, vy, f.z * speed);
        this.move(MoverType.SELF, move);
        double L = kind.length / 2 - 2;
        if (this.horizontalCollision || pointsBlocked(new double[][]{{8.0, kind.waterline + 1, -L}, {8.0, kind.waterline + 1, L}})) speed *= -0.25;
        this.setDeltaMovement(new Vec3(move.x, vy, move.z));
        this.fallDistance = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            prevGunYaw = getGunYaw();
            double sp = getDisplaySpeed();
            if (Math.abs(sp) > 0.15 && !submerged() && this.tickCount % 2 == 0) {
                // the bow wave and the wake
                Vec3 bow = toWorld(8.0, kind.waterline, -kind.length / 2 + 2), stern = toWorld(8.0, kind.waterline, kind.length / 2);
                for (int s = -1; s <= 1; s += 2) {
                    this.level().addParticle(ParticleTypes.SPLASH, bow.x + s * 0.6, bow.y + 0.2, bow.z, 0, 0.1, 0);
                    this.level().addParticle(ParticleTypes.BUBBLE, stern.x + s * 0.8, stern.y + 0.1, stern.z, 0, 0.02, 0);
                }
            }
            if (kind.submarine() && depth() > 0.1f && this.tickCount % 5 == 0) {
                Vec3 b = toWorld(8.0, 10.0, kind.length / 2);
                this.level().addParticle(ParticleTypes.BUBBLE, b.x, b.y, b.z, 0, 0.1, 0);
            }
            return;
        }
        this.entityData.set(DATA_SPEED, (float) speed);
        // a ship does not drown
        this.setAirSupply(this.getMaxAirSupply());
        // the submarine floods or blows its tanks
        float d = depth();
        float wantD = diveWanted ? 1f : 0f;
        if (d != wantD) this.entityData.set(DATA_DEPTH, d + Mth.clamp(wantD - d, -0.01f, 0.01f));
        if (getControllingPassenger() instanceof BotEntity bot) botTick(bot);
        aimTick();
        weaponsTick();
    }

    // ------------------------------------------------------------------------------------ weapons
    private float gunWorldYaw() {
        return this.getYRot() + getGunYaw();
    }

    private void aimTick() {
        if (!kind.hasGun()) return;
        LivingEntity c = getControllingPassenger();
        float wantYaw = getGunYaw(), wantPitch = getGunPitch();
        if (c instanceof Player p) {
            wantYaw = Mth.wrapDegrees(p.getYRot() - this.getYRot());
            wantPitch = Mth.clamp(p.getXRot(), -45f, 8f);
        } else if (c instanceof BotEntity b && b.getTarget() != null) {
            Vec3 to = b.getTarget().position().subtract(this.position());
            wantYaw = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            // a little elevation for the drop of the shell
            wantPitch = Mth.clamp((float) (-Math.toDegrees(Math.atan2(to.y + horiz * horiz * 0.0006, horiz))), -45f, 8f);
        }
        // the mount cannot shoot through its own superstructure: a 300 degree arc
        float limit = kind.gunZ < 0 ? 150f : 160f;
        wantYaw = kind.gunZ < 0 ? Mth.clamp(wantYaw, -limit, limit) : wantYaw;
        float y = getGunYaw();
        y += Mth.clamp(Mth.wrapDegrees(wantYaw - y), -4f, 4f);
        this.entityData.set(DATA_GUN_YAW, y);
        this.entityData.set(DATA_GUN_PITCH, getGunPitch() + Mth.clamp(wantPitch - getGunPitch(), -3f, 3f));
    }

    private Vec3 gunDir() {
        return com.gunsmod.gun.GunLogic.dir(getGunPitch(), gunWorldYaw());
    }

    private Vec3 muzzleWorld() {
        // the muzzle swings round the mount's pivot with the gun
        Vec3 pivot = toWorld(kind.gunX, kind.gunY + kind.gunRaise, kind.gunZ);
        return pivot.add(gunDir().scale(-kind.muzzle * 0.25));
    }

    private void weaponsTick() {
        if (gunReload > 0) gunReload--;
        if (secondReload > 0) secondReload--;
        if (samCooldown > 0) samCooldown--;
        if (ciwsCooldown > 0) ciwsCooldown--;
        if (++rearmIn >= 600) {
            rearmIn = 0;
            if (samLeft < kind.sam) samLeft++;
            if (asmLeft < kind.asm) asmLeft++;
            if (torpLeft < kind.torpedoes) torpLeft++;
        }
        LivingEntity c = getControllingPassenger();
        if (c instanceof BotEntity b) {
            LivingEntity t = b.getTarget();
            boolean inArc = t != null && t.isAlive() && t.distanceTo(this) < kind.gunRange;
            fireMain = inArc && kind.hasGun();
            fireSecond = t != null && t.isAlive() && (t instanceof ShipEntity || t instanceof VehicleEntity && !(t instanceof Aircraft a && a.isAirborne()))
                    && t.distanceTo(this) > 30 && t.distanceTo(this) < 260 && this.random.nextInt(60) == 0;
        }
        if (fireMain && gunReload <= 0 && kind.hasGun()) fireGun(c);
        if (fireMain && kind.submarine() && secondReload <= 0) fireTorpedo(c);
        if (fireSecond && secondReload <= 0) {
            if (kind.torpedoes > 0) fireTorpedo(c);
            else if (kind.asm > 0) fireAsm(c);
        }
        if (kind.sam > 0 && samCooldown <= 0 && samLeft > 0 && !submerged()) samTick();
        if (kind.ciws && ciwsCooldown <= 0) ciwsTick();
    }

    private void fireGun(@Nullable LivingEntity gunner) {
        gunReload = kind.reload;
        Vec3 muzzle = muzzleWorld(), dir = gunDir();
        RocketEntity shell = new RocketEntity(this.level(), gunner, RocketEntity.TANK_SHELL);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.setDeltaMovement(dir.scale(kind.shellSpeed));
        shell.setPower(kind.shellArmour, kind.shellSoft, kind.splash);
        shell.alignToMotion();
        this.level().addFreshEntity(shell);
        boolean big = kind.reload >= 20;
        this.level().playSound(null, muzzle.x, muzzle.y, muzzle.z, Reg.sound(big ? "tank_fire" : "aa_35mm"), SoundSource.NEUTRAL, big ? 12f : 6f, big ? 0.75f : 1.1f);
        if (this.level() instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, muzzle.x, muzzle.y, muzzle.z, com.gunsmod.gun.GunLogic.SHOT_RANGE,
                    new Net.MuzzleFx(muzzle, dir, big ? 2.0f : 0.6f, this.getId()));
            if (gunner != null) BotEntity.alertGunfire(sl, gunner, 128);
        }
    }

    private void fireAsm(@Nullable LivingEntity gunner) {
        if (asmLeft <= 0) return;
        asmLeft--;
        secondReload = 60;
        Vec3 at = toWorld(8.0, kind.waterline + 8, -kind.length * 0.25);
        Vec3 dir = gunner == null ? forward() : com.gunsmod.gun.GunLogic.dir(Math.min(-4f, gunner.getXRot()), gunner.getYRot());
        RocketEntity m = new RocketEntity(this.level(), gunner, RocketEntity.HELI_ROCKET);
        m.setPos(at.x, at.y, at.z);
        m.setDeltaMovement(dir.scale(3.0).add(0, 0.25, 0));
        m.setPower(90f, 50f, 4.0f);
        m.alignToMotion();
        this.level().addFreshEntity(m);
        this.level().playSound(null, at.x, at.y, at.z, Reg.sound("sam_launch"), SoundSource.NEUTRAL, 12f, 0.8f);
        if (gunner instanceof Player p) p.displayClientMessage(Component.translatable("gunsmod.msg.asm_away", asmLeft).withStyle(ChatFormatting.RED), true);
    }

    private void fireTorpedo(@Nullable LivingEntity gunner) {
        if (torpLeft <= 0) return;
        torpLeft--;
        secondReload = 50;
        Vec3 at = toWorld(8.0, 5.0, -kind.length / 2 + 4);
        float yaw = gunner instanceof Player p ? p.getYRot() : this.getYRot();
        TorpedoEntity t = new TorpedoEntity(this.level(), gunner, getBotTeam(), at, yaw);
        this.level().addFreshEntity(t);
        this.level().playSound(null, at.x, at.y, at.z, Reg.sound("torpedo_launch"), SoundSource.NEUTRAL, 6f, 1f);
        if (gunner instanceof Player p) p.displayClientMessage(Component.translatable("gunsmod.msg.torpedo_away", torpLeft).withStyle(ChatFormatting.AQUA), true);
    }

    /** the missile cells: one SAM at the nearest enemy aircraft in reach and in sight */
    private void samTick() {
        samCooldown = 40;
        Vec3 cell = toWorld(8.0, kind.waterline + 6, kind.gunZ + 6);
        VehicleEntity best = null;
        double bd = 240 * 240;
        for (VehicleEntity v : AirTracker.flying(this.level())) {
            if (!v.threat() || BotTeam.of(v) == getBotTeam()) continue;
            double d = v.distanceToSqr(cell);
            if (d < bd && this.level().clip(new ClipContext(cell, v.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
                bd = d;
                best = v;
            }
        }
        if (best == null) return;
        samLeft--;
        GuidedMissileEntity m = new GuidedMissileEntity(this.level(), null, GuidedMissileEntity.SAM).side(getBotTeam()).power(110f, 0f, 0f).target(best).flight(7.0, 9.0);
        if (this.random.nextFloat() > 0.85f) m.missAfter(12 + this.random.nextInt(40));
        m.setPos(cell.x, cell.y, cell.z);
        m.setDeltaMovement(new Vec3(0, 1.4, 0));
        m.alignToMotion();
        this.level().addFreshEntity(m);
        this.level().playSound(null, cell.x, cell.y, cell.z, Reg.sound("sam_launch"), SoundSource.NEUTRAL, 10f, 1f);
    }

    /** the close-in gun: aircraft, drones and incoming rockets and missiles close by */
    private void ciwsTick() {
        ciwsCooldown = 3;
        Vec3 gun = toWorld(8.0, kind.waterline + 6, -kind.length * 0.2);
        Entity best = null;
        double bd = 70 * 70;
        for (Entity e : this.level().getEntities(this, new AABB(gun, gun).inflate(70), e -> e.isAlive() && (e instanceof Aircraft a && a.isAirborne()
                && e instanceof VehicleEntity v && BotTeam.of(v) != getBotTeam() && v.threat() || (e instanceof RocketEntity || e instanceof GuidedMissileEntity || e instanceof MissileEntity)
                && incoming(e)))) {
            double d = e.distanceToSqr(gun);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return;
        if (this.tickCount % 12 == 0) this.level().playSound(null, gun.x, gun.y, gun.z, Reg.sound("ciws_burst"), SoundSource.NEUTRAL, 6f, 1f);
        Vec3 to = best.position().subtract(gun).normalize();
        if (this.level() instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, gun.x, gun.y, gun.z, com.gunsmod.gun.GunLogic.SHOT_RANGE, new Net.MuzzleFx(gun, to, 0.4f, this.getId()));
        }
        if (this.random.nextFloat() < 0.25f) {
            if (best instanceof LivingEntity le) {
                le.hurt(this.damageSources().mobAttack(this), 4f);
            } else {
                com.gunsmod.world.Blast.fx(this.level(), best.position(), com.gunsmod.world.Blast.FX_SMALL, 0.5f);
                best.discard();
            }
        }
    }

    private boolean incoming(Entity e) {
        if (!(e instanceof net.minecraft.world.entity.projectile.Projectile p)) return false;
        Entity owner = p.getOwner();
        if (owner != null && BotTeam.of(owner) == getBotTeam()) return false;
        Vec3 v = e.getDeltaMovement();
        return v.lengthSqr() > 0.25 && v.dot(this.position().subtract(e.position())) > 0;
    }

    // ------------------------------------------------------------------------------------ the crew
    /** a soldier crew: out on the water between random points, slower in a fight, guns on whatever it sees */
    private void botTick(BotEntity bot) {
        if (bot.getTarget() == null && this.tickCount % 20 == 0) {
            LivingEntity best = null;
            double bd = kind.gunRange > 0 ? kind.gunRange : 160;
            for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(bd, 40, bd),
                    e -> e.isAlive() && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam() && !(e instanceof ShipEntity s && s.submerged()))) {
                if (e.getVehicle() != null) continue;
                double d = e.distanceTo(this);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best != null) bot.setTarget(best);
        }
        if (waypoint == null || ++waypointAge > 1200 || waypoint.distanceToSqr(this.position()) < 12 * 12) {
            waypoint = pickWater();
            waypointAge = 0;
        }
        if (waypoint == null) {
            throttleIn = 0;
            rudderIn = 0;
            return;
        }
        Vec3 to = waypoint.subtract(this.position());
        float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.wrapDegrees(want - this.getYRot());
        rudderIn = Mth.clamp(-diff / 25f, -1f, 1f);
        throttleIn = bot.getTarget() != null ? 0.45f : Math.abs(diff) > 60 ? 0.4f : 0.8f;
        if (pointsBlocked(new double[][]{{8.0, kind.waterline + 1, -kind.length / 2 - 6}})) waypoint = null;
        if (kind.submarine()) diveWanted = bot.getTarget() == null || bot.getTarget().distanceTo(this) > 40;
    }

    /** a point out on open water ahead or to the side */
    @Nullable
    private Vec3 pickWater() {
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(this.getYRot() + 90 + (this.random.nextDouble() - 0.5) * 200);
            double d = 60 + this.random.nextDouble() * 80;
            int x = Mth.floor(getX() + Math.cos(a) * d), z = Mth.floor(getZ() + Math.sin(a) * d);
            if (!this.level().hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = this.level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            BlockPos p = new BlockPos(x, y, z);
            if (this.level().getFluidState(p).is(FluidTags.WATER) && this.level().getFluidState(p.below(3)).is(FluidTags.WATER)) return Vec3.atCenterOf(p);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------ the carrier's catapult
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (kind.carrier() && held.getItem() instanceof com.gunsmod.item.VehicleItem vi && vi.kind.group == VehicleKind.Group.JET && vi.kind != VehicleKind.B2) {
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            Vec3 at = toWorld(2.0, 28.6, -40.0);
            Mob m = com.gunsmod.item.VehicleItem.create(this.level(), held, at.x, at.y, at.z, this.getYRot());
            if (m instanceof JetEntity jet) {
                this.level().addFreshEntity(jet);
                jet.catapult(jet.type.rotate * 1.15 + Math.max(0, speed));
                player.startRiding(jet, true);
                if (!player.getAbilities().instabuild) held.shrink(1);
                player.displayClientMessage(Component.translatable("gunsmod.msg.catapult").withStyle(ChatFormatting.GOLD), true);
                this.level().playSound(null, at.x, at.y, at.z, Reg.sound("shahed_launch"), SoundSource.NEUTRAL, 8f, 0.6f);
            }
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Sam", samLeft);
        tag.putInt("Asm", asmLeft);
        tag.putInt("Torpedoes", torpLeft);
        tag.putBoolean("Dive", diveWanted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Sam")) samLeft = tag.getInt("Sam");
        if (tag.contains("Asm")) asmLeft = tag.getInt("Asm");
        if (tag.contains("Torpedoes")) torpLeft = tag.getInt("Torpedoes");
        diveWanted = tag.getBoolean("Dive");
    }
}
