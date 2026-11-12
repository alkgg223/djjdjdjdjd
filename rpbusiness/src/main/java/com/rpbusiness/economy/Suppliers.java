package com.rpbusiness.economy;

import com.rpbusiness.core.ModBlocks;
import com.rpbusiness.core.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;
import java.util.function.Supplier;

/** Supplier companies and their catalogs (price per pack). */
public final class Suppliers {
    public record Product(Supplier<? extends Item> item, int pack, int price) {}
    public record Company(String name, String key, List<Product> products) {}

    private static Product b(DeferredBlock<? extends Block> block, int pack, int price) { return new Product(() -> block.get().asItem(), pack, price); }
    private static Product v(Item item, int pack, int price) { return new Product(() -> item, pack, price); }

    public static final List<Company> ALL = List.of(
            new Company("Green Valley Farms", "supplier.rpbusiness.farm", List.of(
                    new Product(ModItems.TOMATO, 8, 8), new Product(ModItems.ONION, 8, 6), new Product(ModItems.LETTUCE, 8, 7),
                    new Product(ModItems.POTATO, 8, 6))),
            new Company("Prime Meats Co.", "supplier.rpbusiness.meat", List.of(
                    new Product(ModItems.RAW_BEEF, 8, 26), v(Items.CHICKEN, 8, 18), v(Items.PORKCHOP, 8, 20), v(Items.MUTTON, 8, 18))),
            new Company("Golden Crust Bakery", "supplier.rpbusiness.bakery", List.of(
                    new Product(ModItems.BURGER_BUN, 8, 9), new Product(ModItems.BREAD_LOAF, 4, 8), new Product(ModItems.PIZZA_DOUGH, 4, 9))),
            new Company("Sunny Dairy", "supplier.rpbusiness.dairy", List.of(
                    new Product(ModItems.CHEESE_BLOCK, 4, 12))),
            new Company("Fizz & Bean Beverages", "supplier.rpbusiness.drinks", List.of(
                    new Product(ModItems.COLA_SYRUP, 2, 10), new Product(ModItems.COFFEE_BEANS, 2, 12), new Product(ModItems.PAPER_CUP, 16, 4))),
            new Company("City Wholesale Market", "supplier.rpbusiness.wholesale", List.of(
                    v(Items.BREAD, 16, 22), v(Items.APPLE, 16, 18), v(Items.CARROT, 16, 14), v(Items.BAKED_POTATO, 16, 22),
                    v(Items.COOKIE, 16, 14), v(Items.MELON_SLICE, 16, 14), v(Items.PUMPKIN_PIE, 8, 36), v(Items.HONEY_BOTTLE, 8, 30),
                    v(Items.COOKED_BEEF, 16, 60), v(Items.COOKED_CHICKEN, 16, 48), v(Items.CAKE, 1, 9),
                    v(Items.TORCH, 32, 16), v(Items.LANTERN, 8, 24), v(Items.WHITE_WOOL, 16, 20), v(Items.LEATHER_CHESTPLATE, 2, 60),
                    v(Items.LEATHER_BOOTS, 2, 36), v(Items.LEATHER_HELMET, 2, 36), v(Items.COMPASS, 2, 30), v(Items.CLOCK, 2, 40),
                    v(Items.REDSTONE_LAMP, 4, 30), v(Items.DAYLIGHT_DETECTOR, 2, 24), v(Items.COAL, 16, 18), v(Items.IRON_INGOT, 8, 40))),
            new Company("ProKitchen Supply", "supplier.rpbusiness.kitchen", List.of(
                    new Product(ModItems.KITCHEN_KNIFE, 1, 15), new Product(ModItems.SPATULA, 1, 12), new Product(ModItems.MOP, 1, 10),
                    b(ModBlocks.GRILL, 1, 90), b(ModBlocks.FRYER, 1, 80), b(ModBlocks.OVEN, 1, 100), b(ModBlocks.COOKING_POT, 1, 40),
                    b(ModBlocks.CUTTING_BOARD, 1, 15), b(ModBlocks.PREP_STATION, 1, 70), b(ModBlocks.DRINK_MACHINE, 1, 85),
                    b(ModBlocks.FRIDGE, 1, 140), b(ModBlocks.FREEZER, 1, 110), b(ModBlocks.KITCHEN_COUNTER, 4, 40),
                    b(ModBlocks.SERVING_COUNTER, 1, 35), b(ModBlocks.COUNTER_CABINET, 2, 40))),
            new Company("HomeStyle Furniture", "supplier.rpbusiness.furniture", List.of(
                    b(ModBlocks.DINING_TABLE, 1, 30), b(ModBlocks.DINING_CHAIR, 2, 30), b(ModBlocks.BAR_STOOL, 2, 30),
                    b(ModBlocks.SOFA, 1, 45), b(ModBlocks.HOTEL_BED, 1, 70), b(ModBlocks.PENDANT_LAMP, 2, 20),
                    b(ModBlocks.POTTED_PLANT, 2, 16), b(ModBlocks.MENU_BOARD, 1, 18), b(ModBlocks.CHECKERED_FLOOR, 16, 24),
                    b(ModBlocks.KITCHEN_TILES, 16, 24))),
            new Company("Grand Hotel Interiors", "supplier.rpbusiness.hotel", List.of(
                    new Product(ModItems.ROOM_TOOL, 1, 25),
                    b(ModBlocks.DOUBLE_BED, 1, 110), b(ModBlocks.HOTEL_BED, 1, 70), b(ModBlocks.TV, 1, 160),
                    b(ModBlocks.SOFA_GRAY, 1, 55), b(ModBlocks.SOFA_BLUE, 1, 55), b(ModBlocks.ARMCHAIR, 1, 40), b(ModBlocks.OFFICE_CHAIR, 1, 35),
                    b(ModBlocks.COFFEE_TABLE, 1, 30), b(ModBlocks.NIGHTSTAND, 1, 25), b(ModBlocks.TABLE_LAMP, 1, 18), b(ModBlocks.FLOOR_LAMP, 1, 24),
                    b(ModBlocks.WARDROBE, 1, 60), b(ModBlocks.DRESSER, 1, 45), b(ModBlocks.MINIBAR, 1, 80), b(ModBlocks.BOOKSHELF_MODERN, 1, 40),
                    b(ModBlocks.DESK, 1, 35), b(ModBlocks.MIRROR, 1, 20), b(ModBlocks.RUG_RED, 2, 20), b(ModBlocks.RUG_BEIGE, 2, 20),
                    b(ModBlocks.RUG_BLUE, 2, 20), b(ModBlocks.CURTAINS, 2, 18), b(ModBlocks.PAINTING_SEA, 1, 30), b(ModBlocks.PAINTING_CITY, 1, 30),
                    b(ModBlocks.WALL_CLOCK, 1, 15), b(ModBlocks.TALL_PLANT, 1, 18), b(ModBlocks.FLOWER_VASE, 1, 14), b(ModBlocks.CEILING_FAN, 1, 35),
                    b(ModBlocks.AIR_CONDITIONER, 1, 90), b(ModBlocks.RADIATOR, 1, 40), b(ModBlocks.LUGGAGE_RACK, 1, 22), b(ModBlocks.TRASH_BIN, 1, 10),
                    b(ModBlocks.ROOM_PHONE, 1, 20), b(ModBlocks.ROOM_SIGN, 2, 10),
                    b(ModBlocks.TOILET, 1, 60), b(ModBlocks.BATHTUB, 1, 120), b(ModBlocks.SHOWER, 1, 90), b(ModBlocks.BATHROOM_SINK, 1, 50),
                    b(ModBlocks.TOWEL_RACK, 1, 12))),
            new Company("RetailPro Systems", "supplier.rpbusiness.retail", List.of(
                    b(ModBlocks.CASH_REGISTER, 1, 120), b(ModBlocks.PAYMENT_TERMINAL, 1, 140), b(ModBlocks.DISPLAY_SHELF, 1, 45),
                    b(ModBlocks.FRIDGE_DISPLAY, 1, 120), b(ModBlocks.PANTRY_SHELF, 1, 40), b(ModBlocks.DELIVERY_CRATE, 1, 30),
                    b(ModBlocks.BUSINESS_COMPUTER, 1, 150), b(ModBlocks.SUPPLIER_TERMINAL, 1, 120), b(ModBlocks.DELIVERY_TERMINAL, 1, 150),
                    b(ModBlocks.ATM, 1, 400), b(ModBlocks.SAFE, 1, 300), b(ModBlocks.BANK_COUNTER, 1, 200), b(ModBlocks.BANK_COMPUTER, 1, 250),
                    new Product(ModItems.SHOPPING_BASKET, 4, 20), new Product(ModItems.SHOPPING_CART, 2, 40))),
            new Company("BuildRight Construction", "supplier.rpbusiness.construction", List.of(
                    new Product(ModItems.KIT_RESTAURANT, 1, 1200), new Product(ModItems.KIT_CAFE, 1, 900), new Product(ModItems.KIT_FAST_FOOD, 1, 1000),
                    new Product(ModItems.KIT_BAKERY, 1, 900), new Product(ModItems.KIT_GROCERY, 1, 900), new Product(ModItems.KIT_SUPERMARKET, 1, 1600),
                    new Product(ModItems.KIT_CLOTHING, 1, 900), new Product(ModItems.KIT_ELECTRONICS, 1, 1100), new Product(ModItems.KIT_FURNITURE, 1, 1000),
                    new Product(ModItems.KIT_BUTCHER, 1, 900), new Product(ModItems.KIT_GAS_STATION, 1, 1000), new Product(ModItems.KIT_HOTEL, 1, 2000),
                    new Product(ModItems.KIT_BANK, 1, 1500), new Product(ModItems.KIT_DELIVERY, 1, 1000), new Product(ModItems.KIT_WAREHOUSE, 1, 800))));

    /** Finds the catalog entry selling this item (company index, product index) or null. */
    public static int[] find(Item item) {
        for (int c = 0; c < ALL.size(); c++)
            for (int p = 0; p < ALL.get(c).products().size(); p++)
                if (ALL.get(c).products().get(p).item().get() == item) return new int[]{c, p};
        return null;
    }

    private Suppliers() {}
}
