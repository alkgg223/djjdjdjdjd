package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the storm cells around the players (for clouds, local rain and hail, and the radar). */
public record StormSyncPayload(float stratiform, List<Entry> cells) implements CustomPacketPayload {
    public static final Type<StormSyncPayload> TYPE = new Type<>(RealWeather.id("storms"));
    public static final StreamCodec<FriendlyByteBuf, StormSyncPayload> CODEC = StreamCodec.of(StormSyncPayload::write, StormSyncPayload::read);

    public record Entry(int id, double x, double z, float vx, float vz, float radius, float intensity, float hail, int type, int age, int life,
                        long seed) {}

    private static void write(FriendlyByteBuf buf, StormSyncPayload p) {
        buf.writeFloat(p.stratiform);
        buf.writeVarInt(p.cells.size());
        for (Entry e : p.cells) {
            buf.writeVarInt(e.id);
            buf.writeDouble(e.x);
            buf.writeDouble(e.z);
            buf.writeFloat(e.vx);
            buf.writeFloat(e.vz);
            buf.writeFloat(e.radius);
            buf.writeFloat(e.intensity);
            buf.writeFloat(e.hail);
            buf.writeByte(e.type);
            buf.writeVarInt(e.age);
            buf.writeVarInt(e.life);
            buf.writeLong(e.seed);
        }
    }

    private static StormSyncPayload read(FriendlyByteBuf buf) {
        float strat = buf.readFloat();
        int n = Math.min(buf.readVarInt(), 128);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readLong()));
        }
        return new StormSyncPayload(strat, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
