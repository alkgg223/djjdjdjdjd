package com.arkragnarok.economy;

import com.arkragnarok.ArkRagnarok;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.arkragnarok.registry.ModItems;

/**
 * The mod's own economy (what survivors and players gather, process and build with): raw resources, processed
 * materials, tools and weapons, crafting stations, storage, a bed and a crop plot.  Every item is a 3D model.
 */
public final class ModEconomy {
    private ModEconomy() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArkRagnarok.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArkRagnarok.MODID);
    /** items in creative-tab order */
    public static final List<DeferredItem<? extends Item>> ALL = new ArrayList<>();

    private static DeferredItem<Item> res(String id) {
        DeferredItem<Item> d = ModItems.ITEMS.register(id, () -> new Item(new Item.Properties()));
        ALL.add(d);
        return d;
    }

    private static <T extends Item> DeferredItem<T> item(String id, Supplier<T> s) {
        DeferredItem<T> d = ModItems.ITEMS.register(id, s);
        ALL.add(d);
        return d;
    }

    // ------------------------------------------------------------------------------------------ raw resources
    public static final DeferredItem<Item> WOOD = res("wood");
    public static final DeferredItem<Item> THATCH = res("thatch");
    public static final DeferredItem<Item> FIBER = res("fiber");
    public static final DeferredItem<Item> STONE = res("stone");
    public static final DeferredItem<Item> FLINT = res("flint");
    public static final DeferredItem<Item> METAL_ORE = res("metal_ore");
    public static final DeferredItem<Item> CRYSTAL = res("crystal");
    public static final DeferredItem<Item> OBSIDIAN = res("obsidian");
    public static final DeferredItem<Item> HIDE = res("hide");
    public static final DeferredItem<Item> KERATIN = res("keratin");
    public static final DeferredItem<Item> DINO_BONE = res("dino_bone");
    public static final DeferredItem<Item> DINO_TOOTH = res("dino_tooth");
    public static final DeferredItem<Item> RAW_MEAT = item("raw_meat", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
            .nutrition(3).saturationModifier(0.2f).effect(new MobEffectInstance(MobEffects.HUNGER, 300, 0), 0.5f).build())));
    public static final DeferredItem<Item> COOKED_MEAT = item("cooked_meat", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
            .nutrition(7).saturationModifier(0.7f).build())));
    // ------------------------------------------------------------------------------------------ processed
    public static final DeferredItem<Item> CHARCOAL = res("charcoal");
    public static final DeferredItem<Item> METAL_INGOT = res("metal_ingot");
    public static final DeferredItem<Item> STEEL_INGOT = res("steel_ingot");
    public static final DeferredItem<Item> SPARKPOWDER = res("sparkpowder");
    public static final DeferredItem<Item> GUNPOWDER = res("gunpowder");
    public static final DeferredItem<Item> CEMENTING_PASTE = res("cementing_paste");
    public static final DeferredItem<Item> POLYMER = res("polymer");
    public static final DeferredItem<Item> ELECTRONICS = res("electronics");
    // ------------------------------------------------------------------------------------------ tools and weapons
    public static final DeferredItem<PickaxeItem> STONE_PICK = item("stone_pick", () -> new PickaxeItem(Tiers.STONE,
            new Item.Properties().attributes(PickaxeItem.createAttributes(Tiers.STONE, 1.0f, -2.8f))));
    public static final DeferredItem<PickaxeItem> METAL_PICK = item("metal_pick", () -> new PickaxeItem(Tiers.IRON,
            new Item.Properties().attributes(PickaxeItem.createAttributes(Tiers.IRON, 1.0f, -2.8f))));
    public static final DeferredItem<AxeItem> STONE_HATCHET = item("stone_hatchet", () -> new AxeItem(Tiers.STONE,
            new Item.Properties().attributes(AxeItem.createAttributes(Tiers.STONE, 6.0f, -3.1f))));
    public static final DeferredItem<AxeItem> METAL_HATCHET = item("metal_hatchet", () -> new AxeItem(Tiers.IRON,
            new Item.Properties().attributes(AxeItem.createAttributes(Tiers.IRON, 6.0f, -3.1f))));
    public static final DeferredItem<SwordItem> METAL_SICKLE = item("metal_sickle", () -> new SwordItem(Tiers.IRON,
            new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 2, -2.2f))));
    public static final DeferredItem<SwordItem> WOODEN_CLUB = item("wooden_club", () -> new SwordItem(Tiers.WOOD,
            new Item.Properties().attributes(SwordItem.createAttributes(Tiers.WOOD, 1, -2.6f))));
    public static final DeferredItem<SwordItem> STONE_SPEAR = item("stone_spear", () -> new SwordItem(Tiers.STONE,
            new Item.Properties().attributes(SwordItem.createAttributes(Tiers.STONE, 4, -2.9f))));
    public static final DeferredItem<SwordItem> METAL_SWORD = item("metal_sword", () -> new SwordItem(Tiers.IRON,
            new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 4, -2.4f))));
    public static final DeferredItem<BowItem> PRIMITIVE_BOW = item("primitive_bow", () -> new BowItem(new Item.Properties().durability(384)));
    public static final DeferredItem<ArrowItem> STONE_ARROW = item("stone_arrow", () -> new ArrowItem(new Item.Properties()));

    // ------------------------------------------------------------------------------------------ stations, storage, bed, farm
    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 4f).sound(SoundType.WOOD).noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties rock() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4f, 12f).sound(SoundType.STONE).noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> b) {
        DeferredBlock<B> d = BLOCKS.register(id, b);
        ALL.add(ModItems.ITEMS.register(id, () -> new BlockItem(d.get(), new Item.Properties())));
        return d;
    }

    public static final DeferredBlock<StationBlock> CAMPFIRE = block("campfire", () -> new StationBlock(Station.CAMPFIRE,
            rock().lightLevel(s -> 14).strength(1.5f, 3f)));
    public static final DeferredBlock<StationBlock> MORTAR = block("mortar_and_pestle", () -> new StationBlock(Station.MORTAR, rock()));
    public static final DeferredBlock<StationBlock> SMITHY = block("smithy", () -> new StationBlock(Station.SMITHY, wood()));
    public static final DeferredBlock<StationBlock> REFINING_FORGE = block("refining_forge", () -> new StationBlock(Station.FORGE,
            rock().lightLevel(s -> 12)));
    public static final DeferredBlock<StationBlock> FABRICATOR = block("fabricator", () -> new StationBlock(Station.FABRICATOR,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6f, 30f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 6)));
    public static final DeferredBlock<StorageBoxBlock> STORAGE_BOX = block("storage_box", () -> new StorageBoxBlock(false, wood()));
    public static final DeferredBlock<StorageBoxBlock> LARGE_STORAGE_BOX = block("large_storage_box", () -> new StorageBoxBlock(true,
            wood().strength(4f, 20f)));
    public static final DeferredBlock<CropPlotBlock> CROP_PLOT = block("crop_plot", () -> new CropPlotBlock(wood().randomTicks()));
    public static final DeferredBlock<HideBedBlock> HIDE_BED = BLOCKS.register("hide_bed", () -> new HideBedBlock(wood().strength(0.6f)));
    public static final DeferredItem<BedItem> HIDE_BED_ITEM = item("hide_bed", () -> new BedItem(HIDE_BED.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageBoxEntity>> STORAGE_BE = BLOCK_ENTITIES.register("storage_box",
            () -> BlockEntityType.Builder.of(StorageBoxEntity::new, STORAGE_BOX.get(), LARGE_STORAGE_BOX.get()).build(null));

    /** loads the class so its items join ModItems.ITEMS before that register is bound to the bus */
    public static void init() {
    }
}
