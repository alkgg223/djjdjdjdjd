package com.gunsmod.menu;

import com.gunsmod.Reg;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Weapon workbench. The recipe list and 3D preview are client-side; crafting is a menu button (index = recipe). */
public class WorkbenchMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    public final Player player;

    public WorkbenchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public WorkbenchMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(Reg.WORKBENCH_MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        MenuUtil.addPlayerSlots(this::addSlot, inv, 99, 172);
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        List<WorkbenchRecipes.Recipe> all = WorkbenchRecipes.all();
        if (id < 0 || id >= all.size()) return false;
        WorkbenchRecipes.Recipe r = all.get(id);
        if (!r.canCraft(p)) return false;
        WorkbenchRecipes.consume(p, r);
        ItemStack out = r.result().copy();
        if (!p.getInventory().add(out)) p.drop(out, false);
        p.level().playSound(null, p.blockPosition(), Reg.sound("bolt"), SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return MenuUtil.moveWithinPlayerInventory(this, index, 0);
    }

    @Override
    public boolean stillValid(Player p) {
        return stillValid(access, p, Reg.WORKBENCH.get());
    }

    public List<Slot> playerSlots() {
        return this.slots;
    }
}
