package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.food.Foods;
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

/** Place an ingredient, then use the kitchen knife on it until it is cut. */
public class CuttingBoardBlock extends KitchenBlock {
    public CuttingBoardBlock(Properties p) { super(p, Shapes.of(bx(2, 0, 3, 14, 1.5, 13))); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CuttingBoardBE(pos, state); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CuttingBoardBE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (stack.is(ModItems.KITCHEN_KNIFE.get())) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (be.isChoppable()) {
                player.swing(hand, true);
                if (be.chop()) player.displayClientMessage(Component.translatable("msg.rpbusiness.chopped", be.item().getHoverName()), true);
                else {
                    Foods.Chop c = Foods.chopFor(be.item().getItem());
                    player.displayClientMessage(Component.translatable("msg.rpbusiness.chopping", be.chops(), c.chops()), true);
                }
            } else if (be.isEmpty()) {
                player.displayClientMessage(Component.translatable("msg.rpbusiness.board_empty"), true);
            } else {
                player.displayClientMessage(Component.translatable("msg.rpbusiness.board_done"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (be.canPlace(stack)) {
            if (!level.isClientSide) {
                be.place(stack);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!stack.isEmpty() && be.isEmpty() && !level.isClientSide)
            player.displayClientMessage(Component.translatable("msg.rpbusiness.not_choppable"), true);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CuttingBoardBE be) || be.isEmpty()) return InteractionResult.PASS;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (be.isChoppable()) player.displayClientMessage(Component.translatable("msg.rpbusiness.need_knife"), true);
            player.getInventory().placeItemBackInInventory(be.take());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
