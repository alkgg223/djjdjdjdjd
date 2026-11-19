package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.road.RoadEditing;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Edit operation from an editor screen (node/edge settings, tool settings, light commands...). */
public record RoadEditPayload(String op, CompoundTag data) implements CustomPacketPayload {
    public static final Type<RoadEditPayload> TYPE = new Type<>(TrafficCraft.id("road_edit"));
    public static final StreamCodec<ByteBuf, RoadEditPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RoadEditPayload::op,
            ByteBufCodecs.COMPOUND_TAG, RoadEditPayload::data, RoadEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RoadEditPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) RoadEditing.handle(sp, p.op(), p.data());
        });
    }
}
