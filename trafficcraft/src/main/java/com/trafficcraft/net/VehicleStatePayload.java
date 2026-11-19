package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.vehicle.VehicleEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Continuous driving state from the driver's client (for other players' visuals, sounds and server logic). */
public record VehicleStatePayload(int entityId, float speed, float steer, float rpm, float load, int gear, int bits) implements CustomPacketPayload {
    public static final Type<VehicleStatePayload> TYPE = new Type<>(TrafficCraft.id("vehicle_state"));
    public static final StreamCodec<ByteBuf, VehicleStatePayload> CODEC = new StreamCodec<>() {
        @Override
        public VehicleStatePayload decode(ByteBuf b) {
            return new VehicleStatePayload(ByteBufCodecs.VAR_INT.decode(b), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(),
                    ByteBufCodecs.VAR_INT.decode(b));
        }

        @Override
        public void encode(ByteBuf b, VehicleStatePayload p) {
            ByteBufCodecs.VAR_INT.encode(b, p.entityId);
            b.writeFloat(p.speed);
            b.writeFloat(p.steer);
            b.writeFloat(p.rpm);
            b.writeFloat(p.load);
            b.writeByte(p.gear);
            ByteBufCodecs.VAR_INT.encode(b, p.bits);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleStatePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().level().getEntity(p.entityId()) instanceof VehicleEntity v && v.getControllingPassenger() == ctx.player()) {
                float max = v.spec().maxSpeedKmh / 3.6f + 2;
                v.applyClientState(Math.max(-max, Math.min(max, p.speed())), p.steer(), p.rpm(), p.load(), p.gear(), p.bits());
            }
        });
    }
}
