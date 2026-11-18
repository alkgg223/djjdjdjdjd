package com.vehicleworks.network;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.vehicle.VehicleEntity;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/** All VehicleWorks network messages. Physics is server authoritative; clients send raw inputs. */
public final class Payloads {
    private Payloads() {}

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, path);
    }

    // ================================================================== server → client

    public record State(int id, long tick, double x, double y, double z, float qx, float qy, float qz, float qw,
                        float vx, float vy, float vz, float wx, float wy, float wz, float cx, float cy, float cz) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Payloads.rl("state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.ofMember(State::write, State::read);

        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(id); b.writeVarLong(tick);
            b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
            b.writeFloat(qx); b.writeFloat(qy); b.writeFloat(qz); b.writeFloat(qw);
            b.writeFloat(vx); b.writeFloat(vy); b.writeFloat(vz);
            b.writeFloat(wx); b.writeFloat(wy); b.writeFloat(wz);
            b.writeFloat(cx); b.writeFloat(cy); b.writeFloat(cz);
        }

        static State read(RegistryFriendlyByteBuf b) {
            return new State(b.readVarInt(), b.readVarLong(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Telemetry(int id, byte[] data) implements CustomPacketPayload {
        public static final Type<Telemetry> TYPE = new Type<>(Payloads.rl("telemetry"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Telemetry> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeByteArray(p.data); }, b -> new Telemetry(b.readVarInt(), b.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Structure changes: data = [count, (key, stateId, hasTag, tag)…, damageCount, (key, dmg)…]. */
    public record Blocks(int id, byte[] data) implements CustomPacketPayload {
        public static final Type<Blocks> TYPE = new Type<>(Payloads.rl("blocks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Blocks> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeByteArray(p.data); }, b -> new Blocks(b.readVarInt(), b.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Seats(int id, int[] passengers, long[] seats) implements CustomPacketPayload {
        public static final Type<Seats> TYPE = new Type<>(Payloads.rl("seats"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Seats> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeVarIntArray(p.passengers); b.writeLongArray(p.seats); },
                b -> new Seats(b.readVarInt(), b.readVarIntArray(), b.readLongArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Passenger seat states (recline, leg rest, tray, screen, light, door), see CabinSeat. */
    public record Cabin(int id, long[] keys, int[] states) implements CustomPacketPayload {
        public static final Type<Cabin> TYPE = new Type<>(Payloads.rl("cabin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cabin> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeLongArray(p.keys); b.writeVarIntArray(p.states); },
                b -> new Cabin(b.readVarInt(), b.readLongArray(), b.readVarIntArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Opens a client screen. screen: 0 component config, 1 engineering, 2 remote control unit. */
    public record OpenScreen(int screen, int vehicleId, long pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = new Type<>(Payloads.rl("open_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreen> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.screen); b.writeVarInt(p.vehicleId); b.writeLong(p.pos); b.writeNbt(p.data); },
                b -> new OpenScreen(b.readVarInt(), b.readVarInt(), b.readLong(), b.readNbt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Info(int vehicleId, CompoundTag info) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(Payloads.rl("info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Info> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.vehicleId); b.writeNbt(p.info); }, b -> new Info(b.readVarInt(), b.readNbt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================================== client → server

    public record Input(int id, int keys, float yaw, float pitch) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(Payloads.rl("input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeVarInt(p.keys); b.writeFloat(p.yaw); b.writeFloat(p.pitch); },
                b -> new Input(b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Action(int id, int action, int arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Payloads.rl("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeVarInt(p.action); b.writeVarInt(p.arg); },
                b -> new Action(b.readVarInt(), b.readVarInt(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Component configuration change. vehicleId < 0 means a block in the world at {@code pos}. */
    public record Configure(int vehicleId, long pos, boolean isData, CompoundTag tag) implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(Payloads.rl("configure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.vehicleId); b.writeLong(p.pos); b.writeBoolean(p.isData); b.writeNbt(p.tag); },
                b -> new Configure(b.readVarInt(), b.readLong(), b.readBoolean(), b.readNbt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Block interaction on a vehicle. action: 0 use, 1 attack (break), 2 pick. */
    public record Interact(int id, long block, int face, float hx, float hy, float hz, int action, boolean mainHand) implements CustomPacketPayload {
        public static final Type<Interact> TYPE = new Type<>(Payloads.rl("interact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Interact> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.id); b.writeLong(p.block); b.writeByte(p.face); b.writeFloat(p.hx); b.writeFloat(p.hy); b.writeFloat(p.hz); b.writeByte(p.action); b.writeBoolean(p.mainHand); },
                b -> new Interact(b.readVarInt(), b.readLong(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Action on a component block in the world (assemble from a core). */
    public record WorldAction(long pos, int action) implements CustomPacketPayload {
        public static final Type<WorldAction> TYPE = new Type<>(Payloads.rl("world_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldAction> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeLong(p.pos); b.writeVarInt(p.action); }, b -> new WorldAction(b.readLong(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================================== registration

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> VehicleWorks.PROXY.onState(p));
        r.playToClient(Telemetry.TYPE, Telemetry.CODEC, (p, ctx) -> VehicleWorks.PROXY.onTelemetry(p));
        r.playToClient(Blocks.TYPE, Blocks.CODEC, (p, ctx) -> VehicleWorks.PROXY.onBlocks(p));
        r.playToClient(Seats.TYPE, Seats.CODEC, (p, ctx) -> VehicleWorks.PROXY.onSeats(p));
        r.playToClient(Cabin.TYPE, Cabin.CODEC, (p, ctx) -> VehicleWorks.PROXY.onCabin(p));
        r.playToClient(OpenScreen.TYPE, OpenScreen.CODEC, (p, ctx) -> VehicleWorks.PROXY.onOpenScreen(p));
        r.playToClient(Info.TYPE, Info.CODEC, (p, ctx) -> VehicleWorks.PROXY.onInfo(p));
        r.playToServer(Input.TYPE, Input.CODEC, (p, ctx) -> ServerActions.onInput((ServerPlayer) ctx.player(), p));
        r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ServerActions.onAction((ServerPlayer) ctx.player(), p));
        r.playToServer(Configure.TYPE, Configure.CODEC, (p, ctx) -> ServerActions.onConfigure((ServerPlayer) ctx.player(), p));
        r.playToServer(Interact.TYPE, Interact.CODEC, (p, ctx) -> ServerActions.onInteract((ServerPlayer) ctx.player(), p));
        r.playToServer(WorldAction.TYPE, WorldAction.CODEC, (p, ctx) -> ServerActions.onWorldAction((ServerPlayer) ctx.player(), p));
    }

    // ================================================================== send helpers (server)

    public static void sendState(VehicleEntity v, long tick) {
        var o = v.orientation;
        State s = new State(v.getId(), tick, v.getX(), v.getY(), v.getZ(), (float) o.x, (float) o.y, (float) o.z, (float) o.w,
                (float) v.linearVelocity.x, (float) v.linearVelocity.y, (float) v.linearVelocity.z,
                (float) v.angularVelocity.x, (float) v.angularVelocity.y, (float) v.angularVelocity.z,
                (float) v.comLocal.x, (float) v.comLocal.y, (float) v.comLocal.z);
        PacketDistributor.sendToPlayersTrackingEntity(v, s);
    }

    public static void sendTelemetry(VehicleEntity v) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(256));
        v.telemetry.write(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        PacketDistributor.sendToPlayersTrackingEntity(v, new Telemetry(v.getId(), data));
    }

    public static void sendBlockChanges(VehicleEntity v, LongArrayList changed, boolean damage) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(256));
        buf.writeVarInt(changed.size());
        for (long k : changed) {
            buf.writeLong(k);
            var st = v.structure.get(k);
            buf.writeVarInt(Block.getId(st));
            CompoundTag be = v.structure.blockEntity(k);
            buf.writeBoolean(be != null);
            if (be != null) buf.writeNbt(com.vehicleworks.vehicle.VehicleStructure.clientView(be));
        }
        var dm = v.structure.damageMap();
        buf.writeVarInt(dm.size());
        for (var e : dm.long2FloatEntrySet()) {
            buf.writeLong(e.getLongKey());
            buf.writeFloat(e.getFloatValue());
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        PacketDistributor.sendToPlayersTrackingEntity(v, new Blocks(v.getId(), data));
    }

    public static void sendSeats(VehicleEntity v) {
        int n = v.getPassengers().size();
        int[] ids = new int[n];
        long[] seats = new long[n];
        int i = 0;
        for (Entity e : v.getPassengers()) {
            Long s = v.seats.get(e.getUUID());
            ids[i] = e.getId();
            seats[i] = s == null ? Long.MIN_VALUE : s;
            i++;
        }
        PacketDistributor.sendToPlayersTrackingEntity(v, new Seats(v.getId(), ids, seats));
    }

    /** to all trackers, or to one player (null = all) */
    public static void sendCabin(VehicleEntity v, ServerPlayer only) {
        Cabin c = new Cabin(v.getId(), v.cabin.keySet().toLongArray(), v.cabin.values().toIntArray());
        if (only != null) PacketDistributor.sendToPlayer(only, c);
        else PacketDistributor.sendToPlayersTrackingEntity(v, c);
    }

    public static void toPlayer(ServerPlayer p, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(p, payload);
    }

    // ================================================================== client send helpers

    public static void toServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static final int ACT_ENGINES_START = 1, ACT_ENGINES_STOP = 2, ACT_ENGINE_TOGGLE = 3, ACT_GEAR = 4, ACT_FLAPS = 5,
            ACT_ARM = 6, ACT_ASSIST = 7, ACT_MOUSE_AIM = 8, ACT_PARKING = 9, ACT_DISASSEMBLE = 10, ACT_REPAIR = 11,
            ACT_PROFILE = 12, ACT_OPEN_ENGINEERING = 13, ACT_REQUEST_INFO = 14, ACT_THROTTLE_SET = 15, ACT_AFTERBURNER = 16,
            ACT_FLAPS_SET = 17, ACT_REFUEL = 18, ACT_AUTOPILOT = 19, ACT_BATTERY = 20, ACT_LIGHTS = 21, ACT_EMERGENCY_TEST = 22, ACT_SWITCH = 23, ACT_REFUEL_START = 24, ACT_REFUEL_STOP = 25, ACT_CABIN = 26;
    /** Action arg marking a press on the cockpit panel (allowed from outside the pilot seat) */
    public static final int ARG_PANEL = 7777;
    public static final int WACT_ASSEMBLE = 1, WACT_ROTATE_WING = 2;

    public static UUID none() {
        return null;
    }

    public static long pos(BlockPos p) {
        return p.asLong();
    }
}
