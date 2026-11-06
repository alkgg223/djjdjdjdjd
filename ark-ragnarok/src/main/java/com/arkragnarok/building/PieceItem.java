package com.arkragnarok.building;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** places a 3x3 structure piece (snaps to foundations, walls and ceilings) */
public class PieceItem extends Item {
    private final Supplier<PieceBlock> block;
    public final PieceType type;

    public PieceItem(Supplier<PieceBlock> block, PieceType type, Properties p) {
        super(p);
        this.block = block;
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Placement.Plan plan = Placement.plan(level, type, ctx.getClickedPos(), ctx.getClickedFace(), ctx.getClickLocation(),
                ctx.getHorizontalDirection());
        if (plan == null || !Placement.place(level, block.get(), type, plan)) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("msg.arkragnarok.no_room"), true);
            return InteractionResult.FAIL;
        }
        PieceBlock b = block.get();
        level.playSound(null, plan.master(), b.tier.sound.getPlaceSound(), SoundSource.BLOCKS, 1.2f, 0.8f);
        if (ctx.getPlayer() != null) ctx.getItemInHand().consume(1, ctx.getPlayer());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.arkragnarok.piece." + type.getSerializedName() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
