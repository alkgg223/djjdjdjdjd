package com.realweather.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Still flood water. It never flows (so it cannot wash away crops or torches); the flood manager decides
 * where it rises and where it drains. Leftover water evaporates by itself once the rain is over.
 * The full block is used under deeper floods; the top of a flood is the shallow {@link Flowing} sheet.
 */
public class FloodFluid extends BaseFlowingFluid.Source {
    /** Height of the shallow surface sheet, in ninths of a block. */
    public static final int SHEET = 3;

    public FloodFluid(BaseFlowingFluid.Properties properties) {
        super(properties);
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (level instanceof ServerLevel server) {
            FloodManager.onRandomTick(server, pos, random);
        }
    }

    /** A shallow sheet of standing flood water; like the source, it never flows on its own. */
    public static class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        public void tick(Level level, BlockPos pos, FluidState state) {
        }

        @Override
        protected boolean isRandomlyTicking() {
            return true;
        }

        @Override
        protected void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
            if (level instanceof ServerLevel server) {
                FloodManager.onRandomTick(server, pos, random);
            }
        }
    }
}
