package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.building.DoorItem;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.building.PieceBlockEntity;
import com.arkragnarok.building.PieceItem;
import com.arkragnarok.building.PieceType;
import com.arkragnarok.building.Tier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** thatch / wood / stone structure pieces: one block per tier, an item per piece, a door per tier */
public final class ModBuilding {
    private ModBuilding() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArkRagnarok.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArkRagnarok.MODID);

    public static final Map<Tier, DeferredBlock<PieceBlock>> PIECE_BLOCKS = new EnumMap<>(Tier.class);
    public static final Map<Tier, Map<PieceType, DeferredItem<Item>>> PIECE_ITEMS = new EnumMap<>(Tier.class);
    public static final Map<Tier, DeferredItem<Item>> DOORS = new EnumMap<>(Tier.class);

    static {
        for (Tier t : Tier.values()) {
            DeferredBlock<PieceBlock> b = BLOCKS.register(t.id + "_piece", () -> new PieceBlock(t, BlockBehaviour.Properties.of()
                    .strength(t.hardness, t.resistance).sound(t.sound).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
            PIECE_BLOCKS.put(t, b);
            Map<PieceType, DeferredItem<Item>> items = new EnumMap<>(PieceType.class);
            for (PieceType pt : PieceType.values()) {
                items.put(pt, ModItems.ITEMS.register(t.id + "_" + pt.getSerializedName(),
                        () -> new PieceItem(b::get, pt, new Item.Properties().stacksTo(16))));
            }
            PIECE_ITEMS.put(t, items);
            DOORS.put(t, ModItems.ITEMS.register(t.id + "_door", () -> new DoorItem(t, new Item.Properties().stacksTo(16))));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PieceBlockEntity>> PIECE_BE = BLOCK_ENTITIES.register("piece",
            () -> BlockEntityType.Builder.of(PieceBlockEntity::new, PIECE_BLOCKS.values().stream().map(DeferredBlock::get)
                    .toArray(Block[]::new)).build(null));

    /** loads the class so its items are added to ModItems.ITEMS before that register is bound to the bus */
    public static void init() {
    }

    public static ItemStack pieceItem(Tier t, PieceType type) {
        return new ItemStack(PIECE_ITEMS.get(t).get(type).get());
    }

    public static ItemStack doorItem(Tier t) {
        return new ItemStack(DOORS.get(t).get());
    }
}
