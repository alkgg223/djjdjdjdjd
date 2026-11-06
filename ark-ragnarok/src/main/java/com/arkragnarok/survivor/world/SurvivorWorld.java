package com.arkragnarok.survivor.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Everything the survivors of a world are, saved inside the world (data/arkragnarok_survivors.dat): one tribe per
 * summoned survivor - independent rivals by default - with its personality, skills, stockpile, base, stage, tamed
 * dinosaurs, relations and the history of what others did to it.  Survivors that are far away (not loaded) keep
 * progressing here in a cheap simulation (see OfflineSim) and their bases are completed when the area loads again.
 */
public class SurvivorWorld extends SavedData {
    public static final String ID = "arkragnarok_survivors";
    private final Map<UUID, Tribe> tribes = new LinkedHashMap<>();

    public static SurvivorWorld get(ServerLevel level) {
        ServerLevel ow = level.getServer().overworld();
        return ow.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SurvivorWorld::new, SurvivorWorld::load, null), ID);
    }

    public Tribe tribe(UUID id) {
        return tribes.computeIfAbsent(id, Tribe::new);
    }

    @Nullable
    public Tribe find(UUID id) {
        return tribes.get(id);
    }

    public Collection<Tribe> all() {
        return tribes.values();
    }

    public void remove(UUID id) {
        tribes.remove(id);
        for (Tribe t : tribes.values()) t.relations.remove(id);
        setDirty();
    }

    /** the tribe whose base contains this position, if any */
    @Nullable
    public Tribe ownerOf(BlockPos p) {
        for (Tribe t : tribes.values()) if (t.owns(p)) return t;
        return null;
    }

    /** something happened between two tribes: relations move, grudges are remembered */
    public void event(UUID actor, UUID victim, String what, int delta, long time) {
        Tribe v = find(victim);
        if (v != null) {
            v.relations.merge(actor, delta, Integer::sum);
            v.relations.computeIfPresent(actor, (k, x) -> Math.max(-100, Math.min(100, x)));
            v.history.add(new Tribe.Event(time, actor, what, delta));
            while (v.history.size() > 40) v.history.remove(0);
            if (delta < -15 && actor.equals(v.ally)) v.ally = null;            // an ally who hurts us is no ally
        }
        Tribe a = find(actor);
        if (a != null && delta > 0) {
            a.relations.merge(victim, delta / 2, Integer::sum);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        for (Tribe t : tribes.values()) l.add(t.save());
        tag.put("Tribes", l);
        return tag;
    }

    public static SurvivorWorld load(CompoundTag tag, HolderLookup.Provider reg) {
        SurvivorWorld w = new SurvivorWorld();
        for (Tag e : tag.getList("Tribes", Tag.TAG_COMPOUND)) {
            Tribe t = Tribe.load((CompoundTag) e);
            w.tribes.put(t.id, t);
        }
        return w;
    }

    /** one survivor's tribe (the survivor and the dinosaurs it tamed) */
    public static final class Tribe {
        public record Event(long time, UUID other, String what, int delta) {}

        public final UUID id;
        public String name = "";
        public int stage = 1;
        public int design = -1;
        @Nullable
        public BlockPos base;
        public int[] built = new int[0];
        public int[] footprint = new int[0];           // design bounds relative to the base
        public BlockPos lastPos = BlockPos.ZERO;
        public long lastSim;
        public final Map<String, Integer> stock = new HashMap<>();     // resources stored at the base (item id -> count)
        public final Map<UUID, Integer> relations = new HashMap<>();
        public final List<Event> history = new ArrayList<>();
        public final List<UUID> tames = new ArrayList<>();
        @Nullable
        public UUID ally;
        public long allyUntil;
        public int raids, defended, piecesBuilt, piecesLost;
        public int rotation;                                           // quarter turns of the design on its site
        public boolean manualDesign;                                   // a player picked the design
        @Nullable
        public com.arkragnarok.survivor.Personality personality;
        public final com.arkragnarok.survivor.Skills skills = new com.arkragnarok.survivor.Skills();
        public int ingots, electronics, tamed, kills, crafted;
        public long lastRaid;

        Tribe(UUID id) {
            this.id = id;
        }

        public com.arkragnarok.survivor.Personality personality() {
            if (personality == null) personality = com.arkragnarok.survivor.Personality.random(id.getLeastSignificantBits());
            return personality;
        }

        public int relation(UUID other) {
            return relations.getOrDefault(other, -10);                 // rivals by default
        }

        public boolean owns(BlockPos p) {
            if (base == null || footprint.length < 5) return false;
            int dx = p.getX() - base.getX(), dz = p.getZ() - base.getZ(), dy = p.getY() - base.getY();
            int low = footprint.length > 5 ? footprint[5] : 0;
            return dx >= footprint[0] - 1 && dx <= footprint[1] + 1 && dz >= footprint[2] - 1 && dz <= footprint[3] + 1 && dy >= low - 2
                    && dy <= footprint[4] + 2;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putString("Name", name);
            t.putInt("Stage", stage);
            t.putInt("Design", design);
            if (base != null) t.putLong("Base", base.asLong());
            t.putIntArray("Built", built);
            t.putIntArray("Footprint", footprint);
            t.putLong("LastPos", lastPos.asLong());
            t.putLong("LastSim", lastSim);
            CompoundTag st = new CompoundTag();
            stock.forEach(st::putInt);
            t.put("Stock", st);
            ListTag rel = new ListTag();
            relations.forEach((k, v) -> {
                CompoundTag r = new CompoundTag();
                r.putUUID("O", k);
                r.putInt("V", v);
                rel.add(r);
            });
            t.put("Relations", rel);
            ListTag h = new ListTag();
            for (Event e : history) {
                CompoundTag r = new CompoundTag();
                r.putLong("T", e.time());
                r.putUUID("O", e.other());
                r.putString("W", e.what());
                r.putInt("D", e.delta());
                h.add(r);
            }
            t.put("History", h);
            ListTag tm = new ListTag();
            for (UUID u : tames) {
                CompoundTag r = new CompoundTag();
                r.putUUID("U", u);
                tm.add(r);
            }
            t.put("Tames", tm);
            if (ally != null) t.putUUID("Ally", ally);
            t.putLong("AllyUntil", allyUntil);
            t.putIntArray("Counters", new int[]{raids, defended, piecesBuilt, piecesLost, ingots, electronics, tamed, kills, crafted});
            t.putInt("Rotation", rotation);
            t.putBoolean("Manual", manualDesign);
            if (personality != null) t.put("Personality", personality.save());
            t.put("Skills", skills.save());
            t.putLong("LastRaid", lastRaid);
            return t;
        }

        static Tribe load(CompoundTag t) {
            Tribe r = new Tribe(t.getUUID("Id"));
            r.name = t.getString("Name");
            r.stage = Math.max(1, t.getInt("Stage"));
            r.design = t.contains("Design") ? t.getInt("Design") : -1;
            r.base = t.contains("Base") ? BlockPos.of(t.getLong("Base")) : null;
            r.built = t.getIntArray("Built");
            r.footprint = t.getIntArray("Footprint");
            r.lastPos = BlockPos.of(t.getLong("LastPos"));
            r.lastSim = t.getLong("LastSim");
            CompoundTag st = t.getCompound("Stock");
            for (String k : st.getAllKeys()) r.stock.put(k, st.getInt(k));
            for (Tag e : t.getList("Relations", Tag.TAG_COMPOUND)) r.relations.put(((CompoundTag) e).getUUID("O"), ((CompoundTag) e).getInt("V"));
            for (Tag e : t.getList("History", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                r.history.add(new Event(c.getLong("T"), c.getUUID("O"), c.getString("W"), c.getInt("D")));
            }
            for (Tag e : t.getList("Tames", Tag.TAG_COMPOUND)) r.tames.add(((CompoundTag) e).getUUID("U"));
            r.ally = t.hasUUID("Ally") ? t.getUUID("Ally") : null;
            r.allyUntil = t.getLong("AllyUntil");
            int[] c = t.getIntArray("Counters");
            if (c.length >= 4) {
                r.raids = c[0];
                r.defended = c[1];
                r.piecesBuilt = c[2];
                r.piecesLost = c[3];
            }
            if (c.length >= 9) {
                r.ingots = c[4];
                r.electronics = c[5];
                r.tamed = c[6];
                r.kills = c[7];
                r.crafted = c[8];
            }
            r.rotation = t.getInt("Rotation");
            r.manualDesign = t.getBoolean("Manual");
            if (t.contains("Personality")) r.personality = com.arkragnarok.survivor.Personality.load(t.getCompound("Personality"));
            r.skills.load(t.getCompound("Skills"));
            r.lastRaid = t.getLong("LastRaid");
            return r;
        }
    }
}
