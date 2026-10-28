package com.gunsmod.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Red fuel barrel that explodes when shot, burnt or caught in another explosion. */
public class ExplosiveBarrelBlock extends FacingBlock {
    public ExplosiveBarrelBlock(Properties props) {
        super(props, box(1.6, 0, 1.6, 14.4, 16, 14.4));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ExplosiveBarrelBlock::new);
    }

    public void detonate(Level level, BlockPos pos, @Nullable Entity cause) {
        if (level.isClientSide || !(level.getBlockState(pos).getBlock() instanceof ExplosiveBarrelBlock)) return;
        level.removeBlock(pos, false);
        level.explode(cause, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.4f, true, Level.ExplosionInteraction.BLOCK);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        detonate(level, hit.getBlockPos(), projectile.getOwner());
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            // chain reaction one tick later so the current explosion finishes first
            level.explode(explosion.getDirectSourceEntity(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0f, true, Level.ExplosionInteraction.BLOCK);
        }
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction, @Nullable LivingEntity igniter) {
        detonate(level, pos, igniter);
    }

    @Override
    public boolean isFlammable(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }
}
