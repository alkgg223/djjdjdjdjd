package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.food.Dish;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import static com.rpbusiness.block.Shapes.bx;

public class ServingCounterBlock extends KitchenBlock {
    public ServingCounterBlock(Properties p) { super(p, Shapes.of(bx(0, 0, 0, 16, 16, 16))); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ServingCounterBE(pos, state); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ServingCounterBE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (Dish.servedBy(stack.getItem()) != null) {
            if (!level.isClientSide) {
                if (be.insert(stack)) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    com.rpbusiness.server.BusinessManager.onDishOnCounter(level, pos);
                } else player.displayClientMessage(Component.translatable("msg.rpbusiness.counter_full"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ServingCounterBE be) || be.isEmpty()) return InteractionResult.PASS;
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) player.getInventory().placeItemBackInInventory(be.takeLast());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
