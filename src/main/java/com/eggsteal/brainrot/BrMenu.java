package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
import com.eggsteal.Economy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

/**
 * Chest-style menus of the Brainrot Heist mode (server side only, no client screen needed).
 * The shop: speed, items, base skins, rebirth, your stats. The guide: how to play, every brainrot (with its picture),
 * all 35 mutations with their chance and money multiplier, and the rarities.
 */
public class BrMenu extends AbstractContainerMenu {
    public static final int T_SPEED = 0, T_ITEMS = 1, T_REBIRTH = 2, T_INFO = 3, T_SKINS = 4, G_HOW = 10, G_BRAINROTS = 11, G_MUTATIONS = 12, G_RARITY = 13;
    private final ServerPlayer player;
    private final SimpleContainer box = new SimpleContainer(54);
    private final boolean guide;
    private int tab;
    private boolean armed;
    /** A skin clicked once (buying needs a second click), or -1. */
    private int armedSkin = -1;
    static final int[] SKIN_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 49, 50, 51, 52};

    public BrMenu(int id, Inventory inv, ServerPlayer p, boolean guide, int tab) {
        super(MenuType.GENERIC_9x6, id);
        this.player = p;
        this.guide = guide;
        this.tab = tab >= 0 ? tab : guide ? G_HOW : T_SPEED;
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

    static ItemStack icon(Item item, String name, List<String> lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)));
        if (!lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String t : lore) l.add(Component.literal(t).withStyle(st -> st.withItalic(false)));
            s.set(DataComponents.LORE, new ItemLore(l));
        }
        return s;
    }

    static ItemStack icon(Item item, String name, String... lore) { return icon(item, name, List.of(lore)); }

    private int[] tabSlots() { return guide ? new int[]{1, 3, 5, 7} : new int[]{2, 3, 4, 5, 6}; }
    private int[] tabIds() { return guide ? new int[]{G_HOW, G_BRAINROTS, G_MUTATIONS, G_RARITY} : new int[]{T_SPEED, T_ITEMS, T_SKINS, T_REBIRTH, T_INFO}; }

    static String pct(double c) {
        double p = c * 100;
        String s = p >= 1 ? String.format(Locale.ROOT, "%.1f%%", p) : p >= 0.01 ? String.format(Locale.ROOT, "%.2f%%", p) : String.format(Locale.ROOT, "%.4f%%", p);
        return s + " §8(1 in " + Economy.fmt(Math.max(1, Math.round(1 / c))) + ")";
    }

    private void refresh() {
        BrData data = BrData.get(player.server);
        BrData.Stats st = data.stats(player.getUUID());
        box.clearContent();
        ItemStack pane = icon(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) box.setItem(i, pane.copy());
        ItemStack line = icon(Items.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 9; i < 18; i++) box.setItem(i, line.copy());
        String[] names = guide ? new String[]{"How to Play", "Brainrots", "Mutations", "Rarities"} : new String[]{"Speed", "Items", "Base Skins", "Rebirth", "My Stats"};
        Item[] icons = guide ? new Item[]{Items.BOOK, Items.PAINTING, Items.NETHER_STAR, Items.DIAMOND}
                : new Item[]{Items.SUGAR, Items.BLAZE_ROD, BrSkin.ALL[BrSkin.ALL.length - 1].block(BrSkin.LAMP).asItem(), Items.TOTEM_OF_UNDYING, Items.PLAYER_HEAD};
        int[] ts = tabSlots(), ids = tabIds();
        for (int i = 0; i < ts.length; i++) {
            boolean sel = ids[i] == tab;
            ItemStack t = icon(icons[i], (sel ? "§6▶ " : "§7") + names[i], sel ? "§aSelected" : "§7Click to open");
            if (sel) t.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            box.setItem(ts[i], t);
        }
        box.setItem(8, icon(Items.BARRIER, "§cClose"));
        if (!guide) box.setItem(0, icon(Items.GOLD_NUGGET, "§6$" + Economy.fmt(st.money)));
        switch (tab) {
            case T_SPEED -> {
                boolean max = st.speed >= BrData.MAX_SPEED;
                box.setItem(31, icon(Items.SUGAR, "§bSpeed §7Lv" + st.speed + "/" + BrData.MAX_SPEED, "§7Run 6% faster per level (for good)", "",
                        max ? "§cMAX LEVEL" : cost(st, BrData.speedCost(st))));
            }
            case T_ITEMS -> {
                box.setItem(29, icon(EggSteal.SPEED_COIL.get(), "§eSpeed Coil", "§7Hold it to run very fast", "", cost(st, BrShop.SPEED_COIL)));
                box.setItem(31, icon(EggSteal.JUMP_COIL.get(), "§bGravity Coil", "§7Hold it to jump very high", "", cost(st, BrShop.JUMP_COIL)));
                box.setItem(33, icon(EggSteal.BAT.get(), "§6Bat", "§7Hit a thief: your brainrot goes home", "§7and they freeze for 3 seconds", "", cost(st, BrShop.BAT)));
            }
            case T_SKINS -> {
                for (int k = 0; k < BrSkin.ALL.length; k++) {
                    BrSkin sk = BrSkin.ALL[k];
                    List<String> lore = new ArrayList<>();
                    lore.add("§7" + sk.look);
                    if (k > 0) {
                        lore.add("§75 matching blocks: frame, glass, floor, carpet, lamp");
                        List<String> fx = new ArrayList<>();
                        for (int f : sk.fx) if (f != BrSkin.NONE && !fx.contains(BrSkin.fxName(f))) fx.add(BrSkin.fxName(f));
                        if (!fx.isEmpty()) lore.add("§dParticles: §f" + String.join(", ", fx));
                    }
                    lore.add("");
                    boolean on = st.skin == k;
                    if (on) lore.add("§a§l✔ EQUIPPED");
                    else if (BrSkin.owns(st, k)) lore.add("§eOwned - click to equip");
                    else if (armedSkin == k) lore.add("§c§lCLICK AGAIN TO BUY: $" + Economy.fmt(sk.price));
                    else lore.add(cost(st, sk.price));
                    ItemStack s = icon(k == 0 ? Items.WHITE_CONCRETE : sk.block(BrSkin.FRAME).asItem(), sk.color + "§l" + sk.name, lore);
                    if (on || armedSkin == k) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    box.setItem(SKIN_SLOTS[k], s);
                }
            }
            case T_REBIRTH -> {
                double c = BrData.rebirthCost(st);
                ItemStack r = icon(Items.TOTEM_OF_UNDYING, armed ? "§c§lCLICK AGAIN TO CONFIRM!" : "§5§l✦ REBIRTH " + (st.rebirths + 1) + " ✦",
                        "§7Money x" + BrData.rebirthMult(st) + " §7→ §ax" + (1 + 0.5 * (st.rebirths + 1)) + " §7for good",
                        st.rebirths == 0 ? "§7+10 podiums in your base (20 in total)" : "§7Your base already has 20 podiums",
                        "", "§cResets: §7your money", "§aKeeps: §7all your brainrots, speed and items", "",
                        (st.money >= c ? "§aClick to rebirth: $" : "§cCost: $") + Economy.fmt(c));
                if (armed) r.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                box.setItem(31, r);
            }
            case T_INFO -> {
                int owned = BrainrotEntity.owned(player.serverLevel(), player.getUUID());
                int rank = 1;
                for (BrData.Stats o : data.map.values()) if (o.money > st.money) rank++;
                box.setItem(31, icon(Items.PLAYER_HEAD, "§f" + st.name, "§6Money: $" + Economy.fmt(st.money), "§7Rank: #" + rank + " of " + data.map.size(),
                        "§7Brainrots: " + owned + "/" + BrMap.capacity(st), "§dRebirths: " + st.rebirths + " §7(money x" + BrData.rebirthMult(st) + ")", "§bSpeed Lv" + st.speed));
            }
            case G_HOW -> howToPlay();
            case G_BRAINROTS -> {
                for (Brainrot b : Brainrot.ALL) {
                    List<Brainrot> same = Brainrot.ofRarity(b.rarity);
                    int pos = same.indexOf(b);
                    double wt = 0;
                    for (int i = 0; i < same.size(); i++) wt += same.size() - i;
                    double chance = Brainrot.rarityChance(b.rarity, 0) * (same.size() - pos) / wt;
                    box.setItem(18 + b.index, icon(EggSteal.BR_CARDS.get(b.index).get(), b.tag(),
                            Brainrot.RARITY_COLOR[b.rarity] + Brainrot.RARITY[b.rarity], "§7Price: §6$" + Economy.fmt(b.price),
                            "§7Makes: §e$" + Economy.fmt(b.income) + "/s", "§7Chance on the carpet: §f" + pct(chance)));
                }
            }
            case G_MUTATIONS -> {
                for (int i = 1; i < BrMutation.ALL.length; i++) {
                    BrMutation m = BrMutation.ALL[i];
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Chance: §f" + pct(m.chance()));
                    lore.add("§7Money: §ax" + (m.mult == Math.floor(m.mult) ? String.valueOf((long) m.mult) : String.valueOf(m.mult)));
                    lore.add("§7Look: §f" + looks(m));
                    box.setItem(17 + i, icon(EggSteal.BR_MUTS.get(i).get(), m.prefix().trim(), lore));
                }
                box.setItem(53, icon(Items.WHITE_STAINED_GLASS_PANE, "§fNo mutation", "§7Chance: §f" + pct(BrMutation.ALL[0].chance()), "§7Money: x1"));
            }
            case G_RARITY -> {
                Item[] glass = {Items.LIGHT_GRAY_STAINED_GLASS, Items.BLUE_STAINED_GLASS, Items.PURPLE_STAINED_GLASS, Items.ORANGE_STAINED_GLASS,
                        Items.RED_STAINED_GLASS, Items.YELLOW_STAINED_GLASS, Items.WHITE_STAINED_GLASS, Items.LIGHT_BLUE_STAINED_GLASS};
                for (int r = 0; r < Brainrot.RARITY.length; r++) {
                    List<Brainrot> l = Brainrot.ofRarity(r);
                    double lo = l.get(0).price, hi = l.get(l.size() - 1).price;
                    int[] slots = {19, 21, 23, 25, 28, 30, 32, 34};
                    box.setItem(slots[r], icon(glass[r], Brainrot.RARITY_COLOR[r] + Brainrot.RARITY[r],
                            "§7Chance on the carpet: §f" + pct(Brainrot.rarityChance(r, 0)), "§7Prices: §6$" + Economy.fmt(lo) + " - $" + Economy.fmt(hi),
                            "§7Brainrots: §f" + l.size(), r >= 5 ? "§dThe 10-minute rare brainrot is always this good or better" : ""));
                }
                box.setItem(40, icon(Items.CLOCK, "§d§lRare brainrot every 10 minutes", "§7A Brainrot God or better, always mutated,",
                        "§7walks out of the portal. Everybody fights for it!", "§7The lucky event brings a bonus one."));
            }
            default -> { }
        }
    }

    static String looks(BrMutation m) {
        List<String> parts = new ArrayList<>();
        switch (m.special) {
            case BrMutation.RAINBOW -> parts.add("all rainbow colours, shifting");
            case BrMutation.GLITCH -> parts.add("flickering glitch colours");
            case BrMutation.YIN_YANG -> parts.add("half black, half white");
            case BrMutation.HOLO -> parts.add("shiny hologram colours");
            case BrMutation.GHOST -> parts.add("see-through");
            case BrMutation.FULLBRIGHT -> parts.add("glowing bright");
            default -> { }
        }
        if (!m.pattern.equals("vox") && !m.pattern.equals("sparkle")) parts.add(m.pattern + " pattern");
        if (m.glow != null) parts.add("glowing " + m.glow.substring(2));
        if ((m.extras & BrMutation.FIRE) != 0) parts.add("on fire");
        if ((m.extras & BrMutation.RAINBOW_FIRE) != 0) parts.add("cosmic flames");
        if ((m.extras & BrMutation.LIGHTNING) != 0) parts.add("lightning");
        if ((m.extras & BrMutation.HALO) != 0) parts.add("halo");
        if ((m.extras & BrMutation.CROWN) != 0) parts.add("crown");
        if ((m.extras & BrMutation.RAYS) != 0) parts.add("light rays");
        if ((m.extras & BrMutation.ORBIT) != 0) parts.add("orbiting stars");
        if ((m.extras & BrMutation.VOID_RING) != 0) parts.add("void ring");
        if ((m.extras & BrMutation.BUBBLES) != 0) parts.add("bubbles");
        if ((m.extras & BrMutation.SNOW) != 0) parts.add("frost");
        if ((m.extras & BrMutation.PULSE) != 0) parts.add("radiation waves");
        if ((m.extras & BrMutation.SHARDS) != 0) parts.add("crystal shards");
        if ((m.extras & BrMutation.GLINT) != 0) parts.add("shine");
        return parts.isEmpty() ? "new colours" : String.join(", ", parts);
    }

    private void howToPlay() {
        String[][] pages = {
            {"§e1. Buy brainrots", "§7Brainrots walk out of the purple portal", "§7along the red carpet. Right-click one", "§7to buy it: it walks to your base and", "§7stands on a podium."},
            {"§e2. Buy them away", "§7While a bought brainrot walks to its", "§7new base, anybody can buy it from the", "§7buyer for double the price (the buyer", "§7gets their money back). Bots do it too!"},
            {"§e3. Collect money", "§7Every brainrot makes money each second.", "§7Step on the gold pad in front of a", "§7podium to collect it."},
            {"§e4. Steal", "§7Right-click a brainrot in another base", "§7and stay next to it. Then it rides on", "§7your head and you are slower: run home!", "§7If anybody hits you, it goes back."},
            {"§e5. Defend", "§7Press the red lock button in your base:", "§7nobody else can enter for 60 seconds.", "§7Hit thieves with your bat to get your", "§7brainrot back. Rich bots defend hard."},
            {"§e6. Sell", "§7Sneak + right-click your own brainrot", "§7to sell it for half its value", "§7(to make room for better ones)."},
            {"§e7. Rebirth", "§7In the shop: start over with money but", "§7keep your brainrots. Every rebirth", "§7gives x0.5 more money; the first one", "§7adds 10 more podiums."},
            {"§e8. Rare brainrot", "§7Every 10 minutes a Brainrot God or", "§7better, always mutated, comes out of the", "§7portal. Everybody fights for it!"},
            {"§e9. Mutations", "§735 mutations change a brainrot's colours", "§7and patterns (fire burns, lightning", "§7crackles...). The rarer the mutation,", "§7the more money it makes. See the tab!"},
            {"§e10. Random events", "§7Rainbow, Galaxy, Lava, Software... make", "§7their mutation 25x more common for 90s.", "§7Look at the sky!"},
            {"§e11. Bots", "§7Bots play like real players: they buy,", "§7fight for good brainrots, steal, defend,", "§7lock their bases, sell and rebirth."},
            {"§e12. Base skins", "§7Buy one of 25 skins in the shop: your", "§7whole base is rebuilt with 5 matching", "§7custom blocks, many with particles."},
            {"§e13. Commands", "§7/brainrot guide · /brainrot shop", "§7/brainrot home · /brainrot top"},
        };
        Item[] icons = {Items.EMERALD, Items.GOLD_INGOT, Items.GOLD_BLOCK, Items.IRON_BOOTS, Items.REDSTONE_BLOCK, Items.HOPPER,
                Items.TOTEM_OF_UNDYING, Items.CLOCK, Items.NETHER_STAR, Items.FIREWORK_ROCKET, Items.PLAYER_HEAD, Items.PAINTING, Items.COMMAND_BLOCK};
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};
        for (int i = 0; i < pages.length; i++) {
            List<String> lore = new ArrayList<>(List.of(pages[i]).subList(1, pages[i].length));
            box.setItem(slots[i], icon(icons[i], pages[i][0], lore));
        }
    }

    private static String cost(BrData.Stats st, double c) {
        return (st.money >= c ? "§aClick to buy: $" : "§cCost: $") + Economy.fmt(c);
    }

    private void onButton(int slot) {
        if (slot == 8) { player.closeContainer(); return; }
        int[] ts = tabSlots(), ids = tabIds();
        for (int i = 0; i < ts.length; i++) if (ts[i] == slot) { tab = ids[i]; armed = false; armedSkin = -1; refresh(); return; }
        if (tab == T_SKINS) {
            BrData.Stats st = BrData.get(player.server).stats(player.getUUID());
            for (int k = 0; k < BrSkin.ALL.length; k++) {
                if (SKIN_SLOTS[k] != slot) continue;
                boolean buy = !BrSkin.owns(st, k);
                if (buy && armedSkin != k && st.money >= BrSkin.ALL[k].price) armedSkin = k; // confirm first: skins are expensive
                else { armedSkin = -1; BrShop.skin(player, k); }
            }
            refresh();
            return;
        }
        if (tab == T_SPEED && slot == 31) BrShop.buySpeed(player);
        else if (tab == T_ITEMS && slot == 29) BrShop.buyItem(player, "speed");
        else if (tab == T_ITEMS && slot == 31) BrShop.buyItem(player, "jump");
        else if (tab == T_ITEMS && slot == 33) BrShop.buyItem(player, "bat");
        else if (tab == T_REBIRTH && slot == 31) {
            BrData.Stats st = BrData.get(player.server).stats(player.getUUID());
            if (armed) { armed = false; BrShop.rebirth(player); }
            else if (st.money >= BrData.rebirthCost(st)) armed = true;
            else BrShop.rebirth(player);
        }
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
