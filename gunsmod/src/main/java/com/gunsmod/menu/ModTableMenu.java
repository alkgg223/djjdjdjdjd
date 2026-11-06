package com.gunsmod.menu;

import com.gunsmod.Reg;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.item.AttachmentItem;
import com.gunsmod.item.GunItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Gun in slot 0, attachments in slots 1..5 (scope, muzzle, grip, laser, magazine). The attachment slots mirror the
 * attachments installed on the gun: putting one in installs it, taking it out removes it.
 */
public class ModTableMenu extends AbstractContainerMenu {
    public static final Attachment.Slot[] SLOTS = Attachment.Slot.values();
    /** slot positions inside the screen */
    public static final int[][] POS = {{171, 152}, {40, 30}, {304, 30}, {40, 110}, {304, 110}, {40, 70}};

    private final ContainerLevelAccess access;
    private final SimpleContainer table = new SimpleContainer(6);
    private boolean syncing;
    private ItemStack lastGun = ItemStack.EMPTY;
    public final Player player;

    public ModTableMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public ModTableMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(Reg.MOD_TABLE_MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        // gun slot (centre bottom of the preview)
        this.addSlot(new Slot(table, 0, POS[0][0], POS[0][1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof GunItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < SLOTS.length; i++) {
            final Attachment.Slot kind = SLOTS[i];
            int[] p = POS[i + 1];
            this.addSlot(new Slot(table, i + 1, p[0], p[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    ItemStack gun = table.getItem(0);
                    return gun.getItem() instanceof GunItem g && stack.getItem() instanceof AttachmentItem a
                            && a.attachment.slot == kind && g.type.supports(kind);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    ItemStack gun = table.getItem(0);
                    return gun.getItem() instanceof GunItem g && g.type.supports(kind);
                }
            });
        }
        MenuUtil.addPlayerSlots(this::addSlot, inv, 99, 180);
        table.addListener(this::slotsChanged);
    }

    @Override
    public void slotsChanged(Container c) {
        super.slotsChanged(c);
        if (syncing || c != table) return;
        syncing = true;
        try {
            ItemStack gun = table.getItem(0);
            if (gun != lastGun) {
                // a gun was inserted / removed: show its attachments
                lastGun = gun;
                GunAttachments att = gun.getItem() instanceof GunItem ? GunItem.attachments(gun) : GunAttachments.NONE;
                for (int i = 0; i < SLOTS.length; i++) {
                    Attachment a = att.get(SLOTS[i]);
                    table.setItem(i + 1, a == null ? ItemStack.EMPTY : new ItemStack(Reg.ATTACHMENT_ITEMS.get(a).get()));
                }
            } else if (gun.getItem() instanceof GunItem g) {
                GunAttachments att = GunAttachments.NONE;
                for (int i = 0; i < SLOTS.length; i++) {
                    ItemStack s = table.getItem(i + 1);
                    if (s.getItem() instanceof AttachmentItem a) att = att.with(SLOTS[i], a.attachment);
                }
                GunAttachments old = GunItem.attachments(gun);
                if (!att.equals(old)) {
                    if (att.equals(GunAttachments.NONE)) gun.remove(Reg.ATTACHMENTS.get());
                    else gun.set(Reg.ATTACHMENTS.get(), att);
                    int mag = att.magSize(g.type);
                    if (GunItem.ammo(gun) > mag) GunItem.setAmmo(gun, mag);
                    if (!player.level().isClientSide) {
                        player.level().playSound(null, player.blockPosition(), Reg.sound("arm_switch"), SoundSource.BLOCKS, 0.8f, 1.1f);
                    }
                }
            }
        } finally {
            syncing = false;
        }
    }

    public ItemStack gun() {
        return table.getItem(0);
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        // the attachment slots only mirror the gun, so only the gun itself goes back to the player
        ItemStack gun = table.removeItemNoUpdate(0);
        for (int i = 1; i < 6; i++) table.removeItemNoUpdate(i);
        if (!gun.isEmpty() && !p.level().isClientSide) {
            if (!p.isAlive() || p instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasDisconnected()) p.drop(gun, false);
            else p.getInventory().placeItemBackInInventory(gun);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        if (index < 6) {
            if (!this.moveItemStackTo(st, 6, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (st.getItem() instanceof GunItem && !this.slots.get(0).hasItem()) {
                this.slots.get(0).setByPlayer(st.split(1));
            } else if (st.getItem() instanceof AttachmentItem) {
                boolean placed = false;
                for (int i = 1; i < 6 && !placed; i++) {
                    Slot s = this.slots.get(i);
                    if (!s.hasItem() && s.mayPlace(st)) {
                        s.setByPlayer(st.split(1));
                        placed = true;
                    }
                }
                if (!placed) return ItemStack.EMPTY;
            } else {
                return MenuUtil.moveWithinPlayerInventory(this, index, 6);
            }
        }
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return stillValid(access, p, Reg.MOD_TABLE.get());
    }
}
