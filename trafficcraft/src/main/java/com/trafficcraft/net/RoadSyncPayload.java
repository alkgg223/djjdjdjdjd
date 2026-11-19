package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.road.ClientRoads;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Full road network of a dimension, sent to clients when it changes or when they join. */
public record RoadSyncPayload(String dim, CompoundTag data) implements CustomPacketPayload {
    public static final Type<RoadSyncPayload> TYPE = new Type<>(TrafficCraft.id("road_sync"));
    public static final StreamCodec<ByteBuf, RoadSyncPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RoadSyncPayload::dim,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, RoadSyncPayload::data, RoadSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RoadSyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientRoads.dimension = p.dim();
            ClientRoads.net.load(p.data());
        });
    }
}
