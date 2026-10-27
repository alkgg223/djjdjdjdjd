package com.eggsteal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * An invisible entity in the middle of the arena that tells every client which random event is running,
 * so the client can draw the event in the sky (see client/EventSkyRenderer). It is never saved.
 */
public class EventMarkerEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(EventMarkerEntity.class, EntityDataSerializers.INT);
    /** Client only: 0..1 fade of the sky effect, and the last event shown (kept while fading out). */
    public float fade, fadeO;
    public int shown = -1;

    public EventMarkerEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, -1);
    }

    /** The running RandomEvents.Kind ordinal, or -1. */
    public int kind() { return entityData.get(KIND); }

    public void setKind(int k) { if (kind() != k) entityData.set(KIND, k); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        fadeO = fade;
        int k = kind();
        if (k >= 0) {
            if (shown != k && fade > 0.05f) fade = Math.max(0f, fade - 0.04f); // switch events through a quick fade
            else { shown = k; fade = Math.min(1f, fade + 0.015f); }
        } else fade = Math.max(0f, fade - 0.015f);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }
    @Override protected void readAdditionalSaveData(CompoundTag t) { }
    @Override protected void addAdditionalSaveData(CompoundTag t) { }
}
