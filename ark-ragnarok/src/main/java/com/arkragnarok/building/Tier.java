package com.arkragnarok.building;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;

/** building tiers like ARK: thatch (weak), wood, stone, iron, steel (strongest) */
public enum Tier {
    THATCH("thatch", 1.2f, 2f, SoundType.GRASS, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundEvents.WOODEN_TRAPDOOR_CLOSE),
    WOOD("wood", 3.0f, 6f, SoundType.WOOD, SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_DOOR_CLOSE),
    STONE("stone", 6.0f, 40f, SoundType.STONE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_DOOR_CLOSE),
    IRON("iron", 14.0f, 300f, SoundType.METAL, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_DOOR_CLOSE),
    STEEL("steel", 30.0f, 1500f, SoundType.NETHERITE_BLOCK, SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE);

    public final String id;
    public final float hardness, resistance;
    public final SoundType sound;
    public final SoundEvent open, close;

    Tier(String id, float hardness, float resistance, SoundType sound, SoundEvent open, SoundEvent close) {
        this.id = id;
        this.hardness = hardness;
        this.resistance = resistance;
        this.sound = sound;
        this.open = open;
        this.close = close;
    }
}
