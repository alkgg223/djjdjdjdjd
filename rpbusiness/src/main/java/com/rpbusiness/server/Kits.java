package com.rpbusiness.server;

import com.rpbusiness.bank.Bank;
import com.rpbusiness.block.*;
import com.rpbusiness.business.*;
import com.rpbusiness.core.ModBlocks;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.food.Dish;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.*;

/**
 * Optional ready-made buildings. The player uses a kit item on the ground and a complete, furnished,
 * working business appears in front of them, registered in their name and stocked with starter goods.
 * Building everything by hand remains fully supported.
 */
public final class Kits {
    private Kits() {}

    /** Blueprint in local coordinates: x 0..w, z 0..d, back wall at z = 0, door in the front wall at (w/2, 0, d). */
    static final class BP {
        final int w, d, h;
        final LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Map<BlockPos, List<ItemStack>> contents = new HashMap<>();
        final Map<BlockPos, int[]> drinks = new HashMap<>();
        final List<Dish> menu = new ArrayList<>();
        /** Hotel rooms in local coordinates {x0, z0, x1, z1}. */
        final List<int[]> rooms = new ArrayList<>();
        BusinessType type;
        String name;

        BP(int w, int d, int h) { this.w = w; this.d = d; this.h = h; }

        void set(int x, int y, int z, BlockState s) { blocks.put(new BlockPos(x, y, z), s); }
        void set(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
        void face(int x, int y, int z, Block b, Direction f) {
            BlockState s = b.defaultBlockState();
            if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, f);
            set(x, y, z, s);
        }
        void fill(int x, int y, int z, Item it, int n) { contents.computeIfAbsent(new BlockPos(x, y, z), k -> new ArrayList<>()).add(new ItemStack(it, n)); }
        void fill(int x, int y, int z, net.neoforged.neoforge.registries.DeferredItem<? extends Item> it, int n) { fill(x, y, z, it.get(), n); }

        /** Floor, walls with windows, roof, a door in the front wall and ceiling lamps. */
        void shell(Block floor, Block wall, Block roof) {
            for (int x = 0; x <= w; x++) for (int z = 0; z <= d; z++) {
                set(x, -1, z, floor);
                set(x, h, z, roof);
                boolean edge = x == 0 || z == 0 || x == w || z == d;
                if (edge) for (int y = 0; y < h; y++) {
                    boolean window = y >= 1 && y <= 2 && (x % 3 == 1 || z % 3 == 1) && !(x == 0 && z == 0) && !(x == w && z == d)
                            && !(x == 0 && z == d) && !(x == w && z == 0) && z != 0;
                    set(x, y, z, window && !(z == d && Math.abs(x - w / 2) <= 1) ? Blocks.GLASS_PANE : wall);
                }
            }
            int dx = w / 2;
            set(dx, 0, d, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(dx, 1, d, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            for (int x = 3; x < w; x += 4) for (int z = 3; z < d; z += 4) set(x, h - 1, z, ModBlocks.PENDANT_LAMP.get());
        }

        void counterWith(int x, int z, Block top, Direction f) {
            face(x, 0, z, ModBlocks.KITCHEN_COUNTER.get(), f);
            face(x, 1, z, top, f);
        }

        void fridge(int x, int z, Direction f) {
            face(x, 0, z, ModBlocks.FRIDGE.get(), f);
            set(x, 1, z, ModBlocks.FRIDGE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f).setValue(FridgeBlock.HALF, DoubleBlockHalf.UPPER));
        }

        /** A two-block table with four chairs (fits a family). */
        void table4(int x, int z) {
            for (int dz = 0; dz <= 1; dz++) {
                set(x, 0, z + dz, ModBlocks.DINING_TABLE.get());
                face(x - 1, 0, z + dz, ModBlocks.DINING_CHAIR.get(), Direction.EAST);
                face(x + 1, 0, z + dz, ModBlocks.DINING_CHAIR.get(), Direction.WEST);
            }
        }

        /** A table for two. */
        void table2(int x, int z) {
            set(x, 0, z, ModBlocks.DINING_TABLE.get());
            face(x - 1, 0, z, ModBlocks.DINING_CHAIR.get(), Direction.EAST);
            face(x + 1, 0, z, ModBlocks.DINING_CHAIR.get(), Direction.WEST);
        }

        void display(int x, int z, Direction f, boolean cold, Item... products) {
            face(x, 0, z, cold ? ModBlocks.FRIDGE_DISPLAY.get() : ModBlocks.DISPLAY_SHELF.get(), f);
            for (int i = 0; i < 9; i++) fill(x, 0, z, products[i % products.length], 6);
        }

        void office(int cx, int cz, int sx, int sz) {
            counterWith(cx, cz, ModBlocks.BUSINESS_COMPUTER.get(), Direction.SOUTH);
            face(sx, 0, sz, ModBlocks.SUPPLIER_TERMINAL.get(), Direction.SOUTH);
        }
    }

