package com.trafficcraft.net;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.road.Place;
import com.trafficcraft.road.PlaceTypes;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Location tool: create ("add"), change ("edit") or remove ("del") a map location. */
public record PlacePayload(String action, CompoundTag data) implements CustomPacketPayload {
    public static final Type<PlacePayload> TYPE = new Type<>(TrafficCraft.id("place"));
    public static final StreamCodec<ByteBuf, PlacePayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PlacePayload::action,
            ByteBufCodecs.COMPOUND_TAG, PlacePayload::data, PlacePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlacePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!sp.getMainHandItem().is(TCRegistry.PLACE_TOOL.get()) && !sp.getOffhandItem().is(TCRegistry.PLACE_TOOL.get())) return;
            RoadNetwork net = RoadData.get(sp.serverLevel());
            CompoundTag d = p.data();
            String type = PlaceTypes.ALL.containsKey(d.getString("type")) ? d.getString("type") : "home";
            String name = d.getString("name").strip();
            if (name.length() > 40) name = name.substring(0, 40);
            switch (p.action()) {
                case "add" -> {
                    double x = d.getDouble("x"), y = d.getDouble("y"), z = d.getDouble("z");
                    if (sp.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z)) > 12) return;
                    Place pl = net.addPlace(x, y, z, type, name.isEmpty() ? type : name);
                    sp.displayClientMessage(Component.translatable("msg.trafficcraft.place_added", pl.name), true);
                }
                case "edit" -> {
                    Place pl = net.places.get(d.getInt("id"));
                    if (pl == null || sp.position().distanceTo(pl.pos()) > 16) return;
                    pl.type = type;
                    pl.name = name.isEmpty() ? type : name;
                    net.changed();
                }
                case "del" -> {
                    Place pl = net.places.get(d.getInt("id"));
                    if (pl == null || sp.position().distanceTo(pl.pos()) > 16) return;
                    net.places.remove(pl.id);
                    net.changed();
                    sp.displayClientMessage(Component.translatable("msg.trafficcraft.place_removed", pl.name), true);
                }
                default -> { }
            }
        });
    }
}
