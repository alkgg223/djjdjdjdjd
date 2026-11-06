package com.arkragnarok.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StorageBoxEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items;

    public StorageBoxEntity(BlockPos pos, BlockState state) {
        super(ModEconomy.STORAGE_BE.get(), pos, state);
        items = NonNullList.withSize(big(state) ? 54 : 27, ItemStack.EMPTY);
    }

    private static boolean big(BlockState s) {
        return s.getBlock() instanceof StorageBoxBlock b && b.big;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(big(getBlockState()) ? "block.arkragnarok.large_storage_box" : "block.arkragnarok.storage_box");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        items = list;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return big(getBlockState()) ? ChestMenu.sixRows(id, inv, this) : ChestMenu.threeRows(id, inv, this);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, reg);
    }
}