    // ================================================================== blueprints
    static BP blueprint(String kind) {
        Direction S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;
        BP b;
        switch (kind) {
            case "restaurant", "fast_food", "cafe", "bakery" -> {
                boolean fast = kind.equals("fast_food"), cafe = kind.equals("cafe"), bakery = kind.equals("bakery");
                b = new BP(12, 12, 5);
                b.type = BusinessType.valueOf(kind.toUpperCase(Locale.ROOT));
                b.shell(ModBlocks.CHECKERED_FLOOR.get(), cafe ? Blocks.SPRUCE_PLANKS : bakery ? Blocks.BRICKS : Blocks.STRIPPED_OAK_LOG, Blocks.DARK_OAK_PLANKS);
                b.fridge(1, 1, S);
                b.counterWith(2, 1, ModBlocks.CUTTING_BOARD.get(), S);
                b.face(3, 0, 1, ModBlocks.GRILL.get(), S);
                b.face(4, 0, 1, fast ? ModBlocks.GRILL.get() : ModBlocks.FRYER.get(), S);
                b.face(5, 0, 1, fast ? ModBlocks.FRYER.get() : ModBlocks.OVEN.get(), S);
                b.counterWith(6, 1, bakery ? ModBlocks.CUTTING_BOARD.get() : ModBlocks.COOKING_POT.get(), S);
                b.face(7, 0, 1, ModBlocks.PREP_STATION.get(), S);
                b.face(8, 0, 1, ModBlocks.DRINK_MACHINE.get(), S);
                b.face(9, 0, 1, cafe ? ModBlocks.DRINK_MACHINE.get() : ModBlocks.OVEN.get(), S);
                b.counterWith(10, 1, ModBlocks.CUTTING_BOARD.get(), S);
                b.face(11, 0, 1, ModBlocks.FREEZER.get(), S);
                b.face(2, 0, 4, ModBlocks.SERVING_COUNTER.get(), S);
                b.face(3, 0, 4, ModBlocks.SERVING_COUNTER.get(), S);
                b.counterWith(9, 4, ModBlocks.CASH_REGISTER.get(), S);
                b.office(11, 4, 1, 4);
                b.face(11, 0, 7, ModBlocks.PAYMENT_TERMINAL.get(), W);
                if (fast) {
                    for (int x = 2; x <= 9; x += 3) b.table2(x, 8);
                    for (int x = 2; x <= 8; x += 2) b.face(x, 0, 10, ModBlocks.BAR_STOOL.get(), Direction.NORTH);
                    for (int x = 2; x <= 8; x += 2) b.set(x, 0, 11, ModBlocks.DINING_TABLE.get());
                } else if (cafe) {
                    b.set(3, 0, 8, ModBlocks.DINING_TABLE.get()); b.face(3, 0, 9, ModBlocks.SOFA.get(), Direction.NORTH); b.face(3, 0, 7, ModBlocks.DINING_CHAIR.get(), S);
                    b.set(8, 0, 8, ModBlocks.DINING_TABLE.get()); b.face(8, 0, 9, ModBlocks.SOFA.get(), Direction.NORTH); b.face(8, 0, 7, ModBlocks.DINING_CHAIR.get(), S);
                    b.table4(5, 10);
                } else { b.table4(2, 8); b.table2(5, 8); b.table4(8, 8); }
                if (bakery) {
                    b.display(1, 7, E, false, Items.BREAD, Items.COOKIE, Items.PUMPKIN_PIE);
                    b.display(1, 9, E, false, ModItems.BREAD_LOAF.get(), Items.BREAD, Items.COOKIE);
                }
                b.set(1, 0, 11, ModBlocks.POTTED_PLANT.get());
                b.set(11, 0, 11, ModBlocks.POTTED_PLANT.get());
                b.face(10, 0, 10, ModBlocks.MENU_BOARD.get(), W);
                b.face(1, 0, 10, ModBlocks.DELIVERY_CRATE.get(), E);
                for (var it : List.of(ModItems.TOMATO, ModItems.ONION, ModItems.LETTUCE, ModItems.POTATO, ModItems.RAW_BEEF, ModItems.BURGER_BUN,
                        ModItems.CHEESE_BLOCK, ModItems.BREAD_LOAF, ModItems.PIZZA_DOUGH)) b.fill(1, 0, 1, it, 16);
                b.fill(1, 0, 1, ModItems.PAPER_CUP, 32);
                b.fill(11, 0, 1, ModItems.RAW_BEEF, 16);
                b.fill(11, 0, 1, ModItems.POTATO, 16);
                b.drinks.put(new BlockPos(8, 0, 1), new int[]{3, 3});
                if (cafe) b.drinks.put(new BlockPos(9, 0, 1), new int[]{2, 4});
                if (fast) Collections.addAll(b.menu, Dish.BURGER, Dish.CHEESEBURGER, Dish.FRIES, Dish.COLA, Dish.COFFEE);
                else if (cafe) Collections.addAll(b.menu, Dish.COFFEE, Dish.COLA, Dish.CLUB_SANDWICH, Dish.GARDEN_SALAD, Dish.TOMATO_SOUP);
                else if (bakery) Collections.addAll(b.menu, Dish.PIZZA, Dish.CLUB_SANDWICH, Dish.COFFEE, Dish.TOMATO_SOUP, Dish.COLA);
                else Collections.addAll(b.menu, Dish.values());
            }
            case "grocery", "supermarket", "clothing", "electronics", "furniture", "butcher", "gas_station" -> {
                boolean big = kind.equals("supermarket");
                b = new BP(big ? 14 : 10, big ? 12 : 10, 5);
                b.type = BusinessType.valueOf(kind.toUpperCase(Locale.ROOT));
                b.shell(ModBlocks.KITCHEN_TILES.get(), kind.equals("gas_station") ? Blocks.WHITE_CONCRETE : Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS);
                Item[][] goods = switch (kind) {
                    case "clothing" -> new Item[][]{{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS}, {Items.LEATHER_BOOTS, Items.WHITE_WOOL, Items.RED_WOOL},
                            {Items.BLUE_WOOL, Items.BLACK_WOOL, Items.YELLOW_WOOL}, {Items.LEATHER_CHESTPLATE, Items.LEATHER_BOOTS, Items.GREEN_WOOL}};
                    case "electronics" -> new Item[][]{{Items.COMPASS, Items.CLOCK, Items.REDSTONE_LAMP}, {Items.DAYLIGHT_DETECTOR, Items.OBSERVER, Items.COMPARATOR},
                            {Items.REPEATER, Items.NOTE_BLOCK, Items.JUKEBOX}, {Items.REDSTONE, Items.LEVER, Items.TARGET}};
                    case "furniture" -> new Item[][]{{ModBlocks.DINING_TABLE.get().asItem(), ModBlocks.DINING_CHAIR.get().asItem(), ModBlocks.SOFA.get().asItem()},
                            {ModBlocks.PENDANT_LAMP.get().asItem(), ModBlocks.POTTED_PLANT.get().asItem(), ModBlocks.BAR_STOOL.get().asItem()},
                            {Items.WHITE_BED, Items.BOOKSHELF, Items.LANTERN}, {Items.OAK_STAIRS, Items.FLOWER_POT, Items.PAINTING}};
                    case "butcher" -> new Item[][]{{ModItems.RAW_BEEF.get(), Items.CHICKEN, Items.PORKCHOP}, {Items.MUTTON, Items.COOKED_BEEF, Items.COOKED_CHICKEN},
                            {Items.RABBIT, Items.COD, Items.SALMON}, {Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, ModItems.RAW_BEEF.get()}};
                    case "gas_station" -> new Item[][]{{Items.COAL, Items.CHARCOAL, Items.TORCH}, {ModItems.COLA.get(), ModItems.COFFEE.get(), Items.HONEY_BOTTLE},
                            {Items.COOKIE, Items.BREAD, Items.APPLE}, {Items.LANTERN, Items.COAL, Items.MELON_SLICE}};
                    default -> new Item[][]{{Items.BREAD, Items.APPLE, Items.COOKIE}, {ModItems.TOMATO.get(), ModItems.CHEESE_BLOCK.get(), ModItems.BURGER_BUN.get()},
                            {Items.CARROT, Items.PUMPKIN_PIE, Items.MELON_SLICE}, {ModItems.COLA.get(), Items.HONEY_BOTTLE, Items.BAKED_POTATO}};
                };
                boolean cold = kind.equals("butcher") || kind.equals("grocery") || big;
                int rows = big ? 3 : 2;
                int n = 0;
                for (int r = 0; r < rows; r++) {
                    int z = 1 + r * 3;
                    for (int x = 2; x <= b.w - 2; x += 2) {
                        b.display(x, z, S, cold && (x / 2) % 2 == 0 && r == 0, goods[n % goods.length]);
                        n++;
                    }
                }
                b.face(1, 0, b.d - 4, ModBlocks.PANTRY_SHELF.get(), E);
                b.face(1, 0, b.d - 3, ModBlocks.PANTRY_SHELF.get(), E);
                for (Item[] row : goods) for (Item it : row) b.fill(1, 0, b.d - 4, it, 16);
                b.counterWith(b.w - 2, b.d - 4, ModBlocks.CASH_REGISTER.get(), S);
                if (big) b.counterWith(b.w - 5, b.d - 4, ModBlocks.CASH_REGISTER.get(), S);
                b.face(3, 0, b.d - 2, ModBlocks.PAYMENT_TERMINAL.get(), S);
                b.face(b.w - 1, 0, 1, ModBlocks.DELIVERY_CRATE.get(), S);
                b.office(b.w - 1, b.d - 2, b.w - 1, b.d - 6);
                b.set(1, 0, b.d - 1, ModBlocks.POTTED_PLANT.get());
                if (kind.equals("gas_station")) {
                    for (int x : new int[]{b.w / 2 - 3, b.w / 2 + 3}) {
                        b.set(x, 0, b.d + 2, Blocks.IRON_BLOCK);
                        b.set(x, 1, b.d + 2, Blocks.RED_CONCRETE);
                        b.set(x, 2, b.d + 2, Blocks.LANTERN);
                    }
                }
            }
            case "bank" -> {
                b = new BP(10, 9, 5);
                b.type = BusinessType.OTHER;
                b.name = "Bank";
                b.shell(Blocks.POLISHED_DIORITE, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ);
                for (int x = 2; x <= 7; x++) b.face(x, 0, 3, x == 4 ? ModBlocks.BANK_COUNTER.get() : ModBlocks.BANK_COUNTER.get(), S);
                b.face(2, 0, 1, ModBlocks.SAFE.get(), S);
                b.face(3, 0, 1, ModBlocks.SAFE.get(), S);
                b.counterWith(6, 1, ModBlocks.BANK_COMPUTER.get(), S);
                b.counterWith(7, 1, ModBlocks.BUSINESS_COMPUTER.get(), S);
                b.face(1, 0, 6, ModBlocks.ATM.get(), E);
                b.face(9, 0, 6, ModBlocks.ATM.get(), W);
                b.set(1, 0, 8, ModBlocks.POTTED_PLANT.get());
                b.set(9, 0, 8, ModBlocks.POTTED_PLANT.get());
                b.face(3, 0, 6, ModBlocks.SOFA.get(), S);
            }
            case "delivery", "warehouse" -> {
                boolean wh = kind.equals("warehouse");
                b = new BP(wh ? 12 : 9, wh ? 10 : 8, 5);
                b.type = wh ? BusinessType.WAREHOUSE : BusinessType.DELIVERY;
                b.shell(Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS);
                for (int x = 1; x < b.w; x += 2) b.face(x, 0, 1, ModBlocks.PANTRY_SHELF.get(), S);
                for (int x = 2; x < b.w - 1; x += 3) b.face(x, 0, 3, ModBlocks.DELIVERY_CRATE.get(), S);
                b.counterWith(b.w - 2, b.d - 3, ModBlocks.DELIVERY_TERMINAL.get(), S);
                b.office(b.w - 3, b.d - 3, 1, b.d - 2);
                if (wh) for (int x = 1; x < b.w; x += 2) b.face(x, 0, 6, ModBlocks.PANTRY_SHELF.get(), S);
                b.fill(1, 0, 1, Items.PAPER, 32);
            }
            case "hotel" -> {
                // four private rooms along the back (each with its own door), a lobby with the reception in front
                b = new BP(20, 14, 5);
                b.type = BusinessType.HOTEL;
                b.shell(ModBlocks.CHECKERED_FLOOR.get(), Blocks.BRICKS, Blocks.DARK_OAK_PLANKS);
                Direction N = Direction.NORTH;
                for (int i = 0; i < 4; i++) {
                    int x0 = 1 + i * 5;
                    // floor carpet and walls between rooms, front wall with a door
                    for (int x = x0; x <= x0 + 3; x++) for (int z = 1; z <= 6; z++) b.set(x, -1, z, Blocks.RED_WOOL);
                    if (i < 3) for (int z = 1; z <= 7; z++) for (int y = 0; y < b.h; y++) b.set(x0 + 4, y, z, Blocks.BRICKS);
                    for (int x = x0; x <= x0 + 3; x++) for (int y = 0; y < b.h; y++) b.set(x, y, 7, Blocks.BRICKS);
                    b.set(x0 + 3, 0, 7, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, S).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    b.set(x0 + 3, 1, 7, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, S).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    b.face(x0 + 2, 1, 8, ModBlocks.ROOM_SIGN.get(), S);
                    b.set(x0 + 1, b.h - 1, 3, ModBlocks.CEILING_FAN.get());
                    b.face(x0 + 2, 2, 6, ModBlocks.AIR_CONDITIONER.get(), N);
                    // the TV corner: dresser + TV, sofa facing it, coffee table between
                    b.face(x0, 0, 4, ModBlocks.DRESSER.get(), E);
                    b.face(x0, 1, 4, ModBlocks.TV.get(), E);
                    b.face(x0 + 1, 0, 4, ModBlocks.COFFEE_TABLE.get(), E);
                    b.face(x0 + 2, 0, 4, i % 2 == 0 ? ModBlocks.SOFA_GRAY.get() : ModBlocks.SOFA_BLUE.get(), W);
                    b.face(x0 + 1, 0, 3, i == 1 ? ModBlocks.RUG_BEIGE.get() : i == 2 ? ModBlocks.RUG_BLUE.get() : ModBlocks.RUG_RED.get(), S);
                    b.face(x0, 0, 6, ModBlocks.TALL_PLANT.get(), S);
                    b.face(x0 + 1, 0, 6, ModBlocks.TRASH_BIN.get(), S);
                    switch (i) {
                        case 0 -> { // double room
                            b.face(x0 + 1, 0, 1, ModBlocks.DOUBLE_BED.get(), S);
                            b.face(x0, 0, 1, ModBlocks.NIGHTSTAND.get(), S); b.face(x0, 1, 1, ModBlocks.TABLE_LAMP.get(), S);
                            b.face(x0 + 2, 0, 1, ModBlocks.NIGHTSTAND.get(), S); b.face(x0 + 2, 1, 1, ModBlocks.ROOM_PHONE.get(), S);
                            b.face(x0 + 3, 0, 1, ModBlocks.WARDROBE.get(), S);
                            b.face(x0 + 1, 2, 1, ModBlocks.PAINTING_SEA.get(), S);
                            b.face(x0 + 3, 0, 2, ModBlocks.MINIBAR.get(), W);
                        }
                        case 1 -> { // twin room
                            b.face(x0, 0, 1, ModBlocks.HOTEL_BED.get(), S);
                            b.face(x0 + 1, 0, 1, ModBlocks.NIGHTSTAND.get(), S); b.face(x0 + 1, 1, 1, ModBlocks.TABLE_LAMP.get(), S);
                            b.face(x0 + 2, 0, 1, ModBlocks.HOTEL_BED.get(), S);
                            b.face(x0 + 3, 0, 1, ModBlocks.WARDROBE.get(), S);
                            b.face(x0 + 1, 2, 1, ModBlocks.PAINTING_CITY.get(), S);
                            b.face(x0 + 3, 0, 2, ModBlocks.DESK.get(), W);
                            b.face(x0 + 3, 1, 2, ModBlocks.ROOM_PHONE.get(), W);
                        }
                        case 2 -> { // family room: a double bed and two single beds for the children
                            b.face(x0, 0, 1, ModBlocks.DOUBLE_BED.get(), S);
                            b.face(x0 + 1, 0, 1, ModBlocks.NIGHTSTAND.get(), S); b.face(x0 + 1, 1, 1, ModBlocks.TABLE_LAMP.get(), S);
                            b.face(x0 + 2, 0, 1, ModBlocks.HOTEL_BED.get(), S);
                            b.face(x0 + 3, 0, 1, ModBlocks.HOTEL_BED.get(), S);
                            b.face(x0 + 3, 0, 2, ModBlocks.BOOKSHELF_MODERN.get(), W);
                            b.face(x0 + 1, 2, 1, ModBlocks.WALL_CLOCK.get(), S);
                        }
                        default -> { // single room with a small bathroom corner
                            b.face(x0, 0, 1, ModBlocks.HOTEL_BED.get(), S);
                            b.face(x0 + 1, 0, 1, ModBlocks.NIGHTSTAND.get(), S); b.face(x0 + 1, 1, 1, ModBlocks.TABLE_LAMP.get(), S);
                            b.face(x0 + 3, 0, 1, ModBlocks.SHOWER.get(), S);
                            b.face(x0 + 3, 0, 2, ModBlocks.TOILET.get(), W);
                            b.face(x0 + 2, 0, 1, ModBlocks.BATHROOM_SINK.get(), S);
                            b.face(x0 + 2, 1, 1, ModBlocks.MIRROR.get(), S);
                            b.face(x0 + 3, 1, 2, ModBlocks.TOWEL_RACK.get(), W);
                        }
                    }
                    b.rooms.add(new int[]{x0, 1, x0 + 3, 6});
                }
                // lobby
                b.counterWith(15, 11, ModBlocks.CASH_REGISTER.get(), S);
                b.counterWith(17, 11, ModBlocks.BUSINESS_COMPUTER.get(), S);
                b.face(16, 0, 11, ModBlocks.KITCHEN_COUNTER.get(), S);
                b.face(16, 0, 10, ModBlocks.OFFICE_CHAIR.get(), S);
                b.face(1, 0, 11, ModBlocks.SOFA.get(), E);
                b.face(1, 0, 12, ModBlocks.SOFA.get(), E);
                b.face(2, 0, 11, ModBlocks.COFFEE_TABLE.get(), E);
                b.face(1, 0, 13, ModBlocks.FLOOR_LAMP.get(), S);
                b.face(1, 0, 9, ModBlocks.ARMCHAIR.get(), S);
                for (int x = 6; x <= 8; x++) b.face(x, 0, 11, ModBlocks.RUG_RED.get(), S);
                b.face(19, 0, 13, ModBlocks.TALL_PLANT.get(), S);
                b.face(12, 0, 13, ModBlocks.FLOWER_VASE.get(), S);
                b.face(13, 0, 13, ModBlocks.LUGGAGE_RACK.get(), N);
                b.face(19, 0, 12, ModBlocks.RADIATOR.get(), W);
                b.face(7, 2, 8, ModBlocks.PAINTING_CITY.get(), S);
                b.face(12, 2, 8, ModBlocks.WALL_CLOCK.get(), S);
                b.face(5, 0, 13, ModBlocks.TRASH_BIN.get(), N);
                b.face(19, 0, 10, ModBlocks.PAYMENT_TERMINAL.get(), W);
                b.face(19, 0, 9, ModBlocks.PANTRY_SHELF.get(), W);
                b.fill(19, 0, 9, ModItems.MOP, 1);
            }
            default -> { return null; }
        }
        return b;
    }

