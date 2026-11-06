package com.gunsmod.item;

import com.gunsmod.world.ArenaBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.joml.Vector3f;

/** Battlefield deployer: use on the ground to preview a 50x50 battlefield, use again on the same spot to build it. */
public class ArenaToolItem extends Item {
    private static final Map<UUID, BlockPos> PENDING = new HashMap<>();
    private static final Map<UUID, Long> PENDING_TIME = new HashMap<>();

    public ArenaToolItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos center = ctx.getClickedPos();
        Direction facing = player.getDirection();
        long now = sl.getGameTime();
        BlockPos prev = PENDING.get(player.getUUID());
        Long t = PENDING_TIME.get(player.getUUID());
        if (prev != null && t != null && now - t < 300 && prev.closerThan(center, 3.5)) {
            PENDING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("gunsmod.msg.arena_building"), true);
            ArenaBuilder.build(sl, prev, facing, player);
            if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        PENDING.put(player.getUUID(), center);
        PENDING_TIME.put(player.getUUID(), now);
        // outline preview with particles
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.1f), 2.0f);
        int h = ArenaBuilder.SIZE / 2;
        for (int i = -h; i <= h; i += 2) {
            for (int[] p : new int[][]{{i, -h}, {i, h}, {-h, i}, {h, i}}) {
                sl.sendParticles((net.minecraft.server.level.ServerPlayer) player, dust, true, center.getX() + p[0] + 0.5, center.getY() + 1.5, center.getZ() + p[1] + 0.5, 3, 0.1, 0.6, 0.1, 0);
            }
        }
        player.displayClientMessage(Component.translatable("gunsmod.msg.arena_confirm").withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.desc.arena_tool").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.arena_tool").withStyle(ChatFormatting.DARK_GRAY));
    }
}
