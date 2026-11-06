package com.arkragnarok.building;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** use on a doorframe to hang the door; right click the door to open / close it */
public class DoorItem extends Item {
    public final Tier tier;

    public DoorItem(Tier tier, Properties p) {
        super(p);
        this.tier = tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!Placement.installDoor(level, ctx.getClickedPos(), tier)) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("msg.arkragnarok.need_doorframe"), true);
            return InteractionResult.FAIL;
        }
        level.playSound(null, ctx.getClickedPos(), tier.close, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (ctx.getPlayer() != null) ctx.getItemInHand().consume(1, ctx.getPlayer());
        return InteractionResult.CONSUME;
    }
}
