package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server asks the client to open an editor screen (kind = node / edge / tool / block) with initial data. */
public record OpenEditorPayload(String kind, CompoundTag data) implements CustomPacketPayload {
    public static final Type<OpenEditorPayload> TYPE = new Type<>(TrafficCraft.id("open_editor"));
    public static final StreamCodec<ByteBuf, OpenEditorPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenEditorPayload::kind,
            ByteBufCodecs.COMPOUND_TAG, OpenEditorPayload::data, OpenEditorPayload::new);
    public static java.util.function.BiConsumer<String, CompoundTag> CLIENT_HANDLER = (k, d) -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenEditorPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CLIENT_HANDLER.accept(p.kind(), p.data()));
    }
}
