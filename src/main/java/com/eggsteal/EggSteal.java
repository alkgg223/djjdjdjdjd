package com.eggsteal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(EggSteal.MODID)
public class EggSteal {
    public static final String MODID = "eggsteal";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final List<Supplier<? extends Item>> EGG_ITEMS = new ArrayList<>();
    static {
        for (Creature c : Creature.values()) EGG_ITEMS.add(ITEMS.register("egg_" + c.id, () -> new EggItem(c, new Item.Properties())));
    }

    public static final List<DeferredBlock<Block>> SKIN_BLOCKS = new ArrayList<>();
    public static final List<DeferredItem<BlockItem>> SKIN_ITEMS = new ArrayList<>();
    static {
        for (String n : new String[]{"neon", "crystal", "candy", "golden", "rune", "frost", "slime", "lavarock"}) {
            boolean glow = !(n.equals("frost") || n.equals("rune"));
            DeferredBlock<Block> b = BLOCKS.register("skin_" + n, () -> new Block(BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> glow ? 12 : 0)));
            SKIN_BLOCKS.add(b);
            SKIN_ITEMS.add(ITEMS.register("skin_" + n, () -> new BlockItem(b.get(), new Item.Properties())));
        }
    }

    public static final DeferredItem<BatItem> BAT = ITEMS.register("bat", BatItem::new);
    public static final DeferredItem<CoilItem> SPEED_COIL = ITEMS.register("speed_coil", () -> new CoilItem(false));
    public static final DeferredItem<CoilItem> JUMP_COIL = ITEMS.register("jump_coil", () -> new CoilItem(true));
    public static final DeferredBlock<StationBlock> STATION = BLOCKS.register("upgrade_station",
            () -> new StationBlock(BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> 12).sound(SoundType.METAL), false));
    public static final DeferredItem<BlockItem> STATION_ITEM = ITEMS.register("upgrade_station", () -> new BlockItem(STATION.get(), new Item.Properties()));
    public static final DeferredBlock<StationBlock> BUILDER = BLOCKS.register("builder_station",
            () -> new StationBlock(BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> 12).sound(SoundType.METAL), true));
    public static final DeferredItem<BlockItem> BUILDER_ITEM = ITEMS.register("builder_station", () -> new BlockItem(BUILDER.get(), new Item.Properties()));

    public static final DeferredHolder<EntityType<?>, EntityType<CreatureEntity>> CREATURE = ENTITIES.register("creature",
            () -> EntityType.Builder.of(CreatureEntity::new, MobCategory.MISC).sized(0.8f, 0.8f).clientTrackingRange(10).build("creature"));
    public static final DeferredHolder<EntityType<?>, EntityType<BotEntity>> BOT = ENTITIES.register("bot",
            () -> EntityType.Builder.of(BotEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).build("bot"));

    public static final DeferredHolder<EntityType<?>, EntityType<AuctionEggEntity>> AUCTION = ENTITIES.register("auction_egg",
            () -> EntityType.Builder.of(AuctionEggEntity::new, MobCategory.MISC).sized(0.7f, 0.8f).clientTrackingRange(16).updateInterval(1).build("auction_egg"));

    public static final DeferredHolder<EntityType<?>, EntityType<EventMarkerEntity>> EVENT_MARKER = ENTITIES.register("event_marker",
            () -> EntityType.Builder.<EventMarkerEntity>of(EventMarkerEntity::new, MobCategory.MISC).sized(0.2f, 0.2f).clientTrackingRange(16).updateInterval(20).build("event_marker"));

    // ---------------- Brainrot Heist mode
    public static final DeferredHolder<EntityType<?>, EntityType<com.eggsteal.brainrot.BrainrotEntity>> BRAINROT = ENTITIES.register("brainrot",
            () -> EntityType.Builder.<com.eggsteal.brainrot.BrainrotEntity>of(com.eggsteal.brainrot.BrainrotEntity::new, MobCategory.MISC).sized(0.9f, 1.9f).clientTrackingRange(10).updateInterval(2).build("brainrot"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.eggsteal.brainrot.BrBot>> BR_BOT = ENTITIES.register("brainrot_bot",
            () -> EntityType.Builder.of(com.eggsteal.brainrot.BrBot::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).build("brainrot_bot"));
    public static final DeferredBlock<com.eggsteal.brainrot.BrStationBlock> BR_SHOP = BLOCKS.register("brainrot_shop",
            () -> new com.eggsteal.brainrot.BrStationBlock(BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> 12).sound(SoundType.METAL), false));
    public static final DeferredItem<BlockItem> BR_SHOP_ITEM = ITEMS.register("brainrot_shop", () -> new BlockItem(BR_SHOP.get(), new Item.Properties()));
    public static final DeferredBlock<com.eggsteal.brainrot.BrStationBlock> BR_GUIDE = BLOCKS.register("brainrot_guide_station",
            () -> new com.eggsteal.brainrot.BrStationBlock(BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> 10).sound(SoundType.WOOD), true));
    public static final DeferredItem<BlockItem> BR_GUIDE_ITEM = ITEMS.register("brainrot_guide_station", () -> new BlockItem(BR_GUIDE.get(), new Item.Properties()));
    public static final DeferredItem<com.eggsteal.brainrot.BrGuideItem> BR_GUIDE_BOOK = ITEMS.register("brainrot_guide", com.eggsteal.brainrot.BrGuideItem::new);
    /** Picture cards of every brainrot and every mutation (shown in the guide). */
    public static final List<DeferredItem<Item>> BR_CARDS = new ArrayList<>(), BR_MUTS = new ArrayList<>();
    static {
        for (com.eggsteal.brainrot.Brainrot b : com.eggsteal.brainrot.Brainrot.ALL) BR_CARDS.add(ITEMS.register("br_" + b.id, () -> new Item(new Item.Properties())));
        for (com.eggsteal.brainrot.BrMutation m : com.eggsteal.brainrot.BrMutation.ALL) BR_MUTS.add(ITEMS.register("brmut_" + m.id, () -> new Item(new Item.Properties())));
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BR_TAB = TABS.register("brainrot",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.eggsteal.brainrot"))
                    .icon(() -> new ItemStack(BR_CARDS.get(0).get()))
                    .displayItems((p, out) -> {
                        out.accept(BR_GUIDE_BOOK.get());
                        out.accept(BR_SHOP_ITEM.get());
                        out.accept(BR_GUIDE_ITEM.get());
                        for (DeferredItem<Item> c : BR_CARDS) out.accept(c.get());
                        for (DeferredItem<Item> c : BR_MUTS) out.accept(c.get());
                    }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.eggsteal"))
                    .icon(() -> new ItemStack(EGG_ITEMS.get(0).get()))
                    .displayItems((p, out) -> {
                        for (Supplier<? extends Item> s : EGG_ITEMS) out.accept(s.get());
                        out.accept(BAT.get());
                        out.accept(SPEED_COIL.get());
                        out.accept(JUMP_COIL.get());
                        out.accept(STATION_ITEM.get());
                        out.accept(BUILDER_ITEM.get());
                        for (DeferredItem<BlockItem> sb : SKIN_ITEMS) out.accept(sb.get());
                    }).build());

    public EggSteal(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(EggSteal::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(CREATURE.get(), CreatureEntity.attrs().build());
        e.put(BOT.get(), BotEntity.attrs().build());
        e.put(BR_BOT.get(), com.eggsteal.brainrot.BrBot.attrs().build());
    }
}
