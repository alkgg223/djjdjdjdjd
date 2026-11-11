package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.food.Foods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;

import static com.rpbusiness.block.Shapes.bx;

public class CookerBlock extends KitchenBlock {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public final Foods.Cooker cooker;

    public CookerBlock(Properties p, Foods.Cooker cooker) {
        super(p, shapeFor(cooker));
        this.cooker = cooker;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    private static EnumMap<Direction, net.minecraft.world.phys.shapes.VoxelShape> shapeFor(Foods.Cooker c) {
        return switch (c) {
            case GRILL -> Shapes.of(bx(0, 0, 0, 16, 15, 16));
            case FRYER -> Shapes.of(bx(1, 0, 1, 15, 15, 15));
            case OVEN -> Shapes.of(bx(0, 0, 0, 16, 16, 16));
            case POT -> Shapes.of(bx(3, 0, 3, 13, 9, 13));
        };
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CookerBE(pos, state); }

    /** For the 2-spot grill: left (0) or right (1) from the player's point of view, using the hit location. */
    private int slotFromHit(BlockState state, BlockPos pos, Vec3 hit, CookerBE be, boolean wantOccupied) {
        if (be.size() == 1) return 0;
        Direction f = state.getValue(FACING);
        double lx = hit.x - pos.getX(), lz = hit.z - pos.getZ();
        double side = switch (f) {
            case NORTH -> 1 - lx; case SOUTH -> lx; case WEST -> lz; default -> 1 - lz;
        };
        int s = side < 0.5 ? 0 : 1;
        if (wantOccupied && !be.occupied(s)) s = 1 - s;
        if (!wantOccupied && be.occupied(s)) s = 1 - s;
        return s;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CookerBE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (stack.is(ModItems.SPATULA.get())) {
            if (!level.isClientSide) {
                int s = slotFromHit(state, pos, hit.getLocation(), be, true);
                if (!be.flip(s)) {
                    int o = 1 - s;
                    if (be.size() == 1 || !be.flip(o))
                        player.displayClientMessage(Component.translatable(be.anyOccupied() ? "msg.rpbusiness.cant_flip" : "msg.rpbusiness.cooker_empty"), true);
                    else player.displayClientMessage(Component.translatable("msg.rpbusiness.flipped"), true);
                } else player.displayClientMessage(Component.translatable("msg.rpbusiness.flipped"), true);
                player.swing(hand, true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (be.canCook(stack.getItem())) {
            if (be.freeSlot() < 0) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("msg.rpbusiness.cooker_full"), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!level.isClientSide) {
                be.insert(stack);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CookerBE be)) return InteractionResult.PASS;
        com.rpbusiness.server.BusinessManager.touch(level, pos);
        if (!be.anyOccupied()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("msg.rpbusiness.cooker_hint_" + cooker.name().toLowerCase()), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            int s = slotFromHit(state, pos, hit.getLocation(), be, true);
            ItemStack r = be.take(s);
            player.displayClientMessage(Component.translatable("msg.rpbusiness.took_food", r.getHoverName(), Foods.qualityOf(r)), true);
            player.getInventory().placeItemBackInInventory(r);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (!state.getValue(LIT)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + (cooker == Foods.Cooker.POT ? 0.6 : 1.0), z = pos.getZ() + 0.5;
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x + (r.nextDouble() - 0.5) * 0.5, y, z + (r.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
        if (cooker == Foods.Cooker.POT || cooker == Foods.Cooker.FRYER) {
            if (r.nextInt(2) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, x + (r.nextDouble() - 0.5) * 0.4, y - 0.1, z + (r.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
        }
        if (cooker == Foods.Cooker.GRILL && r.nextInt(6) == 0)
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.7, y - 0.15, z + (r.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
    }
}
