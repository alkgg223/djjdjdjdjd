package com.vehicleworks.weapon;

import com.vehicleworks.Config;
import com.vehicleworks.block.ComponentBlockEntity;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.item.AmmoItem;
import com.vehicleworks.registry.ModSounds;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleLayout;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime state of every weapon on a vehicle: magazines, reloading from ammo boxes, fire
 * channels assigned through Remote Control Units, master-arm interlock, turret aiming and
 * projectile launch. Server only.
 */
public final class WeaponSystem {
    public static final int FLAG_MOUNTED = 1, FLAG_LINKED = 2, FLAG_RELOADING = 4, FLAG_EMPTY = 8, FLAG_FIRING = 16, FLAG_TURRET = 32, FLAG_SAFETY = 64;

    private final VehicleEntity vehicle;
    private final List<Slot> slots = new ArrayList<>();
    private boolean anyRcu;
    private int prevFireBits;
    private float gunnerYaw, gunnerPitch;
    private long gunnerTick = -1000;
    private int fireFlash;

    public WeaponSystem(VehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    public static final class Slot {
        public final VehicleLayout.WeaponSlot def;
        public final WeaponSpec spec;
        public int ammo;
        public int reload;
        public int cooldown;
        public int channel = -1;
        public int mode;
        public boolean safety;
        public int burstLeft;
        public float turretYaw, turretPitch;
        public boolean firing;

        Slot(VehicleLayout.WeaponSlot def, WeaponSpec spec) {
            this.def = def;
            this.spec = spec;
        }
    }

    public List<Slot> slots() {
        return slots;
    }

    public void rebuild(VehicleLayout layout) {
        List<Slot> old = new ArrayList<>(slots);
        slots.clear();
        for (VehicleLayout.WeaponSlot w : layout.weapons) {
            WeaponSpec spec = WeaponSpec.of(w.type);
            if (spec == null) continue;
            Slot s = new Slot(w, spec);
            CompoundTag d = VehicleBuilder.data(vehicle.structure, w.pos);
            s.ammo = Math.min(spec.magazine, d.getInt("Mag"));
            CompoundTag c = VehicleBuilder.config(vehicle.structure, w.pos);
            s.mode = w.type.property("fire_mode").getInt(c);
            s.safety = w.type.property("safety").getBool(c);
            for (Slot o : old) if (o.def.pos == w.pos) { s.ammo = o.ammo; s.turretYaw = o.turretYaw; s.turretPitch = o.turretPitch; }
            slots.add(s);
        }
        // resolve remote control bindings
        anyRcu = !layout.rcus.isEmpty();
        for (long rcu : layout.rcus) {
            ListTag links = VehicleBuilder.data(vehicle.structure, rcu).getList("Links", Tag.TAG_COMPOUND);
            for (int i = 0; i < links.size(); i++) {
                CompoundTag l = links.getCompound(i);
                int[] off = l.getIntArray("Off");
                if (off.length != 3) continue;
                long target = BlockPos.asLong(BlockPos.getX(rcu) + off[0], BlockPos.getY(rcu) + off[1], BlockPos.getZ(rcu) + off[2]);
                for (Slot s : slots) if (s.def.pos == target) {
                    s.channel = Mth.clamp(l.getInt("Ch"), 0, VehicleControls.CHANNELS - 1);
                    if (l.contains("Mode")) s.mode = l.getInt("Mode");
                }
            }
        }
        if (!anyRcu) for (Slot s : slots) s.channel = s.spec.defaultChannel();
    }

    public void writeToStructure() {
        for (Slot s : slots) {
            CompoundTag be = vehicle.structure.blockEntity(s.def.pos);
            if (be == null) continue;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            d.putInt("Mag", s.ammo);
            be.put(ComponentBlockEntity.DATA, d);
        }
    }

    public void setAim(ServerPlayer player, float yaw, float pitch, int keys) {
        if (vehicle.isGunner(player)) {
            gunnerYaw = yaw;
            gunnerPitch = pitch;
            gunnerTick = vehicle.level().getGameTime();
        }
    }

    public void tick(int fireBits) {
        if (slots.isEmpty() || !(vehicle.level() instanceof ServerLevel level)) return;
        boolean enabled = Config.SERVER.weaponsEnabled.get();
        int rising = fireBits & ~prevFireBits;
        prevFireBits = fireBits;
        long now = level.getGameTime();
        boolean gunner = now - gunnerTick < 10;
        float aimYaw = gunner ? gunnerYaw : vehicle.controls.lookYaw;
        float aimPitch = gunner ? gunnerPitch : vehicle.controls.lookPitch;
        Vector3d aimWorld = dirFromAngles(aimYaw, aimPitch);
        for (Slot s : slots) {
            if (s.cooldown > 0) s.cooldown--;
            if (s.reload > 0) {
                if (--s.reload == 0) finishReload(s);
            } else if (s.ammo <= 0) {
                startReload(s, level);
            }
            if (s.def.turretBase != Long.MIN_VALUE) aimTurret(s, aimWorld);
            s.firing = false;
            if (!enabled || s.channel < 0 || s.safety || !s.def.mounted) continue;
            boolean held = (fireBits & (1 << s.channel)) != 0;
            boolean press = (rising & (1 << s.channel)) != 0;
            if (press && s.mode == 1) s.burstLeft = 3;
            boolean want = switch (s.mode) {
                case 1 -> s.burstLeft > 0;
                case 2 -> press;
                default -> held;
            };
            if (want && s.cooldown <= 0 && s.reload <= 0 && s.ammo > 0) {
                fire(s, level);
                if (s.mode == 1) s.burstLeft--;
            }
        }
        if (fireFlash > 0) fireFlash--;
    }

    private void aimTurret(Slot s, Vector3d aimWorld) {
        BlockState_ base = BlockState_.of(vehicle, s.def.turretBase);
        CompoundTag c = VehicleBuilder.config(vehicle.structure, s.def.turretBase);
        ComponentType tt = ComponentType.TURRET_BASE;
        double rate = Math.toRadians(tt.property("traverse_speed").get(c)) * 0.05;
        double maxEl = Math.toRadians(tt.property("max_elevation").get(c));
        double maxDep = Math.toRadians(tt.property("max_depression").get(c));
        // aim direction in vehicle-local space, relative to the weapon's rest direction
        Vector3d local = vehicle.worldDirToLocal(aimWorld.x, aimWorld.y, aimWorld.z, new Vector3d());
        Direction f = s.def.facing;
        Direction up = base.facing;
        Vector3d fw = new Vector3d(f.getStepX(), f.getStepY(), f.getStepZ());
        Vector3d uw = new Vector3d(up.getStepX(), up.getStepY(), up.getStepZ());
        if (Math.abs(fw.dot(uw)) > 0.9) uw.set(0, 1, 0);
        Vector3d rw = new Vector3d(fw).cross(uw);
        double ty = Math.atan2(local.dot(rw), local.dot(fw));
        double tp = Math.atan2(local.dot(uw), Math.sqrt(sq(local.dot(fw)) + sq(local.dot(rw))));
        tp = Mth.clamp(tp, -maxDep, maxEl);
        s.turretYaw = (float) approachAngle(s.turretYaw, ty, rate);
        s.turretPitch = (float) approach(s.turretPitch, tp, rate);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double approach(double v, double t, double d) {
        return v < t ? Math.min(t, v + d) : Math.max(t, v - d);
    }

    private static double approachAngle(double v, double t, double d) {
        double diff = Mth.wrapDegrees(Math.toDegrees(t - v));
        return v + Math.toRadians(Mth.clamp(diff, -Math.toDegrees(d), Math.toDegrees(d)));
    }

    /** World-space firing direction of a weapon (fixed or turret). */
    public Vector3d fireDirection(Slot s, Vector3d out) {
        Direction f = s.def.facing;
        out.set(f.getStepX(), f.getStepY(), f.getStepZ());
        if (s.def.turretBase != Long.MIN_VALUE) {
            Direction up = BlockState_.of(vehicle, s.def.turretBase).facing;
            Vector3d uw = new Vector3d(up.getStepX(), up.getStepY(), up.getStepZ());
            if (Math.abs(out.dot(uw)) > 0.9) uw.set(0, 1, 0);
            Vector3d rw = new Vector3d(out).cross(uw);
            Vector3d dir = new Vector3d(out).mul(Math.cos(s.turretYaw) * Math.cos(s.turretPitch))
                    .add(new Vector3d(rw).mul(Math.sin(s.turretYaw) * Math.cos(s.turretPitch)))
                    .add(new Vector3d(uw).mul(Math.sin(s.turretPitch)));
            out.set(dir);
        }
        return vehicle.localDirToWorld(out.x, out.y, out.z, out);
    }

    private void fire(Slot s, ServerLevel level) {
        WeaponSpec spec = s.spec;
        Vector3d dir = fireDirection(s, new Vector3d());
        Direction f = s.def.facing;
        long p = s.def.pos;
        Vector3d muzzle = vehicle.localToWorld(BlockPos.getX(p) + 0.5 + f.getStepX() * 0.7, BlockPos.getY(p) + 0.5 + f.getStepY() * 0.7,
                BlockPos.getZ(p) + 0.5 + f.getStepZ() * 0.7, new Vector3d());
        if (spec == WeaponSpec.BOMB_BAY) {
            dir.set(0, -1, 0);
            muzzle = vehicle.localToWorld(BlockPos.getX(p) + 0.5, BlockPos.getY(p) - 0.3, BlockPos.getZ(p) + 0.5, new Vector3d());
        }
        Vector3d base = vehicle.pointVelocity(muzzle.x, muzzle.y, muzzle.z, new Vector3d());
        double spread = Math.toRadians(spec.spreadDeg);
        RandomSourceHelper.jitter(dir, spread, level.random);
        Vector3d vel = new Vector3d(dir).mul(spec.muzzleVelocity).add(base);
        ProjectileEntity proj = new ProjectileEntity(level, ProjectileEntity.Kind.values()[spec.ordinal()], vehicle, muzzle, vel);
        proj.damage = spec.damage * Config.SERVER.weaponDamageScale.get().floatValue();
        proj.explosion = spec.explosionPower;
        if (spec == WeaponSpec.MISSILE_LAUNCHER) proj.setTarget(findTarget(level, muzzle, dir));
        Entity shooter = vehicle.pilot();
        if (shooter != null) proj.setOwner(shooter);
        level.addFreshEntity(proj);
        s.ammo--;
        s.cooldown = spec.interval;
        s.firing = true;
        fireFlash = 4;
        // recoil impulse on the vehicle
        var sim = vehicle.simulation();
        if (sim != null) {
            double recoil = switch (spec) {
                case MACHINE_GUN -> 25;
                case CANNON -> 1800;
                case MISSILE_LAUNCHER -> 120;
                case BOMB_BAY -> 0;
            };
            sim.body.applyImpulseAtPoint(-dir.x * recoil, -dir.y * recoil, -dir.z * recoil, muzzle.x, muzzle.y, muzzle.z);
            sim.body.wake();
        }
        var sound = switch (spec) {
            case MACHINE_GUN -> ModSounds.MG_FIRE.get();
            case CANNON -> ModSounds.CANNON_FIRE.get();
            case MISSILE_LAUNCHER -> ModSounds.MISSILE_LAUNCH.get();
            case BOMB_BAY -> ModSounds.BOMB_RELEASE.get();
        };
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, sound, SoundSource.NEUTRAL, spec == WeaponSpec.CANNON ? 4f : 2f,
                0.92f + level.random.nextFloat() * 0.16f);
        if (s.ammo <= 0) startReload(s, level);
    }

    private Entity findTarget(ServerLevel level, Vector3d from, Vector3d dir) {
        AABB box = new AABB(from.x, from.y, from.z, from.x, from.y, from.z).inflate(400);
        Entity best = null;
        double bestScore = Math.cos(Math.toRadians(14));
        for (Entity e : level.getEntities(vehicle, box, e -> (e instanceof LivingEntity || e instanceof VehicleEntity) && e.isAlive())) {
            if (e.getRootVehicle() == vehicle || e == vehicle) continue;
            Vector3d to = new Vector3d(e.getX() - from.x, e.getY() + e.getBbHeight() * 0.5 - from.y, e.getZ() - from.z);
            double d = to.length();
            if (d < 8 || d > 400) continue;
            double cos = to.div(d).dot(dir);
            double score = cos - d / 20000.0;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    private void startReload(Slot s, ServerLevel level) {
        if (s.reload > 0 || s.ammo >= s.spec.magazine) return;
        if (availableAmmo(s.spec.ammo) <= 0) return;
        s.reload = s.spec.reloadTicks;
        long p = s.def.pos;
        Vector3d w = vehicle.localToWorld(BlockPos.getX(p) + 0.5, BlockPos.getY(p) + 0.5, BlockPos.getZ(p) + 0.5, new Vector3d());
        level.playSound(null, w.x, w.y, w.z, ModSounds.RELOAD.get(), SoundSource.NEUTRAL, 1f, 1f);
    }

    private void finishReload(Slot s) {
        int need = s.spec.magazine - s.ammo;
        s.ammo += takeAmmo(s.spec.ammo, need);
    }

    /** Rounds of a given kind available in the vehicle's ammo boxes. */
    public int availableAmmo(AmmoItem.Kind kind) {
        VehicleLayout layout = vehicle.layout();
        if (layout == null) return 0;
        int n = 0;
        for (long box : layout.ammoBoxes) n += VehicleBuilder.data(vehicle.structure, box).getCompound("Ammo").getInt(kind.name());
        return n;
    }

    private int takeAmmo(AmmoItem.Kind kind, int amount) {
        VehicleLayout layout = vehicle.layout();
        if (layout == null) return 0;
        int got = 0;
        for (long box : layout.ammoBoxes) {
            CompoundTag be = vehicle.structure.blockEntity(box);
            if (be == null) continue;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            CompoundTag a = d.getCompound("Ammo");
            int have = a.getInt(kind.name());
            int take = Math.min(have, amount - got);
            a.putInt(kind.name(), have - take);
            d.put("Ammo", a);
            be.put(ComponentBlockEntity.DATA, d);
            got += take;
            if (got >= amount) break;
        }
        return got;
    }

    public void fillTelemetry(VehicleTelemetry t) {
        int n = slots.size();
        if (t.weaponAmmo.length != n) {
            t.weaponAmmo = new short[n]; t.weaponReserve = new short[n]; t.weaponChannel = new byte[n]; t.weaponFlags = new byte[n];
            t.weaponReload = new float[n]; t.turretYaw = new float[n]; t.turretPitch = new float[n];
        }
        for (int i = 0; i < n; i++) {
            Slot s = slots.get(i);
            t.weaponAmmo[i] = (short) s.ammo;
            t.weaponReserve[i] = (short) Math.min(Short.MAX_VALUE, availableAmmo(s.spec.ammo));
            t.weaponChannel[i] = (byte) s.channel;
            int fl = (s.def.mounted ? FLAG_MOUNTED : 0) | (s.channel >= 0 ? FLAG_LINKED : 0) | (s.reload > 0 ? FLAG_RELOADING : 0)
                    | (s.ammo <= 0 ? FLAG_EMPTY : 0) | (s.firing ? FLAG_FIRING : 0) | (s.def.turretBase != Long.MIN_VALUE ? FLAG_TURRET : 0)
                    | (s.safety ? FLAG_SAFETY : 0);
            t.weaponFlags[i] = (byte) fl;
            t.weaponReload[i] = s.reload > 0 ? 1f - (float) s.reload / s.spec.reloadTicks : 0f;
            t.turretYaw[i] = s.turretYaw;
            t.turretPitch[i] = s.turretPitch;
        }
        t.fireFlash = fireFlash;
    }

    static Vector3d dirFromAngles(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new Vector3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    /** Small helper to read a structure block's facing. */
    private record BlockState_(Direction facing) {
        static BlockState_ of(VehicleEntity v, long pos) {
            return new BlockState_(VehicleBuilder.facing(v.structure.get(pos)));
        }
    }
}
