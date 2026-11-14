package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: every tsunami running in the overworld. */
public record TsunamiSyncPayload(List<Entry> waves) implements CustomPacketPayload {
    public static final Type<TsunamiSyncPayload> TYPE = new Type<>(RealWeather.id("tsunamis"));
    public static final StreamCodec<FriendlyByteBuf, TsunamiSyncPayload> CODEC = StreamCodec.of(TsunamiSyncPayload::write, TsunamiSyncPayload::read);

    public record Entry(int id, double ox, double oz, float dirX, float dirZ, float baseY, int strength, float front, int stage, float drained,
                        int age, long seed) {}

    private static void write(FriendlyByteBuf buf, TsunamiSyncPayload p) {
        buf.writeVarInt(p.waves.size());
        for (Entry e : p.waves) {
            buf.writeVarInt(e.id);
            buf.writeDouble(e.ox);
            buf.writeDouble(e.oz);
            buf.writeFloat(e.dirX);
            buf.writeFloat(e.dirZ);
            buf.writeFloat(e.baseY);
            buf.writeVarInt(e.strength);
            buf.writeFloat(e.front);
            buf.writeByte(e.stage);
            buf.writeFloat(e.drained);
            buf.writeVarInt(e.age);
            buf.writeLong(e.seed);
        }
    }

    private static TsunamiSyncPayload read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 16);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                    buf.readFloat(), buf.readByte(), buf.readFloat(), buf.readVarInt(), buf.readLong()));
        }
        return new TsunamiSyncPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
