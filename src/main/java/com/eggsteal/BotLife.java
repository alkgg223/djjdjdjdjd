package com.eggsteal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/** Makes bots feel like real players: they leave the "server" after a while and new ones join with ready-made bases. */
public class BotLife {
    public static final int BOT_COUNT = 4;
    static final String[] NAMES = {"Zippy", "Bobo", "Nugget", "Pixel", "Mango", "Turbo", "Waffle", "Comet", "Bean", "Ziggy", "xKira", "DarkWolf_9", "Sunny", "MrPancake", "Ninja_Ali", "Lola", "Rex", "Blaze", "Pumpkin", "Skyler",
            "Noodle", "Falcon", "Mimi", "Shadow", "Hamza", "Luna", "Kenji", "Storm", "ProGamer77", "Tiny", "Cookie", "Jax", "Yara", "Omar_X", "Echo", "Pepper", "Domi", "Raven", "Sami", "Frostbite"};
    static final Map<UUID, Long> leaveAt = new HashMap<>();
    static final List<Long> joins = new ArrayList<>();

    public static void reset() {
        leaveAt.clear();
        joins.clear();
    }

    public static void tick(MinecraftServer s, Economy eco) {
        long now = s.getTickCount();
        ServerLevel sl = s.overworld();
        int bots = 0;
        for (Map.Entry<UUID, Economy.Stats> en : new ArrayList<>(eco.map.entrySet())) {
            Economy.Stats st = en.getValue();
            if (!st.bot || st.base < 0) continue;
            bots++;
            UUID id = en.getKey();
            if (now > 400 && sl.getEntity(id) == null) GameEvents.spawnBot(sl, id, st);
            long t = leaveAt.computeIfAbsent(id, k -> now + 6000 + (long) (Math.random() * 12000));
            if (now >= t) leave(s, sl, eco, id, st);
        }
        if (bots + joins.size() < BOT_COUNT && now > 100) joins.add(now + 60 + (long) (Math.random() * 500));
        for (Iterator<Long> it = joins.iterator(); it.hasNext(); ) {
            if (now >= it.next()) {
                it.remove();
                if (!join(s, sl, eco)) joins.add(now + 400);
                break;
            }
        }
    }

    static void leave(MinecraftServer s, ServerLevel sl, Economy eco, UUID id, Economy.Stats st) {
        s.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", Component.literal(st.name)).withStyle(ChatFormatting.YELLOW), false);
        Entity e = sl.getEntity(id);
        if (e != null) e.discard();
        for (CreatureEntity c : sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), x -> id.equals(x.owner()))) c.discard();
        int base = st.base;
        for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class, Belt.box(base), it -> it.getPersistentData().contains("es_belt"))) ie.discard();
        Laser.reset(sl, base);
        Arena.setFloor2(sl, base, false);
        Arena.paintBase(sl, base, 0);
        eco.release(id);
        leaveAt.remove(id);
    }

    static String pickName(Economy eco) {
        for (int tries = 0; tries < 30; tries++) {
            String n = NAMES[(int) (Math.random() * NAMES.length)];
            boolean used = false;
            for (Economy.Stats o : eco.map.values()) if (o.name.equals(n)) used = true;
            if (!used) return n;
        }
        return NAMES[(int) (Math.random() * NAMES.length)] + (int) (Math.random() * 900 + 100);
    }

    static int rnd(int a, int b) { return a + (int) (Math.random() * (b - a + 1)); }

    static boolean join(MinecraftServer s, ServerLevel sl, Economy eco) {
        if (eco.freeBase() < 0) return false;
        UUID id = UUID.randomUUID();
        int base = eco.claim(id);
        if (base < 0) return false;
        Economy.Stats st = eco.stats(id);
        st.bot = true;
        st.name = pickName(eco);
        st.malicious = Math.random() < 0.3;
        double r = Math.random();
        int tier = r < 0.4 ? 0 : r < 0.8 ? 1 : 2;
        int count, minOrd, maxOrd;
        if (tier == 0) {
            st.money = rnd(100, 400); st.belt = rnd(0, 1); st.speed = rnd(0, 1);
            count = rnd(0, 1); minOrd = 0; maxOrd = 2;
        } else if (tier == 1) {
            st.money = rnd(500, 6000); st.belt = rnd(2, 6); st.income = rnd(2, 6); st.slots = rnd(0, 2); st.speed = rnd(1, 3);
            st.weapon = rnd(0, 1); st.floor2 = Math.random() < 0.25; st.coilSpeed = Math.random() < 0.4;
            count = rnd(3, 6); minOrd = 0; maxOrd = 8;
        } else {
            st.money = rnd(20000, 220000); st.belt = rnd(8, 14); st.income = rnd(8, 16); st.slots = rnd(2, 5); st.speed = rnd(2, 4);
            st.weapon = rnd(1, 2); st.floor2 = Math.random() < 0.8; st.coilSpeed = true;
            count = rnd(7, 12); minOrd = 5; maxOrd = 15;
        }
        if (st.coilSpeed) st.speed += 3;
        if (tier == 1 && Math.random() < 0.3) st.rebirths = 1;
        if (tier == 2) st.rebirths = rnd(1, 4);
        if (tier >= 1 && Math.random() < (tier == 1 ? 0.25 : 0.7)) {
            st.skin = tier == 1 ? rnd(1, 5) : rnd(1, 20);
            st.skinsOwned |= 1L << st.skin;
        }
        Arena.paintBase(sl, base, st.skin);
        Arena.setFloor2(sl, base, st.floor2);
        int avail = Arena.available(st);
        for (int k = 0; k < Math.min(count, avail); k++) {
            int ord = rnd(minOrd, maxOrd);
            if (tier == 2 && Math.random() < 0.4) ord = rnd(10, 15);
            int mut = tier == 2 && Math.random() < 0.2 ? rnd(1, Mutation.values().length - 1) : 0;
            Arena.place(sl, id, ord, mut, st, 0);
        }
        if (tier == 2 && Math.random() < 0.3) { // rich bots may own pack creatures (which are hard to steal)
            int pack = rnd(0, Creature.PACKS.length - 1);
            for (int k = rnd(1, 3); k > 0; k--) Arena.place(sl, id, Creature.ofPack(pack).get(rnd(0, 3)).ordinal(), st, 0);
        }
        eco.setDirty();
        s.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.joined", Component.literal(st.name)).withStyle(ChatFormatting.YELLOW), false);
        GameEvents.spawnBot(sl, id, st);
        return true;
    }
}
