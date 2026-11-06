package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class JeepItem extends Item {
    public final BotTeam team;

    public JeepItem(BotTeam team, Properties props) {
        super(props);
        this.team = team;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide) {
            BlockPos p = ctx.getClickedPos().relative(ctx.getClickedFace());
            JeepEntity j = new JeepEntity(Reg.JEEP.get(), level);
            j.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ctx.getRotation(), 0);
            j.setYHeadRot(ctx.getRotation());
            j.setYBodyRot(ctx.getRotation());
            j.setTeam(team);
            if (!level.noCollision(j, j.getBoundingBox())) {
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("gunsmod.msg.no_room"), true);
                return InteractionResult.FAIL;
            }
            level.addFreshEntity(j);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.desc.jeep").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.jeep1").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip.jeep2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
