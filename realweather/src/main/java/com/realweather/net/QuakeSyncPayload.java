package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: every earthquake shaking (or whose cracks still show) in the overworld. */
public record QuakeSyncPayload(List<Entry> quakes) implements CustomPacketPayload {
    public static final Type<QuakeSyncPayload> TYPE = new Type<>(RealWeather.id("quakes"));
    public static final StreamCodec<FriendlyByteBuf, QuakeSyncPayload> CODEC = StreamCodec.of(QuakeSyncPayload::write, QuakeSyncPayload::read);

    public record Entry(int id, double x, double z, float magnitude, long seed, float fault, int age) {}

    private static void write(FriendlyByteBuf buf, QuakeSyncPayload p) {
        buf.writeVarInt(p.quakes.size());
        for (Entry e : p.quakes) {
            buf.writeVarInt(e.id);
            buf.writeDouble(e.x);
            buf.writeDouble(e.z);
            buf.writeFloat(e.magnitude);
            buf.writeLong(e.seed);
            buf.writeFloat(e.fault);
            buf.writeVarInt(e.age);
        }
    }

    private static QuakeSyncPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 32);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readLong(), buf.readFloat(), buf.readVarInt()));
        }
        return new QuakeSyncPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
