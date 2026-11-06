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
        PayloadRegistrar r = event.registrar("6");
        r.playToServer(Shoot.TYPE, Shoot.CODEC, ServerNet::shoot);
        r.playToServer(Reload.TYPE, Reload.CODEC, ServerNet::reload);
        r.playToServer(Turret.TYPE, Turret.CODEC, ServerNet::turret);
        r.playToServer(VehicleAction.TYPE, VehicleAction.CODEC, ServerNet::vehicleAction);
        r.playToServer(LaunchAction.TYPE, LaunchAction.CODEC, ServerNet::launchAction);
        r.playToServer(VehicleFire.TYPE, VehicleFire.CODEC, ServerNet::vehicleFire);
        r.playToServer(TeamPick.TYPE, TeamPick.CODEC, ServerNet::teamPick);
        r.playToServer(ConvoySave.TYPE, ConvoySave.CODEC, ServerNet::convoySave);
        r.playToServer(ProneState.TYPE, ProneState.CODEC, ServerNet::prone);
        r.playToServer(ConvoySpawn.TYPE, ConvoySpawn.CODEC, ServerNet::convoySpawn);
        r.playToServer(JetBank.TYPE, JetBank.CODEC, ServerNet::jetBank);
        r.playToServer(SquadOrder.TYPE, SquadOrder.CODEC, ServerNet::squadOrder);
        r.playToClient(SquadStatus.TYPE, SquadStatus.CODEC, (p, ctx) -> com.gunsmod.client.SquadHud.status(p));
        r.playToServer(RadarRequest.TYPE, RadarRequest.CODEC, ServerNet::radarRequest);
        r.playToServer(DroneControl.TYPE, DroneControl.CODEC, ServerNet::droneControl);
        r.playToServer(MatchControl.TYPE, MatchControl.CODEC, ServerNet::matchControl);
        r.playToServer(MapRequest.TYPE, MapRequest.CODEC, ServerNet::mapRequest);
        r.playToServer(SquadOrderAt.TYPE, SquadOrderAt.CODEC, ServerNet::squadOrderAt);
        r.playToClient(MatchState.TYPE, MatchState.CODEC, (p, ctx) -> com.gunsmod.client.MatchHud.state(p));
        r.playToClient(MapIntel.TYPE, MapIntel.CODEC, (p, ctx) -> com.gunsmod.client.screen.TacticalMapScreen.intel(p));
        r.playToServer(DroneAction.TYPE, DroneAction.CODEC, ServerNet::droneAction);
        r.playToClient(DronePilot.TYPE, DronePilot.CODEC, (p, ctx) -> com.gunsmod.client.DroneClient.pilot(p));
        r.playToClient(Spotted.TYPE, Spotted.CODEC, (p, ctx) -> com.gunsmod.client.DroneClient.spotted(p));
        r.playToClient(RadarPicture.TYPE, RadarPicture.CODEC, (p, ctx) -> com.gunsmod.client.screen.RadarScreen.picture(p));
        r.playToClient(ShotFx.TYPE, ShotFx.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.shotFx(p));
        r.playToClient(BulletPath.TYPE, BulletPath.CODEC, (p, ctx) -> com.gunsmod.client.ClientNet.bulletPath(p));
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
    /** the legs of a bullet's flight after a penetration or a ricochet: pairs of points (from, to) and what each ends on */
    public record BulletPath(List<Vec3> points, byte[] kinds) implements CustomPacketPayload {
        public static final Type<BulletPath> TYPE = payloadType("bullet_path");
        public static final StreamCodec<RegistryFriendlyByteBuf, BulletPath> CODEC = CustomPacketPayload.codec(BulletPath::write, BulletPath::new);

        BulletPath(RegistryFriendlyByteBuf b) {
            this(readPoints(b), b.readByteArray());
        }

        private static List<Vec3> readPoints(FriendlyByteBuf b) {
            int n = Math.min(64, b.readVarInt());
            List<Vec3> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(points.size());
            for (Vec3 v : points) {
                b.writeDouble(v.x);
                b.writeDouble(v.y);
                b.writeDouble(v.z);
            }
            b.writeByteArray(kinds);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

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

    /** the convoy planner screen: the side and the vehicles of the column, head first */
    public record ConvoySave(int team, int[] kinds) implements CustomPacketPayload {
        public static final Type<ConvoySave> TYPE = payloadType("convoy_save");
        public static final StreamCodec<RegistryFriendlyByteBuf, ConvoySave> CODEC = CustomPacketPayload.codec(ConvoySave::write, ConvoySave::new);

        ConvoySave(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), readInts(b));
        }

        private static int[] readInts(RegistryFriendlyByteBuf b) {
            int n = Math.min(16, Math.max(0, b.readVarInt()));
            int[] k = new int[n];
            for (int i = 0; i < n; i++) k[i] = b.readVarInt();
            return k;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(team);
            b.writeVarInt(kinds.length);
            for (int k : kinds) b.writeVarInt(k);
        }

        @Override
        public Type<ConvoySave> type() {
            return TYPE;
        }
    }

    /** the prone key: lie down or get up */
    public record ProneState(boolean down) implements CustomPacketPayload {
        public static final Type<ProneState> TYPE = payloadType("prone");
        public static final StreamCodec<RegistryFriendlyByteBuf, ProneState> CODEC = CustomPacketPayload.codec(ProneState::write, ProneState::new);

        ProneState(RegistryFriendlyByteBuf b) {
            this(b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBoolean(down);
        }

        @Override
        public Type<ProneState> type() {
            return TYPE;
        }
    }

    /** a player flying with the locked cockpit view: his bank and stick, for the others to see */
    public record JetBank(float bank, float stickX, float stickY, boolean on) implements CustomPacketPayload {
        public static final Type<JetBank> TYPE = payloadType("jet_bank");
        public static final StreamCodec<RegistryFriendlyByteBuf, JetBank> CODEC = CustomPacketPayload.codec(JetBank::write, JetBank::new);

        JetBank(RegistryFriendlyByteBuf b) {
            this(b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeFloat(bank);
            b.writeFloat(stickX);
            b.writeFloat(stickY);
            b.writeBoolean(on);
        }

        @Override
        public Type<JetBank> type() {
            return TYPE;
        }
    }

    /** an order from the squad screen (see entity.ai.Squad: the command ids) */
    public record SquadOrder(int command, int formation) implements CustomPacketPayload {
        public static final Type<SquadOrder> TYPE = payloadType("squad_order");
        public static final StreamCodec<RegistryFriendlyByteBuf, SquadOrder> CODEC = CustomPacketPayload.codec(SquadOrder::write, SquadOrder::new);

        SquadOrder(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(command);
            b.writeVarInt(formation);
        }

        @Override
        public Type<SquadOrder> type() {
            return TYPE;
        }
    }

    /** the camera link of a drone the player flies: state 0 starts it (with the link range), 1 landed, 2 destroyed, 3 link lost */
    public record DronePilot(int id, int state, float range) implements CustomPacketPayload {
        public static final Type<DronePilot> TYPE = payloadType("drone_pilot");
        public static final StreamCodec<RegistryFriendlyByteBuf, DronePilot> CODEC = CustomPacketPayload.codec(DronePilot::write, DronePilot::new);

        DronePilot(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), b.readFloat());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeVarInt(state);
            b.writeFloat(range);
        }

        @Override
        public Type<DronePilot> type() {
            return TYPE;
        }
    }

    /** the drone pilot's controls each tick: where the drone is (flown on the client) and where its camera looks */
    public record DroneControl(int id, double x, double y, double z, float yaw, float pitch) implements CustomPacketPayload {
        public static final Type<DroneControl> TYPE = payloadType("drone_control");
        public static final StreamCodec<RegistryFriendlyByteBuf, DroneControl> CODEC = CustomPacketPayload.codec(DroneControl::write, DroneControl::new);

        DroneControl(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeFloat(yaw);
            b.writeFloat(pitch);
        }

        @Override
        public Type<DroneControl> type() {
            return TYPE;
        }
    }

    /** a drone pilot's button (DroneEntity.A_*) */
    public record DroneAction(int id, int action) implements CustomPacketPayload {
        public static final Type<DroneAction> TYPE = payloadType("drone_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, DroneAction> CODEC = CustomPacketPayload.codec(DroneAction::write, DroneAction::new);

        DroneAction(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeVarInt(action);
        }

        @Override
        public Type<DroneAction> type() {
            return TYPE;
        }
    }

    /** the enemies the player's side has spotted (outlined for him) */
    public record Spotted(int[] ids) implements CustomPacketPayload {
        public static final Type<Spotted> TYPE = payloadType("spotted");
        public static final StreamCodec<RegistryFriendlyByteBuf, Spotted> CODEC = CustomPacketPayload.codec(Spotted::write, Spotted::new);

        Spotted(RegistryFriendlyByteBuf b) {
            this(b.readVarIntArray());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarIntArray(ids);
        }

        @Override
        public Type<Spotted> type() {
            return TYPE;
        }
    }

    /** one objective on the HUD / map. kind: ObjectiveBlock.Kind ordinal (5 = a convoy vehicle); team -1 neutral; progress:
     * a point's balance (-1 red .. +1 green) or a target's planting / defusing; flags: 1 flag home, 2 flag carried,
     * 4 flag dropped, 8 charge planted, 16 convoy vehicle arrived */
    public record ObjInfo(byte kind, byte team, float progress, byte label, float x, float y, float z, float hp, byte flags, int charge) {}

    /** a line of the side's mission: state 0 under way, 1 done, 2 failed */
    public record MissionLine(String key, String arg, byte state, boolean optional) {}

    /** the battle as one player sees it (and the table's settings when he opens the mission control table) */
    public record MatchState(int mode, boolean running, int timeLeft, int ticketsG, int ticketsR, int scoreG, int scoreR, int winner, int attackers, int limit,
                             int minutes, int radius, int myTeam, List<ObjInfo> objectives, List<MissionLine> lines, boolean open, BlockPos table, String banner)
            implements CustomPacketPayload {
        public static final Type<MatchState> TYPE = payloadType("match_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, MatchState> CODEC = CustomPacketPayload.codec(MatchState::write, MatchState::new);

        MatchState(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), readObjs(b), readLines(b), b.readBoolean(), b.readBlockPos(), b.readUtf(128));
        }

        private static List<ObjInfo> readObjs(FriendlyByteBuf b) {
            int n = Math.min(96, b.readVarInt());
            List<ObjInfo> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(new ObjInfo(b.readByte(), b.readByte(), b.readFloat(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(),
                        b.readVarInt()));
            }
            return l;
        }

        private static List<MissionLine> readLines(FriendlyByteBuf b) {
            int n = Math.min(12, b.readVarInt());
            List<MissionLine> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new MissionLine(b.readUtf(96), b.readUtf(32), b.readByte(), b.readBoolean()));
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(mode);
            b.writeBoolean(running);
            b.writeVarInt(timeLeft);
            b.writeVarInt(ticketsG);
            b.writeVarInt(ticketsR);
            b.writeVarInt(scoreG);
            b.writeVarInt(scoreR);
            b.writeVarInt(winner);
            b.writeVarInt(attackers);
            b.writeVarInt(limit);
            b.writeVarInt(minutes);
            b.writeVarInt(radius);
            b.writeVarInt(myTeam);
            int n = Math.min(96, objectives.size());
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                ObjInfo o = objectives.get(i);
                b.writeByte(o.kind());
                b.writeByte(o.team());
                b.writeFloat(o.progress());
                b.writeByte(o.label());
                b.writeFloat(o.x());
                b.writeFloat(o.y());
                b.writeFloat(o.z());
                b.writeFloat(o.hp());
                b.writeByte(o.flags());
                b.writeVarInt(o.charge());
            }
            int k = Math.min(12, lines.size());
            b.writeVarInt(k);
            for (int i = 0; i < k; i++) {
                MissionLine l = lines.get(i);
                b.writeUtf(l.key(), 96);
                b.writeUtf(l.arg(), 32);
                b.writeByte(l.state());
                b.writeBoolean(l.optional());
            }
            b.writeBoolean(open);
            b.writeBlockPos(table);
            b.writeUtf(banner, 128);
        }

        @Override
        public Type<MatchState> type() {
            return TYPE;
        }
    }

    /** the mission control table's buttons: start or stop the battle with these settings, or only save them */
    public record MatchControl(BlockPos pos, int action, int mode, int attackers, int minutes, int limit, int radius) implements CustomPacketPayload {
        public static final int START = 0, STOP = 1, SAVE = 2;
        public static final Type<MatchControl> TYPE = payloadType("match_control");
        public static final StreamCodec<RegistryFriendlyByteBuf, MatchControl> CODEC = CustomPacketPayload.codec(MatchControl::write, MatchControl::new);

        MatchControl(RegistryFriendlyByteBuf b) {
            this(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeVarInt(action);
            b.writeVarInt(mode);
            b.writeVarInt(attackers);
            b.writeVarInt(minutes);
            b.writeVarInt(limit);
            b.writeVarInt(radius);
        }

        @Override
        public Type<MatchControl> type() {
            return TYPE;
        }
    }

    /** the tactical map asks what the side knows round the player */
    public record MapRequest(int radius) implements CustomPacketPayload {
        public static final Type<MapRequest> TYPE = payloadType("map_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, MapRequest> CODEC = CustomPacketPayload.codec(MapRequest::write, MapRequest::new);

        MapRequest(RegistryFriendlyByteBuf b) {
            this(b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(radius);
        }

        @Override
        public Type<MapRequest> type() {
            return TYPE;
        }
    }

    /** one mark on the tactical map (see game.MapIntel for the kinds) */
    public record MapMark(byte kind, byte team, float x, float z, float yaw, byte label) {}

    public record MapIntel(List<MapMark> marks) implements CustomPacketPayload {
        public static final Type<MapIntel> TYPE = payloadType("map_intel");
        public static final StreamCodec<RegistryFriendlyByteBuf, MapIntel> CODEC = CustomPacketPayload.codec(MapIntel::write, MapIntel::new);

        MapIntel(RegistryFriendlyByteBuf b) {
            this(readMarks(b));
        }

        private static List<MapMark> readMarks(FriendlyByteBuf b) {
            int n = Math.min(600, b.readVarInt());
            List<MapMark> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new MapMark(b.readByte(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte()));
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            int n = Math.min(600, marks.size());
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                MapMark m = marks.get(i);
                b.writeByte(m.kind());
                b.writeByte(m.team());
                b.writeFloat(m.x());
                b.writeFloat(m.z());
                b.writeFloat(m.yaw());
                b.writeByte(m.label());
            }
        }

        @Override
        public Type<MapIntel> type() {
            return TYPE;
        }
    }

    /** a squad order given on the tactical map: at a place instead of where the player looks */
    public record SquadOrderAt(int command, int formation, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<SquadOrderAt> TYPE = payloadType("squad_order_at");
        public static final StreamCodec<RegistryFriendlyByteBuf, SquadOrderAt> CODEC = CustomPacketPayload.codec(SquadOrderAt::write, SquadOrderAt::new);

        SquadOrderAt(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(command);
            b.writeVarInt(formation);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
        }

        @Override
        public Type<SquadOrderAt> type() {
            return TYPE;
        }
    }

    /** the radar scope asks for a fresh picture of the radar at pos (sent while the scope is open) */
    public record RadarRequest(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RadarRequest> TYPE = payloadType("radar_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, RadarRequest> CODEC = CustomPacketPayload.codec(RadarRequest::write, RadarRequest::new);

        RadarRequest(RegistryFriendlyByteBuf b) {
            this(b.readBlockPos());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBlockPos(pos);
        }

        @Override
        public Type<RadarRequest> type() {
            return TYPE;
        }
    }

    /** one blip on a radar scope. iff: 0 friend, 1 hostile, 2 unknown. kind: 0 jet, 1 helicopter, 2 drone, 3 other, 4 our
     * launcher. flags: 1 seen by another radar of the network, 2 a launcher is engaging it */
    public record Blip(byte iff, byte kind, byte flags, float dx, float dz, float y, float heading, float speed) {}

    /** what a radar sees: its place, reach, sweep, side and the blips (positions relative to the radar) */
    public record RadarPicture(BlockPos pos, float range, int sweep, boolean sector, float facing, int team, List<Blip> blips)
            implements CustomPacketPayload {
        public static final Type<RadarPicture> TYPE = payloadType("radar_picture");
        public static final StreamCodec<RegistryFriendlyByteBuf, RadarPicture> CODEC = CustomPacketPayload.codec(RadarPicture::write, RadarPicture::new);

        RadarPicture(RegistryFriendlyByteBuf b) {
            this(b.readBlockPos(), b.readFloat(), b.readVarInt(), b.readBoolean(), b.readFloat(), b.readVarInt(), readBlips(b));
        }

        private static List<Blip> readBlips(FriendlyByteBuf b) {
            int n = Math.min(128, b.readVarInt());
            List<Blip> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(new Blip(b.readByte(), b.readByte(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));
            }
            return l;
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeFloat(range);
            b.writeVarInt(sweep);
            b.writeBoolean(sector);
            b.writeFloat(facing);
            b.writeVarInt(team);
            int n = Math.min(128, blips.size());
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Blip p = blips.get(i);
                b.writeByte(p.iff());
                b.writeByte(p.kind());
                b.writeByte(p.flags());
                b.writeFloat(p.dx());
                b.writeFloat(p.dz());
                b.writeFloat(p.y());
                b.writeFloat(p.heading());
                b.writeFloat(p.speed());
            }
        }

        @Override
        public Type<RadarPicture> type() {
            return TYPE;
        }
    }

    /** the leader's squad: how many, their average health, the current order and formation, hold fire */
    public record SquadStatus(int count, float health, int order, int formation, boolean holdFire) implements CustomPacketPayload {
        public static final Type<SquadStatus> TYPE = payloadType("squad_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, SquadStatus> CODEC = CustomPacketPayload.codec(SquadStatus::write, SquadStatus::new);

        SquadStatus(RegistryFriendlyByteBuf b) {
            this(b.readVarInt(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readBoolean());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(count);
            b.writeFloat(health);
            b.writeVarInt(order);
            b.writeVarInt(formation);
            b.writeBoolean(holdFire);
        }

        @Override
        public Type<SquadStatus> type() {
            return TYPE;
        }
    }

    /** attack with the convoy planner: roll the convoy out */
    public record ConvoySpawn(int hand) implements CustomPacketPayload {
        public static final Type<ConvoySpawn> TYPE = payloadType("convoy_spawn");
        public static final StreamCodec<RegistryFriendlyByteBuf, ConvoySpawn> CODEC = CustomPacketPayload.codec(ConvoySpawn::write, ConvoySpawn::new);

        ConvoySpawn(RegistryFriendlyByteBuf b) {
            this(b.readVarInt());
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(hand);
        }

        @Override
        public Type<ConvoySpawn> type() {
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
