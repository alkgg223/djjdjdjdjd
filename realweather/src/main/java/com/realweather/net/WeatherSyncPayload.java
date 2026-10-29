package com.realweather.net;

import com.realweather.RealWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the current weather intensities (all 0..1 except the wind angle in radians). */
public record WeatherSyncPayload(float rain, float snow, float thunder, float windAngle, float wind, float sand, float blizzard)
        implements CustomPacketPayload {
    public static final Type<WeatherSyncPayload> TYPE = new Type<>(RealWeather.id("sync"));
    public static final StreamCodec<FriendlyByteBuf, WeatherSyncPayload> CODEC = StreamCodec.of(WeatherSyncPayload::write, WeatherSyncPayload::read);

    private static void write(FriendlyByteBuf buf, WeatherSyncPayload p) {
        buf.writeFloat(p.rain);
        buf.writeFloat(p.snow);
        buf.writeFloat(p.thunder);
        buf.writeFloat(p.windAngle);
        buf.writeFloat(p.wind);
        buf.writeFloat(p.sand);
        buf.writeFloat(p.blizzard);
    }

    private static WeatherSyncPayload read(FriendlyByteBuf buf) {
        return new WeatherSyncPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
