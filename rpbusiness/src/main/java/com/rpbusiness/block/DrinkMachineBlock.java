package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.core.ModItems;
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

public class DrinkMachineBlock extends KitchenBlock {
    public DrinkMachineBlock(Properties p) { super(p, Shapes.of(bx(2, 0, 3, 14, 16, 14))); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DrinkMachineBE(pos, state); }

    private static Component modeName(int m) {
        return Component.translatable(m == DrinkMachineBE.COLA ? "item.rpbusiness.cola" : "item.rpbusiness.coffee");
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrinkMachineBE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (stack.is(ModItems.COLA_SYRUP.get()) || stack.is(ModItems.COFFEE_BEANS.get())) {
            if (!level.isClientSide) {
                if (be.addRefill(stack.getItem())) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_refilled", be.charges(0), be.charges(1)), true);
                } else player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_full"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(ModItems.PAPER_CUP.get())) {
            if (!level.isClientSide) {
                if (be.start(be.mode())) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_filling", modeName(be.mode())), true);
                } else if (be.charges(be.mode()) <= 0)
                    player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_empty", modeName(be.mode())), true);
                else player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_busy"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrinkMachineBE be)) return InteractionResult.PASS;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!be.output().isEmpty()) player.getInventory().placeItemBackInInventory(be.take());
            else if (be.busy()) player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_busy"), true);
            else {
                be.toggleMode();
                player.displayClientMessage(Component.translatable("msg.rpbusiness.drink_mode", modeName(be.mode()), be.charges(0), be.charges(1)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
