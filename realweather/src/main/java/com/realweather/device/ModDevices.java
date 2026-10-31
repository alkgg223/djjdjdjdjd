package com.realweather.device;

import com.realweather.ModContent;
import com.realweather.RealWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Equipment: the weather radar and its monitor (and the storm-chasing vehicles), with their items and creative tab. */
public final class ModDevices {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealWeather.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RealWeather.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealWeather.MODID);
    private static final List<Supplier<? extends Item>> TAB_ITEMS = new ArrayList<>();

    public static final DeferredBlock<RadarBlock> WEATHER_RADAR = ModContent.BLOCKS.register("weather_radar",
            () -> new RadarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6F).sound(SoundType.METAL)
                    .noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<MonitorBlock> RADAR_MONITOR = ModContent.BLOCKS.register("radar_monitor",
            () -> new MonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6F).sound(SoundType.METAL)
                    .noOcclusion().lightLevel(s -> 4)));

    public static final DeferredItem<BlockItem> WEATHER_RADAR_ITEM = blockItem("weather_radar", WEATHER_RADAR);
    public static final DeferredItem<BlockItem> RADAR_MONITOR_ITEM = blockItem("radar_monitor", RADAR_MONITOR);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarBlockEntity>> RADAR_BE = BLOCK_ENTITIES.register("weather_radar",
            () -> BlockEntityType.Builder.of(RadarBlockEntity::new, WEATHER_RADAR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitorBlockEntity>> MONITOR_BE = BLOCK_ENTITIES.register("radar_monitor",
            () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, RADAR_MONITOR.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realweather"))
            .icon(() -> new ItemStack(RADAR_MONITOR_ITEM.get()))
            .displayItems((params, out) -> TAB_ITEMS.forEach(s -> out.accept(s.get())))
            .build());

    private ModDevices() {}

    private static DeferredItem<BlockItem> blockItem(String name, DeferredBlock<?> block) {
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(name, block);
        TAB_ITEMS.add(item);
        return item;
    }

    /** Adds an item to the Real Weather creative tab (vehicles register theirs). */
    public static <T extends Item> DeferredItem<T> tabItem(DeferredItem<T> item) {
        TAB_ITEMS.add(item);
        return item;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }
}
