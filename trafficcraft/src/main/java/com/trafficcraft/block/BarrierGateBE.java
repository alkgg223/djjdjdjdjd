package com.trafficcraft.block;

import com.trafficcraft.registry.TCBlocks;
import com.trafficcraft.registry.TCRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Animated boom barrier. While lowered it places invisible collision blocks along the arm. */
public class BarrierGateBE extends SyncedBE implements BEBlock.TickingBE {
    public boolean open;
    public int length = 5;
    public boolean manual;
    public float anim, animO;
    private int holdOpen;
    private boolean armPlaced;

    public BarrierGateBE(BlockPos p, BlockState s) {
        super(TCBlocks.BARRIER_GATE_BE.get(), p, s);
    }

    @Override
    protected void write(CompoundTag t) {
        t.putBoolean("open", open);
        t.putInt("len", length);
        t.putBoolean("manual", manual);
    }

    @Override
    protected void read(CompoundTag t) {
        open = t.getBoolean("open");
        length = t.contains("len") ? t.getInt("len") : 5;
        manual = t.getBoolean("manual");
    }

    /** Direction the arm extends (to the left of the post when looking at its front). */
    public Direction armDir() {
        return getBlockState().getValue(FacingBlock.FACING).getCounterClockWise();
    }

    public void setOpen(boolean o, int holdTicks) {
        if (o) holdOpen = Math.max(holdOpen, holdTicks);
        if (open == o) return;
        open = o;
        if (level != null) level.playSound(null, worldPosition, TCRegistry.snd("misc.barrier_motor"), SoundSource.BLOCKS, 0.8f, 1f);
        sync();
    }

    public boolean isPassable() {
        return open && anim > 0.9f;
    }

    @Override
    public void serverTick() {
        animO = anim;
        anim += ((open ? 1 : 0) - anim) * 0.12f;
        if (holdOpen > 0 && --holdOpen == 0 && !manual) setOpen(false, 0);
        boolean wantArm = !open || anim < 0.6f;
        if (wantArm != armPlaced || level.getGameTime() % 40 == 0) {
            Direction d = armDir();
            for (int k = 1; k <= length; k++) {
                BlockPos q = worldPosition.relative(d, k);
                BlockState st = level.getBlockState(q);
                if (wantArm && st.isAir()) level.setBlock(q, TCBlocks.get("barrier_arm").defaultBlockState(), 3);
                else if (!wantArm && st.is(TCBlocks.get("barrier_arm"))) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            }
            armPlaced = wantArm;
        }
    }

    @Override
    public void clientTick() {
        animO = anim;
        anim += ((open ? 1 : 0) - anim) * 0.12f;
    }

    public void removeArm() {
        if (level == null) return;
        Direction d = armDir();
        for (int k = 1; k <= length; k++) {
            BlockPos q = worldPosition.relative(d, k);
            if (level.getBlockState(q).is(TCBlocks.get("barrier_arm"))) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
