package com.gunsmod.block.airdef;

import com.gunsmod.Reg;
import com.gunsmod.block.FacingBlock;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.item.AirLinkToolItem;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
 * An air defence radar or launcher. Placed, it takes its placer's side and links itself with radars (or launchers) of
 * that side close by; the link tool links and unlinks by hand. Sneak + use with an empty hand switches the side.
 */
public class AirDefenseBlock extends FacingBlock implements EntityBlock {
    /** how far apart a radar and a launcher can be linked */
    public static final double LINK_RANGE = 96, AUTO_LINK = 48;
    @Nullable public final RadarType radar;
    @Nullable public final LauncherType launcher;

    public AirDefenseBlock(Properties props, @Nullable RadarType radar, @Nullable LauncherType launcher) {
        super(props, box(0, 0, 0, 16, 10, 16));
        this.radar = radar;
        this.launcher = launcher;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new AirDefenseBlock(p, radar, launcher));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    public String id() {
        return radar != null ? radar.id : launcher != null ? launcher.id : "airdef";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return radar != null ? new RadarBlockEntity(pos, state) : new DefenseBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == Reg.RADAR_BE.get()) return (l, p, s, be) -> ((RadarBlockEntity) be).tick();
        if (type == Reg.DEFENSE_BE.get()) return (l, p, s, be) -> ((DefenseBlockEntity) be).tick();
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BotTeam team = placer instanceof Player p ? BotTeam.of(p) : BotTeam.GREEN;
        if (team == null) team = BotTeam.GREEN;
        BlockEntity be = level.getBlockEntity(pos);
        int linked = 0;
        if (be instanceof RadarBlockEntity r) {
            r.setTeam(team);
            for (DefenseBlockEntity d : AirTracker.launchersNear(level, pos, AUTO_LINK)) {
                if (d.team() == team && d.links().isEmpty() && d.toggleLink(pos)) linked++;
            }
        } else if (be instanceof DefenseBlockEntity d) {
            d.setTeam(team);
            for (RadarBlockEntity r : AirTracker.radarsNear(level, pos, AUTO_LINK)) {
                if (r.team() == team && linked < 2 && d.toggleLink(r.getBlockPos())) linked++;
            }
        }
        if (placer instanceof Player p) {
            p.displayClientMessage(Component.translatable(linked > 0 ? "gunsmod.msg.ad_autolinked" : "gunsmod.msg.ad_placed", linked)
                    .withStyle(linked > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof AirLinkToolItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RadarBlockEntity) {
            AirLinkToolItem.select(stack, pos);
            player.displayClientMessage(Component.translatable("gunsmod.msg.ad_radar_selected", Component.translatable(state.getBlock().getDescriptionId()))
                    .withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, Reg.sound("ui_beep"), SoundSource.BLOCKS, 1f, 1.4f);
        } else if (be instanceof DefenseBlockEntity d) {
            if (player.isShiftKeyDown()) {
                d.clearLinks();
                player.displayClientMessage(Component.translatable("gunsmod.msg.ad_cleared").withStyle(ChatFormatting.YELLOW), true);
                return ItemInteractionResult.SUCCESS;
            }
            BlockPos r = AirLinkToolItem.selected(stack);
            if (r == null || !(level.getBlockEntity(r) instanceof RadarBlockEntity radar)) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.ad_pick_radar").withStyle(ChatFormatting.RED), true);
            } else if (r.distSqr(pos) > LINK_RANGE * LINK_RANGE) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.ad_too_far", (int) LINK_RANGE).withStyle(ChatFormatting.RED), true);
            } else if (radar.team() != d.team()) {
                player.displayClientMessage(Component.translatable("gunsmod.msg.ad_other_side").withStyle(ChatFormatting.RED), true);
            } else {
                boolean was = d.links().contains(r);
                if (d.toggleLink(r)) {
                    player.displayClientMessage(Component.translatable(was ? "gunsmod.msg.ad_unlinked" : "gunsmod.msg.ad_linked", d.links().size())
                            .withStyle(was ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
                    level.playSound(null, pos, Reg.sound("ui_confirm"), SoundSource.BLOCKS, 1f, was ? 0.7f : 1.1f);
                } else {
                    player.displayClientMessage(Component.translatable("gunsmod.msg.ad_full", DefenseBlockEntity.MAX_LINKS).withStyle(ChatFormatting.RED), true);
                }
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (player.isShiftKeyDown()) {
            if (be instanceof RadarBlockEntity r) r.setTeam(r.team().enemy());
            else if (be instanceof DefenseBlockEntity d) d.setTeam(d.team().enemy());
            BotTeam t = be instanceof RadarBlockEntity r ? r.team() : be instanceof DefenseBlockEntity d ? d.team() : BotTeam.GREEN;
            player.displayClientMessage(Component.translatable("gunsmod.msg.ad_side", Component.translatable("gunsmod.team." + t.name().toLowerCase())).withStyle(t.chat),
                    true);
            level.playSound(null, pos, Reg.sound("arm_switch"), SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.SUCCESS;
        }
        if (be instanceof RadarBlockEntity r) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.ad_radar_status", Math.round(r.type.detect * 100), (int) r.type.reach(), r.shownTracks()), true);
            // the operator's scope: the client opens it on the first picture
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, r.picture());
        } else if (be instanceof DefenseBlockEntity d) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.ad_launcher_status", Math.round(d.type.accuracy * 100), (int) d.type.range,
                    d.links().size()), true);
        }
        return InteractionResult.SUCCESS;
    }
}
