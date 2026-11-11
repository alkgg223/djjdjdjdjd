package com.rpbusiness.economy;

import com.rpbusiness.core.ModItems;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

/** Supplier companies and their catalogs (price per pack). */
public final class Suppliers {
    public record Product(Supplier<? extends Item> item, int pack, int price) {}
    public record Company(String name, String key, List<Product> products) {}

    public static final List<Company> ALL = List.of(
            new Company("Green Valley Farms", "supplier.rpbusiness.farm", List.of(
                    new Product(ModItems.TOMATO, 8, 8), new Product(ModItems.ONION, 8, 6), new Product(ModItems.LETTUCE, 8, 7),
                    new Product(ModItems.POTATO, 8, 6))),
            new Company("Prime Meats Co.", "supplier.rpbusiness.meat", List.of(
                    new Product(ModItems.RAW_BEEF, 8, 26))),
            new Company("Golden Crust Bakery", "supplier.rpbusiness.bakery", List.of(
                    new Product(ModItems.BURGER_BUN, 8, 9), new Product(ModItems.BREAD_LOAF, 4, 8), new Product(ModItems.PIZZA_DOUGH, 4, 9))),
            new Company("Sunny Dairy", "supplier.rpbusiness.dairy", List.of(
                    new Product(ModItems.CHEESE_BLOCK, 4, 12))),
            new Company("Fizz & Bean Beverages", "supplier.rpbusiness.drinks", List.of(
                    new Product(ModItems.COLA_SYRUP, 2, 10), new Product(ModItems.COFFEE_BEANS, 2, 12), new Product(ModItems.PAPER_CUP, 16, 4))),
            new Company("ProKitchen Supply", "supplier.rpbusiness.kitchen", List.of(
                    new Product(ModItems.KITCHEN_KNIFE, 1, 15), new Product(ModItems.SPATULA, 1, 12), new Product(ModItems.MOP, 1, 10))));

    private Suppliers() {}
}
