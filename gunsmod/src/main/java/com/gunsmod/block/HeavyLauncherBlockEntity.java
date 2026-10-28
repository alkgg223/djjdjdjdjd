package com.gunsmod.block;

import com.gunsmod.Reg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The 3 x 5 heavy launcher (block entity of the centre block). Fires the thermobaric, bunker buster and Titan missiles. */
public class HeavyLauncherBlockEntity extends LauncherBlockEntity {
    /** erector hinge in half-scale model units (see tools/blocks.py HEAVY_HINGE) */
    public static final float HINGE_Y = 12f, HINGE_Z = 23.5f;
    /** missile axis distance from the hinge on the erector, and the missile render scale */
    public static final float MISSILE_Z = 12f, MISSILE_SCALE = 0.82f;

    public HeavyLauncherBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.HEAVY_LAUNCHER_BE.get(), pos, state);
    }

    @Override
    public boolean isHeavy() {
        return true;
    }

    @Override
    public int erectTime() {
        return 90;
    }

    @Override
    public int igniteTime() {
        return 50;
    }

    @Override
    public int lowerTime() {
        return 80;
    }

    /** the standing missile: axis 2.44 blocks behind the centre block, render centre 3.75 blocks up */
    @Override
    public Vec3 launchPoint() {
        Direction f = facing();
        double back = (HINGE_Z + MISSILE_Z - 8f - 8f) / 8.0;
        return Vec3.atBottomCenterOf(worldPosition).add(-f.getStepX() * back, HINGE_Y / 8.0 + 22 * MISSILE_SCALE * 2 / 16.0, -f.getStepZ() * back);
    }

    @Override
    public Vec3 exhaustPoint() {
        Direction f = facing();
        double back = (HINGE_Z + MISSILE_Z - 8f - 8f) / 8.0;
        return Vec3.atBottomCenterOf(worldPosition).add(-f.getStepX() * back, 0.9, -f.getStepZ() * back);
    }
}
