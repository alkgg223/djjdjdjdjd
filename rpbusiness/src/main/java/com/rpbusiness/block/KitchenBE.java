package com.rpbusiness.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Base for equipment block entities whose contents are visible (synced to the client). */
public abstract class KitchenBE extends BlockEntity {
    protected KitchenBE(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    public void serverTick() {}

    /** Items to drop when the block is broken. */
    public abstract List<ItemStack> contents();

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack s : contents()) if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, s.copy());
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) { return saveWithoutMetadata(provider); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    protected static CompoundTag saveStack(ItemStack s, HolderLookup.Provider p) {
        return s.isEmpty() ? new CompoundTag() : (CompoundTag) s.save(p);
    }
    protected static ItemStack loadStack(CompoundTag t, HolderLookup.Provider p) {
        return t.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(p, t);
    }
}
