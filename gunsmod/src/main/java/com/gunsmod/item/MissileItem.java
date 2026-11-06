package com.gunsmod.item;

import com.gunsmod.Reg;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 0 = high explosive, 1 = cluster, 2 = incendiary (launch station);
 * 3 = thermobaric, 4 = bunker buster, 5 = Titan strategic missile (heavy launcher only).
 */
public class MissileItem extends Item {
    public static final int HE = 0, CLUSTER = 1, INCENDIARY = 2, THERMOBARIC = 3, BUNKER_BUSTER = 4, TITAN = 5;
    public static final int KINDS = 6;

    public final int kind;

    public MissileItem(int kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public static boolean isHeavy(int kind) {
        return kind >= THERMOBARIC;
    }

    public static Item byKind(int kind) {
        return switch (kind) {
            case CLUSTER -> Reg.MISSILE_CLUSTER.get();
            case INCENDIARY -> Reg.MISSILE_INCENDIARY.get();
            case THERMOBARIC -> Reg.MISSILE_THERMOBARIC.get();
            case BUNKER_BUSTER -> Reg.MISSILE_BUNKER_BUSTER.get();
            case TITAN -> Reg.MISSILE_TITAN.get();
            default -> Reg.MISSILE_HE.get();
        };
    }

    /** radius around the impact where friendly units would be hurt (used by soldiers choosing targets) */
    public static double dangerRadius(int kind) {
        return switch (kind) {
            case THERMOBARIC -> 32;
            case BUNKER_BUSTER -> 18;
            case TITAN -> 48;
            case CLUSTER -> 20;
            default -> 14;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gunsmod.desc.missile_" + kind).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(isHeavy(kind) ? "gunsmod.tip.missile_heavy" : "gunsmod.tip.missile_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
