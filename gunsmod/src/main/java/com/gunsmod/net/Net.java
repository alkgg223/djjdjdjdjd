package com.gunsmod.net;

import com.gunsmod.Reg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All custom packets. Server-side handling lives in {@link ServerNet}, client-side in ClientNet. */
public final class Net {
    private Net() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(Reg.id(name));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("3");
        r.playToServer(Shoot.TYPE, Shoot.CODEC, ServerNet::shoot);
        r.playToServer(Reload.TYPE, Reload.CODEC, ServerNet::reload);
        r.playToServer(Turret.TYPE, Turret.CODEC, ServerNet::turret);
        r.playToServer(VehicleAction.TYPE, VehicleAction.CODEC, ServerNet::vehicleAction);
        r.playToServer(LaunchAction.TYPE, LaunchAction.CODEC, ServerNet::launchAction);
        r.playToServer(VehicleFire.TYPE, VehicleFire.CODEC, ServerNet::vehicleFire);
        r.playToServer(TeamPick.TYPE, TeamPick.CODEC, ServerNet::teamPick);
        r.playToClient(ShotFx.TYPE, ShotFx.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.shotFx(p));
        r.playToClient(HitMarker.TYPE, HitMarker.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.hitMarker(p));
        r.playToClient(LaunchStatus.TYPE, LaunchStatus.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.launchStatus(p));
        r.playToClient(ExplosionFx.TYPE, ExplosionFx.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.explosion(p));
        r.playToClient(MuzzleFx.TYPE, MuzzleFx.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.muzzle(p));
        r.playToClient(BattleState.TYPE, BattleState.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.battleState(p));
    }

    // ================================================================================ client -> server
    /** a shot fired by the local player. The seed makes the spread identical on both sides. */
    public record Shoot(float yaw, float pitch, long seed, boolean aiming) implements CustomPacketPayload {
        public static final Type<Shoot> TYPE = payloadType("shoot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Shoot> CODEC = CustomPacketPayload.codec(Shoot::write, Shoot::new);

        Shoot(RegistryFriendlyByteBuf b) {
            this(b.readFloat(), b.readFloat(), b.readLong(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeFloat(yaw);
            b.writeFloat(pitch);
            b.writeLong(seed);
            b.writeBoolean(aiming);
        }

        @Override
        public Type<Shoot> type() {
            return TYPE;
        }
    }

    public record Reload() implements CustomPacketPayload {
        public static final Type<Reload> TYPE = payloadType("reload");
        public static final StreamCodec<RegistryFriendlyByteBuf, Reload> CODEC = StreamCodec.unit(new Reload());

        @Override
        public Type<Reload> type() {
            return TYPE;
        }
    }

    /** jeep gunner trigger state */
    public record Turret(boolean firing) implements CustomPacketPayload {
        public static final Type<Turret> TYPE = payloadType("turret");
        public static final StreamCodec<RegistryFriendlyByteBuf, Turret> CODEC = CustomPacketPayload.codec(Turret::write, Turret::new);

        Turret(RegistryFriendlyByteBuf b) {
            this(b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBoolean(firing);
        }

        @Override
        public Type<Turret> type() {
            return TYPE;
        }
    }

    /** tank / helicopter triggers: weapon 0 = main gun or rockets (attack key), 1 = machine gun or chin gun (use key) */
    public record VehicleFire(int weapon, boolean down) implements CustomPacketPayload {
        public static final Type<VehicleFire> TYPE = payloadType("vehicle_fire");
        public static final StreamCodec<RegistryFriendlyByteBuf, VehicleFire> CODEC = CustomPacketPayload.codec(VehicleFire::write, VehicleFire::new);

        VehicleFire(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(weapon);
            b.writeBoolean(down);
        }

        @Override
        public Type<VehicleFire> type() {
            return TYPE;
        }
    }

    /** 0 = switch seat, 1 = horn */
    public record VehicleAction(int action, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<VehicleAction> TYPE = payloadType("vehicle_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, VehicleAction> CODEC = CustomPacketPayload.codec(VehicleAction::write, VehicleAction::new);

        VehicleAction(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(action);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
        }

        @Override
        public Type<VehicleAction> type() {
            return TYPE;
        }
    }

    /**
     * missile launch station actions. remote = sent from a remote's screen (the stations are the remote's links), otherwise
     * from a station opened by hand. actions: 0 query, 1 set target, 2 arm, 3 launch, 4 disarm, 5 select (x = index),
     * 6 set the target of every station, 7 arm every station, 8 launch every armed station, 9 disarm all, 10 unlink,
     * 11 fire everything: arm and launch every loaded station that has a target, in a quick ripple.
     */
    public record LaunchAction(BlockPos station, int action, int x, int y, int z, boolean remote) implements CustomPacketPayload {
        public static final int QUERY = 0, TARGET = 1, ARM = 2, LAUNCH = 3, DISARM = 4, SELECT = 5, TARGET_ALL = 6, ARM_ALL = 7,
                LAUNCH_ALL = 8, DISARM_ALL = 9, UNLINK = 10, FIRE_ALL = 11;
        public static final Type<LaunchAction> TYPE = payloadType("launch_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, LaunchAction> CODEC = CustomPacketPayload.codec(LaunchAction::write, LaunchAction::new);

        LaunchAction(RegistryFriendlyByteBuf b) {
            this(b.readBlockPos(), b.readVarInt(), b.readInt(), b.readInt(), b.readInt(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBlockPos(station);
            b.writeVarInt(action);
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
            b.writeBoolean(remote);
        }

        @Override
        public Type<LaunchAction> type() {
            return TYPE;
        }
    }

    // ================================================================================ server -> client
    /** bullet/flame/rocket effects. kind: 0 miss, 1 block, 2 entity. gun = GunType ordinal, -1 = jeep turret */
    public record ShotFx(int shooter, int gun, Vec3 origin, List<Vec3> ends, byte[] kinds, boolean suppressed) implements CustomPacketPayload {
        public static final Type<ShotFx> TYPE = payloadType("shot_fx");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShotFx> CODEC = CustomPacketPayload.codec(ShotFx::write, ShotFx::new);

        ShotFx(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), readVec(b), readList(b), b.readByteArray(), b.readBoolean());
        }

        private static Vec3 readVec(FriendlyByteBuf b) {
            return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        }

        private static List<Vec3> readList(FriendlyByteBuf b) {
            int n = b.readVarInt();
            List<Vec3> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Vec3(b.readFloat(), b.readFloat(), b.readFloat()));
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(shooter);
            b.writeVarInt(gun);
            b.writeDouble(origin.x);
            b.writeDouble(origin.y);
            b.writeDouble(origin.z);
            b.writeVarInt(ends.size());
            // ends are already relative to the origin (every sender builds them that way)
            for (Vec3 v : ends) {
                b.writeFloat((float) v.x);
                b.writeFloat((float) v.y);
                b.writeFloat((float) v.z);
            }
            b.writeByteArray(kinds);
            b.writeBoolean(suppressed);
        }

        /** ends are sent relative to the origin for precision */
        public Vec3 end(int i) {
            return origin.add(ends.get(i));
        }

        @Override
        public Type<ShotFx> type() {
            return TYPE;
        }
    }

    /** the player picks a side on an orchard battlefield: 0 green, 1 red, -1 leaves the battle */
    public record TeamPick(int team) implements CustomPacketPayload {
        public static final Type<TeamPick> TYPE = payloadType("team_pick");
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamPick> CODEC = CustomPacketPayload.codec(TeamPick::write, TeamPick::new);

        TeamPick(RegistryFriendlyByteBuf b) {
            this(b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(team);
        }

        @Override
        public Type<TeamPick> type() {
            return TYPE;
        }
    }

    /**
     * The battle as the player's screen shows it, sent every second: both teams' kills, the player's side (-1 none),
     * whether this dimension has a battlefield and the player is on it, and whether to open the side picker.
     */
    public record BattleState(int green, int red, int team, boolean field, boolean inside, boolean openPicker) implements CustomPacketPayload {
        public static final Type<BattleState> TYPE = payloadType("battle_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, BattleState> CODEC = CustomPacketPayload.codec(BattleState::write, BattleState::new);

        BattleState(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(green);
            b.writeVarInt(red);
            b.writeVarInt(team);
            b.writeBoolean(field);
            b.writeBoolean(inside);
            b.writeBoolean(openPicker);
        }

        @Override
        public Type<BattleState> type() {
            return TYPE;
        }
    }

    public record HitMarker(boolean kill, boolean headshot) implements CustomPacketPayload {
        public static final Type<HitMarker> TYPE = payloadType("hit_marker");
        public static final StreamCodec<RegistryFriendlyByteBuf, HitMarker> CODEC = CustomPacketPayload.codec(HitMarker::write, HitMarker::new);

        HitMarker(RegistryFriendlyByteBuf b) {
            this(b.readBoolean(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBoolean(kill);
            b.writeBoolean(headshot);
        }

        @Override
        public Type<HitMarker> type() {
            return TYPE;
        }
    }

    /** one launch station as shown on the launch screen. missile: -1 none. state: 0 idle 1 armed 2 erecting 3 ignition 4 lowering */
    public record StationInfo(BlockPos pos, boolean found, boolean heavy, int missile, boolean hasTarget, int tx, int ty, int tz, int state) {
        public static StationInfo missing(BlockPos pos) {
            return new StationInfo(pos, false, false, -1, false, 0, 0, 0, 0);
        }

        StationInfo(RegistryFriendlyByteBuf b) {
            this(b.readBlockPos(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readInt(), b.readInt(), b.readInt(), b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeBoolean(found);
            b.writeBoolean(heavy);
            b.writeVarInt(missile);
            b.writeBoolean(hasTarget);
            b.writeInt(tx);
            b.writeInt(ty);
            b.writeInt(tz);
            b.writeVarInt(state);
        }
    }

    /** the stations of a remote (or the single station opened by hand) for the launch control screen */
    public record LaunchStatus(List<StationInfo> stations, int selected, boolean remote, boolean open) implements CustomPacketPayload {
        public static final Type<LaunchStatus> TYPE = payloadType("launch_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, LaunchStatus> CODEC = CustomPacketPayload.codec(LaunchStatus::write, LaunchStatus::new);

        LaunchStatus(RegistryFriendlyByteBuf b) {
            this(readStations(b), b.readVarInt(), b.readBoolean(), b.readBoolean());
        }

        private static List<StationInfo> readStations(RegistryFriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 256);
            List<StationInfo> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new StationInfo(b));
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(stations.size());
            for (StationInfo s : stations) s.write(b);
            b.writeVarInt(selected);
            b.writeBoolean(remote);
            b.writeBoolean(open);
        }

        public StationInfo current() {
            return stations.isEmpty() ? StationInfo.missing(BlockPos.ZERO) : stations.get(Math.floorMod(selected, stations.size()));
        }

        @Override
        public Type<LaunchStatus> type() {
            return TYPE;
        }
    }

    /** a big gun firing: muzzle fireball along the barrel, smoke ring and the dust it kicks up */
    public record MuzzleFx(Vec3 pos, Vec3 dir, float size, int entity) implements CustomPacketPayload {
        public static final Type<MuzzleFx> TYPE = payloadType("muzzle_fx");
        public static final StreamCodec<RegistryFriendlyByteBuf, MuzzleFx> CODEC = CustomPacketPayload.codec(MuzzleFx::write, MuzzleFx::new);

        MuzzleFx(RegistryFriendlyByteBuf b) {
            this(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeDouble(pos.x);
            b.writeDouble(pos.y);
            b.writeDouble(pos.z);
            b.writeFloat((float) dir.x);
            b.writeFloat((float) dir.y);
            b.writeFloat((float) dir.z);
            b.writeFloat(size);
            b.writeVarInt(entity);
        }

        @Override
        public Type<MuzzleFx> type() {
            return TYPE;
        }
    }

    /** big explosion effect (see Blast for the styles) */
    public record ExplosionFx(Vec3 pos, int style, float size) implements CustomPacketPayload {
        public static final Type<ExplosionFx> TYPE = payloadType("explosion_fx");
        public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionFx> CODEC = CustomPacketPayload.codec(ExplosionFx::write, ExplosionFx::new);

        ExplosionFx(RegistryFriendlyByteBuf b) {
            this(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readVarInt(), b.readFloat());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeDouble(pos.x);
            b.writeDouble(pos.y);
            b.writeDouble(pos.z);
            b.writeVarInt(style);
            b.writeFloat(size);
        }

        @Override
        public Type<ExplosionFx> type() {
            return TYPE;
        }
    }
}
