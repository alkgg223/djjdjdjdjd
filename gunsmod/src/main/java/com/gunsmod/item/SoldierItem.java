package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** Deploys a soldier of the given team with a random team weapon. */
public class SoldierItem extends Item {
    public final BotTeam team;

    public SoldierItem(BotTeam team, Properties props) {
        super(props);
        this.team = team;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel sl) {
            BlockPos p = ctx.getClickedPos().relative(ctx.getClickedFace());
            BotEntity b = new BotEntity(Reg.BOT.get(), sl);
            b.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ctx.getRotation() + 180f, 0);
            b.setup(team, sl.random, null);
            sl.addFreshEntity(b);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.desc." + (team == BotTeam.GREEN ? "green_soldier" : "red_soldier")).withStyle(team.chat));
        tip.add(Component.translatable("gunsmod.tip.soldier_controls").withStyle(ChatFormatting.DARK_GRAY));
    }
}
