package com.rpbusiness.business;

import net.minecraft.nbt.CompoundTag;

/** A job applicant shown in the hiring screen. Nobody is hired without the player clicking "Hire". */
public record Candidate(String name, int skill, int salary, Job preferred, int skin) {
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name); t.putInt("skill", skill); t.putInt("salary", salary);
        t.putString("job", preferred.name()); t.putInt("skin", skin);
        return t;
    }
    public static Candidate load(CompoundTag t) {
        return new Candidate(t.getString("name"), t.getInt("skill"), t.getInt("salary"), Job.byName(t.getString("job")), t.getInt("skin"));
    }
}
