package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
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
import net.minecraft.world.phys.Vec3;

/** Brainrot Heist bots come and go like real players; new ones join with a ready base (beginner, mid or rich). */
public class BrBotLife {
    public static final int BOT_COUNT = 6;
    static final String[] NAMES = {"xX_Noob_Xx", "BrainrotKing", "Pro_Stealer99", "SkibidiSam", "Lolly_Pop", "TurboTom", "ItsJustMilo", "Kawaii_Kiki",
            "Bacon_Boy22", "SneakyPete", "MegaMaya", "GamerGirl_Zoe", "Ninja_Ali", "Hamza_YT", "Dino_Dan", "CoolCat_7", "FastFreddy", "Mr_Pancake",
            "LuckyLuna", "OmarTheGreat", "Bloxxer", "RobloxRuby", "Cookie_Monster", "Captain_Yara", "Zippy2014", "NoobMaster", "Pixel_Pete", "StealQueen",
            "Sami_Pro", "TacoTuesday", "BlockBuster", "Moonlight_x", "Creeper_Kid", "Rainbow_Rex", "ToxicTony", "Sunny_Day"};
    static final Map<UUID, Long> leaveAt = new HashMap<>();
    static final List<Long> joins = new ArrayList<>();

    public static void reset() { leaveAt.clear(); joins.clear(); }

    public static void tick(MinecraftServer s, BrData data) {
        long now = s.getTickCount();
        ServerLevel sl = s.overworld();
        int bots = 0;
        for (Map.Entry<UUID, BrData.Stats> en : new ArrayList<>(data.map.entrySet())) {
            BrData.Stats st = en.getValue();
            if (!st.bot || st.base < 0) continue;
            bots++;
            UUID id = en.getKey();
            if (now > 400 && sl.getEntity(id) == null) spawnBot(sl, id, st);
            long t = leaveAt.computeIfAbsent(id, k -> now + 8000 + (long) (Math.random() * 16000));
            if (now >= t) leave(s, sl, data, id, st);
        }
        if (bots + joins.size() < BOT_COUNT && data.freeBase() >= 0 && now > 100) joins.add(now + 60 + (long) (Math.random() * 600));
        for (Iterator<Long> it = joins.iterator(); it.hasNext(); ) {
            if (now >= it.next()) {
                it.remove();
                if (!join(s, sl, data)) joins.add(now + 600);
                break;
            }
        }
    }

    public static void spawnBot(ServerLevel sl, UUID id, BrData.Stats st) {
        BrBot b = EggSteal.BR_BOT.get().create(sl);
        if (b == null) return;
        b.setUUID(id);
        Vec3 h = BrMap.home(st.base);
        b.moveTo(h.x, h.y, h.z, 0f, 0f);
        sl.addFreshEntity(b);
    }

    public static void leave(MinecraftServer s, ServerLevel sl, BrData data, UUID id, BrData.Stats st) {
        s.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", Component.literal(st.name)).withStyle(ChatFormatting.YELLOW), false);
        Entity e = sl.getEntity(id);
        if (e instanceof BrBot b && b.carrying() != null) b.carrying().returnHome();
        if (e != null) e.discard();
        for (BrainrotEntity b : new ArrayList<>(BrainrotEntity.all(sl))) {
            if (b.state() == BrainrotEntity.PODIUM && id.equals(b.owner)) b.discard();
            else if (b.state() == BrainrotEntity.WALK && id.equals(b.buyer)) b.discard();
        }
        int base = st.base;
        BrMap.Lock.reset(base);
        BrMap.setPodiums(sl, base, 10);
        data.release(id);
        leaveAt.remove(id);
    }

    static String pickName(BrData data) {
        for (int tries = 0; tries < 30; tries++) {
            String n = NAMES[(int) (Math.random() * NAMES.length)];
            boolean used = false;
            for (BrData.Stats o : data.map.values()) if (o.name.equals(n)) used = true;
            if (!used) return n;
        }
        return NAMES[(int) (Math.random() * NAMES.length)] + (int) (Math.random() * 900 + 100);
    }

    static int rnd(int a, int b) { return a + (int) (Math.random() * (b - a + 1)); }

    static boolean join(MinecraftServer s, ServerLevel sl, BrData data) {
        if (data.freeBase() < 0) return false;
        UUID id = UUID.randomUUID();
        int base = data.claim(id);
        if (base < 0) return false;
        BrData.Stats st = data.stats(id);
        st.bot = true;
        st.kit = true;
        st.name = pickName(data);
        st.malicious = Math.random() < 0.3;
        double r = Math.random();
        int tier = r < 0.4 ? 0 : r < 0.8 ? 1 : 2;
        int count, minR, maxR;
        if (tier == 0) { st.money = rnd(100, 2_000); st.speed = rnd(0, 2); count = rnd(1, 3); minR = 0; maxR = 1; }
        else if (tier == 1) { st.money = rnd(5_000, 200_000); st.speed = rnd(2, 5); st.coil = Math.random() < 0.4; count = rnd(4, 8); minR = 1; maxR = 3; }
        else { st.money = rnd(2_000_000, 300_000_000); st.speed = rnd(4, 8); st.coil = true; st.rebirths = rnd(1, 3); count = rnd(9, 16); minR = 2; maxR = 6; }
        BrMap.setPodiums(sl, base, BrMap.capacity(st));
        for (int k = 0; k < Math.min(count, BrMap.capacity(st)); k++) {
            List<Brainrot> pool = Brainrot.ofRarity(rnd(minR, maxR));
            Brainrot b = pool.get((int) (Math.random() * pool.size()));
            int mut = Math.random() < (tier == 2 ? 0.35 : 0.1) ? BrMutation.roll(sl.random, 1, 0, 1) : 0;
            BrainrotEntity.spawnOnPodium(sl, id, b.index, mut);
        }
        data.setDirty();
        s.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.joined", Component.literal(st.name)).withStyle(ChatFormatting.YELLOW), false);
        spawnBot(sl, id, st);
        return true;
    }

    /** A real player needs a base and they are all taken: the poorest bot leaves. */
    public static void kickOne(MinecraftServer s, ServerLevel sl, BrData data) {
        UUID pick = null;
        double least = Double.MAX_VALUE;
        for (Map.Entry<UUID, BrData.Stats> en : data.map.entrySet())
            if (en.getValue().bot && en.getValue().base >= 0 && en.getValue().money < least) { least = en.getValue().money; pick = en.getKey(); }
        if (pick != null) leave(s, sl, data, pick, data.map.get(pick));
    }
}
