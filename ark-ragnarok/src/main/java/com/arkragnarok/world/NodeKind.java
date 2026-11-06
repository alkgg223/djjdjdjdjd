package com.arkragnarok.world;

/** resource node kinds (ARK: rocks give stone and flint, metal rocks metal, obsidian and crystal need a pick) */
public enum NodeKind {
    ROCK("rock", 60f), METAL("metal", 110f), OBSIDIAN("obsidian", 140f), CRYSTAL("crystal", 90f);

    public final String id;
    /** health of the medium size; small has half, large double */
    public final float health;

    NodeKind(String id, float health) {
        this.id = id;
        this.health = health;
    }

    public static NodeKind of(int i) {
        NodeKind[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
