package com.eggsteal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Server-side chest GUI with tabs (Upgrades / Items / Base / Info). Uses the vanilla 9x6 screen, so no client code is needed. */
public class ShopMenu extends AbstractContainerMenu {
    private final ServerPlayer player;
    private final SimpleContainer box = new SimpleContainer(54);
    private final boolean builder;
    private int tab; // 0 upgrades, 1 items, 2 base, 3 info, 4 skins

    public ShopMenu(int id, Inventory inv, ServerPlayer p, boolean builder) {
        super(MenuType.GENERIC_9x6, id);
        this.player = p;
        this.builder = builder;
        this.tab = builder ? 2 : 0;
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(box, c + r * 9, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPlace(ItemStack s) { return false; }
                    @Override public boolean mayPickup(Player pl) { return false; }
                });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 139 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 197));
        refresh();
    }

    static ItemStack icon(Item item, String name, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String t : lore) l.add(Component.literal(t).withStyle(st -> st.withItalic(false)));
            s.set(DataComponents.LORE, new ItemLore(l));
        }
        return s;
    }

    private int[] tabSlots() { return builder ? new int[]{3, 5} : new int[]{1, 3, 5, 7}; }
    private int[] tabIds() { return builder ? new int[]{2, 3} : new int[]{0, 1, 4, 3}; }

    private void refresh() {
        Economy eco = Economy.get(player.server);
        Economy.Stats st = eco.stats(player.getUUID());
        box.clearContent();
        ItemStack pane = icon(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) box.setItem(i, pane.copy());
        ItemStack line = icon(Items.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 9; i < 18; i++) box.setItem(i, line.copy());
        String[] names = {"§e§lUpgrades", "§b§lItems", "§a§lBase", "§f§lInfo", "§d§lSkins"};
        Item[] icons = {Items.EXPERIENCE_BOTTLE, Items.NETHER_STAR, Items.BRICKS, Items.BOOK, Items.PAINTING};
        int[] ts = tabSlots(), ids = tabIds();
        for (int i = 0; i < ts.length; i++) {
            ItemStack t = icon(icons[ids[i]], (ids[i] == tab ? "§6▶ " : "§7") + names[ids[i]].substring(2).replace("§l", ""), ids[i] == tab ? "§aSelected" : "§7Click to open");
            if (ids[i] == tab) t.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            box.setItem(ts[i], t);
        }
        switch (tab) {
            case 0 -> {
                Economy.Upgrade[] ups = Economy.Upgrade.values();
                Item[] ui = {Items.GOLD_INGOT, Items.RAIL, Items.CHEST};
                String[] desc = {"+15% money from every creature", "Faster belt + rarer eggs", "+1 creature slot (max 8 on the ground)"};
                int[] pos = {20, 22, 24};
                for (int i = 0; i < ups.length; i++) {
                    int lv = ups[i].level(st);
                    boolean max = lv >= ups[i].max;
                    box.setItem(pos[i], icon(ui[i], "§e" + ups[i].label + " §7Lv" + lv + "/" + ups[i].max, "§7" + desc[i], "",
                            max ? "§cMAX LEVEL" : (st.money >= ups[i].cost(lv) ? "§aClick to buy: $" : "§cCost: $") + Economy.fmt(ups[i].cost(lv))));
                }
            }
            case 1 -> {
                box.setItem(20, icon(EggSteal.SPEED_COIL.get(), "§eSpeed Coil", "§7Hold it in your hand to run fast", "", cost(st, Shop.SPEED_COIL)));
                box.setItem(22, icon(EggSteal.JUMP_COIL.get(), "§bJump Coil", "§7Hold it to jump very high", "§7(no fall damage in the arena)", "", cost(st, Shop.JUMP_COIL)));
                box.setItem(24, icon(EggSteal.BAT.get(), "§6Spare Bat", "§7Replace a lost bat", "", cost(st, Shop.BAT)));
            }
            case 2 -> {
                if (st.floor2) box.setItem(22, icon(Items.EMERALD_BLOCK, "§aSecond floor built", "§7+4 creature slots on the upper floor", "§7Stairs: right side of your base"));
                else box.setItem(22, icon(Items.BRICKS, "§aBuild a Second Floor", "§7+4 creature slots on an upper floor", "§7with a staircase in your base", "", cost(st, Shop.FLOOR2)));
            }
            case 4 -> {
                for (int i = 0; i < Skin.ALL.length; i++) {
                    Skin sk = Skin.ALL[i];
                    boolean own = i == 0 || ((st.skinsOwned >> i) & 1) == 1;
                    boolean eq = st.skin == i;
                    box.setItem(18 + i, icon(sk.wall.asItem(), (eq ? "§a✔ " : "§d") + sk.name,
                            "§7Changes the colour of your base and adds decorations",
                            "", eq ? "§aEquipped" : own ? "§eOwned - click to equip" : (st.money >= sk.price ? "§aClick to buy: $" : "§cCost: $") + Economy.fmt(sk.price)));
                }
            }
            default -> {
                int owned = Arena.owned(player.serverLevel(), player.getUUID());
                int rank = 1;
                for (Economy.Stats o : eco.map.values()) if (o.money > st.money) rank++;
                box.setItem(22, icon(Items.PLAYER_HEAD, "§f" + st.name, "§6Money: $" + Economy.fmt(st.money), "§7Rank: #" + rank + " of " + eco.map.size(),
                        "§7Creatures: " + owned + "/" + Arena.available(st), "§7Income Lv" + st.income + " · Belt Lv" + st.belt, "§7Second floor: " + (st.floor2 ? "yes" : "no")));
            }
        }
        box.setItem(49, icon(Items.GOLD_NUGGET, "§6Balance: $" + Economy.fmt(st.money)));
        box.setItem(53, icon(Items.BARRIER, "§cClose"));
    }

    private static String cost(Economy.Stats st, double c) {
        return (st.money >= c ? "§aClick to buy: $" : "§cCost: $") + Economy.fmt(c);
    }

    private void onButton(int slot) {
        if (slot == 53) { player.closeContainer(); return; }
        int[] ts = tabSlots(), ids = tabIds();
        for (int i = 0; i < ts.length; i++) if (ts[i] == slot) { tab = ids[i]; refresh(); return; }
        if (tab == 0) {
            if (slot == 20) Shop.buyUpgrade(player, Economy.Upgrade.INCOME);
            else if (slot == 22) Shop.buyUpgrade(player, Economy.Upgrade.BELT);
            else if (slot == 24) Shop.buyUpgrade(player, Economy.Upgrade.SLOTS);
        } else if (tab == 1) {
            if (slot == 20) Shop.buyItem(player, "speed");
            else if (slot == 22) Shop.buyItem(player, "jump");
            else if (slot == 24) Shop.buyItem(player, "bat");
        } else if (tab == 2 && slot == 22) Shop.buyFloor2(player);
        else if (tab == 4 && slot >= 18 && slot < 18 + Skin.ALL.length) Shop.skin(player, slot - 18);
        refresh();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player pl) {
        if (slotId >= 0 && slotId < 54) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) onButton(slotId);
            broadcastChanges();
            return;
        }
        if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL) return;
        super.clicked(slotId, button, type, pl);
    }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }
}
