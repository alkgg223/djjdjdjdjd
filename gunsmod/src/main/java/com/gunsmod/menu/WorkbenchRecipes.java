package com.gunsmod.menu;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.VehicleKind;
import com.gunsmod.gun.AmmoType;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Weapon workbench recipes, grouped into tabs. */
public final class WorkbenchRecipes {
    private WorkbenchRecipes() {}

    public enum Tab { PISTOLS, SMGS, RIFLES, SHOTGUNS, SNIPERS, HEAVY, AMMO, ATTACHMENTS, GEAR }

    public record Cost(Item item, int count) {}

    public record Recipe(Tab tab, ItemStack result, List<Cost> costs) {
        public boolean canCraft(Player p) {
            if (p.getAbilities().instabuild) return true;
            for (Cost c : costs) if (p.getInventory().countItem(c.item) < c.count) return false;
            return true;
        }

        public int have(Player p, Cost c) {
            return p.getInventory().countItem(c.item);
        }
    }

    private static List<Recipe> all;

    public static List<Recipe> all() {
        if (all == null) all = build();
        return all;
    }

    private static Cost c(Item item, int n) {
        return new Cost(item, n);
    }

    private static List<Recipe> build() {
        List<Recipe> l = new ArrayList<>();
        Item steel = Reg.STEEL_INGOT.get(), parts = Reg.GUN_PARTS.get();
        for (GunType g : GunType.values()) {
            Tab tab = switch (g.category) {
                case PISTOL -> Tab.PISTOLS;
                case SMG -> Tab.SMGS;
                case RIFLE -> Tab.RIFLES;
                case SHOTGUN -> Tab.SHOTGUNS;
                case SNIPER -> Tab.SNIPERS;
                default -> Tab.HEAVY;
            };
            List<Cost> cost = new ArrayList<>();
            switch (g.category) {
                case PISTOL -> {
                    cost.add(c(steel, 3));
                    cost.add(c(parts, 2));
                    cost.add(c(g == GunType.M1911 || g == GunType.REVOLVER ? Items.STRIPPED_DARK_OAK_LOG : Items.BLACK_DYE, 1));
                }
                case SMG -> {
                    cost.add(c(steel, 5));
                    cost.add(c(parts, 4));
                    cost.add(c(Items.REDSTONE, 2));
                }
                case RIFLE -> {
                    cost.add(c(steel, 8));
                    cost.add(c(parts, 6));
                    cost.add(c(g == GunType.AK47 ? Items.DARK_OAK_PLANKS : Items.BLACK_DYE, g == GunType.AK47 ? 4 : 2));
                    cost.add(c(Items.REDSTONE, 2));
                }
                case SHOTGUN -> {
                    cost.add(c(steel, 7));
                    cost.add(c(parts, 4));
                    cost.add(c(Items.OAK_PLANKS, 3));
                }
                case SNIPER -> {
                    cost.add(c(steel, 10));
                    cost.add(c(parts, 8));
                    cost.add(c(Items.DIAMOND, g == GunType.M107 ? 3 : 1));
                }
                case HEAVY -> {
                    cost.add(c(steel, 16));
                    cost.add(c(parts, 12));
                    cost.add(c(Items.DIAMOND, 2));
                    cost.add(c(Items.REDSTONE_BLOCK, 1));
                }
                case EXPLOSIVE -> {
                    cost.add(c(steel, 10));
                    cost.add(c(parts, 6));
                    cost.add(c(Items.TNT, 2));
                }
                case SPECIAL -> {
                    cost.add(c(steel, 12));
                    cost.add(c(parts, 6));
                    cost.add(c(Items.BLAZE_ROD, 2));
                    cost.add(c(Items.BUCKET, 1));
                }
            }
            l.add(new Recipe(tab, GunItem.create(g), cost));
        }
        // ammo
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.PISTOL).get()), List.of(c(Items.COPPER_INGOT, 2), c(Items.GUNPOWDER, 2))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.RIFLE).get()), List.of(c(Items.COPPER_INGOT, 3), c(Items.GUNPOWDER, 3), c(Items.IRON_NUGGET, 4))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.SHOTGUN).get()), List.of(c(Items.PAPER, 2), c(Items.GUNPOWDER, 2), c(Items.IRON_NUGGET, 6))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.SNIPER).get()), List.of(c(Items.COPPER_INGOT, 3), c(Items.GUNPOWDER, 4), c(Items.IRON_INGOT, 1))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.HEAVY).get()), List.of(c(Items.COPPER_INGOT, 6), c(Items.GUNPOWDER, 6), c(Items.IRON_INGOT, 2))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.ROCKET).get(), 2), List.of(c(steel, 1), c(Items.TNT, 1), c(Items.GUNPOWDER, 2))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.GRENADE).get(), 4), List.of(c(Items.COPPER_INGOT, 2), c(Items.GUNPOWDER, 3))));
        l.add(new Recipe(Tab.AMMO, new ItemStack(Reg.AMMO_ITEMS.get(AmmoType.FUEL).get()), List.of(c(Items.IRON_INGOT, 2), c(Items.COAL, 4), c(Items.BLAZE_POWDER, 1))));
        // attachments
        for (Attachment a : Attachment.values()) {
            List<Cost> cost = switch (a) {
                case RED_DOT, HOLO_SIGHT -> List.of(c(steel, 1), c(Items.GLASS_PANE, 2), c(Items.REDSTONE, 2));
                case ACOG -> List.of(c(steel, 2), c(Items.GLASS_PANE, 2), c(Items.AMETHYST_SHARD, 1));
                case SNIPER_SCOPE -> List.of(c(steel, 3), c(Items.GLASS_PANE, 3), c(Items.SPYGLASS, 1));
                case SUPPRESSOR -> List.of(c(steel, 3), c(Items.BLACK_WOOL, 2));
                case COMPENSATOR -> List.of(c(steel, 2), c(Items.IRON_NUGGET, 4));
                case VERTICAL_GRIP, ANGLED_GRIP -> List.of(c(steel, 1), c(Items.BLACK_DYE, 1), c(Items.STICK, 1));
                case LASER_SIGHT -> List.of(c(steel, 1), c(Items.REDSTONE_TORCH, 1), c(Items.GLASS_PANE, 1));
                case EXTENDED_MAG -> List.of(c(steel, 2), c(parts, 1));
                case FLASHLIGHT -> List.of(c(steel, 1), c(Items.GLOWSTONE_DUST, 2), c(Items.GLASS_PANE, 1));
            };
            l.add(new Recipe(Tab.ATTACHMENTS, new ItemStack(Reg.ATTACHMENT_ITEMS.get(a).get()), cost));
        }
        // gear
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.FRAG_GRENADE.get(), 2), List.of(c(Items.IRON_INGOT, 1), c(Items.GUNPOWDER, 2))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.SMOKE_GRENADE.get(), 2), List.of(c(Items.IRON_INGOT, 1), c(Items.COAL, 2))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.MISSILE_HE.get()), List.of(c(steel, 6), c(Items.TNT, 6), c(Items.FIRE_CHARGE, 2), c(Items.REDSTONE, 4))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.MISSILE_CLUSTER.get()), List.of(c(steel, 6), c(Items.TNT, 8), c(Items.FIREWORK_STAR, 2))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.MISSILE_INCENDIARY.get()), List.of(c(steel, 6), c(Items.TNT, 4), c(Items.BLAZE_POWDER, 4))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.MISSILE_THERMOBARIC.get()), List.of(c(steel, 12), c(Items.TNT, 16), c(Items.BLAZE_POWDER, 8), c(Items.FIRE_CHARGE, 4))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.MISSILE_BUNKER_BUSTER.get()), List.of(c(steel, 16), c(Items.TNT, 12), c(Items.IRON_BLOCK, 3), c(Items.DIAMOND, 1))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.MISSILE_TITAN.get()), List.of(c(steel, 24), c(Items.TNT, 32), c(Items.DIAMOND_BLOCK, 1), c(Items.REDSTONE_BLOCK, 4))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.GREEN_SOLDIER.get()), List.of(c(steel, 4), c(Items.IRON_HELMET, 1), c(Items.EMERALD, 2), c(Items.GREEN_DYE, 1))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.RED_SOLDIER.get()), List.of(c(steel, 4), c(Items.IRON_HELMET, 1), c(Items.EMERALD, 2), c(Items.RED_DYE, 1))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.GREEN_JEEP.get()), List.of(c(steel, 24), c(Items.IRON_BLOCK, 4), c(Items.BLACK_WOOL, 4), c(Items.REDSTONE_BLOCK, 2), c(Items.GREEN_DYE, 2))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.RED_JEEP.get()), List.of(c(steel, 24), c(Items.IRON_BLOCK, 4), c(Items.BLACK_WOOL, 4), c(Items.REDSTONE_BLOCK, 2), c(Items.RED_DYE, 2))));
        for (VehicleKind k : VehicleKind.values()) {
            for (BotTeam team : new BotTeam[]{BotTeam.GREEN, BotTeam.RED}) {
                Item dye = team == BotTeam.RED ? Items.RED_DYE : Items.GREEN_DYE;
                List<Cost> cost = switch (k.group) {
                    case TANK -> List.of(c(steel, k == VehicleKind.CHALLENGER ? 56 : 48), c(Items.IRON_BLOCK, 12), c(Items.REDSTONE_BLOCK, 4), c(Items.TNT, 8), c(dye, 4));
                    case HELI -> List.of(c(steel, k == VehicleKind.HIND ? 48 : 40), c(Items.IRON_BLOCK, 8), c(Items.GLASS, 8), c(Items.REDSTONE_BLOCK, 4),
                            c(Items.TNT, k == VehicleKind.COBRA ? 0 : 6), c(dye, 4));
                    case JEEP -> List.of(c(steel, 24), c(Items.IRON_BLOCK, 4), c(Items.REDSTONE_BLOCK, 2), c(Items.TNT, 6), c(dye, 2));
                    case JET -> List.of(c(steel, 64), c(Items.IRON_BLOCK, 12), c(Items.GLASS, 6), c(Items.REDSTONE_BLOCK, 8), c(Items.BLAZE_ROD, 4),
                            c(Items.TNT, 8), c(dye, 4));
                    case CAR -> k == VehicleKind.PANTSIR
                            ? List.of(c(steel, 52), c(Items.IRON_BLOCK, 10), c(Items.REDSTONE_BLOCK, 8), c(Items.COMPASS, 2), c(Items.TNT, 10), c(dye, 4))
                            : List.of(c(steel, 40), c(Items.IRON_BLOCK, 8), c(Items.REDSTONE_BLOCK, 4), c(Items.TNT, 6), c(dye, 4));
                };
                l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.vehicleItem(k, team)), cost.stream().filter(x -> x.count() > 0).toList()));
            }
        }
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.DOG_TAG.get()), List.of(c(Items.IRON_NUGGET, 4), c(Items.CHAIN, 1))));
        l.add(new Recipe(Tab.GEAR, new ItemStack(Reg.GUN_PARTS.get(), 4), List.of(c(steel, 2), c(Items.IRON_NUGGET, 4))));
        return l;
    }

    public static void consume(Player p, Recipe r) {
        if (p.getAbilities().instabuild) return;
        for (Cost c : r.costs()) {
            int left = c.count();
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(c.item())) {
                    int take = Math.min(left, s.getCount());
                    s.shrink(take);
                    left -= take;
                }
            }
        }
    }
}
