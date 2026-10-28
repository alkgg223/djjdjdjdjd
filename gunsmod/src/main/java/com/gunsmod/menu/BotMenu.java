package com.gunsmod.menu;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.item.GunItem;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.RemoteItem;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Soldier editor: weapon, armour, launch remote and missile rack slots, face and grenades. */
public class BotMenu extends AbstractContainerMenu {
    public static final EquipmentSlot[] EQUIP = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
    /** slots: 0 weapon, 1-4 armour, 5 remote (off hand), 6-9 missile rack */
    public static final int REMOTE_SLOT = 5, RACK_START = 6, BOT_SLOTS = 10;

    @Nullable public final BotEntity bot;
    public final int botId;
    public final ContainerData data;

    public BotMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readVarInt());
    }

    private BotMenu(int id, Inventory inv, int botId) {
        super(Reg.BOT_MENU.get(), id);
        this.botId = botId;
        this.bot = inv.player.level().getEntity(botId) instanceof BotEntity b ? b : null;
        this.data = new SimpleContainerData(5);
        init(inv, new SimpleContainer(BOT_SLOTS));
    }

    public BotMenu(int id, Inventory inv, BotEntity bot) {
        super(Reg.BOT_MENU.get(), id);
        this.bot = bot;
        this.botId = bot.getId();
        this.data = new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> 0;
                    case 1 -> bot.getSkin();
                    case 2 -> bot.grenades;
                    case 3 -> bot.getBotTeam().ordinal();
                    case 4 -> bot.artillery.active() ? RemoteItem.links(bot.getOffhandItem()).stations().size() : -1;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {
            }

            @Override
            public int getCount() {
                return 5;
            }
        };
        init(inv, new EquipmentContainer(bot));
    }

    private void init(Inventory inv, Container equip) {
        for (int i = 0; i < EQUIP.length; i++) {
            final EquipmentSlot slot = EQUIP[i];
            int x = i == 0 || i == REMOTE_SLOT ? 10 : 32 + (i - 1) * 18;
            int y = i == REMOTE_SLOT ? 70 : 34;
            this.addSlot(new Slot(equip, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (slot == EquipmentSlot.MAINHAND) return stack.getItem() instanceof GunItem || !(stack.getItem() instanceof ArmorItem);
                    if (slot == EquipmentSlot.OFFHAND) return stack.getItem() instanceof RemoteItem;
                    return stack.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int i = 0; i < 4; i++) {
            this.addSlot(new Slot(equip, RACK_START + i, 32 + i * 18, 70) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MissileItem;
                }
            });
        }
        MenuUtil.addPlayerSlots(this::addSlot, inv, 26, 120);
        this.addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (bot == null || player.level().isClientSide) return true;
        switch (id) {
            case 3 -> bot.setSkin(bot.getSkin() + 1);
            case 4 -> bot.grenades = Math.min(5, bot.grenades + 1);
            default -> { }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        if (index < BOT_SLOTS) {
            if (!this.moveItemStackTo(st, BOT_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (st.getItem() instanceof MissileItem) {
            if (!this.moveItemStackTo(st, RACK_START, BOT_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            boolean placed = false;
            for (int i = 0; i < RACK_START && !placed; i++) {
                Slot s = this.slots.get(i);
                if (!s.hasItem() && s.mayPlace(st)) {
                    s.setByPlayer(st.split(1));
                    placed = true;
                }
            }
            if (!placed) return MenuUtil.moveWithinPlayerInventory(this, index, BOT_SLOTS);
        }
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return bot == null || bot.isAlive() && p.distanceToSqr(bot) < 64;
    }

    /** live view on the soldier's equipment (0-5) and missile rack (6-9) */
    private static class EquipmentContainer implements Container {
        private final BotEntity bot;

        EquipmentContainer(BotEntity bot) {
            this.bot = bot;
        }

        @Override
        public int getContainerSize() {
            return BOT_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot s : EQUIP) if (!bot.getItemBySlot(s).isEmpty()) return false;
            return bot.missiles.isEmpty();
        }

        @Override
        public ItemStack getItem(int i) {
            return i >= RACK_START ? bot.missiles.getItem(i - RACK_START) : bot.getItemBySlot(EQUIP[i]);
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            if (i >= RACK_START) return bot.missiles.removeItem(i - RACK_START, n);
            ItemStack s = bot.getItemBySlot(EQUIP[i]);
            ItemStack out = s.split(n);
            bot.setItemSlot(EQUIP[i], s.isEmpty() ? ItemStack.EMPTY : s);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            if (i >= RACK_START) return bot.missiles.removeItemNoUpdate(i - RACK_START);
            ItemStack s = bot.getItemBySlot(EQUIP[i]);
            bot.setItemSlot(EQUIP[i], ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            if (i >= RACK_START) bot.missiles.setItem(i - RACK_START, stack);
            else bot.setItemSlot(EQUIP[i], stack);
        }

        @Override
        public void setChanged() {
            bot.missiles.setChanged();
        }

        @Override
        public boolean stillValid(Player p) {
            return bot.isAlive();
        }

        @Override
        public void clearContent() {
            for (EquipmentSlot s : EQUIP) bot.setItemSlot(s, ItemStack.EMPTY);
            bot.missiles.clearContent();
        }
    }
}
