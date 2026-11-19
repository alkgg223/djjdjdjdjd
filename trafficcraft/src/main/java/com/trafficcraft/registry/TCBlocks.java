package com.trafficcraft.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.block.BEBlock;
import com.trafficcraft.block.BarrierArmBlock;
import com.trafficcraft.block.BarrierGateBE;
import com.trafficcraft.block.FacingBlock;
import com.trafficcraft.block.GenericBE;
import com.trafficcraft.block.ShapeBlock;
import com.trafficcraft.block.ShapeUtil;
import com.trafficcraft.block.StreetLampBlock;
import com.trafficcraft.block.TrafficLightBE;
import com.trafficcraft.block.TrafficSignBlock;
import com.trafficcraft.block.UseBlock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Infrastructure blocks, created from the generated assets/trafficcraft/blocks.json definition list. */
public final class TCBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TrafficCraft.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TrafficCraft.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TrafficCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrafficCraft.MODID);
    public static final Map<String, DeferredBlock<? extends Block>> MAP = new LinkedHashMap<>();
    private static final List<String> TRAFFIC_LIGHT_BLOCKS = new ArrayList<>(), GATE_BLOCKS = new ArrayList<>(), GENERIC_BLOCKS = new ArrayList<>();

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<TrafficLightBE>> TRAFFIC_LIGHT_BE;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<BarrierGateBE>> BARRIER_GATE_BE;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<GenericBE>> GENERIC_BE;

    public static Block get(String id) {
        return MAP.get(id).get();
    }

    public static boolean isInfrastructure(BlockState s) {
        return s.getBlock().builtInRegistryHolder().key().location().getNamespace().equals(TrafficCraft.MODID);
    }

    public static void register(IEventBus bus) {
        JsonObject root;
        var defs = JsonParser.parseReader(new InputStreamReader(TCBlocks.class.getResourceAsStream("/assets/trafficcraft/blocks.json"), StandardCharsets.UTF_8))
                .getAsJsonArray();
        for (var el : defs) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            String kind = o.get("kind").getAsString();
            double[][] boxes = ShapeUtil.boxes(o.has("shape") ? o.getAsJsonArray("shape") : null);
            boolean noCollide = o.has("nocollide") && o.get("nocollide").getAsBoolean();
            Supplier<BlockBehaviour.Properties> props = () -> props(o);
            DeferredBlock<? extends Block> b = switch (kind) {
                case "cube" -> BLOCKS.register(id, () -> new Block(props.get()));
                case "slab" -> BLOCKS.register(id, () -> new SlabBlock(props.get()));
                case "shape" -> BLOCKS.register(id, () -> new ShapeBlock(props.get().noOcclusion(), boxes, noCollide));
                case "facing_shape", "seat" -> BLOCKS.register(id, () -> new FacingBlock(props.get().noOcclusion(), boxes, noCollide));
                case "sign" -> BLOCKS.register(id, () -> new TrafficSignBlock(props.get().noOcclusion(), boxes, o.get("sign").getAsString()));
                case "street_lamp" -> BLOCKS.register(id, () -> new StreetLampBlock(props.get().noOcclusion()));
                case "barrier_arm" -> BLOCKS.register(id, () -> new BarrierArmBlock(props.get().noOcclusion().noLootTable().strength(-1, 3600000)));
                case "traffic_light", "ped_signal" -> {
                    TRAFFIC_LIGHT_BLOCKS.add(id);
                    yield BLOCKS.register(id, () -> new BEBlock(props.get().noOcclusion(), boxes, false, kind, () -> TRAFFIC_LIGHT_BE.get(), true));
                }
                case "barrier_gate" -> {
                    GATE_BLOCKS.add(id);
                    yield BLOCKS.register(id, () -> new BEBlock(props.get().noOcclusion(), boxes, false, kind, () -> BARRIER_GATE_BE.get(), true));
                }
                case "text_sign", "camera", "fuel_pump", "fuel_tank", "workplace" -> {
                    GENERIC_BLOCKS.add(id);
                    String k = kind.equals("workplace") ? "workplace_" + o.get("workplace").getAsString() : kind;
                    yield BLOCKS.register(id, () -> new BEBlock(props.get().noOcclusion(), boxes, noCollide, k, () -> GENERIC_BE.get(), true));
                }
                case "terminal", "control_panel" -> BLOCKS.register(id, () -> new UseBlock(props.get().noOcclusion(), boxes, noCollide, kind));
                default -> BLOCKS.register(id, () -> new ShapeBlock(props.get().noOcclusion(), boxes, noCollide));
            };
            MAP.put(id, b);
            if (!(o.has("noitem") && o.get("noitem").getAsBoolean())) {
                ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
            }
        }
        TRAFFIC_LIGHT_BE = BES.register("traffic_light", () -> BlockEntityType.Builder.of(TrafficLightBE::new, blocks(TRAFFIC_LIGHT_BLOCKS)).build(null));
        BARRIER_GATE_BE = BES.register("barrier_gate", () -> BlockEntityType.Builder.of(BarrierGateBE::new, blocks(GATE_BLOCKS)).build(null));
        GENERIC_BE = BES.register("generic", () -> BlockEntityType.Builder.of(GenericBE::new, blocks(GENERIC_BLOCKS)).build(null));
        TABS.register("roads", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.trafficcraft.roads"))
                .icon(() -> new ItemStack(MAP.get("sign_stop").get())).withTabsBefore(TrafficCraft.id("vehicles"))
                .displayItems((params, out) -> {
                    out.accept(TCRegistry.ROAD_TOOL.get());
                    for (var en : MAP.entrySet()) {
                        if (en.getKey().equals("barrier_arm")) continue;
                        out.accept(en.getValue().get());
                    }
                }).build());
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BES.register(bus);
        TABS.register(bus);
    }

    private static Block[] blocks(List<String> ids) {
        Block[] arr = new Block[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = MAP.get(ids.get(i)).get();
        return arr;
    }

    private static BlockBehaviour.Properties props(JsonObject o) {
        String mat = o.has("material") ? o.get("material").getAsString() : "stone";
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of();
        switch (mat) {
            case "metal" -> p = p.mapColor(MapColor.METAL).sound(SoundType.METAL);
            case "wood" -> p = p.mapColor(MapColor.WOOD).sound(SoundType.WOOD);
            case "wool" -> p = p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.WOOL);
            default -> p = p.mapColor(MapColor.STONE).sound(SoundType.STONE);
        }
        float h = o.has("hardness") ? o.get("hardness").getAsFloat() : 1.5f;
        p = p.strength(h, Math.max(h * 3, 3));
        if (o.has("light")) {
            int l = o.get("light").getAsInt();
            p = p.lightLevel(s -> l);
        }
        if (o.has("nocollide") && o.get("nocollide").getAsBoolean()) p = p.noCollission();
        return p;
    }
}
