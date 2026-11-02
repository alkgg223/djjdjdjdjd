package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.world.Blast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * B-2 stealth bomber. Called in over a spot, it circles high above it on its own and waits for a group of enemies
 * (several soldiers or vehicles close together, none of its own side near them), then flies over them and drops one
 * guided bomb whose blast rises into a mushroom cloud. Six bombs, then it leaves.
 * <p>
 * No radar and no infrared seeker can find it, and bullets do not hurt it. The only chance is an RPG: every rocket
 * that passes close has a one in a hundred chance to hit, and it takes five hits to bring it down.
 */
public class BomberEntity extends VehicleEntity implements Aircraft {
    public static final double SPEED = 1.45, ORBIT = 72, CRUISE_AGL = 78, MID_Y = 1.9, HALF_SPAN = 15.7;
    public static final float TURN = 1.8f, RPG_HIT_CHANCE = 0.01f;
    public static final int HITS_TO_KILL = 5, BOMBS = 6, BOMB_COOLDOWN = 300;
    /** a group: this much weight (soldier 1, player 1.5, vehicle 2.5) within GROUP_RADIUS of one soldier */
    public static final double GROUP_RADIUS = 9, SAFE_RADIUS = 30, SCAN_RADIUS = 130;
    public static final float GROUP_WEIGHT = 4f;
    /** the bombers in flight (server): soldiers and rockets look here instead of searching the sky */
    private static final java.util.Set<BomberEntity> ACTIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final TicketType<ChunkPos> TICKET = TicketType.create("gunsmod_b2", Comparator.comparingLong(ChunkPos::toLong), 30);
    private static final EntityDataAccessor<Byte> DATA_HITS = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_CRASH = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BAY = SynchedEntityData.defineId(BomberEntity.class, EntityDataSerializers.BOOLEAN);

    /** the spot it circles over */
    @Nullable public Vec3 center;
    private boolean started, leaving;
    private int bombs = BOMBS, cooldown = 120, runTicks, crashTicks, bayTicks, scanIn;
    @Nullable private Vec3 runTarget;
    private double cruiseY = Double.NaN;
    private double groundUnder = Double.NaN;
    /** blocks per tick (server); bots lead their rockets with it */
    public Vec3 velocity = Vec3.ZERO;
    private float climb;
    // client: bank and dive for the renderer
    public float roll, prevRoll, pitchVis, prevPitchVis;

