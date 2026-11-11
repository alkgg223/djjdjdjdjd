package com.rpbusiness.network;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.business.Business;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.server.Actions;
import com.rpbusiness.server.Snapshots;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The only two packets of the mod: GUI actions (client -> integrated server) and screen data
 * (integrated server -> client). This is Minecraft's normal internal client/server communication in singleplayer.
 */
public final class Net {
    private Net() {}

    public record Action(String action, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(RPBusiness.id("action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action, ByteBufCodecs.COMPOUND_TAG, Action::data, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Screen(String screen, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Screen> TYPE = new Type<>(RPBusiness.id("screen"));
        public static final StreamCodec<ByteBuf, Screen> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Screen::screen, ByteBufCodecs.COMPOUND_TAG, Screen::data, Screen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(Action.TYPE, Action.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) Actions.handle(sp, msg.action(), msg.data());
        }));
        r.playToClient(Screen.TYPE, Screen.CODEC, (msg, ctx) -> ctx.enqueueWork(() ->
                com.rpbusiness.client.ClientHooks.openScreen(msg.screen(), msg.data())));
    }

    public static void send(ServerPlayer p, String screen, CompoundTag data) {
        PacketDistributor.sendToPlayer(p, new Screen(screen, data));
    }

    public static void toServer(String action, CompoundTag data) {
        PacketDistributor.sendToServer(new Action(action, data));
    }

    public static void openBusiness(ServerPlayer p, Business b, String tab) {
        CompoundTag t = Snapshots.business(p, b);
        t.putString("tab", tab);
        send(p, "business", t);
    }

    public static void openSupplier(ServerPlayer p, Business b) {
        send(p, "supplier", Snapshots.supplier(p, b));
    }

    public static void openRegistration(ServerPlayer p, BlockPos computer) {
        CompoundTag t = new CompoundTag();
        t.put("pos", NbtUtils.writeBlockPos(computer));
        ItemStack planner = findPlanner(p);
        if (planner != null) {
            BlockPos a = planner.get(ModComponents.CORNER_A.get()), b = planner.get(ModComponents.CORNER_B.get());
            if (a != null) t.put("a", NbtUtils.writeBlockPos(a));
            if (b != null) t.put("b", NbtUtils.writeBlockPos(b));
        }
        send(p, "register", t);
    }

    public static ItemStack findPlanner(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.BUSINESS_PLANNER.get()) && s.has(ModComponents.CORNER_A.get())) return s;
        return null;
    }
}
