package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.vehicle.VehicleEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tuning tool: the client asks to fit a set of parts (config string "fb=1;sp=3;") and a body colour on a vehicle. */
public record TuningPayload(int entity, String config, int color) implements CustomPacketPayload {
    public static final Type<TuningPayload> TYPE = new Type<>(TrafficCraft.id("tuning"));
    public static final StreamCodec<ByteBuf, TuningPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TuningPayload::entity,
            ByteBufCodecs.STRING_UTF8, TuningPayload::config, ByteBufCodecs.INT, TuningPayload::color, TuningPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TuningPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.level().getEntity(p.entity()) instanceof VehicleEntity v)) return;
            if (sp.distanceTo(v) > 10 || !(sp.getMainHandItem().is(TCRegistry.TUNING_TOOL.get()) || sp.getOffhandItem().is(TCRegistry.TUNING_TOOL.get()))) return;
            v.setConfig(sanitize(v, p.config()));
            v.setColor(p.color());
        });
    }

    /** Keep only slots the car has and indices inside its catalog. */
    static String sanitize(VehicleEntity v, String cfg) {
        StringBuilder b = new StringBuilder();
        for (String kv : cfg.split(";")) {
            int i = kv.indexOf('=');
            if (i <= 0 || b.length() > 200) continue;
            String slot = kv.substring(0, i);
            Integer n = v.spec().catalog.get(slot);
            if (n == null) continue;
            try {
                int k = Integer.parseInt(kv.substring(i + 1));
                if (k > 0 && k < n) b.append(slot).append('=').append(k).append(';');
            } catch (NumberFormatException ignored) {
            }
        }
        return b.toString();
    }
}
