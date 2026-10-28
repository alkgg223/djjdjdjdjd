package com.gunsmod.menu;

import com.gunsmod.Reg;
import com.gunsmod.block.ArenaBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Arena command console (no item slots, only settings + live status). */
public class ArenaMenu extends AbstractContainerMenu {
    @Nullable private final ArenaBlockEntity be;
    public final ContainerData data;

    public ArenaMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(Reg.ARENA_MENU.get(), id);
        this.be = null;
        this.data = new SimpleContainerData(10);
        this.addDataSlots(data);
    }

    public ArenaMenu(int id, Inventory inv, ArenaBlockEntity be) {
        super(Reg.ARENA_MENU.get(), id);
        this.be = be;
        this.data = be.data;
        this.addDataSlots(data);
    }

    public int get(int i) {
        return data.get(i);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null) be.button(id, player);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return be == null || !be.isRemoved() && p.distanceToSqr(be.getBlockPos().getCenter()) < 100;
    }
}
