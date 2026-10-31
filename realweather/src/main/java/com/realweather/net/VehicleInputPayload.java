package com.realweather.net;

import com.realweather.RealWeather;
import com.realweather.vehicle.StormVehicle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the driver's pedals, steering and buttons (wipers, horn, spikes, lights). */
public record VehicleInputPayload(int entity, float throttle, float steer, boolean handbrake, int actions) implements CustomPacketPayload {
    public static final Type<VehicleInputPayload> TYPE = new Type<>(RealWeather.id("vehicle_input"));
    public static final StreamCodec<FriendlyByteBuf, VehicleInputPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entity);
                buf.writeFloat(p.throttle);
                buf.writeFloat(p.steer);
                buf.writeBoolean(p.handbrake);
                buf.writeByte(p.actions);
            },
            buf -> new VehicleInputPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleInputPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Entity e = ctx.player().level().getEntity(p.entity);
            if (e instanceof StormVehicle v && ctx.player().getVehicle() == v) {
                v.applyInput(ctx.player(), p.throttle, p.steer, p.handbrake, p.actions);
            }
        });
    }
}
