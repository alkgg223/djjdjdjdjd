package com.gunsmod.block.airdef;

import com.gunsmod.Reg;
import com.gunsmod.entity.Aircraft;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.GuidedMissileEntity;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A surface-to-air launcher or anti-aircraft gun. It fires only at aircraft its linked radars are tracking: missiles
 * (each one finds its target with the type's accuracy, the rest lose it on the way) or bursts of shells (each shell a
 * roll of the accuracy; misses burst in the air near the target or come down on the ground and explode there).
 */
public class DefenseBlockEntity extends BlockEntity {
    public static final int MAX_LINKS = 4;
    public final LauncherType type;
    private BotTeam team = BotTeam.GREEN;
    private final List<BlockPos> links = new ArrayList<>();
    private int ammo, reloadTimer, cooldown, burstLeft, pause, scanIn, muzzleIdx;
    private int targetId = -1;
    private final Map<Integer, Long> engaged = new HashMap<>();
    // client: the turret's turn (world, degrees about +Y as the renderer applies it) and the arm's elevation
    public float aimYaw = Float.NaN, aimPitch, prevAimYaw, prevAimPitch;

    public DefenseBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.DEFENSE_BE.get(), pos, state);
        this.type = state.getBlock() instanceof AirDefenseBlock b && b.launcher != null ? b.launcher : LauncherType.ZU23;
        this.ammo = type.magazine;
        this.burstLeft = type.magazine;
    }

    public BotTeam team() {
        return team;
    }

    public void setTeam(BotTeam t) {
        team = t;
        links.clear();
        targetId = -1;
        sync();
    }

    public List<BlockPos> links() {
        return links;
    }

    public int ammo() {
        return ammo;
    }

    public int targetId() {
        return targetId;
    }

    /** link a radar (toggles: linking a linked radar again unlinks it); false when it is full */
    public boolean toggleLink(BlockPos radar) {
        if (links.remove(radar)) {
            sync();
            return true;
        }
        if (links.size() >= MAX_LINKS) return false;
        links.add(radar.immutable());
        sync();
        return true;
    }

    public void clearLinks() {
        links.clear();
        sync();
    }

    // ------------------------------------------------------------------------------------ geometry
    /** the renderer's turn of the model when it faces the block's way */
    public float baseAngle() {
        return 180f - getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot();
    }

    private static Vec3 rotY(Vec3 v, float deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    private static Vec3 rotX(Vec3 v, float deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    /** a point of the arm (model units) in the world for this turn (world angle) and elevation */
    public Vec3 armPoint(double[] p, float turn, float pitch) {
        AirDefenseGeo.Geo g = AirDefenseGeo.GEO.get(type.id);
        double[] h = g.head(), a = g.arm();
        Vec3 v = rotX(new Vec3(p[0] - a[0], p[1] - a[1], p[2] - a[2]), pitch).add(a[0] - h[0], a[1] - h[1], a[2] - h[2]);
        v = rotY(v, turn - baseAngle()).add(h[0] - 8, h[1], h[2] - 8);
        v = rotY(v, baseAngle()).scale(1 / 16.0);
        return Vec3.atBottomCenterOf(worldPosition).add(v);
    }

    public Vec3 headWorld() {
        AirDefenseGeo.Geo g = AirDefenseGeo.GEO.get(type.id);
        double[] h = g.head();
        Vec3 v = rotY(new Vec3(h[0] - 8, h[1], h[2] - 8), baseAngle()).scale(1 / 16.0);
        return Vec3.atBottomCenterOf(worldPosition).add(v);
    }

    /** turn (renderer angle) pointing the model's front along a direction */
    public static float turnTo(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, -d.z));
    }

    public static float pitchTo(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    /** where the arm points at rest and when engaging, for this kind of mount */
    public float idlePitch() {
        return switch (type.mode) {
            case VERTICAL -> 0f;
            case FIXED -> 50f;
            case MISSILE -> 20f;
            case GUN -> 8f;
        };
    }

    // ------------------------------------------------------------------------------------ tick
    public void tick() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }
        AirTracker.launcher(this, true);
        if (cooldown > 0) cooldown--;
        if (!type.gun() && ammo < type.magazine && ++reloadTimer >= type.reload) {
            reloadTimer = 0;
            ammo++;
            sync();
        }
        if (--scanIn <= 0) {
            scanIn = 5;
            VehicleEntity t = pickTarget();
            int id = t == null ? -1 : t.getId();
            if (id != targetId) {
                targetId = id;
                sync();
            }
        }
        if (targetId < 0) return;
        Entity e = level.getEntity(targetId);
        if (!(e instanceof VehicleEntity t) || !t.isAlive()) {
            targetId = -1;
            sync();
            return;
        }
        if (type.gun()) gun(t);
        else missile(t);
    }

    @Nullable
    private VehicleEntity pickTarget() {
        Vec3 head = headWorld();
        long now = level.getGameTime();
        engaged.values().removeIf(u -> u < now);
        VehicleEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = links.size() - 1; i >= 0; i--) {
            BlockPos rp = links.get(i);
            if (!level.isLoaded(rp)) continue;
            if (!(level.getBlockEntity(rp) instanceof RadarBlockEntity radar)) {
                links.remove(i);
                sync();
                continue;
            }
            if (radar.team() != team) continue;
            for (int id : radar.tracked()) {
                if (!type.gun() && engaged.containsKey(id)) continue;
                if (!(level.getEntity(id) instanceof VehicleEntity v) || !v.isAlive() || !(v instanceof Aircraft a) || !a.isAirborne()) continue;
                if (BotTeam.of(v) == team || !v.threat()) continue;
                Vec3 c = v.position().add(0, v.getBbHeight() * 0.5, 0);
                double d = c.distanceTo(head);
                if (d > type.range || d >= bestD) continue;
                if (level.clip(new ClipContext(head.add(0, 1.0, 0), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v)).getType() != HitResult.Type.MISS) continue;
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    private Vec3 centre(Entity t) {
        return t.position().add(0, t.getBbHeight() * 0.5, 0);
    }

    private void missile(VehicleEntity t) {
        if (ammo <= 0 || cooldown > 0) return;
        AirDefenseGeo.Geo g = AirDefenseGeo.GEO.get(type.id);
        if (g == null || g.muzzles().length == 0) return;
        Vec3 c = centre(t);
        Vec3 d = c.subtract(headWorld());
        float turn = type.mode == LauncherType.Mode.VERTICAL ? baseAngle() : turnTo(d);
        float pitch = switch (type.mode) {
            case VERTICAL -> 88f;
            case FIXED -> 50f;
            default -> Mth.clamp(pitchTo(d) + 8f, 10f, 70f);
        };
        double[] mz = g.muzzles()[muzzleIdx++ % g.muzzles().length];
        Vec3 at = armPoint(mz, turn, pitch);
        Vec3 dir = armPoint(new double[]{mz[0], mz[1], mz[2] - 16}, turn, pitch).subtract(at).normalize();
        GuidedMissileEntity m = new GuidedMissileEntity(level, null, GuidedMissileEntity.SAM).side(team).power(type.damage, 0f, 0f)
                .target(t).flight(type.missileSpeed, type.missileAgility);
        // the dice: this one will not get there (it loses the target on the way and flies off)
        if (level.random.nextFloat() >= type.accuracy) m.missAfter(12 + level.random.nextInt(40));
        m.setPos(at.x, at.y, at.z);
        m.setDeltaMovement(dir.scale(type.mode == LauncherType.Mode.VERTICAL ? 1.3 : 1.6));
        m.alignToMotion();
        level.addFreshEntity(m);
        level.playSound(null, at.x, at.y, at.z, Reg.sound(type.sound), SoundSource.BLOCKS, 10f, 0.95f + level.random.nextFloat() * 0.1f);
        if (level instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, com.gunsmod.gun.GunLogic.SHOT_RANGE, new Net.MuzzleFx(at, dir.scale(-1), 1.3f, -1 - worldPosition.hashCode()));
        }
        ammo--;
        cooldown = type.interval;
        engaged.put(t.getId(), level.getGameTime() + type.interval * 2L + 20);
        sync();
    }

    private void gun(VehicleEntity t) {
        if (pause > 0) {
            pause--;
            return;
        }
        if (cooldown > 0) return;
        AirDefenseGeo.Geo g = AirDefenseGeo.GEO.get(type.id);
        if (g == null || g.muzzles().length == 0) return;
        cooldown = type.interval;
        Vec3 c = centre(t);
        Vec3 d = c.subtract(headWorld());
        float turn = turnTo(d), pitch = Mth.clamp(pitchTo(d), -5f, 85f);
        List<Vec3> ends = new ArrayList<>();
        byte[] kinds = new byte[g.muzzles().length];
        Vec3 origin = null;
        for (int i = 0; i < g.muzzles().length; i++) {
            Vec3 at = armPoint(g.muzzles()[i], turn, pitch);
            if (origin == null) origin = at;
            Vec3 end;
            byte kind;
            if (level.random.nextFloat() < type.accuracy) {
                end = c.add(level.random.nextGaussian() * 0.6, level.random.nextGaussian() * 0.4, level.random.nextGaussian() * 0.6);
                kind = 2;
                t.invulnerableTime = 0;
                t.hurt(level.damageSources().explosion(null, null), type.damage);
                t.invulnerableTime = 0;
            } else if (level.random.nextFloat() < 0.65f) {
                // a near miss: the shell bursts in the air beside it
                Vec3 off = new Vec3(level.random.nextGaussian(), level.random.nextGaussian() * 0.6, level.random.nextGaussian()).normalize()
                        .scale(3 + level.random.nextDouble() * 5);
                end = c.add(off);
                kind = 0;
            } else {
                // a wide miss: the shell carries on and comes down to burst on the ground
                Vec3 dir = c.subtract(at).normalize().add(level.random.nextGaussian() * 0.06, -0.05 - level.random.nextDouble() * 0.15,
                        level.random.nextGaussian() * 0.06).normalize();
                HitResult hr = level.clip(new ClipContext(at, at.add(dir.scale(type.range * 1.8)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, t));
                end = hr.getLocation();
                kind = (byte) (hr.getType() == HitResult.Type.MISS ? 0 : 1);
            }
            ends.add(end.subtract(origin));
            kinds[i] = kind;
        }
        if (t instanceof JetEntity jet) jet.underFire(null);
        if (level instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, origin.x, origin.y, origin.z, com.gunsmod.gun.GunLogic.SHOT_RANGE, new Net.ShotFx(-1, type.fx, origin, ends, kinds, false));
        }
        if (--burstLeft <= 0) {
            burstLeft = type.magazine;
            pause = type.reload;
        }
    }

    // ------------------------------------------------------------------------------------ client
    private void clientTick() {
        float base = baseAngle();
        if (Float.isNaN(aimYaw)) {
            aimYaw = base;
            aimPitch = idlePitch();
        }
        prevAimYaw = aimYaw;
        prevAimPitch = aimPitch;
        Entity t = targetId >= 0 ? level.getEntity(targetId) : null;
        float wantYaw = base + Mth.sin(level.getGameTime() * 0.012f + worldPosition.hashCode()) * 35f, wantPitch = idlePitch();
        if (t != null) {
            Vec3 d = centre(t).subtract(headWorld());
            wantYaw = turnTo(d);
            wantPitch = switch (type.mode) {
                case VERTICAL -> 88f;
                case FIXED -> 50f;
                case MISSILE -> Mth.clamp(pitchTo(d) + 8f, 10f, 70f);
                case GUN -> Mth.clamp(pitchTo(d), -5f, 85f);
            };
        }
        if (type.mode == LauncherType.Mode.VERTICAL) wantYaw = base;
        float rate = type.gun() ? 7f : 3f;
        aimYaw += Mth.clamp(Mth.wrapDegrees(wantYaw - aimYaw), -rate, rate);
        aimPitch += Mth.clamp(wantPitch - aimPitch, -rate * 0.6f, rate * 0.6f);
    }

    @Override
    public void setRemoved() {
        AirTracker.launcher(this, false);
        super.setRemoved();
    }

    // ------------------------------------------------------------------------------------ save / sync
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Team", team.ordinal());
        tag.putInt("Ammo", ammo);
        tag.putInt("Target", targetId);
        int[] l = new int[links.size() * 3];
        for (int i = 0; i < links.size(); i++) {
            BlockPos p = links.get(i);
            l[i * 3] = p.getX();
            l[i * 3 + 1] = p.getY();
            l[i * 3 + 2] = p.getZ();
        }
        tag.putIntArray("Links", l);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        team = BotTeam.byId(tag.getInt("Team"));
        if (tag.contains("Ammo")) ammo = tag.getInt("Ammo");
        targetId = tag.contains("Target") ? tag.getInt("Target") : -1;
        links.clear();
        int[] l = tag.getIntArray("Links");
        for (int i = 0; i + 2 < l.length; i += 3) links.add(new BlockPos(l[i], l[i + 1], l[i + 2]));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, reg);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
