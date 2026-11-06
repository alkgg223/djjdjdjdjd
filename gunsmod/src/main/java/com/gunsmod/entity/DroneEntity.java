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
    /** FPV and SHAHED as before; 3.0: RECON quad (camera, spotting), BOMBER hexacopter (drops bombs), SURVEIL
     * surveillance plane (orbits and spots), LANCET loitering munition (cruises, loiters, dives on a target).
     * Append only: the ordinal is saved. */
    public enum Kind {
        FPV, SHAHED, RECON, BOMBER, SURVEIL, LANCET;

        /** flies like a multirotor (hovers, strafes) rather than a plane */
        public boolean quad() {
            return this == FPV || this == RECON || this == BOMBER;
        }

        /** comes back and lands to be used again */
        public boolean reusable() {
            return this == RECON || this == BOMBER || this == SURVEIL;
        }

        public boolean explosive() {
            return this == FPV || this == SHAHED || this == LANCET;
        }

        /** battery (fuel) in ticks */
        public int battery() {
            return switch (this) {
                case FPV -> 1500;
                case SHAHED -> 6000;
                case RECON -> 7200;
                case BOMBER -> 6000;
                case SURVEIL -> 14400;
                case LANCET -> 3600;
            };
        }

        /** radio range of the control link (blocks) */
        public double link() {
            return switch (this) {
                case FPV -> 260;
                case SHAHED -> 0;
                case RECON -> 320;
                case BOMBER -> 260;
                case SURVEIL -> 600;
                case LANCET -> 420;
            };
        }

        /** top speed, blocks a tick */
        public double maxSpeed() {
            return switch (this) {
                case FPV -> 1.6;
                case SHAHED -> 1.05;
                case RECON -> 0.95;
                case BOMBER -> 0.6;
                case SURVEIL -> 0.75;
                case LANCET -> 1.15;
            };
        }

        public float health() {
            return switch (this) {
                case FPV -> 4f;
                case SHAHED -> 18f;
                case RECON -> 6f;
                case BOMBER -> 10f;
                case SURVEIL -> 12f;
                case LANCET -> 5f;
            };
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** what the controls of the local pilot say this tick (client) */
    public record PilotInput(float forward, float strafe, boolean up, boolean down, boolean boost, float yaw, float pitch) {}

    /** flight modes: AUTO flies itself (bot operators, a locked Lancet, the surveillance orbit), MANUAL is flown by its
     * player from the camera, HOME returns to the operator and lands */
    public static final byte AUTO = 0, MANUAL = 1, HOME = 2;
    /** the pilot's buttons (Net.DroneAction) */
    public static final int A_PRIMARY = 0, A_SECONDARY = 1, A_HOME = 2, A_EXIT = 3;
    public static final int BOMBS = 4;

    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_BATTERY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_BOMBS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final TicketType<ChunkPos> TICKET = TicketType.create("gunsmod_drone", Comparator.comparingLong(ChunkPos::toLong), 30);
    @Nullable private UUID operator;
    @Nullable private Vec3 target;
    private int targetId = -1, life;
    private boolean gone;
    public Vec3 velocity = Vec3.ZERO;
    // 3.0: where it was launched (home when the operator is gone), the battery, the pilot's last controls (server)
    @Nullable private Vec3 home;
    private int battery = -1, dropCooldown, scanIn;
    private boolean falling;
    @Nullable private Vec3 pilotPos;
    private float pilotYaw, pilotPitch;
    private long lastControl;
    /** client: this drone is flown from this game (its position comes from the local controls, not the server) */
    public boolean localPilot;
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
        d.setHealth(kind.health());
        d.home = from;
        d.battery = kind.battery();
        d.entityData.set(DATA_BATTERY, d.battery);
        d.entityData.set(DATA_BOMBS, (byte) (kind == Kind.BOMBER ? BOMBS : 0));
        Vec3 to = d.target == null ? new Vec3(0, 0, 1) : d.target.subtract(from).multiply(1, 0, 1).normalize();
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        d.setYRot(yaw);
        d.velocity = kind == Kind.SHAHED ? to.scale(0.6).add(0, 0.9, 0) : kind == Kind.SURVEIL || kind == Kind.LANCET ? to.scale(0.5).add(0, 0.45, 0)
                : new Vec3(0, 0.35, 0);
        level.addFreshEntity(d);
        level.playSound(null, from.x, from.y, from.z, Reg.sound(kind == Kind.SHAHED ? "shahed_launch" : "ui_confirm"), SoundSource.NEUTRAL,
                kind == Kind.SHAHED ? 6f : 1f, 1f);
        return d;
    }

    /** a drone its player flies from its camera */
    public static DroneEntity launchManual(net.minecraft.server.level.ServerPlayer p, Kind kind, Vec3 from) {
        BotTeam team = BotTeam.of(p);
        Vec3 look = p.getViewVector(1f);
        Vec3 ahead = p.position().add(look.multiply(1, 0, 1).normalize().scale(40));
        DroneEntity d = launch(p.level(), kind, team == null ? BotTeam.GREEN : team, from, null, kind == Kind.SURVEIL ? ahead : null, p.getUUID());
        d.setMode(MANUAL);
        d.lastControl = p.level().getGameTime() + 40;
        d.pilotYaw = p.getYRot();
        d.pilotPitch = p.getXRot();
        d.setYRot(p.getYRot());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.gunsmod.net.Net.DronePilot(d.getId(), LINK_START, (float) d.linkRange()));
        return d;
    }

    public byte mode() {
        return this.entityData.get(DATA_MODE);
    }

    public void setMode(byte m) {
        this.entityData.set(DATA_MODE, m);
    }

    /** battery left, 0..1 (synced) */
    public float batteryLeft() {
        return Mth.clamp(this.entityData.get(DATA_BATTERY) / (float) kind().battery(), 0f, 1f);
    }

    public int batteryTicks() {
        return this.entityData.get(DATA_BATTERY);
    }

    public int bombs() {
        return this.entityData.get(DATA_BOMBS);
    }

    @Nullable
    public UUID operator() {
        return operator;
    }

    /** how far the control link reaches: the type's radio range, but no further than the server sends the world to its
     * player (the camera would look into nothing) */
    public double linkRange() {
        double r = kind().link();
        if (this.level() instanceof ServerLevel sl && operator != null && sl.getPlayerByUUID(operator) != null) {
            r = Math.min(r, Math.max(48, sl.getServer().getPlayerList().getViewDistance() * 16.0 - 16));
        }
        return r;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, (byte) 0);
        b.define(DATA_MODE, AUTO);
        b.define(DATA_BATTERY, 0);
        b.define(DATA_BOMBS, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_KIND.equals(key)) this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (kind()) {
            case FPV -> EntityDimensions.scalable(0.9f, 0.45f);
            case SHAHED -> EntityDimensions.scalable(2.0f, 0.7f);
            case RECON -> EntityDimensions.scalable(0.8f, 0.4f);
            case BOMBER -> EntityDimensions.scalable(1.5f, 0.6f);
            case SURVEIL -> EntityDimensions.scalable(2.4f, 0.6f);
            case LANCET -> EntityDimensions.scalable(1.1f, 0.5f);
        };
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
        return new ItemStack(itemOf(kind()));
    }

    public static net.minecraft.world.item.Item itemOf(Kind k) {
        return switch (k) {
            case FPV -> Reg.FPV_DRONE.get();
            case SHAHED -> Reg.SHAHED.get();
            case RECON -> Reg.RECON_DRONE.get();
            case BOMBER -> Reg.BOMBER_DRONE.get();
            case SURVEIL -> Reg.SURVEIL_DRONE.get();
            case LANCET -> Reg.LANCET.get();
        };
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
        tellPilot(LINK_DESTROYED);
        Vec3 c = this.position();
        if (kind().explosive() && this.random.nextInt(3) == 0) Blast.explode(this.level(), this, c, fpv() ? 1.6f : 2.6f, false, Level.ExplosionInteraction.NONE, Blast.FX_SMALL, 0.7f);
        else Blast.fx(this.level(), c, Blast.FX_SMALL, 0.5f);
        this.discard();
    }

    /** the camera link states sent to the player flying it */
    public static final int LINK_START = 0, LINK_LANDED = 1, LINK_DESTROYED = 2, LINK_LOST = 3;

    /** tell the player flying it what became of the picture */
    private void tellPilot(int state) {
        if (operator != null && this.level() instanceof ServerLevel sl && sl.getPlayerByUUID(operator) instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.gunsmod.net.Net.DronePilot(this.getId(), state, 0f));
        }
    }

    // ------------------------------------------------------------------------------------ flight
    @Override
    public void tick() {
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        Kind k = kind();
        if (this.level().isClientSide) {
            rotorO = rotor;
            rotor += k.quad() ? (k == Kind.BOMBER ? 48f : 70f) : 55f;
            prevVisPitch = visPitch;
            prevVisRoll = visRoll;
            Vec3 m = this.position().subtract(this.xo, this.yo, this.zo);
            float pitchWant = (float) (Mth.atan2(m.y, m.horizontalDistance()) * Mth.RAD_TO_DEG);
            if (k.quad()) pitchWant = (float) -m.horizontalDistance() * (k == Kind.FPV ? 22f : 30f) + (k == Kind.FPV && m.y < -0.3 ? -40f : 0f);
            visPitch += (pitchWant - visPitch) * 0.3f;
            float dyaw = Mth.wrapDegrees(this.getYRot() - this.yRotO);
            visRoll += (Mth.clamp(dyaw * 6f, -45f, 45f) - visRoll) * 0.25f;
            if (k == Kind.SHAHED && life++ < 40) {
                this.level().addParticle(Reg.FLAME.get(), getX(), getY(), getZ(), 0, -0.05, 0);
                this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        life++;
        if (battery < 0) battery = k.battery() - life;
        if (battery > 0) battery--;
        if (life % 10 == 0 || battery == 0) this.entityData.set(DATA_BATTERY, battery);
        if (dropCooldown > 0) dropCooldown--;
        if (k != Kind.FPV && this.level() instanceof ServerLevel sl && this.tickCount % 5 == 0) {
            ChunkPos here = this.chunkPosition();
            sl.getChunkSource().addRegionTicket(TICKET, here, 3, here);
            ChunkPos ahead = new ChunkPos(BlockPos.containing(this.position().add(velocity.scale(24))));
            sl.getChunkSource().addRegionTicket(TICKET, ahead, 3, ahead);
        }
        byte mode = mode();
        if (mode == MANUAL) {
            // the pilot is gone, too far, or the controls stopped coming: the link is lost
            if (!pilotLinkOk() || this.level().getGameTime() - lastControl > 40) linkLost();
        }
        if (k.reusable() && mode() != HOME && !falling && battery < k.battery() * 0.12) {
            setMode(HOME);
            tellOperator("gunsmod.msg.drone_low_battery");
        }
        mode = mode();
        if (falling || battery <= 0 && k != Kind.FPV && k != Kind.SHAHED) {
            velocity = velocity.scale(0.95).add(0, -0.07, 0);
        } else if (mode == MANUAL && k != Kind.SURVEIL) {
            velocity = pilotPos == null ? Vec3.ZERO : pilotPos.subtract(this.position());
            pilotPos = null;
            if (k.quad()) this.setYRot(pilotYaw);
        } else if (mode == HOME) {
            homeFlight();
        } else {
            switch (k) {
                case FPV -> fpvFlight();
                case SHAHED -> shahedFlight();
                case RECON -> hoverFlight();
                case BOMBER -> bomberFlight();
                case SURVEIL -> orbitFlight();
                case LANCET -> lancetFlight();
            }
        }
        if (this.isRemoved()) return;
        if ((k == Kind.SURVEIL || k == Kind.RECON && mode != MANUAL) && --scanIn <= 0) {
            scanIn = 20;
            autoSpot(k == Kind.SURVEIL ? 80 : 60);
        }
        Vec3 next = this.position().add(velocity);
        HitResult hr = this.level().clip(new ClipContext(this.position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (!k.explosive()) {
            // the reusable drones bump into things: a hard hit breaks them, a soft one stops them; on the ground they land
            if (hr.getType() != HitResult.Type.MISS && mode == MANUAL && !falling && battery > 0) {
                // flown by hand: the pilot's own picture (which collides) decides; a graze here only holds it back
                velocity = Vec3.ZERO;
                next = this.position();
            } else if (hr.getType() != HitResult.Type.MISS) {
                double speed = velocity.length();
                if (speed > 0.75 || falling && speed > 0.45) {
                    shotDown();
                    return;
                }
                if (mode == HOME || falling || battery <= 0 || velocity.y < -0.05 && Math.abs(velocity.x) + Math.abs(velocity.z) < 0.2) {
                    land();
                    return;
                }
                velocity = Vec3.ZERO;
                next = this.position();
            }
            if (k == Kind.SURVEIL || mode != MANUAL) {
                float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90f;
                if (velocity.horizontalDistanceSqr() > 1e-3) this.setYRot(yaw);
            }
            this.setPos(next.x, next.y, next.z);
            return;
        }
        // anything solid on the way, or an enemy close by: the warhead goes off
        if (hr.getType() != HitResult.Type.MISS) {
            detonate(hr.getLocation());
            return;
        }
        for (Entity e : this.level().getEntities(this, this.getBoundingBox().expandTowards(velocity).inflate(k == Kind.FPV ? 1.2 : k == Kind.LANCET ? 1.4 : 2.0),
                e -> e instanceof LivingEntity le && le.isAlive() && !(e instanceof DroneEntity) && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam())) {
            detonate(e.position().add(0, e.getBbHeight() * 0.5, 0));
            return;
        }
        // whatever it was sent after (a mob, a player's pick): close enough is a hit
        Entity c = chased();
        if (c != null && c.getBoundingBox().inflate(k == Kind.SHAHED ? 1.8 : 1.0).intersects(this.getBoundingBox().expandTowards(velocity))) {
            detonate(c.position().add(0, c.getBbHeight() * 0.5, 0));
            return;
        }
        if (!(mode == MANUAL && k.quad())) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90f;
            if (velocity.horizontalDistanceSqr() > 1e-4) this.setYRot(yaw);
        }
        this.setPos(next.x, next.y, next.z);
    }

    private void tellOperator(String key) {
        if (operator != null && this.level() instanceof ServerLevel sl && sl.getPlayerByUUID(operator) instanceof Player p) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(key).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
    }

    @Nullable
    private Entity operatorEntity() {
        if (operator == null || !(this.level() instanceof ServerLevel sl)) return null;
        Entity op = sl.getEntity(operator);
        return op != null && op.isAlive() ? op : null;
    }

    private boolean pilotLinkOk() {
        Entity op = operatorEntity();
        return op != null && op.distanceToSqr(this) < linkRange() * linkRange();
    }

    /** the control link is gone: the reusable drones fly home by themselves, the explosive ones drop */
    private void linkLost() {
        tellPilot(LINK_LOST);
        if (kind().reusable()) {
            setMode(HOME);
        } else {
            setMode(AUTO);
            falling = true;
        }
    }

    /** ground height under a point */
    private int ground(double x, double z) {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    /** back to the operator (or where it was launched): over the trees, then straight down onto him */
    private void homeFlight() {
        Entity op = operatorEntity();
        Vec3 to3 = op != null ? op.position().add(0, op.getBbHeight() + 0.6, 0) : home != null ? home : this.position();
        Vec3 to = to3.subtract(this.position());
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        double sp = kind().maxSpeed() * 0.75;
        Vec3 want;
        if (horiz > 3.0) {
            double cruise = Math.max(ground(getX(), getZ()) + 10, to3.y + 6);
            Vec3 flat = new Vec3(to.x, 0, to.z).normalize().scale(Math.min(sp, horiz * 0.1 + 0.2));
            want = flat.add(0, Mth.clamp((cruise - getY()) * 0.1, -0.35, 0.35), 0);
        } else {
            want = new Vec3(to.x * 0.2, Mth.clamp(to.y * 0.15, -0.3, 0.1), to.z * 0.2);
            if (to.lengthSqr() < 2.5 * 2.5) {
                land();
                return;
            }
        }
        velocity = velocity.add(want.subtract(velocity).scale(kind().quad() ? 0.15 : 0.07));
    }

    /** down safely: back into the operator's pack (or on the ground as an item) */
    private void land() {
        if (this.isRemoved() || gone) return;
        gone = true;
        tellPilot(LINK_LANDED);
        ItemStack st = new ItemStack(itemOf(kind()));
        Entity op = operatorEntity();
        if (op instanceof Player p && p.distanceToSqr(this) < 8 * 8 && p.getInventory().add(st)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("gunsmod.msg.drone_recovered").withStyle(net.minecraft.ChatFormatting.GREEN), true);
        } else if (op instanceof BotEntity bot && bot.distanceToSqr(this) < 8 * 8) {
            if (kind() == Kind.RECON) bot.reconDrones++;
            else if (kind() == Kind.BOMBER) bot.bomberDrones++;
        } else if (!st.isEmpty()) {
            this.spawnAtLocation(st);
        }
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("ui_confirm"), SoundSource.NEUTRAL, 0.8f, 0.8f);
        this.discard();
    }

    /** the recon quad of a soldier: up over the place it was sent to, and hanging there looking down */
    private void hoverFlight() {
        Vec3 c = target != null ? target : home != null ? home : this.position();
        double alt = Math.max(ground(c.x, c.z) + 26, c.y + 22);
        double a = life * 0.012;
        Vec3 spot = new Vec3(c.x + Math.cos(a) * 7, alt, c.z + Math.sin(a) * 7);
        Vec3 to = spot.subtract(this.position());
        double sp = kind().maxSpeed() * 0.7;
        Vec3 want = to.length() > sp * 8 ? to.normalize().scale(sp) : to.scale(0.12);
        velocity = velocity.add(want.subtract(velocity).scale(0.12));
    }

    /** the bomber of a soldier: over the enemy at bombing height, a bomb on whoever is right below, home when empty */
    private void bomberFlight() {
        Entity t = chased();
        if (t == null && target != null && this.tickCount % 20 == 0) {
            LivingEntity n = nearestEnemy(target, 24);
            if (n != null) targetId = n.getId();
            t = chased();
        }
        if (t != null) target = t.position();
        if (bombs() <= 0 || target == null) {
            setMode(HOME);
            return;
        }
        double alt = Math.max(ground(target.x, target.z) + 18, target.y + 16);
        Vec3 lead = t != null ? target.add(t.getDeltaMovement().scale(20)) : target;
        Vec3 over = new Vec3(lead.x, alt, lead.z);
        Vec3 to = over.subtract(this.position());
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        double sp = kind().maxSpeed() * 0.8;
        Vec3 want = horiz > 6 ? new Vec3(to.x, 0, to.z).normalize().scale(sp).add(0, Mth.clamp(to.y * 0.1, -0.3, 0.3), 0) : to.scale(0.14);
        velocity = velocity.add(want.subtract(velocity).scale(0.1));
        if (horiz < 1.2 && velocity.horizontalDistance() < 0.15 && dropCooldown <= 0) {
            dropBomb();
            targetId = -1;
        }
    }

    @Nullable
    private LivingEntity nearestEnemy(Vec3 around, double r) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(around, around).inflate(r, 16, r),
                e -> e.isAlive() && !(e instanceof DroneEntity) && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam())) {
            double d = e.position().distanceToSqr(around) - (e instanceof VehicleEntity || e instanceof JeepEntity ? 200 : 0);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** the surveillance plane: a wide circle round its point, high up, its camera on the middle */
    private void orbitFlight() {
        Vec3 c = target != null ? target : home != null ? home : this.position();
        double alt = Math.max(ground(c.x, c.z) + 55, c.y + 50);
        double R = 42;
        Vec3 r = new Vec3(getX() - c.x, 0, getZ() - c.z);
        double d = r.length();
        Vec3 radial = d < 1e-3 ? new Vec3(1, 0, 0) : r.scale(1 / d);
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
        Vec3 dir = tangent.subtract(radial.scale(Mth.clamp((d - R) * 0.05, -0.9, 0.9))).normalize();
        double sp = kind().maxSpeed();
        Vec3 want = dir.scale(sp).add(0, Mth.clamp((alt - getY()) * 0.03, -0.2, 0.2), 0);
        velocity = velocity.add(want.subtract(velocity).scale(life < 40 ? 0.03 : 0.05));
    }

    /** the Lancet: out to its target at height, loitering when it has none (and looking for an enemy vehicle under it),
     * then the terminal dive */
    private void lancetFlight() {
        Entity t = chased();
        if (t != null) target = t.position().add(0, t.getBbHeight() * 0.5, 0);
        double sp = kind().maxSpeed();
        if (target == null) {
            // nothing to go for: circle round where it was sent from, looking for an enemy vehicle below
            Vec3 c = home != null ? home : this.position();
            if (this.tickCount % 20 == 0) {
                for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(90, 60, 90),
                        e -> e.isAlive() && (e instanceof VehicleEntity v && !(v instanceof DroneEntity) && v.isManned() && !(v instanceof Aircraft a && a.isAirborne())
                                || e instanceof JeepEntity j && !j.getPassengers().isEmpty()) && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam())) {
                    targetId = e.getId();
                    target = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    return;
                }
            }
            double R = 34;
            Vec3 r = new Vec3(getX() - c.x, 0, getZ() - c.z);
            double d = Math.max(1e-3, r.length());
            Vec3 radial = r.scale(1 / d), tangent = new Vec3(-radial.z, 0, radial.x);
            Vec3 dir = tangent.subtract(radial.scale(Mth.clamp((d - R) * 0.05, -0.9, 0.9))).normalize();
            double alt = Math.max(ground(c.x, c.z) + 35, c.y + 30);
            Vec3 want = dir.scale(sp * 0.8).add(0, Mth.clamp((alt - getY()) * 0.04, -0.25, 0.25), 0);
            velocity = velocity.add(want.subtract(velocity).scale(0.06));
            return;
        }
        Vec3 to = target.subtract(this.position());
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        Vec3 want;
        if (horiz > 45) {
            double cruise = Math.max(ground(getX(), getZ()), ground(target.x, target.z)) + 30;
            want = new Vec3(to.x, 0, to.z).normalize().scale(sp).add(0, Mth.clamp((cruise - getY()) * 0.04, -0.25, 0.25), 0);
            velocity = velocity.add(want.subtract(velocity).scale(0.08));
        } else {
            // terminal dive, leading a moving target
            Vec3 lead = t != null ? target.add(t.getDeltaMovement().scale(horiz / 2.0)) : target;
            want = lead.subtract(this.position()).normalize().scale(2.0);
            velocity = velocity.add(want.subtract(velocity).scale(0.18));
        }
    }

    /** recon and surveillance: every enemy in sight of the camera below is reported to the side */
    private void autoSpot(double r) {
        Vec3 eye = this.position();
        Vec3 c = new Vec3(getX(), ground(getX(), getZ()), getZ());
        int checks = 0;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 30, r),
                e -> e.isAlive() && !(e instanceof DroneEntity) && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam())) {
            if (com.gunsmod.entity.ai.Spotting.spotted(this.level(), getBotTeam(), e) || checks++ > 12) continue;
            Vec3 at = e.getEyePosition();
            if (this.level().clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            com.gunsmod.entity.ai.Spotting.spot(this.level(), getBotTeam(), e, 160);
        }
    }

    // ------------------------------------------------------------------------------------ the pilot (player)
    /** client: one tick of flight from the local pilot's controls (the server checks and copies the result) */
    public void pilotStep(PilotInput in) {
        Kind k = kind();
        boolean dead = batteryTicks() <= 0;
        double yaw = in.yaw() * Mth.DEG_TO_RAD;
        if (k.quad()) {
            double sp = k.maxSpeed() * (in.boost() ? 1.0 : 0.62);
            double f = in.forward(), l = in.strafe();
            double hx = (l * Math.cos(yaw) - f * Math.sin(yaw)) * sp, hz = (f * Math.cos(yaw) + l * Math.sin(yaw)) * sp;
            double climb = (k == Kind.FPV ? 0.6 : k == Kind.BOMBER ? 0.3 : 0.42);
            double vy = in.up() ? climb : in.down() ? -climb : 0;
            Vec3 want = new Vec3(hx, vy, hz);
            if (dead) want = new Vec3(velocity.x * 0.9, -1.2, velocity.z * 0.9);
            double acc = k == Kind.BOMBER ? 0.09 : k == Kind.FPV ? 0.2 : 0.14;
            velocity = velocity.add(want.subtract(velocity).scale(acc));
            // a little drift in the wind while it hovers
            double t = this.tickCount * 0.05;
            velocity = velocity.add(Math.sin(t * 1.3) * 0.002, Math.sin(t * 2.1) * 0.0015, Math.cos(t * 0.9) * 0.002);
            this.setYRot(in.yaw());
        } else {
            // the Lancet flies where its camera looks, at its cruise speed, turning no faster than it can
            Vec3 look = Vec3.directionFromRotation(in.pitch(), in.yaw());
            double sp = k.maxSpeed() * (in.boost() ? 1.3 : in.down() ? 0.75 : 1.0);
            Vec3 cur = velocity.lengthSqr() < 1e-4 ? look : velocity.normalize();
            Vec3 dir = cur.add(look.subtract(cur).scale(0.08)).normalize();
            velocity = dir.scale(sp);
            if (dead) velocity = velocity.add(0, -0.1, 0);
            float vyaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90f;
            this.setYRot(vyaw);
        }
        if (k.explosive()) {
            this.setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        } else {
            this.noPhysics = false;
            this.move(net.minecraft.world.entity.MoverType.SELF, velocity);
            this.noPhysics = true;
            if (this.horizontalCollision) velocity = new Vec3(velocity.x * 0.2, velocity.y, velocity.z * 0.2);
            if (this.verticalCollision) velocity = new Vec3(velocity.x * 0.5, 0, velocity.z * 0.5);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (localPilot) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return localPilot || super.isControlledByLocalInstance();
    }

    /** server: the pilot's controls for this tick (its position, checked against what the drone can do) */
    public void control(net.minecraft.server.level.ServerPlayer sp, double x, double y, double z, float yaw, float pitch) {
        if (operator == null || !operator.equals(sp.getUUID()) || !Double.isFinite(x + y + z) || !Float.isFinite(yaw + pitch)) return;
        lastControl = this.level().getGameTime();
        pilotYaw = yaw;
        pilotPitch = Mth.clamp(pitch, -90f, 90f);
        if (mode() != MANUAL || kind() == Kind.SURVEIL) return;
        Vec3 d = new Vec3(x, y, z).subtract(this.position());
        double max = kind().maxSpeed() * 1.4 + 0.5;
        if (d.length() > max) d = d.normalize().scale(max);
        pilotPos = this.position().add(d);
    }

    private Vec3 pilotLook() {
        return Vec3.directionFromRotation(pilotPitch, pilotYaw);
    }

    /** server: a button the pilot pressed */
    public void action(net.minecraft.server.level.ServerPlayer sp, int a) {
        if (operator == null || !operator.equals(sp.getUUID()) || gone) return;
        Kind k = kind();
        Vec3 eye = this.position().add(0, this.getBbHeight() * 0.5, 0);
        switch (a) {
            case A_PRIMARY -> {
                if (mode() != MANUAL) return;
                switch (k) {
                    case BOMBER -> {
                        if (bombs() > 0 && dropCooldown <= 0) dropBomb();
                    }
                    case FPV -> detonate(this.position());
                    case LANCET -> {
                        com.gunsmod.gun.GunLogic.Trace tr = com.gunsmod.gun.GunLogic.trace(this.level(), this, eye, pilotLook(), 420);
                        targetId = tr.entity() instanceof LivingEntity le && le.isAlive() ? le.getId() : -1;
                        target = tr.end();
                        setMode(AUTO);
                        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("gunsmod.msg.lancet_locked", Mth.floor(eye.distanceTo(target)))
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                    default -> spotAt(sp, eye);
                }
            }
            case A_SECONDARY -> {
                if (k == Kind.SURVEIL) {
                    com.gunsmod.gun.GunLogic.Trace tr = com.gunsmod.gun.GunLogic.trace(this.level(), this, eye, pilotLook(), 600);
                    target = tr.end();
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("gunsmod.msg.drone_orbit", Mth.floor(target.x), Mth.floor(target.z)), true);
                } else {
                    spotAt(sp, eye);
                }
            }
            case A_HOME -> {
                if (!k.reusable()) return;
                setMode(mode() == HOME ? MANUAL : HOME);
                lastControl = this.level().getGameTime();
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(mode() == HOME ? "gunsmod.msg.drone_home" : "gunsmod.msg.drone_manual"), true);
            }
            case A_EXIT -> {
                if (k.reusable()) {
                    setMode(HOME);
                } else if (mode() == MANUAL) {
                    // an FPV or a Lancet let go of: it blows itself up where it is
                    detonate(this.position());
                    return;
                }
            }
            default -> { }
        }
    }

    /** mark what is under the crosshair (and whoever is close to it) for the side */
    private void spotAt(net.minecraft.server.level.ServerPlayer sp, Vec3 eye) {
        com.gunsmod.gun.GunLogic.Trace tr = com.gunsmod.gun.GunLogic.trace(this.level(), this, eye, pilotLook(), 360);
        int n = 0;
        if (tr.entity() instanceof LivingEntity le && BotTeam.of(le) != null && BotTeam.of(le) != getBotTeam()) {
            com.gunsmod.entity.ai.Spotting.spot(this.level(), getBotTeam(), le, 600);
            n++;
        }
        Vec3 at = tr.end();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6),
                e -> e.isAlive() && e != tr.entity() && !(e instanceof DroneEntity) && BotTeam.of(e) != null && BotTeam.of(e) != getBotTeam())) {
            com.gunsmod.entity.ai.Spotting.spot(this.level(), getBotTeam(), e, 600);
            n++;
        }
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(n > 0 ? "gunsmod.msg.drone_spotted" : "gunsmod.msg.drone_nothing", n,
                Mth.floor(eye.distanceTo(at))).withStyle(n > 0 ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY), true);
    }

    /** the bomber lets one go: it falls with the drone's speed and goes off where it lands */
    private void dropBomb() {
        int b = bombs();
        if (b <= 0) return;
        this.entityData.set(DATA_BOMBS, (byte) (b - 1));
        dropCooldown = 16;
        GrenadeEntity g = GrenadeEntity.bomb(this.level(), this, this.position().add(0, -0.4, 0), velocity.add(0, -0.15, 0));
        this.level().addFreshEntity(g);
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("drone_drop"), SoundSource.NEUTRAL, 1.2f, 1f);
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
        tellPilot(LINK_DESTROYED);
        Level level = this.level();
        if (kind() == Kind.LANCET) {
            // shaped charge: kills armour it lands on, a burst for soldiers round it
            Blast.explode(level, this, at, 3.6f, false, Level.ExplosionInteraction.NONE, Blast.FX_SMALL, 1.1f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5), e -> e instanceof VehicleEntity || e instanceof JeepEntity)) {
                if (e == this) continue;
                e.invulnerableTime = 0;
                e.hurt(this.damageSources().explosion(this, null), 85f);
                e.invulnerableTime = 0;
            }
            this.discard();
            return;
        }
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
        return d < (kind().quad() ? 160 * 160 : 320 * 320);
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
        tag.putByte("Mode", mode());
        tag.putInt("Battery", battery);
        tag.putByte("Bombs", (byte) bombs());
        tag.putBoolean("Falling", falling);
        if (home != null) {
            tag.putDouble("HX", home.x);
            tag.putDouble("HY", home.y);
            tag.putDouble("HZ", home.z);
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
        // a drone flown by hand when the world was saved comes back flying itself home
        byte m = tag.getByte("Mode");
        setMode(m == MANUAL ? (kind().reusable() ? HOME : AUTO) : m);
        if (m == MANUAL && !kind().reusable()) falling = true;
        battery = tag.contains("Battery") ? tag.getInt("Battery") : -1;
        this.entityData.set(DATA_BATTERY, Math.max(0, battery));
        this.entityData.set(DATA_BOMBS, tag.getByte("Bombs"));
        falling |= tag.getBoolean("Falling");
        if (tag.contains("HX")) home = new Vec3(tag.getDouble("HX"), tag.getDouble("HY"), tag.getDouble("HZ"));
    }
}
