package com.rpbusiness.core;

import com.mojang.serialization.Codec;
import com.rpbusiness.RPBusiness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

public final class ModComponents {
    public static final DeferredRegister.DataComponents REG = DeferredRegister.createDataComponents(RPBusiness.MODID);

    /** Food quality 0..100 carried by prepared and cooked food. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> QUALITY =
            REG.registerComponentType("quality", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Grill: whether the item was flipped while cooking. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FLIPPED =
            REG.registerComponentType("flipped", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> CORNER_A =
            REG.registerComponentType("corner_a", b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> CORNER_B =
            REG.registerComponentType("corner_b", b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> STAFF_TARGET =
            REG.registerComponentType("staff_target", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STAFF_NAME =
            REG.registerComponentType("staff_name", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TOOL_MODE =
            REG.registerComponentType("tool_mode", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Bank card: linked account number and card holder. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CARD_ACCOUNT =
            REG.registerComponentType("card_account", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CARD_HOLDER =
            REG.registerComponentType("card_holder", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Parcel: delivery job id and destination. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PARCEL_JOB =
            REG.registerComponentType("parcel_job", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> PARCEL_DEST =
            REG.registerComponentType("parcel_dest", b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    private ModComponents() {}
}
