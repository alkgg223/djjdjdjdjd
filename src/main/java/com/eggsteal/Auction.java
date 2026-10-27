package com.eggsteal;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

/** The very-rare-egg system of the center belt (pack creatures only appear here). */
public class Auction {
    public static final double BELT_SPEED = 0.01;   // blocks per tick while on the belt (~0.2 b/s, half of the old speed)
    public static final double WALK_SPEED = 0.215;  // same as a walking player
    static long nextSpawn;

    public static AuctionEggEntity active(ServerLevel sl) {
        List<? extends AuctionEggEntity> l = sl.getEntities(EntityTypeTest.forClass(AuctionEggEntity.class), e -> true);
        return l.isEmpty() ? null : l.get(0);
    }

    public static void reset() { nextSpawn = 0; }

    public static void tick(MinecraftServer s) {
        long now = s.getTickCount();
        ServerLevel sl = s.overworld();
        if (nextSpawn == 0) nextSpawn = now + 600;
        if (now < nextSpawn || active(sl) != null) return;
        nextSpawn = now + 1800 + (long) (Math.random() * 1800);
        spawn(s);
    }

    /** Puts a new rare egg at the start of the center belt (if none is there already). */
    public static void spawn(MinecraftServer s) {
        ServerLevel sl = s.overworld();
        if (active(sl) != null) return;
        int pack = (int) (Math.random() * Creature.PACKS.length);
        int k = 0;
        while (k < 9 && Math.random() < 0.45) k++;
        Creature c = Creature.ofPack(pack).get(k);
        AuctionEggEntity e = EggSteal.AUCTION.get().create(sl);
        if (e == null) return;
        e.moveTo(-8.5, 64.5, 0.5, 0f, 0f);
        e.init(c.ordinal(), (long) c.price);
        e.mut = RandomEvents.rollMutation(sl.random);
        e.refreshName();
        sl.addFreshEntity(e);
        Hud.announce(s, "§d§lRARE EGG!", "§f" + Mutation.of(e.mut).prefix() + c.tag() + " §fegg (" + Creature.PACKS[pack] + " pack) appeared on the center belt for §6$" + Economy.fmt(c.price));
    }

    /** Buy the egg for its current price. The price doubles and the egg turns toward the buyer's base. */
    public static boolean buy(AuctionEggEntity egg, UUID buyer, MinecraftServer s) {
        Economy eco = Economy.get(s);
        Economy.Stats st = eco.map.get(buyer);
        if (st == null || st.base < 0 || buyer.equals(egg.lastBuyer)) return false;
        long ask = egg.price();
        if (st.money < ask) return false;
        st.money -= ask;
        if (egg.lastBuyer != null) {
            Economy.Stats prev = eco.map.get(egg.lastBuyer);
            if (prev != null) prev.money += egg.lastPaid;
        }
        egg.lastPaid = ask;
        egg.lastBuyer = buyer;
        egg.setPrice(ask * 2);
        egg.dest = st.base;
        egg.stage = 0;
        eco.setDirty();
        egg.refreshName();
        s.getPlayerList().broadcastSystemMessage(Component.literal("§e" + st.name + " §fbought the " + egg.creature().tag() + " §fegg for §6$" + Economy.fmt(ask) + "§f! Next price: §c$" + Economy.fmt(ask * 2)), false);
        return true;
    }

    public static void deliver(ServerLevel sl, AuctionEggEntity egg) {
        MinecraftServer s = sl.getServer();
        Economy eco = Economy.get(s);
        UUID id = egg.lastBuyer;
        Economy.Stats st = id == null ? null : eco.map.get(id);
        if (st != null) {
            boolean ok = Arena.place(sl, id, egg.variant(), egg.mut, st, CreatureEntity.HATCH_TIME);
            ServerPlayer p = s.getPlayerList().getPlayer(id);
            if (ok) {
                s.getPlayerList().broadcastSystemMessage(Component.literal("§a" + st.name + "'s " + egg.creature().tag() + " §aegg arrived and is hatching!"), false);
            } else if (p != null) {
                ItemStack item = Steal.makeEgg(egg.variant(), null, egg.mut);
                if (!p.getInventory().add(item)) p.drop(item, false);
                p.sendSystemMessage(Component.literal("§eNo free slot - the egg was put in your inventory."));
            } else {
                st.money += egg.lastPaid;
            }
            eco.setDirty();
        }
        egg.discard();
    }
}
