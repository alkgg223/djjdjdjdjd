package com.gunsmod.item;

import com.gunsmod.gun.Attachment;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AttachmentItem extends Item {
    public final Attachment attachment;

    public AttachmentItem(Attachment attachment, Properties props) {
        super(props);
        this.attachment = attachment;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.slot." + attachment.slot.name().toLowerCase()).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("gunsmod.desc." + attachment.id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gunsmod.tip.attach_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
