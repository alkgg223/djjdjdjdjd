package com.vehicleworks.registry;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.item.AmmoItem;
import com.vehicleworks.item.EngineeringWrenchItem;
import com.vehicleworks.item.FuelItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VehicleWorks.MODID);
    public static final Map<ComponentType, DeferredItem<BlockItem>> COMPONENT_ITEMS = new EnumMap<>(ComponentType.class);
    public static final Map<com.vehicleworks.block.SkinBlock.Kind, DeferredItem<BlockItem>> SKIN_ITEMS = new EnumMap<>(com.vehicleworks.block.SkinBlock.Kind.class);

    static {
        for (ComponentType t : ComponentType.values()) {
            COMPONENT_ITEMS.put(t, ITEMS.registerSimpleBlockItem(t.id, ModBlocks.COMPONENTS.get(t),
                    new Item.Properties().rarity(t == ComponentType.VEHICLE_CORE ? Rarity.UNCOMMON : Rarity.COMMON)));
        }
        for (var k : com.vehicleworks.block.SkinBlock.Kind.values())
            SKIN_ITEMS.put(k, ITEMS.registerSimpleBlockItem(ModBlocks.skinId(k), ModBlocks.SKINS.get(k), new Item.Properties()));
    }


    public static final DeferredItem<EngineeringWrenchItem> WRENCH = ITEMS.register("engineering_wrench",
            () -> new EngineeringWrenchItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<FuelItem> FUEL_CANISTER = ITEMS.register("fuel_canister",
            () -> new FuelItem(new Item.Properties().stacksTo(16), 120));
    public static final DeferredItem<AmmoItem> AMMO_BELT = ITEMS.register("ammo_belt",
            () -> new AmmoItem(new Item.Properties().stacksTo(64), AmmoItem.Kind.BULLET, 100));
    public static final DeferredItem<AmmoItem> CANNON_SHELL = ITEMS.register("cannon_shell",
            () -> new AmmoItem(new Item.Properties().stacksTo(32), AmmoItem.Kind.SHELL, 4));
    public static final DeferredItem<AmmoItem> MISSILE = ITEMS.register("missile",
            () -> new AmmoItem(new Item.Properties().stacksTo(8), AmmoItem.Kind.MISSILE, 1));
    public static final DeferredItem<AmmoItem> AERIAL_BOMB = ITEMS.register("aerial_bomb",
            () -> new AmmoItem(new Item.Properties().stacksTo(8), AmmoItem.Kind.BOMB, 1));

    private ModItems() {}
}
