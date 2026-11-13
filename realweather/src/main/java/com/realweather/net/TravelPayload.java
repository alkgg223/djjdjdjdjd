package com.realweather.net;

import com.realweather.RealWeather;
import com.realweather.space.PlanetTravel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: from the planet map, go to a world's surface or orbit (where on it, in radians), or go home. */
public record TravelPayload(String world, int mode, double lon, double lat) implements CustomPacketPayload {
    public static final int SURFACE = 0, ORBIT = 1, HOME = 2;
    public static final Type<TravelPayload> TYPE = new Type<>(RealWeather.id("travel"));
    public static final StreamCodec<FriendlyByteBuf, TravelPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.world, 64);
                buf.writeVarInt(p.mode);
                buf.writeDouble(p.lon);
                buf.writeDouble(p.lat);
            },
            buf -> new TravelPayload(buf.readUtf(64), buf.readVarInt(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TravelPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) {
                return;
            }
            if (p.mode == HOME) {
                PlanetTravel.home(sp);
            } else {
                PlanetTravel.travel(sp, p.world, p.mode == ORBIT, p.lon, p.lat);
            }
        });
    }
}
