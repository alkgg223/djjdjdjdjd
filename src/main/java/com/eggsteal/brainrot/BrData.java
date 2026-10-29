package com.eggsteal.brainrot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved data of the Brainrot Heist mode: money, rebirths and base of every player and bot. */
public class BrData extends SavedData {
    public static class Stats {
        public String name = "?";
        public boolean bot, kit, malicious, coil;
        public double money;
        public int rebirths, speed, base = -1;
    }

    public final Map<UUID, Stats> map = new HashMap<>();
    public final UUID[] baseOwner = new UUID[BrMap.BASES];
    public boolean built;
    /** Game time when the next guaranteed rare brainrot walks onto the carpet. */
    public long nextRare;

    public static BrData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BrData::new, BrData::load, null), "eggsteal_brainrot");
    }

    public Stats stats(UUID id) { return map.computeIfAbsent(id, k -> new Stats()); }

    public int claim(UUID id) {
        Stats s = stats(id);
        if (s.base >= 0) return s.base;
        int b = freeBase();
        if (b >= 0) { baseOwner[b] = id; s.base = b; setDirty(); }
        return b;
    }

    public int freeBase() {
        for (int i = 0; i < BrMap.BASES; i++) if (baseOwner[i] == null) return i;
        return -1;
    }

    public void release(UUID id) {
        Stats s = map.remove(id);
        if (s != null && s.base >= 0 && id.equals(baseOwner[s.base])) baseOwner[s.base] = null;
        setDirty();
    }

    public static double rebirthCost(Stats s) { return 1_000_000 * Math.pow(10, s.rebirths); }
    public static double rebirthMult(Stats s) { return 1 + 0.5 * s.rebirths; }
    public static double speedCost(Stats s) { return 500 * Math.pow(3, s.speed); }
    public static final int MAX_SPEED = 10;

    /** Money per second of one brainrot for its owner. */
    public static double rate(Stats s, Brainrot b, int mutation) {
        return b.income * BrMutation.of(mutation).mult * (s == null ? 1 : rebirthMult(s)) * com.eggsteal.RandomEvents.incomeMult();
    }

    /** What a brainrot is worth (its carpet price times its mutation). */
    public static double value(Brainrot b, int mutation) { return b.price * BrMutation.of(mutation).mult; }

    /** How well a bot protects its brainrots: 0 normal, 1 rich, 2 very rich, 3 super rich (players are always 0). */
    public static int guard(Stats s) {
        if (s == null || !s.bot) return 0;
        if (s.money >= 200_000_000 || s.rebirths >= 3) return 3;
        if (s.money >= 5_000_000 || s.rebirths >= 2) return 2;
        if (s.money >= 100_000 || s.rebirths >= 1) return 1;
        return 0;
    }

    public static final String[] GUARD_NAMES = {"", "§eRich", "§6Very rich", "§c§lSuper rich"};

    public static BrData load(CompoundTag tag, HolderLookup.Provider reg) {
        BrData d = new BrData();
        ListTag l = tag.getList("stats", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            Stats s = new Stats();
            s.name = t.getString("name"); s.bot = t.getBoolean("bot"); s.kit = t.getBoolean("kit"); s.malicious = t.getBoolean("malicious");
            s.coil = t.getBoolean("coil"); s.money = t.getDouble("money"); s.rebirths = t.getInt("rebirths"); s.speed = t.getInt("speed");
            s.base = t.getInt("base");
            d.map.put(t.getUUID("id"), s);
        }
        ListTag b = tag.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < b.size() && i < BrMap.BASES; i++) if (b.getCompound(i).hasUUID("o")) d.baseOwner[i] = b.getCompound(i).getUUID("o");
        d.built = tag.getBoolean("built");
        d.nextRare = tag.getLong("nextRare");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Stats> en : map.entrySet()) {
            Stats s = en.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("id", en.getKey());
            t.putString("name", s.name); t.putBoolean("bot", s.bot); t.putBoolean("kit", s.kit); t.putBoolean("malicious", s.malicious);
            t.putBoolean("coil", s.coil); t.putDouble("money", s.money); t.putInt("rebirths", s.rebirths); t.putInt("speed", s.speed);
            t.putInt("base", s.base);
            l.add(t);
        }
        tag.put("stats", l);
        ListTag b = new ListTag();
        for (int i = 0; i < BrMap.BASES; i++) {
            CompoundTag t = new CompoundTag();
            if (baseOwner[i] != null) t.putUUID("o", baseOwner[i]);
            b.add(t);
        }
        tag.put("bases", b);
        tag.putBoolean("built", built);
        tag.putLong("nextRare", nextRare);
        return tag;
    }
}
