package com.arkragnarok.economy;

import com.arkragnarok.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** planter box: berry bushes grow in it (3 stages); right click when ripe to harvest mejoberries and narcoberries */
public class CropPlotBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5.5, 16);

    public CropPlotBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (s.getValue(AGE) < 2 && level.getRawBrightness(pos.above(), 0) >= 8 && r.nextInt(4) == 0) {
            level.setBlock(pos, s.setValue(AGE, s.getValue(AGE) + 1), 2);
        }
    }

    public static boolean ripe(BlockState s) {
        return s.getBlock() instanceof CropPlotBlock && s.getValue(AGE) == 2;
    }

    /** harvest by a player or a survivor; returns what it gave */
    public static ItemStack[] harvest(Level level, BlockPos pos, RandomSource r) {
        BlockState s = level.getBlockState(pos);
        if (!ripe(s)) return new ItemStack[0];
        level.setBlock(pos, s.setValue(AGE, 0), 3);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 1.0f);
        return new ItemStack[]{new ItemStack(ModItems.MEJOBERRY.get(), 4 + r.nextInt(4)), new ItemStack(ModItems.NARCOBERRY.get(), 1 + r.nextInt(3)),
                new ItemStack(ModEconomy.FIBER.get(), 1 + r.nextInt(2))};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!ripe(s)) return InteractionResult.PASS;
        if (!level.isClientSide) for (ItemStack st : harvest(level, pos, level.random)) if (!player.getInventory().add(st)) player.drop(st, false);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
