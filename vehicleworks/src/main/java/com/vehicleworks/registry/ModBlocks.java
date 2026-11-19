package com.vehicleworks.registry;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.component.ComponentType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VehicleWorks.MODID);
    public static final Map<ComponentType, DeferredBlock<ComponentBlock>> COMPONENTS = new EnumMap<>(ComponentType.class);

    /** aircraft skin blocks (fuselage panels, windows, rounded corners, slopes, cabin floor, cockpit glass) */
    public static final Map<com.vehicleworks.block.SkinBlock.Kind, DeferredBlock<com.vehicleworks.block.SkinBlock>> SKINS =
            new EnumMap<>(com.vehicleworks.block.SkinBlock.Kind.class);

    static {
        for (ComponentType t : ComponentType.values()) {
            COMPONENTS.put(t, BLOCKS.register(t.id, () -> create(t)));
        }
        for (com.vehicleworks.block.SkinBlock.Kind k : com.vehicleworks.block.SkinBlock.Kind.values()) {
            SKINS.put(k, BLOCKS.register(skinId(k), () -> {
                BlockBehaviour.Properties p = BlockBehaviour.Properties.of().strength(2.0f, 5.0f).mapColor(k == com.vehicleworks.block.SkinBlock.Kind.FLOOR ? MapColor.COLOR_BLUE : MapColor.SNOW)
                        .sound(k == com.vehicleworks.block.SkinBlock.Kind.FLOOR ? SoundType.WOOL : (k == com.vehicleworks.block.SkinBlock.Kind.COCKPIT_GLASS ? SoundType.GLASS : SoundType.COPPER));
                if (k.shaped || k == com.vehicleworks.block.SkinBlock.Kind.WINDOW || k == com.vehicleworks.block.SkinBlock.Kind.COCKPIT_GLASS) p = p.noOcclusion();
                return new com.vehicleworks.block.SkinBlock(k, p);
            }));
        }
    }

    public static String skinId(com.vehicleworks.block.SkinBlock.Kind k) {
        return switch (k) {
            case FLOOR -> "cabin_floor";
            case COCKPIT_GLASS -> "cockpit_glass";
            default -> "skin_" + k.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    public static net.minecraft.world.level.block.state.BlockState skin(com.vehicleworks.block.SkinBlock.Kind k) {
        return SKINS.get(k).get().defaultBlockState();
    }

    private ModBlocks() {}

    private static ComponentBlock create(ComponentType t) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                .strength(2.5f, 6.0f)
                .sound(soundFor(t))
                .mapColor(colorFor(t))
                .pushReaction(PushReaction.BLOCK);
        if (t.shape != ComponentType.Shape.FULL) p = p.noOcclusion();
        if (t == ComponentType.BALLOON_ENVELOPE || t == ComponentType.GAS_CELL) p = p.strength(0.6f, 0.5f);
        if (t == ComponentType.BURNER) p = p.lightLevel(s -> 9);
        if (t == ComponentType.FLIGHT_INSTRUMENTS || t == ComponentType.COCKPIT_PANEL || t == ComponentType.NAVIGATION_COMPUTER || t == ComponentType.REMOTE_CONTROL_UNIT) p = p.lightLevel(s -> 5);
        return switch (t.facing) {
            case ALL -> new ComponentBlock.Directional(t, p);
            case HORIZONTAL -> new ComponentBlock.Horizontal(t, p);
            case WING -> new ComponentBlock.Wing(t, p);
            case NONE -> new ComponentBlock(t, p);
        };
    }

    private static SoundType soundFor(ComponentType t) {
        return switch (t) {
            case BALLOON_ENVELOPE, GAS_CELL -> SoundType.WOOL;
            case PILOT_SEAT, PASSENGER_SEAT, COPILOT_SEAT, SEAT_ECONOMY, SEAT_ECONOMY_PLUS, SEAT_PREMIUM, SEAT_BUSINESS, SEAT_FIRST -> SoundType.WOOL;
            case WHEEL -> SoundType.MUD_BRICKS;
            default -> SoundType.METAL;
        };
    }

    private static MapColor colorFor(ComponentType t) {
        return switch (t.category) {
            case WEAPON -> MapColor.COLOR_GREEN;
            case AERODYNAMICS -> MapColor.METAL;
            case LIFT -> MapColor.COLOR_RED;
            case FUEL -> MapColor.COLOR_ORANGE;
            default -> MapColor.COLOR_GRAY;
        };
    }

    public static ComponentBlock get(ComponentType t) {
        return COMPONENTS.get(t).get();
    }
}
