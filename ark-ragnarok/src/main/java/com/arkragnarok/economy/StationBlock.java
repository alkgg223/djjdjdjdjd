package com.arkragnarok.economy;

import com.arkragnarok.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** a crafting station: right click opens its recipe screen */
public class StationBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape LOW = Block.box(1, 0, 1, 15, 7, 15);
    private static final VoxelShape MID = Block.box(1, 0, 1, 15, 14, 15);
    private static final VoxelShape FULL = Block.box(0.5, 0, 0.5, 15.5, 16, 15.5);
    public final Station station;

    public StationBlock(Station station, Properties p) {
        super(p);
        this.station = station;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (station) {
            case CAMPFIRE -> LOW;
            case FORGE, FABRICATOR -> FULL;
            default -> MID;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new StationMenu(id, inv, station, pos),
                    Component.translatable("block.arkragnarok." + station.id)), buf -> {
                buf.writeVarInt(station.ordinal());
                buf.writeBlockPos(pos);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
