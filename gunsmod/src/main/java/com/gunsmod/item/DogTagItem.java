package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Soldier dog tag: use it on a soldier to save its loadout (team, face, weapon with attachments, armour, orders).
 * Put it into a deployment pad, or use it on the ground to deploy a copy.
 */
public class DogTagItem extends Item {
    public DogTagItem(Properties props) {
        super(props);
    }

    public static boolean hasData(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains("gunsmod_bot");
    }

    public static CompoundTag data(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag().getCompound("gunsmod_bot");
    }

    public static void save(ItemStack s, BotEntity bot, Player player) {
        CompoundTag root = new CompoundTag();
        root.put("gunsmod_bot", bot.saveLoadout());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        player.displayClientMessage(Component.translatable("gunsmod.msg.tag_saved", bot.getDisplayName()), true);
        bot.level().playSound(null, bot.blockPosition(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1f);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasData(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack s = ctx.getItemInHand();
        if (!hasData(s)) return InteractionResult.PASS;
        if (ctx.getLevel() instanceof ServerLevel sl) {
            BlockPos p = ctx.getClickedPos().relative(ctx.getClickedFace());
            BotEntity b = new BotEntity(Reg.BOT.get(), sl);
            b.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ctx.getRotation() + 180f, 0);
            b.applyLoadout(data(s));
            if (ctx.getPlayer() != null) b.owner = ctx.getPlayer().getUUID();
            b.home = p;
            sl.addFreshEntity(b);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                // a deployed tag is spent; keep the data on creative
                s.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (!hasData(stack)) {
            tip.add(Component.translatable("gunsmod.tip.tag_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag t = data(stack);
        BotTeam team = BotTeam.byId(t.getInt("team"));
        if (t.contains("name")) tip.add(Component.literal(t.getString("name")).withStyle(team.chat));
        tip.add(Component.translatable("gunsmod.team." + team.name().toLowerCase()).withStyle(team.chat));
        CompoundTag eq = t.getCompound("equipment");
        if (eq.contains("mainhand")) {
            String id = eq.getCompound("mainhand").getString("id");
            tip.add(Component.translatable("gunsmod.tip.tag_weapon", Component.translatable("item." + id.replace(':', '.'))).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("gunsmod.tip.tag_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
