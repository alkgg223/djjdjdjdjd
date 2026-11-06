package com.gunsmod.block;

import com.gunsmod.menu.WorkbenchMenu;
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
import net.minecraft.world.phys.shapes.Shapes;

/** Weapon workbench: crafts guns, ammo and attachments with a 3D preview UI. */
public class WorkbenchBlock extends FacingBlock {
    public WorkbenchBlock(Properties props) {
        super(props, Shapes.or(box(0, 0, 0, 16, 14, 16), box(0, 14, 14.5, 16, 16, 15.6)));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(WorkbenchBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new WorkbenchMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.gunsmod.weapon_workbench")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
