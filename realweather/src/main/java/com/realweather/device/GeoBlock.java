package com.realweather.device;

import com.realweather.space.Geology;
import com.realweather.space.Tiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A planetary material: fine dust that puffs up where you walk and drifts off (lunar regolith grey, Martian dust red,
 * the colour of the block itself), ice that sheds glittering crystals, sulfur and lava that smoke and glow, plumes of
 * vapour from cryovolcanic ice, and cloud decks you fall straight through.
 */
public class GeoBlock extends Block {
    private final int tile;

    public GeoBlock(Properties props, int tile) {
        super(props);
        this.tile = tile;
    }

    private Geology.Geo geo() {
        return Tiles.geo(tile);
    }

    private int material() {
        return Tiles.ALL[tile].material();
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        int m = material();
        if (level.isClientSide && (m == Tiles.M_DUST || m == Tiles.M_SNOW) && entity.getDeltaMovement().horizontalDistanceSqr() > 0.002
                && level.random.nextInt(3) == 0) {
            // Fine dust kicked up by each step (the block's own colour), slowly settling.
            for (int i = 0; i < 3; i++) {
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), entity.getX() + (level.random.nextDouble() - 0.5) * 0.6,
                        pos.getY() + 1.05, entity.getZ() + (level.random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        Geology.Geo g = geo();
        String type = g == null ? "" : Geology.TYPES[g.type()];
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + 1.02, z = pos.getZ() + random.nextDouble();
        int m = material();
        if (m == Tiles.M_DUST && random.nextInt(40) == 0) {
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, y + 0.3, z, 0, 0, 0);
        } else if ((m == Tiles.M_ICE || m == Tiles.M_SNOW) && random.nextInt(60) == 0) {
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.1, z, 0, 0.005, 0);
        } else if (type.equals("cryovolcanic") && random.nextInt(12) == 0) {
            // Vapour and ice grains venting from below.
            level.addParticle(ParticleTypes.CLOUD, x, y, z, 0, 0.06 + random.nextDouble() * 0.06, 0);
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, 0.12, 0);
        } else if (g != null && g.light() >= 5 && random.nextInt(14) == 0) {
            level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
        } else if (type.equals("volatile_deposit") && random.nextInt(50) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, x, y + 0.2, z, 0, 0.01, 0);
        } else if ((type.equals("crystalline") || type.equals("mineral_deposit") || type.equals("carbon")) && g.light() > 0 && random.nextInt(30) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.005, 0);
        } else if (m == Tiles.M_GAS && random.nextInt(25) == 0) {
            level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + random.nextDouble(), z, 0.01, 0, 0);
        }
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return material() == Tiles.M_GAS ? 1F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return material() == Tiles.M_GAS || super.propagatesSkylightDown(state, level, pos);
    }
}
