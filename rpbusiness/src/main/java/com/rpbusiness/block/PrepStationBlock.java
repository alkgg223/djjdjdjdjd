package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
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

public class PrepStationBlock extends KitchenBlock {
    public PrepStationBlock(Properties p) { super(p, Shapes.of(bx(0, 0, 0, 16, 16, 16))); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PrepStationBE(pos, state); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PrepStationBE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (PrepStationBE.isPart(stack.getItem())) {
            if (!level.isClientSide) {
                if (be.add(stack)) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    Foods.Assembly a = be.match();
                    player.displayClientMessage(a != null
                            ? Component.translatable("msg.rpbusiness.prep_ready", Component.translatable("item.rpbusiness." + a.id()))
                            : Component.translatable("msg.rpbusiness.prep_added", be.items().size()), true);
                } else player.displayClientMessage(Component.translatable("msg.rpbusiness.prep_full"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PrepStationBE be)) return InteractionResult.PASS;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (be.isEmpty()) {
                player.displayClientMessage(Component.translatable("msg.rpbusiness.prep_hint"), true);
            } else if (!player.isShiftKeyDown() && be.match() != null) {
                ItemStack r = be.assemble();
                player.displayClientMessage(Component.translatable("msg.rpbusiness.assembled", r.getHoverName(), Foods.qualityOf(r)), true);
                player.getInventory().placeItemBackInInventory(r);
            } else {
                player.getInventory().placeItemBackInInventory(be.takeLast());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
