package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.vehicle.VehicleEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A discrete driver action (lights, indicators, horn, engine...). */
public record VehicleControlPayload(int entityId, int action) implements CustomPacketPayload {
    public static final Type<VehicleControlPayload> TYPE = new Type<>(TrafficCraft.id("vehicle_control"));
    public static final StreamCodec<ByteBuf, VehicleControlPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, VehicleControlPayload::entityId,
            ByteBufCodecs.VAR_INT, VehicleControlPayload::action, VehicleControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleControlPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player.level().getEntity(p.entityId()) instanceof VehicleEntity v && player.getVehicle() == v) {
                boolean driver = v.getControllingPassenger() == player;
                if (driver || p.action() == VehicleEntity.A_INTERIOR) v.handleAction(player, p.action());
            }
        });
    }
}
