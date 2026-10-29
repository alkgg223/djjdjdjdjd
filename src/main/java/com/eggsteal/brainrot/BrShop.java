package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
import com.eggsteal.Economy;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/** Brainrot Heist purchases: speed levels, coils, a spare bat, and rebirths (for players and bots). */
public class BrShop {
    public static final double SPEED_COIL = 2_000, JUMP_COIL = 3_000, BAT = 100;

    public static void open(ServerPlayer p, boolean guide, int tab) {
        BrData.get(p.server).stats(p.getUUID());
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new BrMenu(id, inv, (ServerPlayer) pl, guide, tab),
                Component.literal(guide ? "Brainrot Heist - Guide" : "Brainrot Heist - Shop")));
    }

    static void msg(ServerPlayer p, String text, boolean ok) {
        p.displayClientMessage(Component.literal(text), true);
        p.playNotifySound(ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
    }

    static boolean pay(ServerPlayer p, BrData data, BrData.Stats st, double cost) {
        if (st.money < cost) { msg(p, "§cNot enough money! Need $" + Economy.fmt(cost), false); return false; }
        st.money -= cost;
        data.setDirty();
        return true;
    }

    public static void buySpeed(ServerPlayer p) {
        BrData data = BrData.get(p.server);
        BrData.Stats st = data.stats(p.getUUID());
        if (st.speed >= BrData.MAX_SPEED) { msg(p, "§cSpeed is maxed!", false); return; }
        if (!pay(p, data, st, BrData.speedCost(st))) return;
        st.speed++;
        msg(p, "§aSpeed upgraded to Lv" + st.speed + "!", true);
    }

    public static void buyItem(ServerPlayer p, String kind) {
        BrData data = BrData.get(p.server);
        BrData.Stats st = data.stats(p.getUUID());
        double price = kind.equals("speed") ? SPEED_COIL : kind.equals("jump") ? JUMP_COIL : BAT;
        if (!pay(p, data, st, price)) return;
        ItemStack s = new ItemStack(kind.equals("speed") ? EggSteal.SPEED_COIL.get() : kind.equals("jump") ? EggSteal.JUMP_COIL.get() : EggSteal.BAT.get());
        if (!p.getInventory().add(s)) p.drop(s, false);
        msg(p, "§aPurchased!", true);
    }

    /** Money starts over; every rebirth adds x0.5 money for good, and the first one adds 10 more podiums. Brainrots are kept. */
    public static boolean rebirth(ServerLevel sl, BrData data, UUID id, BrData.Stats st) {
        double cost = BrData.rebirthCost(st);
        if (st.money < cost) return false;
        st.rebirths++;
        st.money = 0;
        data.setDirty();
        if (st.base >= 0) BrMap.setPodiums(sl, st.base, BrMap.capacity(st));
        sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§d§l✦ " + st.name + " §dREBIRTHED! §7(Rebirth " + st.rebirths
                + " · money x" + BrData.rebirthMult(st) + (st.rebirths == 1 ? " · 10 more podiums" : "") + ")"), false);
        return true;
    }

    public static void rebirth(ServerPlayer p) {
        BrData data = BrData.get(p.server);
        BrData.Stats st = data.stats(p.getUUID());
        if (!rebirth(p.serverLevel(), data, p.getUUID(), st)) { msg(p, "§cYou need $" + Economy.fmt(BrData.rebirthCost(st)) + " to rebirth!", false); return; }
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1, 0.6, 0.5);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
        msg(p, "§d§lREBIRTH " + st.rebirths + "!", true);
    }
}
