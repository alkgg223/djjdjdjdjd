package com.realweather.vehicle;

import com.realweather.RealWeather;
import com.realweather.device.ModDevices;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class VehicleRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RealWeather.MODID);
    private static final Map<VehicleType, DeferredHolder<EntityType<?>, EntityType<StormVehicle>>> TYPES = new EnumMap<>(VehicleType.class);
    private static final Map<VehicleType, DeferredItem<VehicleItem>> ITEMS = new EnumMap<>(VehicleType.class);

    static {
        for (VehicleType kind : VehicleType.values()) {
            float w = kind.size[0] + 0.2F;
            float h = Math.min(2.6F, kind.size[1]);
            TYPES.put(kind, ENTITIES.register(kind.id, () -> EntityType.Builder.<StormVehicle>of((t, l) -> new StormVehicle(t, l, kind), MobCategory.MISC)
                    .sized(w, h).clientTrackingRange(12).updateInterval(1).build(RealWeather.MODID + ":" + kind.id)));
            ITEMS.put(kind, ModDevices.tabItem(ModDevices.ITEMS.register(kind.id, () -> new VehicleItem(kind, new Item.Properties().stacksTo(1)))));
        }
    }

    private VehicleRegistry() {}

    public static EntityType<StormVehicle> type(VehicleType kind) {
        return TYPES.get(kind).get();
    }

    public static Item item(VehicleType kind) {
        return ITEMS.get(kind).get();
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(VehicleRegistry::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        for (VehicleType kind : VehicleType.values()) {
            event.put(type(kind), StormVehicle.attributes(kind).build());
        }
    }
}
