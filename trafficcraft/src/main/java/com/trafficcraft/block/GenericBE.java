package com.trafficcraft.block;

import com.trafficcraft.registry.TCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Data-only block entity (text signs, cameras, fuel pumps/tanks, workplaces): keeps a CompoundTag of settings. */
public class GenericBE extends SyncedBE implements BEBlock.TickingBE {
    public CompoundTag data = new CompoundTag();
    public float anim;

    public GenericBE(BlockPos p, BlockState s) {
        super(TCBlocks.GENERIC_BE.get(), p, s);
    }

    @Override
    protected void write(CompoundTag t) {
        t.put("d", data.copy());
    }

    @Override
    protected void read(CompoundTag t) {
        data = t.getCompound("d");
    }

    @Override
    public void serverTick() {
        if (getBlockState().getBlock() instanceof BEBlock b && b.kind.equals("fuel_pump")) com.trafficcraft.station.FuelStations.pumpTick(this);
    }

    @Override
    public void clientTick() {
        anim += 1;
    }
}
