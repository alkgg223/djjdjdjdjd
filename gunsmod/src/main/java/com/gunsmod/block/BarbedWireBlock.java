package com.gunsmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Slows and cuts anything walking through it. */
public class BarbedWireBlock extends FacingBlock {
    public BarbedWireBlock(Properties props) {
        super(props, box(0, 0, 1.5, 16, 12, 14.5));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(BarbedWireBlock::new);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity le) || entity instanceof com.gunsmod.entity.JeepEntity) return;
        entity.makeStuckInBlock(state, new Vec3(0.35, 0.8, 0.35));
        if (!level.isClientSide && (le.xOld != le.getX() || le.zOld != le.getZ()) && level.getGameTime() % 10 == 0) {
            entity.hurt(level.damageSources().cactus(), 1.5f);
        }
    }
}
