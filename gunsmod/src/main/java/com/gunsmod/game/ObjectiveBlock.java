package com.gunsmod.game;

import com.gunsmod.block.FacingBlock;
import com.gunsmod.entity.BotTeam;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The pieces a battle is played over: a capture point (flag mast), a capture-the-flag stand, a target (command post /
 * HQ, also the demolition sites), a convoy destination, and the mission control table that starts and ends a battle.
 * Sneak + use changes the side; use shows its state (the table opens its screen).
 */
public class ObjectiveBlock extends FacingBlock implements EntityBlock {
    public enum Kind { POINT, FLAG, TARGET, DESTINATION, CONTROL }

    public final Kind kind;

    public ObjectiveBlock(Properties props, Kind kind) {
        super(props, kind == Kind.CONTROL ? box(0, 0, 0, 16, 10, 16) : kind == Kind.TARGET ? box(1, 0, 2, 15, 9, 15) : box(3, 0, 3, 13, 3, 13));
        this.kind = kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new ObjectiveBlock(p, kind));
    }

    /** during a battle its objectives cannot be dug out (the table can: that ends the battle) */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (kind != Kind.CONTROL && level instanceof net.minecraft.server.level.ServerLevel sl && !player.getAbilities().instabuild) {
            Match m = MatchLogic.running(sl);
            if (m != null && m.covers(pos)) return 0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObjectiveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == com.gunsmod.Reg.OBJECTIVE_BE.get() ? (l, p, s, be) -> ((ObjectiveBlockEntity) be).tick() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ObjectiveBlockEntity be)) return;
        BotTeam team = placer instanceof Player p ? BotTeam.of(p) : null;
        if (kind == Kind.POINT) {
            be.setLabel(Objectives.nextLabel(level, pos));
            be.setOwner(null);
        } else {
            be.setOwner(team == null ? BotTeam.GREEN : team);
        }
        if (placer instanceof Player p) {
            p.displayClientMessage(Component.translatable("gunsmod.obj.placed." + kind.name().toLowerCase(), String.valueOf(be.label())).withStyle(ChatFormatting.AQUA), true);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ObjectiveBlockEntity be)) return InteractionResult.PASS;
        if (kind == Kind.CONTROL) {
            if (player instanceof ServerPlayer sp) MatchLogic.openControl(sp, pos);
            return InteractionResult.SUCCESS;
        }
        Match m = Match.get((net.minecraft.server.level.ServerLevel) level);
        if (player.isShiftKeyDown()) {
            if (m.running && m.covers(pos)) {
                player.displayClientMessage(Component.translatable("gunsmod.obj.locked").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            BotTeam o = be.owner();
            BotTeam next = kind == Kind.POINT ? (o == null ? BotTeam.GREEN : o == BotTeam.GREEN ? BotTeam.RED : null) : (o == BotTeam.RED ? BotTeam.GREEN : BotTeam.RED);
            be.setOwner(next);
            level.playSound(null, pos, com.gunsmod.Reg.sound("arm_switch"), SoundSource.BLOCKS, 1f, 1f);
        }
        player.displayClientMessage(be.status(), true);
        return InteractionResult.SUCCESS;
    }
}
