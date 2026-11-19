package com.trafficcraft.registry;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.vehicle.FuelCanItem;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleItem;
import com.trafficcraft.vehicle.VehicleSpecs;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TCRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, TrafficCraft.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TrafficCraft.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, TrafficCraft.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TrafficCraft.MODID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.trafficcraft.vehicle.HeadlightGlowBlock> HEADLIGHT_GLOW = BLOCKS.register("headlight_glow",
            () -> new com.trafficcraft.vehicle.HeadlightGlowBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().replaceable().noCollission()
                    .noLootTable().air().lightLevel(s -> 13).instabreak()));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrafficCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> VEHICLE = ENTITIES.register("vehicle",
            () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC).sized(1.9f, 1.5f).clientTrackingRange(10)
                    .updateInterval(1).fireImmune().build("vehicle"));

    public static final Map<String, DeferredItem<VehicleItem>> VEHICLE_ITEMS = new LinkedHashMap<>();
    public static final DeferredItem<FuelCanItem> FUEL_CAN = ITEMS.register("fuel_canister", () -> new FuelCanItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.trafficcraft.road.RoadToolItem> ROAD_TOOL = ITEMS.register("road_planner",
            () -> new com.trafficcraft.road.RoadToolItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.trafficcraft.station.FuelNozzleItem> FUEL_NOZZLE = ITEMS.register("fuel_nozzle",
            () -> new com.trafficcraft.station.FuelNozzleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PASSPORT = ITEMS.registerSimpleItem("passport", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> INSPECTION_STAMP = ITEMS.registerSimpleItem("inspection_stamp", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> CAR_KEY = ITEMS.registerSimpleItem("car_key", new Item.Properties().stacksTo(1));

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUND_MAP = new HashMap<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("vehicles", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.trafficcraft.vehicles"))
            .icon(() -> new ItemStack(VEHICLE_ITEMS.values().iterator().next().get()))
            .displayItems((params, out) -> {
                VEHICLE_ITEMS.values().forEach(i -> out.accept(i.get()));
                out.accept(FUEL_CAN.get());
                out.accept(CAR_KEY.get());
                out.accept(PASSPORT.get());
                out.accept(INSPECTION_STAMP.get());
            }).build());

    public static void register(IEventBus bus) {
        for (String id : VehicleSpecs.ids()) {
            VEHICLE_ITEMS.put(id, ITEMS.register("vehicle_" + id, () -> new VehicleItem(id, new Item.Properties().stacksTo(1))));
            for (String k : new String[]{"idle", "drive", "start"}) sound("engine." + id + "." + k);
        }
        for (String k : new String[]{"horn.car", "horn.high", "horn.low", "horn.truck", "siren.wail", "misc.indicator_on", "misc.indicator_off",
                "misc.door_close", "misc.door_open", "misc.barrier_motor", "misc.fuel_pump", "misc.crash", "misc.reverse_beep", "misc.gear_shift"}) {
            sound(k);
        }
        BLOCKS.register(bus);
        TCBlocks.register(bus);
        ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
    }

    private static void sound(String name) {
        SOUND_MAP.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(TrafficCraft.id(name))));
    }

    public static SoundEvent sound(String name, Supplier<String> fallback) {
        var h = SOUND_MAP.get(name);
        if (h == null) h = SOUND_MAP.get(fallback.get());
        return h.get();
    }

    public static SoundEvent snd(String name) {
        return SOUND_MAP.get(name).get();
    }
}
