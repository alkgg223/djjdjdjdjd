package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the sky events going on or coming (type, start, length, seed). */
public record SkyEventsPayload(List<Entry> events) implements CustomPacketPayload {
    public record Entry(int type, long start, int duration, long seed) {}

    public static final Type<SkyEventsPayload> TYPE = new Type<>(RealWeather.id("sky_events"));
    public static final StreamCodec<FriendlyByteBuf, SkyEventsPayload> CODEC = StreamCodec.of(SkyEventsPayload::write, SkyEventsPayload::read);

    private static void write(FriendlyByteBuf buf, SkyEventsPayload p) {
        buf.writeVarInt(p.events.size());
        for (Entry e : p.events) {
            buf.writeVarInt(e.type);
            buf.writeLong(e.start);
            buf.writeVarInt(e.duration);
            buf.writeLong(e.seed);
        }
    }

    private static SkyEventsPayload read(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readLong()));
        }
        return new SkyEventsPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
