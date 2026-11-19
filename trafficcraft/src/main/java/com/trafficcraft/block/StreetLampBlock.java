package com.trafficcraft.block;

import com.trafficcraft.TCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 5-block street lamp (base, 3 pole sections, head with arm). The head lights up automatically at night. */
public class StreetLampBlock extends Block {
    public enum Part implements StringRepresentable {
        BASE, POLE, HEAD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = FacingBlock.FACING;
    private static final VoxelShape BASE = Block.box(5, 0, 5, 11, 16, 11), POLE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
    public static final int HEIGHT = 5;

    public StreetLampBlock(Properties p) {
        super(p.lightLevel(s -> s.getValue(PART) == Part.HEAD && s.getValue(LIT) ? 15 : 0));
        registerDefaultState(stateDefinition.any().setValue(PART, Part.BASE).setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(PART, LIT, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(PART) == Part.BASE ? BASE : POLE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level l = ctx.getLevel();
        BlockPos p = ctx.getClickedPos();
        for (int i = 1; i < HEIGHT; i++) if (!l.getBlockState(p.above(i)).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState s, LivingEntity by, ItemStack stack) {
        for (int i = 1; i < HEIGHT; i++) {
            Part part = i == HEIGHT - 1 ? Part.HEAD : Part.POLE;
            level.setBlock(pos.above(i), s.setValue(PART, part), 3);
        }
        if (!level.isClientSide) level.scheduleTick(pos.above(HEIGHT - 1), this, 20);
    }

    @Override
    protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (s.getValue(PART) == Part.HEAD && !level.isClientSide) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (s.getValue(PART) != Part.HEAD) return;
        boolean night = level.getSkyDarken() >= 4 || level.isThundering();
        boolean lit = TCConfig.STREET_LAMPS_AUTO.get() ? night : s.getValue(LIT);
        if (lit != s.getValue(LIT)) level.setBlock(pos, s.setValue(LIT, lit), 3);
        level.scheduleTick(pos, this, 100 + r.nextInt(40));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState s, Player player) {
        int idx = switch (s.getValue(PART)) {
            case BASE -> 0;
            case HEAD -> HEIGHT - 1;
            default -> -1;
        };
        BlockPos base = pos;
        if (idx < 0) {
            base = pos;
            while (level.getBlockState(base.below()).is(this) && level.getBlockState(base).getValue(PART) != Part.BASE) base = base.below();
        } else base = pos.below(idx);
        for (int i = 0; i < HEIGHT; i++) {
            BlockPos q = base.above(i);
            if (!q.equals(pos) && level.getBlockState(q).is(this)) level.setBlock(q, Blocks.AIR.defaultBlockState(), 35);
        }
        return super.playerWillDestroy(level, pos, s, player);
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        return true;
    }
}