    // ================================================================== placement
    private static Rotation rotationFor(Direction look) {
        return switch (look) {
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Feet height of the natural ground at a column (ignores leaves and plants), searched around a reference height. */
    private static int naturalFeet(ServerLevel sl, BlockPos col, int ref) {
        for (int y = ref + 10; y >= ref - 10; y--) {
            BlockPos q = new BlockPos(col.getX(), y, col.getZ());
            BlockState below = sl.getBlockState(q.below());
            if (below.isAir() || below.canBeReplaced() || below.is(net.minecraft.tags.BlockTags.LEAVES) || below.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            if (!below.getFluidState().isEmpty()) return y;
            return y;
        }
        return ref;
    }

    public static boolean place(ServerPlayer p, String kind, BlockPos clicked) {
        return place(p.serverLevel(), p.getUUID(), p.getGameProfile().getName(), kind, clicked, p.getDirection(), m -> p.displayClientMessage(m, false)) != null;
    }

    public static Business place(ServerLevel sl, UUID owner, String ownerName, String kind, BlockPos clicked, Direction look,
                                 java.util.function.Consumer<Component> say) {
        if (sl != sl.getServer().overworld()) { say.accept(Component.translatable("msg.rpbusiness.overworld_only").withStyle(ChatFormatting.RED)); return null; }
        BP bp = blueprint(kind);
        if (bp == null) return null;
        Rotation rot = rotationFor(look);
        BlockPos entranceLocal = new BlockPos(bp.w / 2, 0, bp.d + 2);
        BlockPos base = clicked.above().subtract(entranceLocal.rotate(rot));
        // bounds of the building (with a 1 block margin for clearing)
        BlockPos a = base.offset(new BlockPos(-1, -1, -1).rotate(rot)), c = base.offset(new BlockPos(bp.w + 1, bp.h + 1, bp.d + 3).rotate(rot));
        BlockPos min = new BlockPos(Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), c.getX()), Math.max(a.getY(), c.getY()), Math.max(a.getZ(), c.getZ()));
        BusinessData data = BusinessManager.data(sl);
        if (data.overlaps(min, max, null)) { say.accept(Component.translatable("msg.rpbusiness.overlap").withStyle(ChatFormatting.RED)); return null; }
        for (BlockPos q : BlockPos.betweenClosed(min, max)) {
            if (!sl.isLoaded(q)) { say.accept(Component.translatable("msg.rpbusiness.kit_unloaded").withStyle(ChatFormatting.RED)); return null; }
            if (sl.getBlockEntity(q) != null) {
                // never destroy the player's chests or machines
                say.accept(Component.translatable("msg.rpbusiness.kit_blocked", q.getX(), q.getY(), q.getZ()).withStyle(ChatFormatting.RED));
                return null;
            }
        }
        // clear the volume and build a foundation down to the ground
        for (int x = -1; x <= bp.w + 1; x++) for (int z = -1; z <= bp.d + 3; z++) {
            for (int y = 0; y <= bp.h + 1; y++) sl.setBlock(base.offset(new BlockPos(x, y, z).rotate(rot)), Blocks.AIR.defaultBlockState(), 2);
            boolean inside = x >= 0 && x <= bp.w && z >= 0 && z <= bp.d;
            boolean path = z > bp.d && Math.abs(x - bp.w / 2) <= 1;
            BlockPos top = base.offset(new BlockPos(x, -1, z).rotate(rot));
            if (inside || path) {
                for (int y = -2; y >= -6; y--) {
                    BlockPos q = base.offset(new BlockPos(x, y, z).rotate(rot));
                    if (!sl.getBlockState(q).canBeReplaced()) break;
                    sl.setBlock(q, Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
                if (path) sl.setBlock(top, Blocks.STONE_BRICKS.defaultBlockState(), 2);
            } else if (sl.getBlockState(top).canBeReplaced()) sl.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
        // a walkway from the door to the natural ground (carved through hills, raised over dips), one step at a time
        Direction out = rot.rotate(Direction.SOUTH);
        Direction side = out.getClockWise();
        BlockPos cur = base.offset(new BlockPos(bp.w / 2, 0, bp.d + 4).rotate(rot));
        int level = base.getY();
        for (int i = 0; i < 24; i++) {
            BlockPos col = cur.relative(out, i);
            if (!sl.isLoaded(col)) break;
            int ground = naturalFeet(sl, col, level);
            if (ground > level) level++;
            else if (ground < level) level--;
            for (int l = -1; l <= 1; l++) {
                BlockPos q = new BlockPos(col.getX(), level, col.getZ()).relative(side, l);
                if (sl.getBlockEntity(q) != null || BusinessManager.data(sl).at(q) != null) continue;
                sl.setBlock(q.below(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                for (int y = 2; y <= 8; y++) {
                    BlockPos f = q.below(y);
                    if (!sl.getBlockState(f).canBeReplaced()) break;
                    sl.setBlock(f, Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
                for (int y = 0; y < 3; y++) if (sl.getBlockEntity(q.above(y)) == null) sl.setBlock(q.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
            if (ground == level) break;
        }
        // place the blueprint (lower door/fridge halves before upper halves)
        for (var e : bp.blocks.entrySet()) {
            BlockState s = e.getValue();
            if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, rot.rotate(s.getValue(HorizontalDirectionalBlock.FACING)));
            sl.setBlock(base.offset(e.getKey().rotate(rot)), s, 2);
        }
        for (var e : bp.blocks.entrySet()) sl.blockUpdated(base.offset(e.getKey().rotate(rot)), e.getValue().getBlock());
        for (var e : bp.contents.entrySet()) {
            if (sl.getBlockEntity(base.offset(e.getKey().rotate(rot))) instanceof Container cont) {
                if (cont instanceof DisplayBE d) {
                    int i = 0;
                    for (ItemStack s : e.getValue()) if (i < 9) d.setItem(i++, s.copy());
                } else for (ItemStack s : e.getValue()) ContainerUtil.insert(cont, s.copy());
            }
        }
        for (var e : bp.drinks.entrySet()) {
            if (sl.getBlockEntity(base.offset(e.getKey().rotate(rot))) instanceof DrinkMachineBE dm) {
                for (int i = 0; i < e.getValue()[0]; i++) dm.addRefill(ModItems.COLA_SYRUP.get());
                for (int i = 0; i < e.getValue()[1]; i++) dm.addRefill(ModItems.COFFEE_BEANS.get());
            }
        }
        // register the business in the player's name
        BlockPos ba = base.offset(new BlockPos(0, -1, 0).rotate(rot)), bc = base.offset(new BlockPos(bp.w, bp.h, bp.d).rotate(rot));
        Business b = new Business();
        b.id = UUID.randomUUID();
        b.type = bp.type;
        String typeName = bp.name != null ? bp.name : Component.translatable(bp.type.key()).getString();
        b.name = (ownerName + "'s " + typeName);
        if (b.name.length() > 32) b.name = b.name.substring(0, 32);
        b.owner = owner;
        b.ownerName = ownerName;
        b.min = new BlockPos(Math.min(ba.getX(), bc.getX()), Math.min(ba.getY(), bc.getY()), Math.min(ba.getZ(), bc.getZ()));
        b.max = new BlockPos(Math.max(ba.getX(), bc.getX()), Math.max(ba.getY(), bc.getY()), Math.max(ba.getZ(), bc.getZ()));
        b.entrance = base.offset(entranceLocal.rotate(rot));
        for (Dish d : bp.menu) b.menu.get(d).enabled = true;
        data.businesses.put(b.id, b);
        BusinessManager.scan(sl, b);
        for (int[] r : bp.rooms) {
            HotelRoom hr = BusinessManager.createRoom(sl, b, base.offset(new BlockPos(r[0], 0, r[1]).rotate(rot)), base.offset(new BlockPos(r[2], 0, r[3]).rotate(rot)));
            if (hr != null) hr.name = Component.translatable("gui.rpbusiness.room_n", hr.id).getString();
        }
        BusinessManager.refreshCandidates(sl, b, true);
        b.lastSalaryDay = Business.day(sl);
        Bank.get(sl).businessAccount(sl, b);
        data.setDirty();
        say.accept(Component.translatable("msg.rpbusiness.kit_built", b.name).withStyle(ChatFormatting.GREEN));
        return b;
    }
}
