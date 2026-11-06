package com.gunsmod.item;

import com.gunsmod.world.OrchardBuilder;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.joml.Vector3f;

/** Orchard battlefield deployer: use once to see the 300 x 500 outline, again on the same spot to build it. */
public class OrchardToolItem extends Item {
    private static final Map<UUID, BlockPos> PENDING = new HashMap<>();
    private static final Map<UUID, Long> PENDING_TIME = new HashMap<>();

    public OrchardToolItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (OrchardBuilder.busy()) {
            player.displayClientMessage(Component.translatable("gunsmod.msg.orchard_busy"), true);
            return InteractionResult.FAIL;
        }
        BlockPos center = ctx.getClickedPos();
        Direction facing = player.getDirection();
        long now = sl.getGameTime();
        BlockPos prev = PENDING.get(player.getUUID());
        Long t = PENDING_TIME.get(player.getUUID());
        if (prev != null && t != null && now - t < 400 && prev.closerThan(center, 4.5)) {
            PENDING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("gunsmod.msg.orchard_building"), false);
            OrchardBuilder.start(sl, prev, facing, sp);
            if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        PENDING.put(player.getUUID(), center);
        PENDING_TIME.put(player.getUUID(), now);
        // outline: the near part of the border, the far end is too far away to be seen anyway
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.3f, 1f, 0.3f), 2.5f);
        Direction right = facing.getClockWise();
        int hw = OrchardBuilder.W / 2, hl = OrchardBuilder.L / 2;
        for (int i = -hw; i <= hw; i += 3) {
            for (int z : new int[]{-hl, hl}) {
                BlockPos p = center.offset(right.getStepX() * i + facing.getStepX() * z, 1, right.getStepZ() * i + facing.getStepZ() * z);
                sl.sendParticles(sp, dust, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.1, 0.6, 0.1, 0);
            }
        }
        for (int i = -hl; i <= hl; i += 3) {
            for (int x : new int[]{-hw, hw}) {
                BlockPos p = center.offset(right.getStepX() * x + facing.getStepX() * i, 1, right.getStepZ() * x + facing.getStepZ() * i);
                sl.sendParticles(sp, dust, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.1, 0.6, 0.1, 0);
            }
        }
        player.displayClientMessage(Component.translatable("gunsmod.msg.orchard_confirm").withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.desc.orchard_tool").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.orchard_tool").withStyle(ChatFormatting.DARK_GRAY));
    }
}
