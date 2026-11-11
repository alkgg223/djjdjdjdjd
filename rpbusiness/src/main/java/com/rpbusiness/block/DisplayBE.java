package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shop display shelf / refrigerated display. Products are visible to the player and customers;
 * the shelf remembers which product belongs in each slot so stock managers can refill it.
 */
public class DisplayBE extends StorageBE {
    public final String[] planogram = new String[9];

    public DisplayBE(BlockPos pos, BlockState state) { super(ModBlockEntities.DISPLAY.get(), pos, state); }

    @Override
    public void setChanged() {
        super.setChanged();
        for (int i = 0; i < Math.min(9, items.size()); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) planogram[i] = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        }
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p) { return saveWithoutMetadata(p); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        CompoundTag pl = new CompoundTag();
        for (int i = 0; i < 9; i++) if (planogram[i] != null) pl.putString("s" + i, planogram[i]);
        t.put("planogram", pl);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        CompoundTag pl = t.getCompound("planogram");
        for (int i = 0; i < 9; i++) planogram[i] = pl.contains("s" + i) ? pl.getString("s" + i) : null;
    }

    public boolean hasProducts() { return !isEmpty(); }
}
