package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: every tornado in the overworld. */
public record TornadoSyncPayload(List<Entry> tornadoes) implements CustomPacketPayload {
    public static final Type<TornadoSyncPayload> TYPE = new Type<>(RealWeather.id("tornadoes"));
    public static final StreamCodec<FriendlyByteBuf, TornadoSyncPayload> CODEC = StreamCodec.of(TornadoSyncPayload::write, TornadoSyncPayload::read);

    public record Entry(int id, double x, double z, float groundY, float heading, float speed, int rating, int stage,
                        float contact, float strength, long seed) {}

    private static void write(FriendlyByteBuf buf, TornadoSyncPayload p) {
        buf.writeVarInt(p.tornadoes.size());
        for (Entry e : p.tornadoes) {
            buf.writeVarInt(e.id);
            buf.writeDouble(e.x);
            buf.writeDouble(e.z);
            buf.writeFloat(e.groundY);
            buf.writeFloat(e.heading);
            buf.writeFloat(e.speed);
            buf.writeByte(e.rating);
            buf.writeByte(e.stage);
            buf.writeFloat(e.contact);
            buf.writeFloat(e.strength);
            buf.writeLong(e.seed);
        }
    }

    private static TornadoSyncPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readLong()));
        }
        return new TornadoSyncPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
