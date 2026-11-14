package com.realweather.net;

import com.realweather.RealWeather;
import com.realweather.device.LightDrive;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the light drive in a hand set to a speed (percent of the speed of light), on or off. */
public record DrivePayload(int percent, boolean on, boolean offhand) implements CustomPacketPayload {
    public static final Type<DrivePayload> TYPE = new Type<>(RealWeather.id("drive"));
    public static final StreamCodec<FriendlyByteBuf, DrivePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.percent);
                buf.writeBoolean(p.on);
                buf.writeBoolean(p.offhand);
            },
            buf -> new DrivePayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DrivePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack s = sp.getItemInHand(p.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (s.getItem() instanceof LightDrive) {
                LightDrive.set(s, p.percent, p.on);
            }
        });
    }
}
