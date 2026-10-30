package com.gunsmod.menu;

import com.gunsmod.Reg;
import com.gunsmod.block.SpawnerBlockEntity;
import com.gunsmod.item.DogTagItem;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SpawnerMenu extends AbstractContainerMenu {
    @Nullable private final SpawnerBlockEntity be;
    public final ContainerData data;
    private final Container inv;

    public SpawnerMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        super(Reg.SPAWNER_MENU.get(), id);
        this.be = null;
        this.data = new SimpleContainerData(4);
        this.inv = new SimpleContainer(1);
        init(playerInv);
    }

    public SpawnerMenu(int id, Inventory playerInv, SpawnerBlockEntity be) {
        super(Reg.SPAWNER_MENU.get(), id);
        this.be = be;
        this.data = be.data;
        this.inv = be.inventory;
        init(playerInv);
    }

    private void init(Inventory playerInv) {
        this.addSlot(new Slot(inv, 0, 26, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DogTagItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        MenuUtil.addPlayerSlots(this::addSlot, playerInv, 26, 120);
        this.addDataSlots(data);
    }

    public int count() {
        return data.get(0);
    }

    public int delay() {
        return data.get(1);
    }

    public boolean active() {
        return data.get(2) != 0;
    }

    public int alive() {
        return data.get(3);
    }

    public ItemStack tag() {
        return this.slots.get(0).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null) be.button(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        if (index == 0) {
            if (!this.moveItemStackTo(st, 1, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (st.getItem() instanceof DogTagItem && !this.slots.get(0).hasItem()) {
            this.slots.get(0).setByPlayer(st.split(1));
        } else {
            return MenuUtil.moveWithinPlayerInventory(this, index, 1);
        }
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return be == null || !be.isRemoved() && p.distanceToSqr(be.getBlockPos().getCenter()) < 64;
    }
}
