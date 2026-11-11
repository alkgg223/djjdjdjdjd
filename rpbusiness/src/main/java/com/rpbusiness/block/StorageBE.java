package com.rpbusiness.block;

import com.rpbusiness.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Fridge, freezer, cabinets, shelves, delivery crates and packages. Employees take ingredients from these. */
public class StorageBE extends BaseContainerBlockEntity {
    protected NonNullList<ItemStack> items;
    protected int openers;

    public StorageBE(BlockPos pos, BlockState state) { this(ModBlockEntities.STORAGE.get(), pos, state); }

    protected StorageBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int size = state.getBlock() instanceof StorageBlock sb ? sb.size : 27;
        items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override public int getContainerSize() { return items.size(); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        int n = items.size();
        if (n == 9) return new DispenserMenu(id, inv, this);
        if (n == 18) return new ChestMenu(MenuType.GENERIC_9x2, id, inv, this, 2);
        return ChestMenu.threeRows(id, inv, this);
    }

    @Override
    public void startOpen(Player p) {
        if (!p.isSpectator()) { openers++; onOpenersChanged(); }
    }

    @Override
    public void stopOpen(Player p) {
        if (!p.isSpectator()) { openers = Math.max(0, openers - 1); onOpenersChanged(); }
        if (openers == 0 && isEmpty() && getBlockState().getBlock() instanceof StorageBlock sb && sb.shape == StorageBlock.PACKAGE && level != null)
            level.removeBlock(worldPosition, false);
    }

    protected void onOpenersChanged() {}

    public int extract(java.util.function.Predicate<ItemStack> test, int n, java.util.List<ItemStack> out) {
        return ContainerUtil.extract(this, test, n, out);
    }

    public ItemStack insert(ItemStack stack) { return ContainerUtil.insert(this, stack); }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.saveAdditional(t, p);
        ContainerHelper.saveAllItems(t, items, p);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider p) {
        super.loadAdditional(t, p);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(t, items, p);
    }
}
