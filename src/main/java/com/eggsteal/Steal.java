package com.eggsteal;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.entity.EntityTypeTest;

public class Steal {
    public static ItemStack makeEgg(int variant, UUID from) {
        ItemStack s = new ItemStack(EggSteal.EGG_ITEMS.get(variant).get());
        if (from != null) {
            CompoundTag t = new CompoundTag();
            t.putUUID("from", from);
            s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        }
        return s;
    }

    public static UUID fromOf(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag t = cd.copyTag();
        return t.hasUUID("from") ? t.getUUID("from") : null;
    }

    public static String nameOf(ServerLevel sl, Entity e) {
        Economy.Stats st = Economy.get(sl.getServer()).map.get(e.getUUID());
        return st != null ? st.name : e.getName().getString();
    }

    /** A player starts stealing a creature (instant for normal ones, 8 seconds of standing next to it for pack creatures). */
    public static void playerSteal(CreatureEntity c, ServerPlayer p) {
        if (c.hatching()) { p.displayClientMessage(Component.literal("§cStill hatching!"), true); return; }
        if (c.stealing()) { p.displayClientMessage(Component.literal("§cSomeone is already stealing it!"), true); return; }
        c.beginSteal(p);
    }

    /** The theft is complete: the creature leaves the base and the thief carries it. */
    public static void finish(CreatureEntity c, LivingEntity thief) {
        ServerLevel sl = (ServerLevel) c.level();
        UUID from = c.owner();
        int v = c.variant();
        c.discard();
        if (thief instanceof ServerPlayer p) {
            ItemStack egg = makeEgg(v, from);
            if (!p.getInventory().add(egg)) p.drop(egg, false);
            p.sendSystemMessage(Component.literal("§aYou stole " + Creature.VALUES[v].tag() + "§a! Run home and place the egg in your base before they hit you!"));
        } else if (thief instanceof BotEntity b) {
            b.carried = v;
            b.carriedFrom = from;
            b.stolen();
        }
        notifyVictim(sl, from, thief, v);
    }

    public static void notifyAttempt(ServerLevel sl, UUID victim, LivingEntity thief, int variant) {
        String msg = "§c⚠ " + nameOf(sl, thief) + " is trying to steal your " + Creature.VALUES[variant].tag() + "§c! Hit them to stop it!";
        ServerPlayer vp = sl.getServer().getPlayerList().getPlayer(victim);
        if (vp != null) { vp.sendSystemMessage(Component.literal(msg)); return; }
        if (sl.getEntity(victim) instanceof BotEntity b) b.aggro(thief);
    }

    public static void notifyVictim(ServerLevel sl, UUID victim, LivingEntity thief, int variant) {
        String msg = "§c" + nameOf(sl, thief) + " stole your " + Creature.VALUES[variant].tag() + "§c! Hit them with your bat to get it back!";
        ServerPlayer vp = sl.getServer().getPlayerList().getPlayer(victim);
        if (vp != null) vp.sendSystemMessage(Component.literal(msg));
        else if (sl.getEntity(victim) instanceof BotEntity b) b.aggro(thief);
        // malicious bots love to join in, even if it is none of their business
        Economy eco = Economy.get(sl.getServer());
        for (BotEntity mb : sl.getEntities(EntityTypeTest.forClass(BotEntity.class), x -> true)) {
            if (mb == thief || mb.getUUID().equals(victim)) continue;
            Economy.Stats ms = eco.map.get(mb.getUUID());
            if (ms != null && ms.malicious && Math.random() < 0.6) mb.aggro(thief);
        }
    }

    /** Called whenever a living entity is about to be hurt by another living entity. */
    public static void onHit(LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel sl) {
            UUID tid = target.getUUID();
            for (CreatureEntity c : sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), x -> tid.equals(x.stealer()))) c.cancelSteal();
        }
        UUID aid = attacker.getUUID();
        if (target instanceof Player p) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() instanceof EggItem ei && aid.equals(fromOf(s))) {
                    int n = s.getCount();
                    p.getInventory().setItem(i, ItemStack.EMPTY);
                    for (int k = 0; k < n; k++) give(attacker, ei.creature.ordinal());
                }
            }
        } else if (target instanceof BotEntity b && b.carried >= 0 && aid.equals(b.carriedFrom)) {
            int v = b.carried;
            b.carried = -1;
            b.carriedFrom = null;
            b.setTarget(null);
            give(attacker, v);
        }
    }

    /** The creature goes straight back to its owner's base (not into an inventory). */
    public static void give(LivingEntity who, int variant) {
        if (who instanceof ServerPlayer sp) {
            Economy eco = Economy.get(sp.server);
            Economy.Stats st = eco.stats(sp.getUUID());
            if (Arena.place((ServerLevel) sp.level(), sp.getUUID(), variant, st, 0)) {
                sp.sendSystemMessage(Component.literal("§aYour " + Creature.VALUES[variant].tag() + " §ais back in your base!"));
            } else {
                ItemStack egg = makeEgg(variant, null);
                if (!sp.getInventory().add(egg)) sp.drop(egg, false);
                sp.sendSystemMessage(Component.literal("§eYour base is full - the " + Creature.VALUES[variant].tag() + " §eegg is in your inventory."));
            }
        } else if (who instanceof BotEntity b) {
            b.receive(variant);
        }
    }
}
