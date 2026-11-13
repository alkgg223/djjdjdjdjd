package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the solar eclipse in progress (or coming), if any. */
public record EclipseSyncPayload(boolean active, long start, int duration, float miss, float angle, int kind) implements CustomPacketPayload {
    public static final Type<EclipseSyncPayload> TYPE = new Type<>(RealWeather.id("eclipse"));
    public static final StreamCodec<FriendlyByteBuf, EclipseSyncPayload> CODEC = StreamCodec.of(EclipseSyncPayload::write, EclipseSyncPayload::read);

    private static void write(FriendlyByteBuf buf, EclipseSyncPayload p) {
        buf.writeBoolean(p.active);
        buf.writeLong(p.start);
        buf.writeVarInt(p.duration);
        buf.writeFloat(p.miss);
        buf.writeFloat(p.angle);
        buf.writeVarInt(p.kind);
    }

    private static EclipseSyncPayload read(FriendlyByteBuf buf) {
        return new EclipseSyncPayload(buf.readBoolean(), buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
