package com.arkragnarok.survivor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/**
 * What a survivor has seen with its own eyes (no x-ray): trees, cave openings, water, resource nodes and dangerous
 * animals, each with the time it was last seen.  Places that turn out to be gone are forgotten when revisited.
 */
public final class Memory {
    public enum Kind { TREE, CAVE, WATER, NODE, THREAT, FLAT, DINO, PREY, BASE, STONE }

    public record Fact(Kind kind, BlockPos pos, long seen, @Nullable UUID entity, String detail) {}

    private final Map<Kind, Map<Long, Fact>> facts = new HashMap<>();
    private static final int MAX_PER_KIND = 160;

    public void see(Kind kind, BlockPos pos, long time, @Nullable UUID entity, String detail) {
        Map<Long, Fact> m = facts.computeIfAbsent(kind, k -> new HashMap<>());
        long key = entity != null ? entity.getLeastSignificantBits() : (kind == Kind.TREE || kind == Kind.WATER || kind == Kind.FLAT
                || kind == Kind.BASE || kind == Kind.STONE
                ? BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2) : pos.asLong());
        m.put(key, new Fact(kind, pos.immutable(), time, entity, detail));
        if (m.size() > MAX_PER_KIND) {
            // forget the oldest
            Fact old = m.values().stream().min(Comparator.comparingLong(Fact::seen)).orElse(null);
            if (old != null) m.values().remove(old);
        }
    }

    public void forget(Fact f) {
        Map<Long, Fact> m = facts.get(f.kind());
        if (m != null) m.values().remove(f);
    }

    public int count(Kind k) {
        Map<Long, Fact> m = facts.get(k);
        return m == null ? 0 : m.size();
    }

    public List<Fact> all(Kind k) {
        Map<Long, Fact> m = facts.get(k);
        return m == null ? List.of() : new ArrayList<>(m.values());
    }

    @Nullable
    public Fact nearest(Kind k, Vec3 from, double maxDist) {
        Fact best = null;
        double bd = maxDist * maxDist;
        for (Fact f : all(k)) {
            double d = f.pos().distToCenterSqr(from);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        for (Kind k : Kind.values()) {
            if (k == Kind.THREAT || k == Kind.NODE || k == Kind.DINO || k == Kind.PREY) continue;   // moving things are re-seen
            ListTag l = new ListTag();
            for (Fact f : all(k)) l.add(LongTag.valueOf(f.pos().asLong()));
            t.put(k.name(), l);
        }
        return t;
    }

    public void load(CompoundTag t, long now) {
        for (Kind k : Kind.values()) {
            if (!t.contains(k.name())) continue;
            ListTag l = t.getList(k.name(), Tag.TAG_LONG);
            for (Tag e : l) see(k, BlockPos.of(((LongTag) e).getAsLong()), now, null, "");
        }
    }
}