    public BomberEntity(EntityType<? extends BomberEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_HITS, (byte) 0);
        b.define(DATA_CRASH, false);
        b.define(DATA_BAY, false);
    }

    /** live bombers in this level */
    public static List<BomberEntity> active(Level level) {
        List<BomberEntity> l = new ArrayList<>(2);
        for (BomberEntity b : ACTIVE) if (b.level() == level && b.isAlive() && !b.isRemoved()) l.add(b);
        return l;
    }

    @Override
    public void remove(RemovalReason reason) {
        ACTIVE.remove(this);
        super.remove(reason);
    }

    // ------------------------------------------------------------------------------------ kind
    @Override
    public double unit() {
        return 1.0;
    }

    @Override
    public ItemStack pickItem() {
        return new ItemStack(Reg.vehicleItem(VehicleKind.B2, getBotTeam()));
    }

    @Override
    public int seatCount() {
        return 0;
    }

    @Override
    protected Vec3 seatModel(int seat) {
        return new Vec3(8, MID_Y, 4);
    }

    @Override
    public boolean isArmored() {
        return true;
    }

    @Override
    public float bulletFactor() {
        return 0f;
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

    /** nobody aims at it with a gun: bullets pass, rockets are handled by {@link #rpgPass} */
    @Override
    public boolean isPickable() {
        return false;
    }

    public int getHits() {
        return this.entityData.get(DATA_HITS);
    }

    public boolean isCrashing() {
        return this.entityData.get(DATA_CRASH);
    }

    public boolean bayOpen() {
        return this.entityData.get(DATA_BAY);
    }

    public int bombsLeft() {
        return bombs;
    }

    // ------------------------------------------------------------------------------------ aircraft
    @Override
    public boolean isAirborne() {
        return started && !this.onGround();
    }

    @Override
    public boolean stealth() {
        return true;
    }

    @Override
    public void missileWarning(GuidedMissileEntity m) {
    }

    @Override
    public void radarWarning() {
    }

    // ------------------------------------------------------------------------------------ damage: only the dice
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);   // /kill
        if (source.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public void hurtByBullet(DamageSource src, float amount) {
    }

    /**
     * An RPG rocket passing this close: one roll of the dice per rocket. True = a hit (the rocket bursts on the wing).
     */
    public boolean rpgPass(Entity rocket, @Nullable Entity shooter) {
        if (isCrashing() || !this.isAlive()) return false;
        if (this.random.nextFloat() >= RPG_HIT_CHANCE) return false;
        int hits = getHits() + 1;
        this.entityData.set(DATA_HITS, (byte) hits);
        Vec3 at = rocket.position();
        Blast.fx(this.level(), at, Blast.FX_MISSILE, 1.4f);
        this.level().playSound(null, at.x, at.y, at.z, Reg.sound("explosion_far"), SoundSource.NEUTRAL, 12f, 1.2f);
        if (this.level() instanceof ServerLevel sl) {
            Component msg = hits >= HITS_TO_KILL ? Component.translatable("gunsmod.msg.b2_down").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("gunsmod.msg.b2_hit", hits, HITS_TO_KILL).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(this) < 400 * 400 && (p == shooter || hits >= HITS_TO_KILL)) p.displayClientMessage(msg, true);
            }
        }
        if (hits >= HITS_TO_KILL) startCrash();
        return true;
    }

    private void startCrash() {
        this.entityData.set(DATA_CRASH, true);
        this.entityData.set(DATA_BAY, false);
        crashTicks = 0;
        runTarget = null;
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("explosion_big"), SoundSource.NEUTRAL, 16f, 0.7f);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || this.isRemoved()) return;
        VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
        Blast.fx(this.level(), this.position().add(0, MID_Y, 0), Blast.FX_HEAVY, 3.0f);
        this.discard();
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, DamageSource src) {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        // flies itself: see flight()
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 640 * 640;
    }

    // ------------------------------------------------------------------------------------ tick
    @Override
    public void tick() {
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (this.level().isClientSide) {
            clientVisuals();
            return;
        }
        if (!started) start();
        ACTIVE.add(this);
        keepChunks();
        if (isCrashing()) {
            crash();
            return;
        }
        if (bayTicks > 0 && --bayTicks == 0) this.entityData.set(DATA_BAY, false);
        if (cooldown > 0) cooldown--;
        if (!leaving) bombing();
        flight();
        if (leaving && center != null && horiz(center) > 420) {
            VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
            this.discard();
        }
    }

    /** first tick: climb to the cruise height over the spot it was called to, heading the way it was placed */
    private void start() {
        started = true;
        if (center == null) center = this.position();
        double ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(center.x), Mth.floor(center.z));
        cruiseY = Math.min(ground + CRUISE_AGL, this.level().getMaxBuildHeight() - 12);
        Vec3 f = forward();
        Vec3 p = new Vec3(center.x, cruiseY, center.z).subtract(f.scale(40));
        this.moveTo(p.x, p.y, p.z, this.getYRot(), 0);
        velocity = f.scale(SPEED);
    }

    /** chunks round it (and ahead of it) keep running while it flies its circuit */
    private void keepChunks() {
        if (!(this.level() instanceof ServerLevel sl) || this.tickCount % 5 != 0) return;
        ChunkPos here = this.chunkPosition();
        sl.getChunkSource().addRegionTicket(TICKET, here, 3, here);
        Vec3 ahead = this.position().add(velocity.scale(24));
        ChunkPos a = new ChunkPos(BlockPos.containing(ahead));
        sl.getChunkSource().addRegionTicket(TICKET, a, 3, a);
    }

    private double horiz(Vec3 p) {
        double dx = p.x - this.getX(), dz = p.z - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** a steady turn towards the wanted heading, holding the cruise height above the land */
    private void flight() {
        Vec3 goal;
        if (leaving) {
            goal = center == null ? this.position().add(forward().scale(100)) : this.position().add(this.position().subtract(center).multiply(1, 0, 1).normalize().scale(100));
        } else if (runTarget != null) {
            goal = runTarget;
        } else {
            // circle the spot: aim at a point a little further round the circle
            Vec3 c = center == null ? this.position() : center;
            double ang = Math.atan2(this.getZ() - c.z, this.getX() - c.x) + Math.toRadians(32);
            goal = new Vec3(c.x + Math.cos(ang) * ORBIT, 0, c.z + Math.sin(ang) * ORBIT);
        }
        float want = (float) (Mth.atan2(goal.z - this.getZ(), goal.x - this.getX()) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.clamp(Mth.wrapDegrees(want - this.getYRot()), -TURN, TURN);
        float yaw = this.getYRot() + diff;
        // height: the cruise level, or higher over hills
        if (Double.isNaN(groundUnder) || this.tickCount % 10 == 0) {
            Vec3 ahead = this.position().add(forward().scale(30));
            groundUnder = Math.max(this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.getX()), Mth.floor(this.getZ())),
                    this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ahead.x), Mth.floor(ahead.z)));
        }
        double wantY = Math.max(Double.isNaN(cruiseY) ? this.getY() : cruiseY, groundUnder + 40);
        if (leaving) wantY += 30;
        climb = (float) Mth.clamp((wantY - this.getY()) * 0.05, -0.3, 0.3);
        float yr = yaw * Mth.DEG_TO_RAD;
        velocity = new Vec3(-Mth.sin(yr) * SPEED, climb, Mth.cos(yr) * SPEED);
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
        this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
    }

    // ------------------------------------------------------------------------------------ bombing
    private void bombing() {
        if (bombs <= 0) {
            leaving = true;
            return;
        }
        if (runTarget != null) {
            runTicks++;
            // the group may have moved or split up: look again now and then
            if (runTicks % 40 == 0) {
                Vec3 again = findGroup(runTarget, GROUP_RADIUS * 3);
                if (again == null) {
                    runTarget = null;
                    return;
                }
                runTarget = again;
            }
            double d = horiz(runTarget);
            float want = (float) (Mth.atan2(runTarget.z - this.getZ(), runTarget.x - this.getX()) * Mth.RAD_TO_DEG) - 90f;
            float off = Math.abs(Mth.wrapDegrees(want - this.getYRot()));
            // the guided bomb trims its fall a long way: close enough and not flying away from them is enough
            if (d < 25 || d < 60 && off < 60) {
                dropBomb(runTarget);
            } else if (runTicks > 700) {
                runTarget = null;
            }
            return;
        }
        if (cooldown > 0 || --scanIn > 0) return;
        scanIn = 20;
        Vec3 c = center == null ? this.position() : center;
        Vec3 g = findGroup(c, SCAN_RADIUS);
        if (g != null) {
            runTarget = g;
            runTicks = 0;
        }
    }

    private float weight(LivingEntity e) {
        if (e instanceof VehicleEntity || e instanceof JeepEntity) return 2.5f;
        return e instanceof Player ? 1.5f : 1f;
    }

    private boolean enemy(LivingEntity e) {
        if (!e.isAlive() || e instanceof BomberEntity || e.isSpectator()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (VehicleEntity.shieldOf(e) != null) return false;     // counted with its vehicle
        BotTeam t = BotTeam.of(e);
        if (t == null || t == getBotTeam()) return false;
        if (e instanceof VehicleEntity v) return v.isManned() && !(v instanceof Aircraft a && a.isAirborne());
        if (e instanceof JeepEntity j) return !j.getPassengers().isEmpty();
        return e instanceof BotEntity || e instanceof Player;
    }

    private boolean friend(LivingEntity e) {
        if (!e.isAlive() || e == this || e instanceof BomberEntity) return false;
        if (e instanceof Player p && p.isSpectator()) return false;
        BotTeam t = BotTeam.of(e);
        return t == getBotTeam() && (e instanceof BotEntity || e instanceof Player || e instanceof VehicleEntity || e instanceof JeepEntity);
    }

    /**
     * The biggest group of enemies near {@code around}: the middle of the cluster on the ground, or null when there is
     * none big enough or one of its own side would be caught by the blast.
     */
    @Nullable
    private Vec3 findGroup(Vec3 around, double radius) {
        AABB box = new AABB(around.x - radius, around.y - 200, around.z - radius, around.x + radius, around.y + 40, around.z + radius);
        List<LivingEntity> all = this.level().getEntitiesOfClass(LivingEntity.class, box, e -> enemy(e) || friend(e));
        List<LivingEntity> foes = new ArrayList<>(), friends = new ArrayList<>();
        for (LivingEntity e : all) (enemy(e) ? foes : friends).add(e);
        Vec3 best = null;
        float bestW = GROUP_WEIGHT - 0.01f;
        double r2 = GROUP_RADIUS * GROUP_RADIUS;
        for (LivingEntity a : foes) {
            float w = 0;
            double sx = 0, sy = 0, sz = 0;
            for (LivingEntity b : foes) {
                if (a.distanceToSqr(b) > r2) continue;
                float wb = weight(b);
                w += wb;
                sx += b.getX() * wb;
                sy += b.getY() * wb;
                sz += b.getZ() * wb;
            }
            if (w <= bestW) continue;
            Vec3 mid = new Vec3(sx / w, sy / w, sz / w);
            boolean safe = true;
            for (LivingEntity f : friends) {
                if (f.distanceToSqr(mid) < SAFE_RADIUS * SAFE_RADIUS) {
                    safe = false;
                    break;
                }
            }
            if (!safe) continue;
            bestW = w;
            best = mid;
        }
        return best;
    }

    private void dropBomb(Vec3 target) {
        Vec3 bay = toWorld(8, MID_Y - 1.2, 8.5);
        RocketEntity b = new RocketEntity(this.level(), this, RocketEntity.B2_BOMB);
        b.setPos(bay.x, bay.y, bay.z);
        b.setDeltaMovement(velocity.multiply(1, 0, 1).scale(0.6).add(0, -0.2, 0));
        b.guideTo(target);
        b.alignToMotion();
        this.level().addFreshEntity(b);
        this.level().playSound(null, bay.x, bay.y, bay.z, Reg.sound("b2_release"), SoundSource.NEUTRAL, 6f, 1f);
        this.entityData.set(DATA_BAY, true);
        bayTicks = 50;
        bombs--;
        cooldown = BOMB_COOLDOWN;
        runTarget = null;
    }

    // ------------------------------------------------------------------------------------ going down
    private void crash() {
        crashTicks++;
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        double dive = Math.min(1.6, 0.15 + crashTicks * 0.02);
        this.setYRot(this.getYRot() + 0.9f);
        velocity = new Vec3(-Mth.sin(yr) * SPEED * 0.9, -dive, Mth.cos(yr) * SPEED * 0.9);
        Vec3 next = this.position().add(velocity);
        int gy = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(next.x), Mth.floor(next.z));
        if (next.y <= gy + 0.5 || crashTicks > 600) {
            Vec3 at = new Vec3(next.x, Math.max(gy, next.y) + 1, next.z);
            Blast.explode(this.level(), this, at, 6f, false, Level.ExplosionInteraction.NONE, Blast.FX_HEAVY, 3.4f);
            Blast.fx(this.level(), at.add(forward().scale(6)), Blast.FX_FIRE, 2.4f);
            Blast.shockwave(this.level(), at, 14, 30f, 6, this, null);
            this.level().playSound(null, at.x, at.y, at.z, Reg.sound("explosion_big"), SoundSource.NEUTRAL, 20f, 0.6f);
            VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
            this.discard();
            return;
        }
        this.setPos(next.x, next.y, next.z);
    }

    // ------------------------------------------------------------------------------------ client
    private void clientVisuals() {
        prevRoll = roll;
        prevPitchVis = pitchVis;
        float dyaw = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (isCrashing()) {
            roll += 1.2f;
            pitchVis = Math.min(38f, pitchVis + 0.25f);
        } else {
            roll += (Mth.clamp(dyaw * 13f, -32f, 32f) - roll) * 0.08f;
            pitchVis += (0f - pitchVis) * 0.1f;
        }
        int hits = getHits();
        if (hits <= 0 && !isCrashing()) return;
        // smoke (and fire when it is going down) from the engines that were hit
        for (int side = -1; side <= 1; side += 2) {
            if (hits < 3 && side > 0 && !isCrashing()) continue;
            Vec3 e = toWorld(8 + side * 3.0, MID_Y + 0.6, 12.2);
            int n = isCrashing() ? 4 : hits;
            for (int i = 0; i < n; i++) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, e.x + this.random.nextGaussian() * 0.4, e.y,
                        e.z + this.random.nextGaussian() * 0.4, 0, 0.04, 0);
            }
            if (isCrashing() || hits >= 4) {
                this.level().addParticle(Reg.FLAME.get(), e.x, e.y, e.z, 0, 0.02, 0);
                FxHooks.light.accept(this, 14);
            }
        }
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Started", started);
        tag.putBoolean("Leaving", leaving);
        tag.putInt("Bombs", bombs);
        tag.putInt("Hits", getHits());
        tag.putDouble("CruiseY", Double.isNaN(cruiseY) ? this.getY() : cruiseY);
        if (center != null) {
            tag.putDouble("CX", center.x);
            tag.putDouble("CY", center.y);
            tag.putDouble("CZ", center.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        started = tag.getBoolean("Started");
        leaving = tag.getBoolean("Leaving");
        bombs = tag.contains("Bombs") ? tag.getInt("Bombs") : BOMBS;
        this.entityData.set(DATA_HITS, (byte) tag.getInt("Hits"));
        if (tag.contains("CruiseY")) cruiseY = tag.getDouble("CruiseY");
        if (tag.contains("CX")) center = new Vec3(tag.getDouble("CX"), tag.getDouble("CY"), tag.getDouble("CZ"));
        if (started) {
            float yr = this.getYRot() * Mth.DEG_TO_RAD;
            velocity = new Vec3(-Mth.sin(yr) * SPEED, 0, Mth.cos(yr) * SPEED);
        }
    }
}
