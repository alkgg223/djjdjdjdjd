package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Money safe: only the player who placed it can open it. */
public class SafeBE extends StorageBE {
    public UUID owner;
    public SafeBE(BlockPos pos, BlockState state) { super(ModBlockEntities.SAFE.get(), pos, state); }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        if (owner != null) t.putUUID("owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
    }
}
