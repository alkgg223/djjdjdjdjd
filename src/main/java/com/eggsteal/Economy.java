package com.eggsteal;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved data: money, upgrades and base ownership for every player and bot. */
public class Economy extends SavedData {
    public static class Stats {
        public String name = "?";
        public boolean bot, kit, floor2, coilSpeed, coilJump, malicious;
        public double money;
        /** bots only: movement level (random per bot, +3 with a speed coil) */
        public int speed, income, belt, slots, weapon, skin, rebirths;
        public long skinsOwned;
        public int base = -1;
    }

    public enum Upgrade {
        INCOME("income", "Income +15%", 300, 1.9, 30),
        BELT("belt", "Conveyor Belt", 200, 1.8, 20),
        SLOTS("slots", "Base Slots", 500, 3.0, 5);

        public final String id, label;
        public final double base, mult;
        public final int max;

        Upgrade(String id, String label, double base, double mult, int max) {
            this.id = id; this.label = label; this.base = base; this.mult = mult; this.max = max;
        }

        public int level(Stats s) {
            return switch (this) { case INCOME -> s.income; case BELT -> s.belt; case SLOTS -> s.slots; };
        }

        public void set(Stats s, int v) {
            switch (this) { case INCOME -> s.income = v; case BELT -> s.belt = v; case SLOTS -> s.slots = v; }
        }

        public double cost(int level) { return Math.floor(base * Math.pow(mult, level)); }

        public static Upgrade byId(String id) {
            for (Upgrade u : values()) if (u.id.equalsIgnoreCase(id)) return u;
            return null;
        }
    }

    public final Map<UUID, Stats> map = new HashMap<>();
    public final UUID[] baseOwner = new UUID[Arena.BASES];
    public boolean arenaBuilt;

    public static Economy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Economy::new, Economy::load, null), "eggsteal");
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
        for (int i = 0; i < Arena.BASES; i++) if (baseOwner[i] == null) return i;
        return -1;
    }

    public void release(UUID id) {
        Stats s = map.remove(id);
        if (s != null && s.base >= 0) baseOwner[s.base] = null;
        setDirty();
    }

    public static double rate(Stats s, Creature c) { return rate(s, c, 0); }

    /** Money per second of one creature: upgrades, rebirths, its mutation and the money-rain event all multiply it. */
    public static double rate(Stats s, Creature c, int mutation) {
        return c.income * (1 + 0.15 * s.income) * rebirthMult(s) * Mutation.of(mutation).mult * RandomEvents.incomeMult();
    }

    /** Steal time per guard level: rich bots watch their creatures much more closely. */
    public static final int[] GUARD_STEAL_TICKS = {0, 120, 260, 400};
    public static final String[] GUARD_NAMES = {"", "§eRich", "§6Very rich", "§c§lSuper rich"};

    /** How well a bot protects its creatures: 0 normal, 1 rich, 2 very rich, 3 super rich. Players are always 0. */
    public static int guard(Stats s) {
        if (s == null || !s.bot) return 0;
        if (s.money >= 20_000_000 || s.rebirths >= 4) return 3;
        if (s.money >= 1_000_000 || s.rebirths >= 2) return 2;
        if (s.money >= 50_000 || s.rebirths >= 1) return 1;
        return 0;
    }

    public static double rebirthMult(Stats s) { return 1 + 0.5 * s.rebirths; }
    public static double rebirthCost(Stats s) { return 1_000_000 * Math.pow(4, s.rebirths); }
    public static String rebirthTag(Stats s) { return s.rebirths > 0 ? "§d✦R" + s.rebirths + " " : ""; }

    /** Rebirth: money and the Income/Belt upgrades start over, in exchange for a permanent income boost and luckier belts. */
    public void rebirth(Stats s) {
        s.rebirths++;
        s.money = 500.0 * s.rebirths;
        s.income = 0;
        s.belt = 0;
        setDirty();
    }

    public static String fmt(double v) {
        if (v < 1000) return String.valueOf((long) v);
        String[] u = {"K", "M", "B", "T"};
        int i = -1;
        while (v >= 1000 && i < 3) { v /= 1000; i++; }
        return String.format(Locale.ROOT, "%.1f%s", v, u[i]);
    }

    public static Economy load(CompoundTag tag, HolderLookup.Provider reg) {
        Economy e = new Economy();
        ListTag l = tag.getList("stats", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            Stats s = new Stats();
            s.name = t.getString("name"); s.bot = t.getBoolean("bot"); s.kit = t.getBoolean("kit");
            s.money = t.getDouble("money"); s.speed = t.getInt("speed"); s.income = t.getInt("income");
            s.belt = t.getInt("belt"); s.slots = t.getInt("slots"); s.base = t.getInt("base");
            s.floor2 = t.getBoolean("floor2"); s.coilSpeed = t.getBoolean("coilSpeed"); s.coilJump = t.getBoolean("coilJump"); s.weapon = t.getInt("weapon");
            s.malicious = t.getBoolean("malicious"); s.skin = t.getInt("skin"); s.skinsOwned = t.getLong("skinsOwned");
            s.rebirths = t.getInt("rebirths");
            e.map.put(t.getUUID("id"), s);
        }
        ListTag b = tag.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < b.size() && i < Arena.BASES; i++) if (b.getCompound(i).hasUUID("o")) e.baseOwner[i] = b.getCompound(i).getUUID("o");
        e.arenaBuilt = tag.getBoolean("built");
        return e;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Stats> en : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            Stats s = en.getValue();
            t.putUUID("id", en.getKey());
            t.putString("name", s.name); t.putBoolean("bot", s.bot); t.putBoolean("kit", s.kit);
            t.putDouble("money", s.money); t.putInt("speed", s.speed); t.putInt("income", s.income);
            t.putInt("belt", s.belt); t.putInt("slots", s.slots); t.putInt("base", s.base);
            t.putBoolean("floor2", s.floor2); t.putBoolean("coilSpeed", s.coilSpeed); t.putBoolean("coilJump", s.coilJump); t.putInt("weapon", s.weapon);
            t.putBoolean("malicious", s.malicious); t.putInt("skin", s.skin); t.putLong("skinsOwned", s.skinsOwned);
            t.putInt("rebirths", s.rebirths);
            l.add(t);
        }
        tag.put("stats", l);
        ListTag b = new ListTag();
        for (int i = 0; i < Arena.BASES; i++) {
            CompoundTag t = new CompoundTag();
            if (baseOwner[i] != null) t.putUUID("o", baseOwner[i]);
            b.add(t);
        }
        tag.put("bases", b);
        tag.putBoolean("built", arenaBuilt);
        return tag;
    }
}
