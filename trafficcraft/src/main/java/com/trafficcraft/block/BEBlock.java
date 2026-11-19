package com.trafficcraft.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Facing block with a block entity (and optional server ticker). */
public class BEBlock extends FacingBlock implements EntityBlock {
    private final Supplier<BlockEntityType<?>> type;
    private final boolean ticks;
    public final String kind;

    public BEBlock(Properties p, double[][] boxes, boolean noCollide, String kind, Supplier<BlockEntityType<?>> type, boolean ticks) {
        super(p, boxes, noCollide);
        this.type = type;
        this.ticks = ticks;
        this.kind = kind;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        return BlockInteractions.use(kind, s, level, pos, player);
    }

    @Override
    protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState ns, boolean moving) {
        if (!s.is(ns.getBlock()) && level.getBlockEntity(pos) instanceof BarrierGateBE g) g.removeArm();
        super.onRemove(s, level, pos, ns, moving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> t) {
        if (!ticks) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof TickingBE tb) {
                if (lvl.isClientSide) tb.clientTick();
                else tb.serverTick();
            }
        };
    }

    public interface TickingBE {
        void serverTick();

        default void clientTick() {
        }
    }
}
