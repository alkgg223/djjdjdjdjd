package com.eggsteal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

public class Shop {
    public static final double SPEED_COIL = 750, JUMP_COIL = 1000, BAT = 50, FLOOR2 = 5000;

    public static void open(ServerPlayer p, boolean builder) { open(p, builder, builder ? ShopMenu.TAB_BASE : ShopMenu.TAB_UPGRADES); }

    public static void open(ServerPlayer p, boolean builder, int tab) {
        Economy.get(p.server).stats(p.getUUID());
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ShopMenu(id, inv, (ServerPlayer) pl, builder, tab),
                Component.literal(builder ? "Base Builder" : "Egg Steal Shop")));
    }

    /** Rebirth: start over with money and the Income/Belt upgrades, for a permanent income boost. */
    public static boolean rebirth(ServerPlayer p) {
        Economy eco = Economy.get(p.server);
        Economy.Stats st = eco.stats(p.getUUID());
        double cost = Economy.rebirthCost(st);
        if (st.money < cost) { msg(p, "§cYou need $" + Economy.fmt(cost) + " to rebirth!", false); return false; }
        eco.rebirth(st);
        ServerLevel sl = (ServerLevel) p.level();
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1, 0.6, 0.5);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
        p.server.getPlayerList().broadcastSystemMessage(Component.literal("§d§l✦ " + st.name + " §dREBIRTHED! §7(Rebirth " + st.rebirths + " · income x" + Economy.rebirthMult(st) + ")"), false);
        msg(p, "§d§lREBIRTH " + st.rebirths + "! §7All money now earns x" + Economy.rebirthMult(st), true);
        return true;
    }

    static void msg(ServerPlayer p, String text, boolean ok) {
        p.displayClientMessage(Component.literal(text), true);
        p.playNotifySound(ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
    }

    static boolean pay(ServerPlayer p, Economy eco, Economy.Stats st, double cost) {
        if (st.money < cost) { msg(p, "§cNot enough money! Need $" + Economy.fmt(cost), false); return false; }
        st.money -= cost;
        eco.setDirty();
        return true;
    }

    public static void buyUpgrade(ServerPlayer p, Economy.Upgrade u) {
        Economy eco = Economy.get(p.server);
        Economy.Stats st = eco.stats(p.getUUID());
        int lv = u.level(st);
        if (lv >= u.max) { msg(p, "§cAlready at max level.", false); return; }
        if (!pay(p, eco, st, u.cost(lv))) return;
        u.set(st, lv + 1);
        msg(p, "§a" + u.label + " upgraded to Lv" + (lv + 1) + "!", true);
    }

    public static void buyItem(ServerPlayer p, String kind) {
        Economy eco = Economy.get(p.server);
        Economy.Stats st = eco.stats(p.getUUID());
        double price = kind.equals("speed") ? SPEED_COIL : kind.equals("jump") ? JUMP_COIL : BAT;
        if (!pay(p, eco, st, price)) return;
        ItemStack s = new ItemStack(kind.equals("speed") ? EggSteal.SPEED_COIL.get() : kind.equals("jump") ? EggSteal.JUMP_COIL.get() : EggSteal.BAT.get());
        if (!p.getInventory().add(s)) p.drop(s, false);
        msg(p, "§aPurchased!", true);
    }

    public static void skin(ServerPlayer p, int id) {
        Economy eco = Economy.get(p.server);
        Economy.Stats st = eco.stats(p.getUUID());
        if (st.base < 0 || id < 0 || id >= Skin.ALL.length) return;
        Skin sk = Skin.ALL[id];
        boolean owned = id == 0 || ((st.skinsOwned >> id) & 1) == 1;
        if (!owned) {
            if (!pay(p, eco, st, sk.price)) return;
            st.skinsOwned |= 1L << id;
        }
        st.skin = id;
        eco.setDirty();
        Arena.paintBase((ServerLevel) p.level(), st.base, id);
        msg(p, "§a" + sk.name + " skin equipped!", true);
    }

    public static void buyFloor2(ServerPlayer p) {
        Economy eco = Economy.get(p.server);
        Economy.Stats st = eco.stats(p.getUUID());
        if (st.floor2) { msg(p, "§cYou already have a second floor.", false); return; }
        if (st.base < 0) return;
        if (!pay(p, eco, st, FLOOR2)) return;
        st.floor2 = true;
        Arena.setFloor2((ServerLevel) p.level(), st.base, true);
        msg(p, "§aSecond floor built! Stairs are at the right side of your base.", true);
    }
}
