package com.gunsmod.block;

import com.gunsmod.menu.ModTableMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Gunsmith table: install scopes, suppressors, grips, lasers and extended magazines. */
public class ModTableBlock extends FacingBlock {
    public ModTableBlock(Properties props) {
        super(props, box(0, 0, 0, 16, 13, 16));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ModTableBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ModTableMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.gunsmod.modification_table")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
