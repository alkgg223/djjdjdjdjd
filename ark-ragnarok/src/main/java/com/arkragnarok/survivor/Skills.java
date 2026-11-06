package com.arkragnarok.survivor;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/** experience per skill; levels make the work faster and better (more yield, sturdier builds, better aim) */
public final class Skills {
    public enum Skill { GATHERING, MINING, BUILDING, CRAFTING, COMBAT, TAMING }

    private final Map<Skill, Float> xp = new EnumMap<>(Skill.class);

    public void gain(Skill s, float amount, Personality p) {
        float k = s == Skill.CRAFTING || s == Skill.BUILDING ? 0.6f + p.craft() : 0.6f + p.intelligence() * 0.8f;
        xp.merge(s, amount * k, Float::sum);
    }

    public int level(Skill s) {
        return (int) Math.min(50, Math.sqrt(xp.getOrDefault(s, 0f) / 6f));
    }

    /** work speed multiplier: 1.0 at level 0, 2.5 at level 50 */
    public float speed(Skill s) {
        return 1f + level(s) * 0.03f;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        for (var e : xp.entrySet()) t.putFloat(e.getKey().name(), e.getValue());
        return t;
    }

    public void load(CompoundTag t) {
        xp.clear();
        for (Skill s : Skill.values()) if (t.contains(s.name())) xp.put(s, t.getFloat(s.name()));
    }

    public String summary() {
        StringBuilder b = new StringBuilder();
        for (Skill s : Skill.values()) b.append(s.name().charAt(0)).append(s.name().substring(1, 3).toLowerCase()).append(' ').append(level(s)).append("  ");
        return b.toString().trim();
    }
}
